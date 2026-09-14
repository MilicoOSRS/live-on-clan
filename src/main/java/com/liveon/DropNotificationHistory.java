package com.liveon;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Client-thread history of items admitted by each destination's filters. */
final class DropNotificationHistory
{
	private final int duplicateWindowTicks;
	private final Map<String, Integer> queued = new LinkedHashMap<String, Integer>()
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest)
		{
			return size() > 1024;
		}
	};

	DropNotificationHistory(int duplicateWindowTicks)
	{
		this.duplicateWindowTicks = duplicateWindowTicks;
	}

	boolean admit(String account, String destination, String item, long quantity,
		int tick, boolean fallback)
	{
		String key = normalize(account) + "|" + destination + "|" + normalize(item) + "|" + quantity;
		Integer previous = queued.get(key);
		if (fallback && previous != null && tick >= previous
			&& tick - previous <= duplicateWindowTicks)
		{
			return false;
		}
		// A new direct loot event is authoritative; never discard it because a
		// fallback or another event contained the same item recently.
		queued.put(key, tick);
		return true;
	}

	void clear()
	{
		queued.clear();
	}

	private static String normalize(String value)
	{
		return value.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
