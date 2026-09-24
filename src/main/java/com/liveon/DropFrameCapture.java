package com.liveon;

import java.awt.Image;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Gives a pending frame a chance to arrive after loading before sending without it. */
final class DropFrameCapture implements AutoCloseable
{
	private static final long FRAME_WAIT_MILLIS = 2000;
	private final ScheduledExecutorService executor;
	private final Consumer<java.util.function.BooleanSupplier> onClientThread;
	private final Consumer<Consumer<Image>> requestFrame;
	private final Set<ScheduledFuture<?>> timeouts = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean closed = new AtomicBoolean();

	DropFrameCapture(ScheduledExecutorService executor,
		Consumer<java.util.function.BooleanSupplier> onClientThread,
		Consumer<Consumer<Image>> requestFrame)
	{
		this.executor = executor;
		this.onClientThread = onClientThread;
		this.requestFrame = requestFrame;
	}

	void capture(Supplier<DropSessionGate.State> state, Consumer<Image> delivery)
	{
		if (closed.get()) return;
		AtomicBoolean delivered = new AtomicBoolean();
		Consumer<Image> finish = image -> {
			if (closed.get()) return;
			onClientThread.accept(DropSessionGate.task(state, () -> {
				if (!closed.get() && delivered.compareAndSet(false, true)) delivery.accept(image);
			}));
		};
		try
		{
			requestFrame.accept(finish);
			// A loading screen can delay the frame beyond this first timeout. Wait
			// for the session to become ready, then give the frame another chance.
			schedule(() -> onClientThread.accept(DropSessionGate.task(state, () -> {
				if (!delivered.get() && !closed.get()) schedule(() -> finish.accept(null));
			})));
		}
		catch (RuntimeException exception)
		{
			// A failed frame request must not lose the accepted loot event.
			finish.accept(null);
		}
	}

	private void schedule(Runnable action)
	{
		if (closed.get()) return;
		AtomicReference<ScheduledFuture<?>> reference = new AtomicReference<>();
		ScheduledFuture<?> future = executor.schedule(() -> {
			timeouts.remove(reference.get());
			if (!closed.get()) action.run();
		}, FRAME_WAIT_MILLIS, TimeUnit.MILLISECONDS);
		reference.set(future);
		timeouts.add(future);
		if (closed.get() && timeouts.remove(future)) future.cancel(false);
	}

	@Override public void close()
	{
		if (!closed.compareAndSet(false, true)) return;
		for (ScheduledFuture<?> future : timeouts) future.cancel(false);
		timeouts.clear();
	}
}
