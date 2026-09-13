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

	@Test
	public void resolvesRaidModesFromRecentCompletionAnnouncements()
	{
		assertEquals("Chambers of Xeric: Challenge Mode", ClanMessagesPlugin.resolveDropSource(
			"Chambers of Xeric", "EVENT", 100, 98, "Chambers of Xeric: Challenge Mode"));
		assertEquals("Theatre of Blood Hard Mode", ClanMessagesPlugin.resolveDropSource(
			"Theatre of Blood", "EVENT", 100, 99, "Theatre of Blood Hard Mode"));
		assertEquals("Tombs of Amascut: Expert Mode", ClanMessagesPlugin.resolveDropSource(
			"Tombs of Amascut", "EVENT", 100, 100, "Tombs of Amascut: Expert Mode"));
	}

	@Test
	public void ignoresStaleOrUnrelatedRaidAnnouncements()
	{
		assertEquals("Chambers of Xeric", ClanMessagesPlugin.resolveDropSource(
			"Chambers of Xeric", "EVENT", 100, 90, "Chambers of Xeric: Challenge Mode"));
		assertEquals("Chambers of Xeric", ClanMessagesPlugin.resolveDropSource(
			"Chambers of Xeric", "EVENT", 100, 99, "Tombs of Amascut: Expert Mode"));
		assertEquals("Chambers of Xeric", ClanMessagesPlugin.resolveDropSource(
			"Chambers of Xeric", "NPC", 100, 99, "Chambers of Xeric: Challenge Mode"));
	}

	@Test
	public void normalizesTaggedAndSpecialLootSources()
	{
		assertEquals("The Gauntlet", ClanMessagesPlugin.resolveDropSource(
			" <col=ff0000>Crystalline Hunllef</col>\u00a0 ", "EVENT", 100, -1000, ""));
		assertEquals("Corrupted Gauntlet", ClanMessagesPlugin.resolveDropSource(
			"Corrupted Hunllef", "EVENT", 100, -1000, ""));
		assertEquals("Nex", ClanMessagesPlugin.resolveDropSource(
			"  Nex  ", "NPC", 100, -1000, ""));
		assertEquals("Loot", ClanMessagesPlugin.resolveDropSource(
			" \u00a0 ", "EVENT", 100, -1000, ""));
	}
}
