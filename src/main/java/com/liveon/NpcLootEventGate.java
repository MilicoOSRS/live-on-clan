package com.liveon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.client.game.ItemStack;

/**
 * Matches RuneLite's two notifications for the same NPC loot without hiding separate kills.
 * The ground event and the loot tracker copy (server loot packet) can differ by a tick and by
 * items a Bonecrusher or Ash sanctifier consumed, so they are matched by the items they share.
 */
final class NpcLootEventGate
{
	private static final int MAX_PRIMARY_EVENTS = 512;
	private static final int REPEAT_TICKS = 1;
	private final List<Pending> primaryEvents = new ArrayList<>();
	private final List<Pending> fallbackEvents = new ArrayList<>();
	private final Map<String, Integer> consumedEvents = new LinkedHashMap<>();

	private static final class Pending
	{
		private final String owner;
		private final int tick;
		private final Map<Integer, Long> items;

		private Pending(String owner, int tick, Map<Integer, Long> items)
		{
			this.owner = owner;
			this.tick = tick;
			this.items = items;
		}
	}

	void recordPrimary(String account, String source, Collection<ItemStack> items, int tick)
	{
		record(primaryEvents, account, source, items, tick);
	}

	/**
	 * Matches a loot tracker notification against a delivered primary event. Returns null when no
	 * primary shares items with it, otherwise the tracker items the primary did not deliver
	 * (empty when the primary already covered everything).
	 */
	List<ItemStack> takePrimary(String account, String source, Collection<ItemStack> items, int tick)
	{
		List<ItemStack> uncovered = take(primaryEvents, account, source, items, tick);
		if (uncovered != null)
		{
			String loot = lootKey(account, source, items);
			consumedEvents.remove(loot);
			consumedEvents.put(loot, tick);
			if (consumedEvents.size() > MAX_PRIMARY_EVENTS)
			{
				consumedEvents.remove(consumedEvents.keySet().iterator().next());
			}
		}
		return uncovered;
	}

	boolean consumePrimary(String account, String source, Collection<ItemStack> items, int tick)
	{
		List<ItemStack> uncovered = takePrimary(account, source, items, tick);
		return uncovered != null && uncovered.isEmpty();
	}

	/** Remembers loot delivered by the tracker fallback, which can arrive before its primary. */
	void recordFallback(String account, String source, Collection<ItemStack> items, int tick)
	{
		record(fallbackEvents, account, source, items, tick);
	}

	/**
	 * Matches a primary event against loot the tracker fallback already delivered. Returns null
	 * when nothing matched, otherwise the primary items the fallback did not deliver.
	 */
	List<ItemStack> takeFallback(String account, String source, Collection<ItemStack> items, int tick)
	{
		return take(fallbackEvents, account, source, items, tick);
	}

	/** True for an extra copy (another plugin re-posting) of loot that already matched its primary. */
	boolean isRepeatOfConsumed(String account, String source, Collection<ItemStack> items, int tick)
	{
		Integer consumedTick = consumedEvents.get(lootKey(account, source, items));
		return consumedTick != null && Math.abs(tick - consumedTick) <= REPEAT_TICKS;
	}

	void clear()
	{
		primaryEvents.clear();
		fallbackEvents.clear();
		consumedEvents.clear();
	}

	private static void record(List<Pending> events, String account, String source,
		Collection<ItemStack> items, int tick)
	{
		Map<Integer, Long> quantities = quantities(items);
		if (quantities.isEmpty()) return;
		events.add(new Pending(owner(account, source), tick, quantities));
		if (events.size() > MAX_PRIMARY_EVENTS) events.remove(0);
	}

	private static List<ItemStack> take(List<Pending> events, String account, String source,
		Collection<ItemStack> items, int tick)
	{
		String owner = owner(account, source);
		Map<Integer, Long> wanted = quantities(items);
		Pending best = null;
		long bestOverlap = 0;
		for (Pending pending : events)
		{
			if (!pending.owner.equals(owner) || Math.abs(pending.tick - tick) > REPEAT_TICKS) continue;
			long overlap = overlap(pending.items, wanted);
			// Prefer the event that shares the most items; on a tie keep the oldest one.
			if (overlap > bestOverlap)
			{
				best = pending;
				bestOverlap = overlap;
			}
		}
		if (best == null) return null;
		List<ItemStack> uncovered = new ArrayList<>();
		for (Map.Entry<Integer, Long> entry : wanted.entrySet())
		{
			long available = best.items.getOrDefault(entry.getKey(), 0L);
			long shared = Math.min(available, entry.getValue());
			if (available - shared > 0) best.items.put(entry.getKey(), available - shared);
			else best.items.remove(entry.getKey());
			long left = entry.getValue() - shared;
			if (left > 0) uncovered.add(new ItemStack(entry.getKey(), (int) Math.min(Integer.MAX_VALUE, left)));
		}
		if (best.items.isEmpty()) events.remove(best);
		return uncovered;
	}

	private static long overlap(Map<Integer, Long> first, Map<Integer, Long> second)
	{
		long shared = 0;
		for (Map.Entry<Integer, Long> entry : second.entrySet())
		{
			shared += Math.min(entry.getValue(), first.getOrDefault(entry.getKey(), 0L));
		}
		return shared;
	}

	private static Map<Integer, Long> quantities(Collection<ItemStack> items)
	{
		Map<Integer, Long> quantities = new TreeMap<>();
		for (ItemStack item : items)
		{
			if (item.getQuantity() > 0) quantities.merge(item.getId(), (long) item.getQuantity(), Long::sum);
		}
		return quantities;
	}

	private static String owner(String account, String source)
	{
		return normalize(account) + "|" + normalize(source);
	}

	private static String lootKey(String account, String source, Collection<ItemStack> items)
	{
		List<String> parts = new ArrayList<>();
		quantities(items).forEach((id, quantity) -> parts.add(id + "x" + quantity));
		return owner(account, source) + "|" + String.join(",", parts);
	}

	private static String normalize(String text)
	{
		return text == null ? "" : text.replace(' ', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
