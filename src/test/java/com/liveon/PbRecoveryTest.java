package com.liveon;

import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.concurrent.*;
import okhttp3.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PbRecoveryTest
{
	@Rule public TemporaryFolder temporary = new TemporaryFolder();

	@Test public void actualTransportFailuresProduceOneWarningAndRecoveryOnlyAfterAcceptance() throws Exception
	{
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
		java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
		CompletableFuture<Void> accepted = new CompletableFuture<>();
		RecoveryNotice notice = new RecoveryNotice();
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
			int attempt = attempts.incrementAndGet();
			int status = attempt == 1 ? 425 : attempt < 4 ? 503 : 201;
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(status).message("simulation").body(ResponseBody.create(null, "{}")).build();
		}).build();
		int warnings = 0;
		try (DropDeliveryClient delivery = new DropDeliveryClient(http, callbacks::add, executor))
		{
			delivery.setProgressListener((request, progress) -> {
				switch (progress)
				{
					case QUEUED: notice.queued("pb"); break;
					case FAILED: notice.failed("pb"); break;
					case ACCEPTED: notice.accepted("pb"); accepted.complete(null); break;
					case CANCELLED: notice.cancelled("pb"); break;
				}
			});
			Request request = new Request.Builder().url("https://example.invalid/stats/pbs").build();
			delivery.send(request, request, "PB", () -> DropSessionGate.State.READY);
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
			while (!accepted.isDone() && System.nanoTime() < deadline)
			{
				Runnable next = callbacks.poll(200, TimeUnit.MILLISECONDS);
				if (next != null) next.run();
				RecoveryNotice.Message message = notice.poll(System.currentTimeMillis(), false, true);
				assertNotEquals(RecoveryNotice.Message.RECOVERED, message);
				if (message == RecoveryNotice.Message.WAITING)
				{
					assertTrue("425 processing alone must stay silent", attempts.get() >= 2);
					warnings++;
				}
			}
			assertTrue(accepted.isDone());
			assertEquals(4, attempts.get());
			assertEquals(1, warnings);
			assertEquals(RecoveryNotice.Message.RECOVERED,
				notice.poll(System.currentTimeMillis() + 5001L, false, true));
		}
		finally
		{
			executor.shutdownNow();
			http.dispatcher().executorService().shutdown();
			http.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS);
			http.connectionPool().evictAll();
		}
	}

	@Test public void pbWaitsForAuthenticationAndSurvivesRestartWithoutSendingAsAnotherAccount() throws Exception
	{
		Path directory = temporary.newFolder().toPath();
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
		BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
			requests.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(201).message("Created").body(ResponseBody.create(null, "{\"recorded\":true}")).build();
		}).build();
		HttpUrl base = HttpUrl.parse("https://example.invalid/");
		DropOutbox outbox = new DropOutbox(directory, new Gson());
		Request pb = new Request.Builder().url(base.resolve("stats/pbs"))
			.header("X-Live-On-Player", "Tamzz").header("X-Live-On-Event", "pb-original")
			.post(RequestBody.create(MediaType.parse("application/json"),
				"{\"playerName\":\"Tamzz\",\"boss\":\"Chambers of Xeric\",\"seconds\":1006}")).build();
		try
		{
			try (DropDeliveryClient waiting = new DropDeliveryClient(http, callbacks::add, executor))
			{
				waiting.setOutbox(outbox);
				waiting.send(pb, pb, "PB", () -> DropSessionGate.State.WAIT);
				Runnable dispatch = callbacks.poll(5, TimeUnit.SECONDS);
				assertNotNull(dispatch);
				dispatch.run();
				assertNull(requests.poll(100, TimeUnit.MILLISECONDS));
			}
			assertEquals(1, outbox.pending(base, "Tamzz").size());
			assertTrue(outbox.pending(base, "Other").isEmpty());
			callbacks.clear();
			try (DropDeliveryClient recovered = new DropDeliveryClient(http, callbacks::add, executor))
			{
				recovered.setOutbox(new DropOutbox(directory, new Gson()));
				recovered.recover(base, "Tamzz", path -> DropSessionGate.State.READY);
				Runnable dispatch = callbacks.poll(5, TimeUnit.SECONDS);
				assertNotNull(dispatch);
				dispatch.run();
				Request delivered = requests.poll(5, TimeUnit.SECONDS);
				assertNotNull(delivered);
				assertEquals("Tamzz", delivered.header("X-Live-On-Player"));
				assertEquals("pb-original", delivered.header("X-Live-On-Event"));
			}
		}
		finally
		{
			executor.shutdownNow();
			http.dispatcher().executorService().shutdown();
			http.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS);
			http.connectionPool().evictAll();
		}
	}
}
