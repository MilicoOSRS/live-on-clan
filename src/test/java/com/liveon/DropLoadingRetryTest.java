package com.liveon;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.GameState;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the production session gate and HTTP retry client without external requests. */
public class DropLoadingRetryTest
{
	@Test
	public void raidRetriesSurviveLoadingAndKeepEventIdentity() throws Exception
	{
		for (String raid : new String[] {"Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut"})
		{
			try (Wire wire = new Wire(503))
			{
				wire.send(raid);
				wire.nextCallback().run();
				assertEquals("with-image", wire.requests.poll(5, TimeUnit.SECONDS));
				Runnable retry = wire.nextCallback();
				wire.game.set(GameState.LOADING);
				retry.run();
				assertEquals(1, wire.attempts.get());
				assertFalse(wire.executor.getQueue().isEmpty());
				wire.game.set(GameState.LOGGED_IN);
				wire.nextCallback().run();
				assertEquals("with-image", wire.requests.poll(5, TimeUnit.SECONDS));
				assertEquals(2, wire.attempts.get());
			}
		}
	}

	@Test
	public void loadingBeforeFirstAttemptPreservesScreenshot() throws Exception
	{
		try (Wire wire = new Wire(200))
		{
			wire.game.set(GameState.LOADING);
			wire.send("clue");
			wire.nextCallback().run();
			assertEquals(0, wire.attempts.get());
			wire.game.set(GameState.LOGGED_IN);
			wire.nextCallback().run();
			assertEquals("with-image", wire.requests.poll(5, TimeUnit.SECONDS));
		}
	}

	@Test
	public void cancelledSessionDoesNotStartOrResumeRequest() throws Exception
	{
		for (boolean firstAttempt : new boolean[] {true, false})
		{
			try (Wire wire = new Wire(503))
			{
				wire.send("raid");
				if (!firstAttempt)
				{
					wire.nextCallback().run();
					assertNotNull(wire.requests.poll(5, TimeUnit.SECONDS));
				}
				wire.game.set(GameState.LOGIN_SCREEN);
				wire.nextCallback().run();
				assertEquals(firstAttempt ? 0 : 1, wire.attempts.get());
				assertTrue(wire.executor.getQueue().isEmpty());
			}
		}
	}

	@Test
	public void shutdownCancelsLoadingRetry() throws Exception
	{
		try (Wire wire = new Wire(200))
		{
			wire.game.set(GameState.LOADING);
			wire.send("raid");
			wire.nextCallback().run();
			wire.delivery.close();
			assertTrue(wire.executor.getQueue().isEmpty());
			assertEquals(0, wire.attempts.get());
		}
	}

	private static final class Wire implements AutoCloseable
	{
		final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
		final LinkedBlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
		final LinkedBlockingQueue<String> requests = new LinkedBlockingQueue<>();
		final AtomicInteger attempts = new AtomicInteger();
		final AtomicReference<GameState> game = new AtomicReference<>(GameState.LOGGED_IN);
		final OkHttpClient http;
		final DropDeliveryClient delivery;
		Wire(int firstStatus)
		{
			executor.setRemoveOnCancelPolicy(true);
			http = new OkHttpClient.Builder().addInterceptor(chain -> {
				assertEquals("original-drop", chain.request().header("Idempotency-Key"));
				int attempt = attempts.incrementAndGet();
				requests.add(chain.request().header("Payload-Kind"));
				return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
					.code(attempt == 1 ? firstStatus : 200).message("local simulation")
					.body(ResponseBody.create(null, "{}")).build();
			}).build();
			delivery = new DropDeliveryClient(http, callbacks::add, executor);
		}
		void send(String source)
		{
			Request initial = new Request.Builder().url("https://example.invalid/drop")
				.header("Idempotency-Key", "original-drop").header("Payload-Kind", "with-image").build();
			delivery.send(initial, initial.newBuilder().header("Payload-Kind", "without-image").build(), source,
				() -> DropSessionGate.evaluate(1, 1, "Milico", "Milico", true, game.get()));
		}
		Runnable nextCallback() throws InterruptedException
		{
			Runnable callback = callbacks.poll(5, TimeUnit.SECONDS);
			assertNotNull("Expected scheduled delivery callback", callback);
			return callback;
		}
		public void close()
		{
			delivery.close();
			executor.shutdownNow();
			http.dispatcher().executorService().shutdownNow();
			http.connectionPool().evictAll();
		}
	}
}
