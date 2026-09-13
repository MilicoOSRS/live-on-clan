package com.liveon;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/** Builds an initial notification with its screenshot-free retry body. */
final class DropMultipartPayload
{
	private static final MediaType PNG = MediaType.parse("image/png");
	final RequestBody initialBody;
	final RequestBody retryBody;

	private DropMultipartPayload(RequestBody initialBody, RequestBody retryBody)
	{
		this.initialBody = initialBody;
		this.retryBody = retryBody;
	}

	static DropMultipartPayload create(Gson gson, Map<String, Object> payload,
		Map<String, Object> embed, byte[] screenshot, String fileName)
	{
		if (screenshot == null)
		{
			RequestBody body = multipart(gson.toJson(payload), null, fileName);
			return new DropMultipartPayload(body, body);
		}
		Map<String, Object> image = new LinkedHashMap<>();
		image.put("url", "attachment://" + fileName);
		embed.put("image", image);
		RequestBody initial = multipart(gson.toJson(payload), screenshot, fileName);
		embed.remove("image");
		RequestBody retry = multipart(gson.toJson(payload), null, fileName);
		return new DropMultipartPayload(initial, retry);
	}

	private static RequestBody multipart(String payloadJson, byte[] screenshot, String fileName)
	{
		MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM)
			.addFormDataPart("payload_json", payloadJson);
		if (screenshot != null)
		{
			builder.addFormDataPart("file", fileName, RequestBody.create(PNG, screenshot));
		}
		return builder.build();
	}
}
