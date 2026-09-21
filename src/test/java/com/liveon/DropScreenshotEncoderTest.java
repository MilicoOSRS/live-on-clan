package com.liveon;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropScreenshotEncoderTest
{
	@Test
	public void sharesEncodingAcrossDestinationsAndReleasesItWhenCleared() throws Exception
	{
		DropScreenshotEncoder encoder = new DropScreenshotEncoder();
		BufferedImage frame = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
		frame.setRGB(0, 0, 0xFF123456);
		byte[] encoded = encoder.encode(frame);
		assertSame(encoded, encoder.encode(frame));
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded));
		assertEquals(0xFF123456, decoded.getRGB(0, 0));
		assertEquals(3, decoded.getHeight());
		encoder.clear();
		assertNotSame(encoded, encoder.encode(frame));
	}

	@Test
	public void boundsHighResolutionFramesBeforeUpload() throws Exception
	{
		DropScreenshotEncoder encoder = new DropScreenshotEncoder();
		BufferedImage frame = new BufferedImage(2560, 1440, BufferedImage.TYPE_INT_RGB);
		byte[] encoded = encoder.encode(frame);
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded));
		assertEquals(1920, decoded.getWidth());
		assertEquals(1080, decoded.getHeight());
		assertTrue(encoded.length <= 7 * 1024 * 1024);
	}
}
