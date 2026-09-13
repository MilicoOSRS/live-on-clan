package com.liveon;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropDeliveryClientTest
{
	@Test
	public void temporaryFailuresRetryButPermanentErrorsStop() throws Exception
	{
		for (int status : new int[] {429, 503, -1, 400, 401})
		{
			ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
			java.util.concurrent.BlockingQueue<Runnable> queued = new java.util.concurrent.LinkedBlockingQueue<>();
			AtomicInteger attempts = new AtomicInteger();
			CompletableFuture<Void> responded = new CompletableFuture<>();
			OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
				attempts.incrementAndGet();
				responded.complete(null);
				if (status == -1) throw new java.io.IOException("Simulated network failure");
				return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
					.code(status).message("simulation").body(ResponseBody.create(null, "")).build();
			}).build();
			try (DropDeliveryClient delivery = new DropDeliveryClient(http, queued::add, executor))
			{
				Request request = new Request.Builder().url("https://example.invalid/drop").build();
				delivery.send(request, request, "simulation", () -> true);
				queued.remove().run();
				responded.get(5, TimeUnit.SECONDS);
				Runnable retry = queued.poll(3, TimeUnit.SECONDS);
				if (status == 400 || status == 401) assertNull(retry);
				else
				{
					assertNotNull(retry);
					delivery.close();
					retry.run();
					assertEquals("Closing must invalidate a queued retry", 1, attempts.get());
				}
			}
			finally
			{
				executor.shutdownNow();
				http.dispatcher().executorService().shutdownNow();
				http.connectionPool().evictAll();
			}
		}
	}

	@Test
	public void revokedPermissionStopsQueuedRetry() throws Exception
	{
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		java.util.concurrent.BlockingQueue<Runnable> queued = new java.util.concurrent.LinkedBlockingQueue<>();
		java.util.concurrent.atomic.AtomicBoolean allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
		AtomicInteger attempts = new AtomicInteger();
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
			attempts.incrementAndGet();
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(503).message("simulation").body(ResponseBody.create(null, "")).build();
		}).build();
		try (DropDeliveryClient delivery = new DropDeliveryClient(http, queued::add, executor))
		{
			Request request = new Request.Builder().url("https://example.invalid/drop").build();
			delivery.send(request, request, "simulation", allowed::get);
			queued.remove().run();
			Runnable retry = queued.poll(5, TimeUnit.SECONDS);
			assertNotNull(retry);
			allowed.set(false);
			retry.run();
			assertEquals(1, attempts.get());
		}
		finally
		{
			executor.shutdownNow();
			http.dispatcher().executorService().shutdownNow();
			http.connectionPool().evictAll();
		}
	}

	@Test
	public void rechecksPermissionBeforeQueuedInitialSend()
	{
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		List<Runnable> queued = new ArrayList<>();
		AtomicInteger checks = new AtomicInteger();
		try (DropDeliveryClient delivery = new DropDeliveryClient(new OkHttpClient(), queued::add, executor))
		{
			Request request = new Request.Builder().url("https://example.invalid/drop").build();
			delivery.send(request, request, "test", () -> { checks.incrementAndGet(); return false; });
			queued.remove(0).run();
			assertEquals(1, checks.get());
		}
		finally { executor.shutdownNow(); }
	}

	@Test
	public void retryUsesLightweightRequestWithSameIdentity() throws Exception
	{
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		AtomicInteger attempts = new AtomicInteger();
		CompletableFuture<Void> retried = new CompletableFuture<>();
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
			assertEquals("original-event", chain.request().header("Idempotency-Key"));
			int attempt = attempts.getAndIncrement();
			assertEquals(attempt == 0 ? "with-image" : "without-image",
				chain.request().header("Payload-Kind"));
			if (attempt > 0) retried.complete(null);
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(attempt == 0 ? 503 : 200).message("test")
				.body(ResponseBody.create(null, "")).build();
		}).build();
		try (DropDeliveryClient delivery = new DropDeliveryClient(http, Runnable::run, executor))
		{
			Request initial = new Request.Builder().url("https://example.invalid/drop")
				.header("Idempotency-Key", "original-event").header("Payload-Kind", "with-image").build();
			Request retry = initial.newBuilder().header("Payload-Kind", "without-image").build();
			delivery.send(initial, retry, "test", () -> true);
			retried.get(5, TimeUnit.SECONDS);
			assertEquals(2, attempts.get());
		}
		finally
		{
			executor.shutdownNow();
			http.dispatcher().executorService().shutdownNow();
			http.connectionPool().evictAll();
		}
	}
}
