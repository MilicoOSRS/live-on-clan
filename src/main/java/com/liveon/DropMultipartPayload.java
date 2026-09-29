package com.liveon;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
		// The retry is only sent when the server rejects the image size (HTTP 413).
		Object fields = embed.get("fields");
		addScreenshotStatus(embed, screenshotStatus(SIZE_REJECTED, null));
		RequestBody retry = multipart(gson.toJson(payload), null, fileName);
		if (fields == null) embed.remove("fields");
		else embed.put("fields", fields);
		return new DropMultipartPayload(initial, retry);
	}

	/** Screenshot failure codes shown in Discord; the full error stays in drop-diagnostics.log. */
	static final int CAPTURE_MISSING = 101;
	static final int ENCODE_OUT_OF_MEMORY = 201;
	static final int ENCODE_FAILED = 202;
	static final int SIZE_REJECTED = 301;
	static final int SEND_FAILED = 901;

	/** Only the error type is shown: exception messages can contain local paths. */
	static String screenshotStatus(int code, Throwable error)
	{
		return "indisponível (erro " + code + (error == null ? "" : " · " + error.getClass().getSimpleName()) + ")";
	}

	@SuppressWarnings("unchecked")
	static void addScreenshotStatus(Map<String, Object> embed, String status)
	{
		Object current = embed.get("fields");
		List<Object> fields = current instanceof List ? new ArrayList<>((List<Object>) current) : new ArrayList<>();
		Map<String, Object> field = new LinkedHashMap<>();
		field.put("name", "Print");
		field.put("value", "```\n" + status + "\n```");
		field.put("inline", false);
		fields.add(field);
		embed.put("fields", fields);
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
