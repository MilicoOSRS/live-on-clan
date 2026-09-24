package com.liveon;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropFrameCaptureTest
{
	@Test
	public void frameAfterLoadingWinsOverExpiredInitialTimeout() throws Exception
	{
		try (Harness harness = new Harness())
		{
			harness.state.set(DropSessionGate.State.WAIT);
			harness.capture.capture(harness.state::get, harness::record);
			// The first timeout fires while the client is loading.
			BooleanSupplier timeout = harness.callbacks.poll(4, TimeUnit.SECONDS);
			assertNotNull(timeout);
			assertFalse(timeout.getAsBoolean());
			Image frame = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
			harness.frame.get().accept(frame);
			harness.state.set(DropSessionGate.State.READY);
			// Process the already queued timeout before the frame, as in the worst order.
			harness.callbacks.addFirst(timeout);
			assertTrue(harness.callbacks.poll(2, TimeUnit.SECONDS).getAsBoolean());
			assertTrue(harness.callbacks.poll(2, TimeUnit.SECONDS).getAsBoolean());
			assertSame(frame, harness.deliveries.poll(2, TimeUnit.SECONDS));
			assertTrue(harness.callbacks.poll(3, TimeUnit.SECONDS).getAsBoolean());
			assertTrue("A later text-only fallback must not duplicate the drop", harness.deliveries.isEmpty());
			assertEquals(0, harness.noImage.get());
		}
	}

	@Test
	public void absentFrameEventuallySendsOnceWithoutImage() throws Exception
	{
		try (Harness harness = new Harness())
		{
			harness.capture.capture(harness.state::get, harness::record);
			assertTrue(harness.callbacks.poll(4, TimeUnit.SECONDS).getAsBoolean());
			assertTrue(harness.callbacks.poll(4, TimeUnit.SECONDS).getAsBoolean());
			assertEquals(1, harness.noImage.get());
			assertTrue(harness.deliveries.isEmpty());
		}
	}

	@Test
	public void accountChangeCancelsPendingCapture() throws Exception
	{
		try (Harness harness = new Harness())
		{
			harness.state.set(DropSessionGate.State.WAIT);
			harness.capture.capture(harness.state::get, harness::record);
			Image frame = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
			harness.frame.get().accept(frame);
			harness.state.set(DropSessionGate.State.CANCEL);
			assertTrue(harness.callbacks.poll(2, TimeUnit.SECONDS).getAsBoolean());
			assertTrue(harness.deliveries.isEmpty());
		}
	}

	private static final class Harness implements AutoCloseable
	{
		final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
		final LinkedBlockingDeque<BooleanSupplier> callbacks = new LinkedBlockingDeque<>();
		final LinkedBlockingQueue<Image> deliveries = new LinkedBlockingQueue<>();
		final AtomicInteger noImage = new AtomicInteger();
		final AtomicReference<Consumer<Image>> frame = new AtomicReference<>();
		final AtomicReference<DropSessionGate.State> state =
			new AtomicReference<>(DropSessionGate.State.READY);
		final DropFrameCapture capture = new DropFrameCapture(executor, callbacks::add, frame::set);
		void record(Image image)
		{
			if (image == null) noImage.incrementAndGet();
			else deliveries.add(image);
		}
		@Override public void close()
		{
			capture.close();
			executor.shutdownNow();
		}
	}
}
