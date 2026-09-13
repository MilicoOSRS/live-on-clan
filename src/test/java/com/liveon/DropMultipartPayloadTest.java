package com.liveon;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import okhttp3.MultipartBody;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropMultipartPayloadTest
{
	@Test
	public void retryKeepsMetadataAndDropsScreenshot() throws Exception
	{
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("title", "Loot Drop");
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("idempotencyKey", "event-123");
		payload.put("playerName", "Milico");
		payload.put("embeds", Collections.singletonList(embed));

		DropMultipartPayload bodies = DropMultipartPayload.create(
			new Gson(), payload, embed, new byte[] {1, 2, 3}, "loot.png");

		assertEquals(2, ((MultipartBody) bodies.initialBody).size());
		assertEquals(1, ((MultipartBody) bodies.retryBody).size());
		String initialJson = bodyText((MultipartBody) bodies.initialBody);
		String retryJson = bodyText((MultipartBody) bodies.retryBody);
		assertTrue(initialJson.contains("attachment://loot.png"));
		assertFalse(retryJson.contains("attachment://loot.png"));
		assertTrue(retryJson.contains("event-123"));
		assertTrue(retryJson.contains("Milico"));
		assertFalse(embed.containsKey("image"));
	}

	private static String bodyText(MultipartBody body) throws Exception
	{
		Buffer buffer = new Buffer();
		body.part(0).body().writeTo(buffer);
		return buffer.readUtf8();
	}
}
