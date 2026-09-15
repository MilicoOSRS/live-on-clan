package com.liveon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropFallbackQueueTest
{
	@Test
	public void waitsForThreeActualTicksAndRunsOnce()
	{
		DropFallbackQueue queue = new DropFallbackQueue(3);
		List<String> sent = new ArrayList<>();
		queue.add(100, () -> sent.add("fallback"));
		for (int i = 0; i < 100; i++) queue.advance(100);
		queue.advance(102);
		assertTrue(sent.isEmpty());
		queue.advance(103);
		queue.advance(104);
		assertEquals(Arrays.asList("fallback"), sent);
	}

	@Test
	public void lootArrivingDuringWaitCancelsOnlyItsMatchingFallback()
	{
		DropFallbackQueue queue = new DropFallbackQueue(3);
		DropNotificationHistory history = new DropNotificationHistory(8);
		List<String> sent = new ArrayList<>();
		queue.add(100, () -> {
			if (history.admit("player", "discord", "Zenyte shard", 1, 103, true)) sent.add("fallback");
		});
		assertTrue(history.admit("player", "discord", "Ashes", 1, 101, false));
		assertTrue(history.admit("player", "discord", "Zenyte shard", 1, 101, false));
		sent.add("loot");
		queue.advance(103);
		assertEquals(Arrays.asList("loot"), sent);
	}

	@Test
	public void normalLootSuppressesPersonalAndClanFallbacksForTheSameDrop()
	{
		DropFallbackQueue queue = new DropFallbackQueue(3);
		DropNotificationHistory history = new DropNotificationHistory(8);
		List<String> sent = new ArrayList<>();
		Runnable personalFallback = () -> {
			if (history.admit("boramosso", "discord", "Zenyte shard", 1, 103, true))
				sent.add("personal fallback");
		};
		Runnable clanFallback = () -> {
			if (history.admit("boramosso", "discord", "Zenyte shard", 1, 103, true))
				sent.add("clan fallback");
		};
		queue.add(100, personalFallback);
		queue.add(100, clanFallback);
		assertTrue(history.admit("boramosso", "discord", "Zenyte shard", 1, 101, false));
		sent.add("normal loot");
		queue.advance(103);
		assertEquals(Arrays.asList("normal loot"), sent);
	}

	@Test
	public void twoFallbackSourcesStillProduceOneNotificationWhenLootEventIsMissing()
	{
		DropFallbackQueue queue = new DropFallbackQueue(3);
		DropNotificationHistory history = new DropNotificationHistory(8);
		List<String> sent = new ArrayList<>();
		queue.add(100, () -> {
			if (history.admit("boramosso", "discord", "Zenyte shard", 1, 103, true))
				sent.add("personal fallback");
		});
		queue.add(100, () -> {
			if (history.admit("boramosso", "discord", "Zenyte shard", 1, 103, true))
				sent.add("clan fallback");
		});
		queue.advance(103);
		assertEquals(1, sent.size());
	}

	@Test
	public void normalLootCanArriveOnAnyTickBeforeFallbackDeadline()
	{
		for (int lootTick = 100; lootTick <= 103; lootTick++)
		{
			DropFallbackQueue queue = new DropFallbackQueue(3);
			DropNotificationHistory history = new DropNotificationHistory(8);
			List<String> sent = new ArrayList<>();
			queue.add(100, () -> {
				if (history.admit("player", "discord", "Zenyte shard", 1, 103, true))
					sent.add("fallback");
			});
			for (int tick = 100; tick < lootTick; tick++) queue.advance(tick);
			assertTrue(history.admit("player", "discord", "Zenyte shard", 1, lootTick, false));
			sent.add("normal loot");
			queue.advance(103);
			assertEquals("loot tick " + lootTick, Arrays.asList("normal loot"), sent);
		}
	}

	@Test
	public void independentPendingDropsAreNotOverwritten()
	{
		DropFallbackQueue queue = new DropFallbackQueue(3);
		List<Integer> sent = new ArrayList<>();
		queue.add(100, () -> sent.add(1));
		queue.add(101, () -> sent.add(2));
		queue.advance(103);
		assertEquals(Arrays.asList(1), sent);
		queue.advance(104);
		assertEquals(Arrays.asList(1, 2), sent);
	}

	@Test
	public void logoutAndTickResetDiscardOldTasks()
	{
		DropFallbackQueue queue = new DropFallbackQueue(3);
		List<String> sent = new ArrayList<>();
		queue.add(100, () -> sent.add("old session"));
		queue.clear();
		queue.advance(103);
		queue.add(200, () -> sent.add("old tick"));
		queue.advance(1);
		queue.advance(203);
		assertTrue(sent.isEmpty());
	}
}
