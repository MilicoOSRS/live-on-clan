package com.liveon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Waits for actual game ticks, rather than client-thread queue iterations. */
final class DropFallbackQueue
{
	private final int delayTicks;
	private final List<Pending> pending = new ArrayList<>();

	DropFallbackQueue(int delayTicks)
	{
		this.delayTicks = delayTicks;
	}

	void add(int tick, Runnable action)
	{
		pending.add(new Pending(tick, action));
	}

	void advance(int tick)
	{
		List<Runnable> ready = new ArrayList<>();
		Iterator<Pending> iterator = pending.iterator();
		while (iterator.hasNext())
		{
			Pending entry = iterator.next();
			if (tick < entry.tick || tick - entry.tick >= delayTicks)
			{
				iterator.remove();
				if (tick >= entry.tick) ready.add(entry.action);
			}
		}
		ready.forEach(Runnable::run);
	}

	void clear()
	{
		pending.clear();
	}

	private static final class Pending
	{
		private final int tick;
		private final Runnable action;

		private Pending(int tick, Runnable action)
		{
			this.tick = tick;
			this.action = action;
		}
	}
}
