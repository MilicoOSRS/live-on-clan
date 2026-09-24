package com.liveon;

import java.util.Arrays;
import net.runelite.api.gameval.NpcID;
import net.runelite.http.api.loottracker.LootRecordType;
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
	public void routesNpcEventsWithoutDependingOnChatFallbacks()
	{
		assertTrue(ClanMessagesPlugin.usesServerNpcLoot(NpcID.YAMA, "Yama"));
		assertFalse(ClanMessagesPlugin.usesNpcLootReceived(NpcID.YAMA, "Yama"));
		assertFalse(ClanMessagesPlugin.usesServerNpcLoot(1, "Goblin"));
		assertTrue(ClanMessagesPlugin.usesNpcLootReceived(1, "Goblin"));
		assertTrue(ClanMessagesPlugin.usesServerNpcLoot(1, "Hallowed Sepulchre Grand Coffin"));
		assertFalse(ClanMessagesPlugin.usesNpcLootReceived(1, "Hallowed Sepulchre Grand Coffin"));
		assertFalse(ClanMessagesPlugin.usesNpcLootReceived(1, "Araxxor"));
		assertTrue(ClanMessagesPlugin.usesLootReceived(LootRecordType.EVENT, "Chambers of Xeric"));
		assertTrue(ClanMessagesPlugin.usesLootReceived(LootRecordType.PICKPOCKET, "Vyrewatch Sentinel"));
		assertTrue(ClanMessagesPlugin.usesLootReceived(LootRecordType.NPC, "Araxxor"));
		assertFalse(ClanMessagesPlugin.usesLootReceived(LootRecordType.NPC, "Goblin"));
		assertFalse(ClanMessagesPlugin.usesLootReceived(LootRecordType.PLAYER, "Player"));
	}

	@Test
	public void letsExplicitAllowlistBypassMissingPriceButNeverDenylist()
	{
		assertTrue(ClanMessagesPlugin.shouldNotifyDiscordItem(false, true, 0, 1_000_000));
		assertFalse(ClanMessagesPlugin.shouldNotifyDiscordItem(true, true, 0, 1_000_000));
		assertFalse(ClanMessagesPlugin.shouldNotifyDiscordItem(false, false, 0, 0));
		assertFalse(ClanMessagesPlugin.shouldNotifyDiscordItem(false, false, 999_999, 1_000_000));
		assertTrue(ClanMessagesPlugin.shouldNotifyDiscordItem(false, false, 1_000_000, 1_000_000));
	}

	@Test
	public void clueTotalCanIncludeSmallerItemsWithoutOverridingDenylist()
	{
		assertTrue(ClanMessagesPlugin.shouldNotifyDiscordItem(false, false, 200_000, 1_000_000, true));
		assertFalse(ClanMessagesPlugin.shouldNotifyDiscordItem(true, false, 200_000, 1_000_000, true));
		assertFalse(ClanMessagesPlugin.shouldNotifyDiscordItem(false, false, 200_000, 1_000_000, false));
	}

	@Test
	public void recognizesOnlyExactExceptionalLootMessages()
	{
		assertEquals("Pyramid Plunder", ClanMessagesPlugin.exceptionalGameMessageLoot(
			"You have found a Pharaoh's sceptre! It fell on the floor.").getKey());
		for (String message : new String[] {
			"You catch a giant blue krill!", "You catch a golden haddock!",
			"You catch a orangefin!", "You catch a huge halibut!",
			"You catch a purplefin!", "You catch a swift marlin!"
		})
		{
			assertEquals(message, "Deep sea trawling",
				ClanMessagesPlugin.exceptionalGameMessageLoot(message).getKey());
		}
		assertNull(ClanMessagesPlugin.exceptionalGameMessageLoot(
			"Akazudo received special loot from a raid: Tumeken's shadow."));
	}

	@Test
	public void bingoDeliveryUsesItsOwnDuplicateHistory()
	{
		java.util.Map<String, Integer> bingo = new java.util.HashMap<>();
		java.util.Map<String, Integer> normal = new java.util.HashMap<>();
		java.util.List<String> item = Arrays.asList("magus vestigex1");
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
	public void parsesClueCompletionBeforeRewardWidgetLoads()
	{
		java.util.Map.Entry<String, Integer> clue = ClanMessagesPlugin.parseClueCompletion(
			"You have completed 320 hard Treasure Trails.");
		assertEquals("hard", clue.getKey());
		assertEquals(Integer.valueOf(320), clue.getValue());
		assertNull(ClanMessagesPlugin.parseClueCompletion(
			"Your treasure is worth around 73,479,532 coins!"));
		assertNull(ClanMessagesPlugin.parseClueCompletion(
			"Well done, you've completed the Treasure Trail!"));
	}

}
