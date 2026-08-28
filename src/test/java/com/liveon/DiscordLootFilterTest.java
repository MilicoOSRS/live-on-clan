package com.liveon;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DiscordLootFilterTest
{
	@Test
	public void matchesExactNamesWithoutCaseSensitivity()
	{
		assertTrue(ClanMessagesPlugin.matchesDiscordFilter(
			Arrays.asList("crimson kisten"), "Crimson kisten"));
		assertFalse(ClanMessagesPlugin.matchesDiscordFilter(
			Arrays.asList("crimson kisten"), "Crimson kisten (broken)"));
	}

	@Test
	public void matchesTrailingWildcardVariants()
	{
		assertTrue(ClanMessagesPlugin.matchesDiscordFilter(
			Arrays.asList("elder venator*"), "Elder venator bow"));
		assertTrue(ClanMessagesPlugin.matchesDiscordFilter(
			Arrays.asList("araxyte fang*"), "Araxyte fang (uncharged)"));
		assertFalse(ClanMessagesPlugin.matchesDiscordFilter(
			Arrays.asList("araxyte fang*"), "Amulet of rancour"));
	}

	@Test
	public void extractsOnlyAllowlistedLocalCollectionLogItems()
	{
		assertEquals("Crimson kisten", ClanMessagesPlugin.allowlistedCollectionItem(
			"New item added to your collection log: Crimson kisten"));
		assertEquals("Araxyte fang", ClanMessagesPlugin.allowlistedCollectionItem(
			"Collection log: Araxyte fang."));
		assertNull(ClanMessagesPlugin.allowlistedCollectionItem(
			"New item added to your collection log: Big bones"));
	}

	@Test
	public void extractsAllowlistedValuableDropsWithReliableValue()
	{
		ClanMessagesPlugin.PendingAllowlistedDrop drop = ClanMessagesPlugin.allowlistedValuableDrop(
			"Valuable drop: 2 x Crimson kisten (7,200,000 coins)");
		assertEquals("Crimson kisten", drop.itemName);
		assertEquals(2, drop.quantity);
		assertEquals(Long.valueOf(7_200_000L), drop.totalValue);
	}

	@Test
	public void ignoresValuableDropsOutsideAllowlist()
	{
		assertNull(ClanMessagesPlugin.allowlistedValuableDrop(
			"Valuable drop: 1 x Big bones (4,000,000 coins)"));
	}
}
