package com.liveon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;
import org.junit.Test;
import static org.junit.Assert.*;

/** Component integration: production multipart builder and HTTP retry client, no external network. */
public class DropWireIntegrationTest
{
	@Test
	public void identicalDropsHaveIndependentRequests() throws Exception
	{
		try (Wire wire = new Wire(200, false))
		{
			wire.send("drop-one", "Chambers of Xeric", null);
			wire.send("drop-two", "Chambers of Xeric", null);
			JsonObject first = wire.next();
			JsonObject second = wire.next();
			assertNotEquals(first.get("idempotencyKey"), second.get("idempotencyKey"));
			assertEquals(first.get("extra"), second.get("extra"));
		}
	}

	@Test
	public void imageRejectionKeepsActualPayloadIdentityAndItems() throws Exception
	{
		try (Wire wire = new Wire(413, false))
		{
			wire.send("original-drop", "Tombs of Amascut: Expert Mode", new byte[] {1, 2, 3});
			JsonObject original = wire.next();
			JsonObject retry = wire.next();
			assertEquals(original.get("idempotencyKey"), retry.get("idempotencyKey"));
			assertEquals(original.get("extra"), retry.get("extra"));
			assertTrue(original.getAsJsonArray("embeds").get(0).getAsJsonObject().has("image"));
			assertFalse(retry.getAsJsonArray("embeds").get(0).getAsJsonObject().has("image"));
		}
	}

	@Test
	public void brokenResponseBodyDoesNotAbandonRetryableResponse() throws Exception
	{
		try (Wire wire = new Wire(503, true))
		{
			wire.send("interrupted-response", "Chambers of Xeric", null);
			assertEquals(wire.next().get("idempotencyKey"), wire.next().get("idempotencyKey"));
		}
	}

	@Test
	public void raidMetadataSurvivesMultipartForBothCompletionOrders() throws Exception
	{
		String[][] raids = {
			{"Chambers of Xeric", "Chambers of Xeric: Challenge Mode"},
			{"Theatre of Blood", "Theatre of Blood: Hard Mode"},
			{"Tombs of Amascut", "Tombs of Amascut: Expert Mode"}
		};
		try (Wire wire = new Wire(200, false))
		{
			for (String[] raid : raids)
			{
				for (boolean completionFirst : new boolean[] {true, false})
				{
					RaidLootContext context = new RaidLootContext();
					if (completionFirst) context.remember(raid[1], 42, 100);
					RaidLootContext.Completion completion = context.take(raid[0], "EVENT", 100);
					if (!completionFirst)
					{
						context.remember(raid[1], 42, 101);
						completion = context.take(raid[0], "EVENT", 101);
					}
					assertNotNull(completion);
					wire.send(raid[0] + completionFirst, completion.source, null);
					assertEquals(raid[1], wire.next().getAsJsonObject("extra").get("source").getAsString());
				}
			}
		}
	}

	@Test
	public void openingRaidChestLongAfterCompletionStillSendsTheLoot() throws Exception
	{
		String[][] raids = {
			{"Chambers of Xeric", "Chambers of Xeric: Challenge Mode"},
			{"Theatre of Blood", "Theatre of Blood: Hard Mode"},
			{"Tombs of Amascut", "Tombs of Amascut: Expert Mode"}
		};
		try (Wire wire = new Wire(200, false))
		{
			for (String[] raid : raids)
			{
				RaidLootContext context = new RaidLootContext();
				context.remember(raid[1], 42, 100);
				assertNull("Old completion metadata must not relabel a later chest",
					context.take(raid[0], "EVENT", 600));
				// The EVENT still carries its loot. Missing/stale completion metadata
				// must only affect the label, never whether a request is sent.
				wire.send("late-" + raid[0], raid[0], null);
				JsonObject sent = wire.next();
				assertEquals(raid[0], sent.getAsJsonObject("extra").get("source").getAsString());
				assertEquals("Twisted bow", sent.getAsJsonObject("extra")
					.getAsJsonArray("items").get(0).getAsJsonObject().get("name").getAsString());
			}
		}
	}

	private static final class Wire implements AutoCloseable
	{
		final Gson gson = new Gson();
		final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		final BlockingQueue<JsonObject> received = new LinkedBlockingQueue<>();
		final OkHttpClient http;
		final DropDeliveryClient delivery;

		Wire(int firstStatus, boolean brokenBody)
		{
			AtomicInteger attempts = new AtomicInteger();
			http = new OkHttpClient.Builder().addInterceptor(chain -> {
				MultipartBody multipart = (MultipartBody) chain.request().body();
				Buffer json = new Buffer();
				multipart.part(0).body().writeTo(json);
				received.add(gson.fromJson(json.readUtf8(), JsonObject.class));
				int attempt = attempts.getAndIncrement();
				ResponseBody body = ResponseBody.create(null, "{}");
				if (brokenBody && attempt == 0)
				{
					body = new ResponseBody()
					{
						@Override public MediaType contentType() { return null; }
						@Override public long contentLength() { return -1; }
						@Override public BufferedSource source()
						{
							return Okio.buffer(new Source()
							{
								@Override public long read(Buffer sink, long count) throws IOException
								{ throw new IOException("simulated truncated response"); }
								@Override public Timeout timeout() { return Timeout.NONE; }
								@Override public void close() {}
							});
						}
					};
				}
				return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
					.code(attempt == 0 ? firstStatus : 200).message("simulation").body(body).build();
			}).build();
			delivery = new DropDeliveryClient(http, Runnable::run, executor);
		}

		void send(String identity, String source, byte[] image)
		{
			Map<String, Object> embed = new LinkedHashMap<>();
			embed.put("title", "Loot Drop");
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("idempotencyKey", identity);
			payload.put("type", "LOOT");
			payload.put("extra", Map.of("source", source, "items", Collections.singletonList(
				Map.of("name", "Twisted bow", "quantity", 1, "priceEach", 1_000_000_000L))));
			payload.put("embeds", Collections.singletonList(embed));
			DropMultipartPayload bodies = DropMultipartPayload.create(gson, payload, embed, image, "loot.png");
			Request request = new Request.Builder().url("https://example.invalid/drop").post(bodies.initialBody).build();
			delivery.send(request, request.newBuilder().post(bodies.retryBody).build(), "simulation", () -> true);
		}

		JsonObject next() throws InterruptedException
		{
			JsonObject result = received.poll(4, TimeUnit.SECONDS);
			assertNotNull("Expected an HTTP attempt", result);
			return result;
		}

		@Override public void close()
		{
			delivery.close();
			executor.shutdownNow();
			http.dispatcher().executorService().shutdownNow();
			http.connectionPool().evictAll();
		}
	}
}
