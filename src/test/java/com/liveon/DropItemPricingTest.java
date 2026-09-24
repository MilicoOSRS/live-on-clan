package com.liveon;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntToLongFunction;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DropItemPricingTest
{
	@Test
	public void usesDerivedValueForUntradeableComponentsAndFallsBackSafely()
	{
		Map<Integer, Long> ge = new HashMap<>();
		ge.put(ItemID.ETCHED_ARAXYTE_FANG, 50_000_000L);
		ge.put(ItemID.NOXIOUS_HALBERD, 90_000_000L);
		ge.put(ItemID.CONFLICTION_GAUNTLETS, 100_000_000L);
		ge.put(ItemID.ZENYTE_BRACELET_ENCHANTED, 10_000_000L);
		ge.put(ItemID.DEMON_TEAR, 1_000L);
		IntToLongFunction wiki = id -> ge.getOrDefault(id, 0L);
		IntToLongFunction direct = id -> id == ItemID.PHARAOHS_SCEPTRE ? 8_000_000L : 0L;
		IntToLongFunction store = id -> 500L;

		assertEquals(50_000_000L, DropItemPricing.unitPrice(ItemID.ARAXYTE_FANG, direct, wiki, store));
		assertEquals(30_000_000L, DropItemPricing.unitPrice(ItemID.NOXIOUS_HALBERD_PART_1, direct, wiki, store));
		assertEquals(80_000_000L, DropItemPricing.unitPrice(ItemID.MOKHAIOTL_CLOTH, direct, wiki, store));
		assertEquals(8_000_000L, DropItemPricing.unitPrice(ItemID.PHARAOHS_SCEPTRE, direct, wiki, store));
		assertEquals(500L, DropItemPricing.unitPrice(ItemID.BONES, direct, wiki, store));
		assertEquals(500L, DropItemPricing.unitPrice(ItemID.ARAXYTE_FANG, direct, id -> 0, store));
	}
}
