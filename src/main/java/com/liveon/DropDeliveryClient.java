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
import java.util.function.BooleanSupplier;
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

	void send(Request initialRequest, Request retryRequest, String destination,
		BooleanSupplier retryAllowed)
	{
		onClientThread.accept(() -> {
			if (!closed.get())
			{
				send(initialRequest, retryRequest, destination, retryAllowed, 0);
			}
		});
	}

	private void send(Request request, Request retryRequest, String destination,
		BooleanSupplier retryAllowed, int attempt)
	{
		if (closed.get()) return;
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
					scheduleRetry(retryRequest, destination, retryAllowed, attempt);
				}
			}

			@Override
			public void onResponse(Call completedCall, Response response) throws IOException
			{
				calls.remove(completedCall);
				try (Response ignored = response)
				{
					String responseBody = response.body() == null ? "" : response.body().string();
					if (!response.isSuccessful())
					{
						log.debug("{} delivery returned {}: {}", destination, response.code(), responseBody);
						if (response.code() == 413 && request != retryRequest)
						{
							// Preserve the notification when a proxy rejects only its screenshot.
							send(retryRequest, retryRequest, destination, retryAllowed,
								Math.min(1000, attempt + 1));
							return;
						}
						if (response.code() == 408 || response.code() == 425
							|| response.code() == 429 || response.code() >= 500)
						{
							scheduleRetry(retryRequest, destination, retryAllowed, attempt);
						}
					}
					else
					{
						log.debug("{} delivered (attempt {}): {}", destination, attempt + 1, responseBody);
					}
				}
			}
		});
	}

	private synchronized void scheduleRetry(Request request, String destination,
		BooleanSupplier retryAllowed, int attempt)
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
				onClientThread.accept(() -> {
					if (!closed.get() && retryAllowed.getAsBoolean())
					{
						send(request, request, destination, retryAllowed,
							Math.min(1000, attempt + 1));
					}
				});
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
