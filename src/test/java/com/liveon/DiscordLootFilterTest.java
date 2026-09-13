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
	public void usesMatchingDiscordAttachmentReferences()
	{
		assertEquals("attachment://loot.png", ClanMessagesPlugin.discordAttachmentUrl(
			ClanMessagesPlugin.DISCORD_LOOT_ATTACHMENT));
		assertEquals("attachment://pet.png", ClanMessagesPlugin.discordAttachmentUrl(
			ClanMessagesPlugin.DISCORD_PET_ATTACHMENT));
	}

	@Test
	public void limitsDiscordDescriptionsWithoutChangingNormalDrops()
	{
		String normal = "1x Oathplate legs (89.0M)\nYama";
		assertEquals(normal, ClanMessagesPlugin.limitDiscordDescription(normal));

		char[] characters = new char[5000];
		Arrays.fill(characters, 'x');
		String limited = ClanMessagesPlugin.limitDiscordDescription(new String(characters));
		assertEquals(4096, limited.length());
		assertTrue(limited.endsWith("..."));
	}

	@Test
	public void matchesExactNamesWithoutCaseSensitivity()
	{
		assertTrue(ClanMessagesPlugin.matchesDiscordFilter(
			Arrays.asList("crimson kisten"), "Crimson kisten"));
		assertFalse(ClanMessagesPlugin.matchesDiscordFilter(
			Arrays.asList("crimson kisten"), "Crimson kisten (broken)"));
	}

	@Test
	public void zeroValueAllowlistedItemsWaitForChatValue()
	{
		assertTrue(ClanMessagesPlugin.shouldDeferZeroValueDrop("Magus vestige", 0, null));
		assertFalse(ClanMessagesPlugin.shouldDeferZeroValueDrop("Magus vestige", 0, 12_000_000L));
		assertFalse(ClanMessagesPlugin.shouldDeferZeroValueDrop("Magus vestige", 12_000_000L, null));
		assertFalse(ClanMessagesPlugin.shouldDeferZeroValueDrop("Rune platebody", 0, null));
	}

	@Test
	public void bingoDeliveryDoesNotConsumeNormalFallbackAndSuppressesRepeat()
	{
		java.util.Map<String, Integer> bingo = new java.util.HashMap<>();
		java.util.Map<String, Integer> normal = new java.util.HashMap<>();
		java.util.List<String> item = Arrays.asList("magus vestigex1");
		assertTrue(ClanMessagesPlugin.shouldDeferZeroValueDrop("Magus vestige", 0, null));
		assertTrue(ClanMessagesPlugin.claimDropFingerprint(bingo, "player", item, false, 100));
		assertTrue(ClanMessagesPlugin.claimDropFingerprint(normal, "player", item, true, 102));
		assertFalse(ClanMessagesPlugin.claimDropFingerprint(bingo, "player", item, false, 102));
		assertTrue(ClanMessagesPlugin.claimDropFingerprint(bingo, "other", item, false, 102));
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
			"New item added to your collection log: Rune platebody"));
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
			"Valuable drop: 1 x Rune platebody (4,000,000 coins)"));
	}

	@Test
	public void extractsAnyUntradeableDropWithGameValue()
	{
		ClanMessagesPlugin.PendingAllowlistedDrop drop = ClanMessagesPlugin.allowlistedValuableDrop(
			"Untradeable drop: Araxyte fang (18,400,000 coins)");
		assertEquals("Araxyte fang", drop.itemName);
		assertEquals(1, drop.quantity);
		assertEquals(Long.valueOf(18_400_000L), drop.totalValue);

		ClanMessagesPlugin.PendingAllowlistedDrop generic = ClanMessagesPlugin.allowlistedValuableDrop(
			"Untradeable drop: Future untradeable reward (2,500,000 coins)");
		assertEquals("Future untradeable reward", generic.itemName);
		assertEquals(Long.valueOf(2_500_000L), generic.totalValue);
	}
}
