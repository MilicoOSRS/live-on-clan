package com.liveon;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;

/**
 * Single-thread scheduler that reports every failed task. A plain scheduled executor keeps
 * exceptions inside the task's Future, so a failure in background work vanished without a trace.
 */
final class LoggingScheduledExecutor extends ScheduledThreadPoolExecutor
{
	private final Consumer<Throwable> onFailure;

	LoggingScheduledExecutor(Consumer<Throwable> onFailure)
	{
		super(1);
		this.onFailure = onFailure;
	}

	@Override
	protected void afterExecute(Runnable task, Throwable failure)
	{
		super.afterExecute(task, failure);
		if (failure == null && task instanceof Future && ((Future<?>) task).isDone())
		{
			try
			{
				((Future<?>) task).get();
			}
			catch (CancellationException ignored)
			{
				// Cancelled on purpose, for example when the plugin stops.
			}
			catch (ExecutionException exception)
			{
				failure = exception.getCause();
			}
			catch (InterruptedException ignored)
			{
				// Not reachable: the task is already done, so get() returns without waiting.
			}
		}
		if (failure != null) onFailure.accept(failure);
	}
}
