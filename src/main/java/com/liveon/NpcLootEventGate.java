package com.liveon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.client.game.ItemStack;

/** Matches RuneLite's two notifications for the same NPC loot without hiding separate kills. */
final class NpcLootEventGate
{
	private static final int MAX_PRIMARY_EVENTS = 512;
	private final Map<String, Integer> primaryEvents = new LinkedHashMap<>();

	void recordPrimary(String account, String source, Collection<ItemStack> items, int tick)
	{
		String key = key(account, source, items, tick);
		primaryEvents.merge(key, 1, Integer::sum);
		if (primaryEvents.size() > MAX_PRIMARY_EVENTS)
		{
			String oldest = primaryEvents.keySet().iterator().next();
			primaryEvents.remove(oldest);
		}
	}

	boolean consumePrimary(String account, String source, Collection<ItemStack> items, int tick)
	{
		String key = key(account, source, items, tick);
		Integer count = primaryEvents.get(key);
		if (count == null) return false;
		if (count == 1) primaryEvents.remove(key);
		else primaryEvents.put(key, count - 1);
		return true;
	}

	void clear()
	{
		primaryEvents.clear();
	}

	private static String key(String account, String source, Collection<ItemStack> items, int tick)
	{
		Map<Integer, Long> quantities = new java.util.TreeMap<>();
		List<String> parts = new ArrayList<>();
		for (ItemStack item : items)
		{
			quantities.merge(item.getId(), (long) item.getQuantity(), Long::sum);
		}
		quantities.forEach((id, quantity) -> parts.add(id + "x" + quantity));
		return tick + "|" + normalize(account) + "|" + normalize(source) + "|" + String.join(",", parts);
	}

	private static String normalize(String text)
	{
		return text == null ? "" : text.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
