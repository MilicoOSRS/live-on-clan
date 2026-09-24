package com.liveon;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.*;

/** Review characterizations, not an end-to-end RuneLite event simulation. */
public class RaidRetryRecoveryTest
{
	@Test
	public void validSessionRetriesAllThreeRaidFamilies() throws Exception
	{
		checkRetry(false);
	}

	@Test
	public void temporaryGuardWaitRetriesAllThreeRaidFamilies() throws Exception
	{
		// Exercise the actual delivery scheduler while its session guard waits.
		checkRetry(true);
	}

	private void checkRetry(boolean transientRejection) throws Exception
	{
		String[][] raids = {
			{"Chambers of Xeric", "Chambers of Xeric: Challenge Mode"},
			{"Theatre of Blood", "Theatre of Blood: Hard Mode"},
			{"Tombs of Amascut", "Tombs of Amascut: Expert Mode"}
		};
		for (String[] raid : raids)
		{
			RaidLootContext context = new RaidLootContext();
			context.remember(raid[1], 42, 100);
			assertEquals(raid[1], context.take(raid[0], "EVENT", 101).source);
			ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
			LinkedBlockingQueue<Runnable> clientCallbacks = new LinkedBlockingQueue<>();
			LinkedBlockingQueue<Integer> httpAttempts = new LinkedBlockingQueue<>();
			AtomicInteger attempts = new AtomicInteger();
			AtomicBoolean sessionAllowed = new AtomicBoolean(true);
			OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
				int attempt = attempts.incrementAndGet();
				httpAttempts.add(attempt);
				return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
					.code(attempt == 1 ? 503 : 200).message("local audit")
					.body(ResponseBody.create(null, "{}")).build();
			}).build();
			try (DropDeliveryClient delivery = new DropDeliveryClient(http, clientCallbacks::add, executor))
			{
				Request request = new Request.Builder().url("https://example.invalid/drop").build();
				delivery.send(request, request, raid[1], () -> sessionAllowed.get() ? DropSessionGate.State.READY : DropSessionGate.State.WAIT);
				clientCallbacks.remove().run();
				assertEquals(Integer.valueOf(1), httpAttempts.poll(5, TimeUnit.SECONDS));
				Runnable retry = clientCallbacks.poll(5, TimeUnit.SECONDS);
				assertNotNull(raid[0], retry);
				// Models the production guard returning false during LOADING.
				// It does not pretend to invoke the actual private plugin guard.
				sessionAllowed.set(!transientRejection);
				retry.run();
				sessionAllowed.set(true);
				if (transientRejection)
				{
					assertEquals(1, attempts.get());
					Runnable resumed = clientCallbacks.poll(5, TimeUnit.SECONDS);
					assertNotNull("A waiting delivery must remain scheduled", resumed);
					resumed.run();
					assertEquals(Integer.valueOf(2), httpAttempts.poll(5, TimeUnit.SECONDS));
				}
				else
				{
					assertEquals(Integer.valueOf(2), httpAttempts.poll(5, TimeUnit.SECONDS));
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
}
