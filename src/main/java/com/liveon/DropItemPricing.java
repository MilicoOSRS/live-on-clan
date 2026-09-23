package com.liveon;

import java.util.function.IntToLongFunction;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;

/** Item values used consistently for Discord and MVP drop decisions. */
final class DropItemPricing
{
	private DropItemPricing() {}

	static long unitPrice(ItemManager itemManager, int itemId)
	{
		return unitPrice(itemId, itemManager::getItemPrice,
			id -> itemManager.getItemPriceWithSource(id, true),
			id -> itemManager.getItemComposition(id).getPrice());
	}

	static long unitPrice(int itemId, IntToLongFunction price, IntToLongFunction gePrice,
		IntToLongFunction storePrice)
	{
		long inferred = 0;
		if (itemId == ItemID.ARAXYTE_FANG)
		{
			inferred = gePrice.applyAsLong(ItemID.ETCHED_ARAXYTE_FANG);
		}
		else if (itemId == ItemID.NOXIOUS_HALBERD_PART_1
			|| itemId == ItemID.NOXIOUS_HALBERD_PART_2
			|| itemId == ItemID.NOXIOUS_HALBERD_PART_3)
		{
			inferred = gePrice.applyAsLong(ItemID.NOXIOUS_HALBERD) / 3;
		}
		else if (itemId == ItemID.MOKHAIOTL_CLOTH)
		{
			inferred = gePrice.applyAsLong(ItemID.CONFLICTION_GAUNTLETS)
				- gePrice.applyAsLong(ItemID.ZENYTE_BRACELET_ENCHANTED)
				- 10_000L * gePrice.applyAsLong(ItemID.DEMON_TEAR);
		}
		if (inferred > 0) return inferred;
		long direct = price.applyAsLong(itemId);
		return direct > 0 ? direct : Math.max(0L, storePrice.applyAsLong(itemId));
	}
}
