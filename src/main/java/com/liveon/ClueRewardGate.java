package com.liveon;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.client.game.ItemStack;

/** Lets the reward widget cover clues for which RuneLite emits no loot event. */
final class ClueRewardGate
{
	private String officialFingerprint = "";
	private int officialTick = Integer.MIN_VALUE;

	void clear()
	{
		officialFingerprint = "";
		officialTick = Integer.MIN_VALUE;
	}

	void rememberOfficial(Collection<ItemStack> items, int tick)
	{
		officialFingerprint = fingerprint(items);
		officialTick = tick;
	}

	boolean shouldSendWidget(Collection<ItemStack> items, int rewardTick)
	{
		return !(officialTick >= rewardTick - 1 && officialTick <= rewardTick + 1
			&& fingerprint(items).equals(officialFingerprint));
	}

	private static String fingerprint(Collection<ItemStack> items)
	{
		Map<Integer, Long> quantities = new TreeMap<>();
		for (ItemStack item : items)
		{
			if (item != null && item.getQuantity() > 0)
			{
				quantities.merge(item.getId(), (long) item.getQuantity(), Long::sum);
			}
		}
		return quantities.toString();
	}
}
