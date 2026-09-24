package com.liveon;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Owns the retry lifecycle for drop and pet notifications. */
@Slf4j
final class DropDeliveryClient implements AutoCloseable
{
	private static final int MAX_BACKOFF_EXPONENT = 6;
	private static final long MAX_RETRY_DELAY_SECONDS = 60L;
	private static final long RELAY_READ_TIMEOUT_SECONDS = 20L;

	private final OkHttpClient httpClient;
	private final Consumer<Runnable> onClientThread;
	private final ScheduledExecutorService executor;
	private final Set<Call> calls = ConcurrentHashMap.newKeySet();
	private final Set<ScheduledFuture<?>> retries = ConcurrentHashMap.newKeySet();
	private DropOutbox outbox;
	private DropDiagnosticJournal diagnosticJournal;
	private Consumer<String> deliveredCallback;
	enum Progress { QUEUED, FAILED, ACCEPTED, CANCELLED }
	private java.util.function.BiConsumer<Request, Progress> progressListener;
	private final Set<String> activeRecords = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean closed = new AtomicBoolean();

	DropDeliveryClient(OkHttpClient httpClient, ClientThread clientThread, ScheduledExecutorService executor)
	{
		this(httpClient, clientThread::invokeLater, executor);
	}

	DropDeliveryClient(OkHttpClient httpClient, Consumer<Runnable> onClientThread,
		ScheduledExecutorService executor)
	{
		// The clan server can wait up to 15 seconds for its Discord relay. RuneLite's
		// shared client times out sooner, so give this isolated delivery client enough
		// time to receive the server's definitive forwarded/failed response.
		this.httpClient = httpClient.newBuilder()
			.readTimeout(RELAY_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.build();
		this.onClientThread = onClientThread;
		this.executor = executor;
	}

	void setOutbox(DropOutbox outbox) { this.outbox = outbox; }
	void setDiagnosticJournal(DropDiagnosticJournal journal) { diagnosticJournal = journal; }
	void setDeliveredCallback(Consumer<String> callback) { deliveredCallback = callback; }
	void setProgressListener(java.util.function.BiConsumer<Request, Progress> listener) { progressListener = listener; }
	private void progress(Request request, Progress progress)
	{
		if (progressListener != null)
			onClientThread.accept(() -> { if (!closed.get()) progressListener.accept(request, progress); });
	}

	void recover(okhttp3.HttpUrl base, String player,
		java.util.function.Function<String, DropSessionGate.State> permission)
	{
		if (outbox == null || base == null || closed.get()) return;
		executor.execute(() -> {
			try {
				for (DropOutbox.Entry entry : outbox.pending(base, player)) {
					if (activeRecords.contains(entry.id)) continue;
					Request initial = entry.request(false).newBuilder().tag(String.class, entry.id).build();
					Request retry = entry.request(true).newBuilder().tag(String.class, entry.id).build();
					if (!activeRecords.add(entry.id)) continue;
					onClientThread.accept(() -> dispatch(initial, retry, entry.destination,
						() -> permission.apply(initial.url().encodedPath()), 0));
				}
			} catch (IOException | RuntimeException e) { log.warn("Unable to recover pending drop records", e); }
		});
	}

	void send(Request initialRequest, Request retryRequest, String destination,
		Supplier<DropSessionGate.State> retryAllowed)
	{
		if (closed.get()) return;
		if (outbox == null) {
			onClientThread.accept(() -> dispatch(initialRequest, retryRequest, destination, retryAllowed, 0));
			return;
		}
		executor.execute(() -> {
			Request initial = initialRequest;
			Request retry = retryRequest;
			try {
				String id = outbox.save(initial, retry, destination);
				trace(initial, "QUEUED", destination);
				activeRecords.add(id);
				initial = initial.newBuilder().tag(String.class, id).build();
				retry = retry.newBuilder().tag(String.class, id).build();
			} catch (IOException | RuntimeException e) {
				log.warn("Unable to persist pending drop; attempting network delivery", e);
				trace(initial, "PERSISTENCE_ERROR", destination);
			}
			final Request ready = initial, fallback = retry;
			onClientThread.accept(() -> dispatch(ready, fallback, destination, retryAllowed, 0));
		});
	}

	private void dispatch(Request request, Request retryRequest, String destination,
		Supplier<DropSessionGate.State> state, int attempt)
	{
		if (closed.get()) return;
		DropSessionGate.State current = state.get();
		progress(request, Progress.QUEUED);
		if (current == DropSessionGate.State.WAIT)
			scheduleRetry(request, retryRequest, destination, state, attempt);
		else if (current == DropSessionGate.State.READY)
			send(request, retryRequest, destination, state, attempt);
		else {
			progress(request, Progress.CANCELLED);
			trace(request, "CANCELLED", destination + " participation_disabled");
			String id = request.tag(String.class); if (id != null) activeRecords.remove(id);
		}
	}

	private void send(Request request, Request retryRequest, String destination,
		Supplier<DropSessionGate.State> retryAllowed, int attempt)
	{
		if (closed.get()) return;
		log.debug("Starting {} server request (attempt {})", destination, attempt + 1);
		trace(request, "SEND", destination + " attempt=" + (attempt + 1)
			+ " image=" + (request.header("X-Live-On-Image") == null ? "unknown"
				: request.header("X-Live-On-Image")));
		Call call = httpClient.newCall(request);
		calls.add(call);
		if (closed.get())
		{
			calls.remove(call);
			call.cancel();
			return;
		}
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call failedCall, IOException exception)
			{
				calls.remove(failedCall);
				if (!closed.get())
				{
					log.debug("Unable to deliver {} (attempt {})", destination, attempt + 1, exception);
					trace(request, "NETWORK_ERROR", destination + " attempt=" + (attempt + 1));
					progress(request, Progress.FAILED);
					scheduleRetry(request, retryRequest, destination, retryAllowed, attempt);
				}
			}

			@Override
			public void onResponse(Call completedCall, Response response) throws IOException
			{
				calls.remove(completedCall);
				try (Response ignored = response)
				{
					String responseBody = "";
					try
					{
						responseBody = response.body() == null ? "" : response.body().string();
					}
					catch (IOException exception)
					{
						// The status is already known: an unreadable diagnostic body must
						// not prevent retrying a transient error or removing a rejected image.
						log.debug("Unable to read {} response body (HTTP {})", destination, response.code(), exception);
					}
					if (!response.isSuccessful())
					{
						log.debug("{} delivery returned {}: {}", destination, response.code(), responseBody);
						trace(request, "HTTP", destination + " status=" + response.code());
						if (response.code() == 413 && request != retryRequest)
						{
							// Preserve the notification when a proxy rejects only its screenshot.
							trace(request, "IMAGE_REJECTED", destination + " status=413");
							onClientThread.accept(() -> dispatch(retryRequest, retryRequest, destination, retryAllowed,
								Math.min(1000, attempt + 1)));
							return;
						}
						if (response.code() == 408 || response.code() == 425
							|| response.code() == 429 || response.code() >= 500)
						{
							// 425 means the same event is still processing, not a confirmed failure.
							if (response.code() != 425) progress(request, Progress.FAILED);
							scheduleRetry(request, retryRequest, destination, retryAllowed, attempt);
						}
					}
					else
					{
						log.debug("{} server response (attempt {}): {}", destination, attempt + 1, responseBody);
						String result = responseBody.contains("\"filtered\"") ? "filtered"
							: responseBody.contains("\"duplicate\": true") || responseBody.contains("\"duplicate\":true")
								? "duplicate" : responseBody.contains("\"forwarded\": true")
									|| responseBody.contains("\"forwarded\":true") ? "relay_accepted" : "accepted";
						trace(request, "HTTP", destination + " status=" + response.code() + " result=" + result);
						progress(request, "filtered".equals(result) ? Progress.CANCELLED : Progress.ACCEPTED);
						if (deliveredCallback != null)
							onClientThread.accept(() -> { if (!closed.get()) deliveredCallback.accept(destination); });
						if (outbox != null) {
							String id = request.tag(String.class);
							try { outbox.complete(id); if (id != null) activeRecords.remove(id); }
							catch (IOException e) { log.warn("Unable to acknowledge persisted drop", e); }
						}
					}
				}
			}
		});
	}

	private void trace(Request request, String stage, String detail)
	{
		if (diagnosticJournal != null)
			diagnosticJournal.record(request.header("X-Live-On-Event"), stage, detail);
	}

	private synchronized void scheduleRetry(Request request, Request retryRequest, String destination,
		Supplier<DropSessionGate.State> retryAllowed, int attempt)
	{
		if (closed.get() || executor.isShutdown()) return;
		long delaySeconds = Math.min(MAX_RETRY_DELAY_SECONDS,
			1L << Math.min(attempt, MAX_BACKOFF_EXPONENT));
		AtomicReference<ScheduledFuture<?>> reference = new AtomicReference<>();
		try
		{
			ScheduledFuture<?> retry = executor.schedule(() -> {
				synchronized (DropDeliveryClient.this)
				{
					retries.remove(reference.get());
				}
				if (closed.get()) return;
				onClientThread.accept(() -> dispatch(request, retryRequest, destination, retryAllowed,
					Math.min(1000, attempt + 1)));
			}, delaySeconds, TimeUnit.SECONDS);
			reference.set(retry);
			retries.add(retry);
			if (closed.get())
			{
				retries.remove(retry);
				retry.cancel(false);
			}
		}
		catch (RejectedExecutionException ignored)
		{
			// The plugin is shutting down between the lifecycle check and scheduling.
		}
	}

	@Override
	public synchronized void close()
	{
		if (!closed.compareAndSet(false, true)) return;
		for (ScheduledFuture<?> retry : retries) retry.cancel(false);
		retries.clear();
		for (Call call : calls) call.cancel();
		calls.clear();
	}
}
