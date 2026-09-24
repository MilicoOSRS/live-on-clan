package com.liveon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;

/** Reuses the most recent frame encoding across drop destinations. */
final class DropScreenshotEncoder
{
	private static final int MAX_WIDTH = 1920;
	private static final int MAX_HEIGHT = 1080;
	private static final int MAX_ENCODED_BYTES = 7 * 1024 * 1024;
	private static final int MIN_DIMENSION = 640;
	private final Map<BufferedImage, byte[]> encodings = new WeakHashMap<>();

	synchronized byte[] encode(BufferedImage frame) throws IOException
	{
		if (encodings.containsKey(frame)) return encodings.get(frame);
		BufferedImage candidate = fit(frame, MAX_WIDTH, MAX_HEIGHT);
		byte[] encoded = encodePng(candidate);
		while (encoded != null && encoded.length > MAX_ENCODED_BYTES
			&& (candidate.getWidth() > MIN_DIMENSION || candidate.getHeight() > MIN_DIMENSION))
		{
			candidate = fit(candidate,
				Math.max(MIN_DIMENSION, candidate.getWidth() * 4 / 5),
				Math.max(MIN_DIMENSION, candidate.getHeight() * 4 / 5));
			encoded = encodePng(candidate);
		}
		encodings.put(frame, encoded);
		return encoded;
	}

	private static byte[] encodePng(BufferedImage image) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		return ImageIO.write(image, "png", output) ? output.toByteArray() : null;
	}

	private static BufferedImage fit(BufferedImage source, int maxWidth, int maxHeight)
	{
		double scale = Math.min(1D, Math.min((double) maxWidth / source.getWidth(),
			(double) maxHeight / source.getHeight()));
		if (scale >= 1D) return source;
		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
		BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = resized.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally
		{
			graphics.dispose();
		}
		return resized;
	}

	synchronized void clear()
	{
		encodings.clear();
	}
}
