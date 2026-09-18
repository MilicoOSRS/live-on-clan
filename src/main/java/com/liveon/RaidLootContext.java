package com.liveon;

import java.util.HashMap;
import java.util.Map;

/** Recent raid completions waiting for their matching chest. */
final class RaidLootContext
{
	static final int MAX_AGE_TICKS = 1000;
	private static final String[] RAIDS = {
		"Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut"
	};
	private final Map<String, Completion> pendingByRaid = new HashMap<>();

	void remember(String source, int count, int tick)
	{
		String raid = raidFamily(source);
		if (raid != null && count > 0) pendingByRaid.put(raid, new Completion(source, count, tick));
	}

	Completion take(String source, String category, int tick)
	{
		if (!"EVENT".equals(category)) return null;
		String raid = raidFamily(source);
		if (raid == null) return null;
		Completion pending = pendingByRaid.get(raid);
		if (pending == null) return null;
		if (tick < pending.tick || tick - pending.tick > MAX_AGE_TICKS)
		{
			pendingByRaid.remove(raid);
			return null;
		}
		return pendingByRaid.remove(raid);
	}

	static boolean isRaid(String source)
	{
		return raidFamily(source) != null;
	}

	static boolean isGenericRaid(String source)
	{
		String raid = raidFamily(source);
		return raid != null && raid.equalsIgnoreCase(source);
	}

	private static String raidFamily(String source)
	{
		for (String raid : RAIDS)
			if (ClanMessagesPlugin.raidSourceMatches(raid, source)) return raid;
		return null;
	}

	void clear()
	{
		pendingByRaid.clear();
	}

	static final class Completion
	{
		final String source;
		final int count;
		final int tick;

		Completion(String source, int count, int tick)
		{
			this.source = source;
			this.count = count;
			this.tick = tick;
		}
	}
}
