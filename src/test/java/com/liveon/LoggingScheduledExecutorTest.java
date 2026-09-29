package com.liveon;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoggingScheduledExecutorTest
{
	@Test
	public void reportsErrorsThatAPlainExecutorWouldSwallow() throws Exception
	{
		List<Throwable> failures = new CopyOnWriteArrayList<>();
		LoggingScheduledExecutor executor = new LoggingScheduledExecutor(failures::add);
		executor.execute(() -> { throw new OutOfMemoryError("frame"); });
		executor.schedule(() -> { throw new IllegalStateException("later"); }, 1, TimeUnit.MILLISECONDS);
		executor.execute(() -> { });
		executor.shutdown();
		assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		assertEquals(2, failures.size());
		assertTrue(failures.get(0) instanceof OutOfMemoryError);
		assertTrue(failures.get(1) instanceof IllegalStateException);
	}
}
