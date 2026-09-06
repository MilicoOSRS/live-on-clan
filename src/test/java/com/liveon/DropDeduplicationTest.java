package com.liveon;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropDeduplicationTest
{
	@Test
	public void clanAnnouncementDoesNotDuplicateOneItemFromMultiItemLoot()
	{
		Map<String, Integer> cache = new HashMap<>();
		assertTrue(ClanMessagesPlugin.claimDropFingerprint(cache, "player",
			Arrays.asList("bonesx1", "fangx1"), false, 100));
		assertFalse(ClanMessagesPlugin.claimDropFingerprint(cache, "player",
			Arrays.asList("fangx1"), true, 103));
		assertTrue(ClanMessagesPlugin.claimDropFingerprint(cache, "player",
			Arrays.asList("fangx1"), true, 110));
	}

	@Test
	public void differentAccountsQuantitiesAndResetTicksRemainIndependent()
	{
		Map<String, Integer> cache = new HashMap<>();
		assertTrue(ClanMessagesPlugin.claimDropFingerprint(cache, "one", Arrays.asList("fangx1"), false, 100));
		assertTrue(ClanMessagesPlugin.claimDropFingerprint(cache, "two", Arrays.asList("fangx1"), true, 103));
		assertTrue(ClanMessagesPlugin.claimDropFingerprint(cache, "one", Arrays.asList("fangx2"), true, 103));
		assertTrue(ClanMessagesPlugin.claimDropFingerprint(cache, "one", Arrays.asList("fangx1"), false, 1));
	}

	@Test
	public void raidModeCanReplaceOnlyItsGenericRaidSource()
	{
		assertTrue(ClanMessagesPlugin.raidSourceMatches(
			"Chambers of Xeric", "Chambers of Xeric Challenge Mode"));
		assertTrue(ClanMessagesPlugin.raidSourceMatches(
			"Theatre of Blood", "Theatre of Blood Hard Mode"));
		assertTrue(ClanMessagesPlugin.raidSourceMatches(
			"Tombs of Amascut", "Tombs of Amascut: Expert Mode"));
		assertFalse(ClanMessagesPlugin.raidSourceMatches(
			"Chambers of Xeric", "Tombs of Amascut: Expert Mode"));
		assertFalse(ClanMessagesPlugin.raidSourceMatches("Vorkath", "Vorkath"));
	}

	@Test
	public void gauntletSourcesMatchRuneLiteLootNames()
	{
		assertEquals("The Gauntlet",
			ClanMessagesPlugin.standardizeKnownLootSource("Crystalline Hunllef"));
		assertEquals("Corrupted Gauntlet",
			ClanMessagesPlugin.standardizeKnownLootSource("Corrupted Hunllef"));
		assertEquals("Vorkath", ClanMessagesPlugin.standardizeKnownLootSource("Vorkath"));
	}
}
