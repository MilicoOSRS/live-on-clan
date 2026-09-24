package com.liveon;

import java.util.Map;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PbPayloadTest
{
	@Test
	public void filtersJadChallengesWithoutRemovingFightCave()
	{
		assertFalse(PbCategory.isAllowed("Jad Challenge"));
		assertFalse(PbCategory.isAllowed("Jad Challenge II"));
		assertFalse(PbCategory.isAllowed("Six Jad Challenge"));
		assertFalse(PbCategory.isAllowed("Fastest Wave time (former)"));
		assertFalse(PbCategory.isAllowed("TzHaar-Ket-Rak's Challenges"));
		assertFalse(PbCategory.isAllowed("TzHaar-Ket-Rak's First Challenge"));
		assertFalse(PbCategory.isAllowed("TzHaar-Ket-Rak's Fourth Challenge"));
		assertFalse(PbCategory.isAllowed("TzHaar Fight Cave", "Six Jad Challenge"));
		assertTrue(PbCategory.isAllowed("TzTok-Jad"));
		assertTrue(PbCategory.isAllowed("TzHaar Fight Cave"));
		assertTrue(PbCategory.isAllowed("Chambers of Xeric", "Challenge Mode"));
	}

	@Test
	public void treatsLegacyAndCanonicalSoloCategoriesAsEquivalent()
	{
		assertTrue(PbPanel.sameTeamSize(0, 1));
		assertTrue(PbPanel.sameTeamSize(1, 0));
		assertFalse(PbPanel.sameTeamSize(1, 5));
	}

	@Test
	public void readsPlainAndColoredBossPbMessages()
	{
		for (String message : Arrays.asList("Fight duration: 4:31.20 (new personal best)",
			"Fight duration: <col=ff0000>4:31.20</col> (new personal best)",
			"Subdued in 4:31.20 (new personal best)"))
		{
			assertEquals(271.2, (Double) ClanMessagesPlugin.parseChatNewPb(message).get("seconds"), 0.001);
		}
	}

	@Test
	public void recognizesCoxTeamAndNamedRaidTotals()
	{
		Map<String, Object> cox = ClanMessagesPlugin.parseChatNewPb(
			"Congratulations - your raid is complete!\nTeam size: 24+ players Duration: 36:04.20 (new personal best)");
		assertEquals(24, cox.get("teamSize"));
		assertEquals(2164.2, (Double) cox.get("seconds"), 0.001);
		Map<String, Object> toa = ClanMessagesPlugin.parseChatNewPb(
			"Tombs of Amascut: Expert Mode total completion time: 25:00 (new personal best)");
		assertEquals("Tombs of Amascut: Expert Mode", toa.get("boss"));
		assertEquals(1500.0, (Double) toa.get("seconds"), 0.001);
		// Theatre of Blood's total-completion line is now its own "Overall" PB category,
		// distinct from the room/challenge-time PB. It never carries a mode itself - that is
		// resolved later at submission time from whatever pendingPbMode the wave-complete
		// message (parsed elsewhere, moments earlier) already captured.
		Map<String, Object> tob = ClanMessagesPlugin.parseChatNewPb(
			"Theatre of Blood total completion time: 25:28.80 (new personal best)");
		assertEquals("Theatre of Blood", tob.get("boss"));
		assertEquals(1528.8, (Double) tob.get("seconds"), 0.001);
	}

	@Test
	public void readsOwnClanPbAnnouncementsAcrossActivitiesAndModes()
	{
		Map<String, Object> cox = ClanMessagesPlugin.parseClanPbAnnouncement(
			"Tammz has achieved a new Chambers of Xeric (Team Size: Solo) personal best: 16:46", "Tammz");
		assertEquals("Chambers of Xeric", cox.get("boss"));
		assertEquals("Normal", cox.get("mode"));
		assertEquals(0, cox.get("teamSize"));
		assertEquals(1006.0, (Double) cox.get("seconds"), 0.001);

		Map<String, Object> cm = ClanMessagesPlugin.parseClanPbAnnouncement(
			"Tammz has achieved a new Chambers of Xeric Challenge Mode (Team Size: 3) personal best: 35:53", "Tammz");
		assertEquals("Challenge Mode", cm.get("mode"));
		assertEquals(3, cm.get("teamSize"));
		Map<String, Object> tob = ClanMessagesPlugin.parseClanPbAnnouncement(
			"Tammz has achieved a new Theatre of Blood: Hard Mode (Team Size: 5) personal best: 22:03.20", "Tammz");
		assertEquals("Theatre of Blood", tob.get("boss"));
		assertEquals("Hard Mode", tob.get("mode"));
		assertEquals(5, tob.get("teamSize"));
		Map<String, Object> toa = ClanMessagesPlugin.parseClanPbAnnouncement(
			"Tammz has achieved a new Tombs of Amascut: Expert Mode (Team Size: 2) personal best: 27:10", "Tammz");
		assertEquals("Tombs of Amascut", toa.get("boss"));
		assertEquals("Expert Mode", toa.get("mode"));
		assertEquals(2, toa.get("teamSize"));
		Map<String, Object> boss = ClanMessagesPlugin.parseClanPbAnnouncement(
			"Tammz has achieved a new Vardorvis personal best: 1:23.40", "Tammz");
		assertEquals("Vardorvis", boss.get("boss"));
		assertEquals(83.4, (Double) boss.get("seconds"), 0.001);
	}

	@Test
	public void rejectsOtherPlayersAndAmbiguousGroupClanPbAnnouncements()
	{
		assertNull(ClanMessagesPlugin.parseClanPbAnnouncement(
			"Other player has achieved a new Chambers of Xeric (Team Size: Solo) personal best: 16:46", "Tammz"));
		assertNull(ClanMessagesPlugin.parseClanPbAnnouncement(
			"Tammz has achieved a new Chambers of Xeric personal best: 16:46", "Tammz"));
		assertNull(ClanMessagesPlugin.parseClanPbAnnouncement(
			"Tammz has achieved a new Chambers of Xeric (Team Size: Solo) personal best: 0:00", "Tammz"));
		assertNull(ClanMessagesPlugin.parseClanPbAnnouncement(
			"Tammz has achieved a new Chambers of Xeric (Team Size: 25) personal best: 16:46", "Tammz"));
		assertNull(ClanMessagesPlugin.parseClanPbAnnouncement(
			"Tammz has completed Chambers of Xeric in 16:46", "Tammz"));
	}

	@Test
	public void readsPhosaniSoloPbFromPlainGameMessage()
	{
		Map<String, Object> pb = ClanMessagesPlugin.parseChatNewPb(
			"Team size: Solo Fight duration: 4:31.20 (new personal best)");
		assertEquals(0, pb.get("teamSize"));
		assertEquals(271.2, (Double) pb.get("seconds"), 0.001);
		assertRaid("Phosani's Nightmare", "Phosani's Nightmare", "");
	}

	@Test
	public void normalizesSoloButPreservesRealGroupSizes()
	{
		assertEquals(0, ClanMessagesPlugin.pbPayload("The Nightmare", 0, 300).get("teamSize"));
		assertEquals(0, ClanMessagesPlugin.pbPayload("The Nightmare", 1, 300).get("teamSize"));
		assertEquals(2, ClanMessagesPlugin.pbPayload("The Nightmare", 2, 300).get("teamSize"));
		assertEquals(5, ClanMessagesPlugin.pbPayload("The Nightmare", 5, 300).get("teamSize"));
		assertEquals(0, ClanMessagesPlugin.pbPayload("Tombs of Amascut", 1, 1500).get("teamSize"));
		assertEquals(3, ClanMessagesPlugin.pbPayload("Tombs of Amascut", 3, 1500).get("teamSize"));
	}

	@Test
	public void ignoresOrdinaryFightTimesWithoutNewPb()
	{
		assertNull(ClanMessagesPlugin.parseChatNewPb("Team size: Solo Fight duration: 4:31.20"));
		assertNull(ClanMessagesPlugin.parseChatNewPb("Fight duration: 4:31.20 Personal best: 4:20.00"));
	}

	@Test
	public void mapsTobScoreboardVarbitOrder()
	{
		assertEquals("Normal", ClanMessagesPlugin.tobScoreboardMode(0));
		assertEquals("Entry Mode", ClanMessagesPlugin.tobScoreboardMode(1));
		assertEquals("Hard Mode", ClanMessagesPlugin.tobScoreboardMode(2));
	}

	@Test
	public void mapsToaScoreboardVarbitOrder()
	{
		assertEquals("Normal", ClanMessagesPlugin.toaScoreboardMode(0));
		assertEquals("Entry Mode", ClanMessagesPlugin.toaScoreboardMode(1));
		assertEquals("Expert Mode", ClanMessagesPlugin.toaScoreboardMode(2));
	}

	@Test
	public void recognizesRaidModeNameVariants()
	{
		assertRaid("Theatre of Blood: Hard Mode", "Theatre of Blood", "Hard Mode");
		assertRaid("Theatre of Blood (Hard Mode)", "Theatre of Blood", "Hard Mode");
		assertRaid("Theatre of Blood HM", "Theatre of Blood", "Hard Mode");
		assertRaid("Theatre of Blood - Hard", "Theatre of Blood", "Hard Mode");
		assertRaid("Theatre of Blood: Entry Mode", "Theatre of Blood", "Entry Mode");
		assertRaid("Theatre of Blood", "Theatre of Blood", "Normal");
		assertRaid("Tombs of Amascut: Expert Mode", "Tombs of Amascut", "Expert Mode");
		assertRaid("Tombs of Amascut (Entry Mode)", "Tombs of Amascut", "Entry Mode");
		assertRaid("Chambers of Xeric: Challenge Mode", "Chambers of Xeric", "Challenge Mode");
		assertRaid("Chambers of Xeric CM", "Chambers of Xeric", "Challenge Mode");
	}

	@Test
	public void separatesNormalAndAwakenedDesertTreasureBosses()
	{
		assertRaid("The Whisperer", "The Whisperer", "Normal");
		assertRaid("The Whisperer - Awakened", "The Whisperer", "Awakened");
		assertRaid("The Leviathan Awakened", "The Leviathan", "Awakened");
		assertRaid("Vardorvis (Awakened)", "Vardorvis", "Awakened");
		assertRaid("Duke Sucellus", "Duke Sucellus", "Normal");
	}

	@Test
	public void unifiesCombatAchievementAndAdventureLogNames()
	{
		assertRaid("TzTok-Jad", "TzHaar Fight Cave", "");
		assertRaid("TzHaar Fight Cave", "TzHaar Fight Cave", "");
		assertRaid("TzKal-Zuk", "Inferno", "");
		assertRaid("The Inferno", "Inferno", "");
	}

	@Test
	public void unifiesActivityBossNamesAcrossPbSources()
	{
		assertRaid("Crystalline Hunllef", "Gauntlet", "");
		assertRaid("The Gauntlet", "Gauntlet", "");
		assertRaid("Corrupted Hunllef", "Corrupted Gauntlet", "");
		assertRaid("The Corrupted Gauntlet", "Corrupted Gauntlet", "");
		assertRaid("Sol Heredit", "Fortis Colosseum", "");
		assertRaid("Colosseum", "Fortis Colosseum", "");
		assertRaid("The Hueycoatl", "Hueycoatl", "");
		assertRaid("The Phantom Muspah", "Phantom Muspah", "");
		assertRaid("The Royal Titans", "Royal Titans", "");
		assertRaid("Mad Angel", "The Mad Angel", "");
	}

	@Test
	public void keepsNightmareEncountersSeparate()
	{
		assertRaid("Nightmare", "The Nightmare", "");
		assertRaid("The Nightmare", "The Nightmare", "");
		assertRaid("Phosani's Nightmare", "Phosani's Nightmare", "");
		assertRaid("Phosani Nightmare", "Phosani's Nightmare", "");
	}

	@Test
	public void readsTobHardRoomAndOverallTimesFromAdventureLog()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseAdventureLogPbs(Arrays.asList(
			"Theatre of Blood - Hard",
			"Fastest Room time - (Team size: 3 player hard mode):",
			"20:21.60",
			"Fastest Overall time - (Team size: 3 player hard mode):",
			"22:19.30",
			"Fastest Room time - (Team size: 4 player hard mode): 17:13.20",
			"Fastest Overall time - (Team size: 4 player hard mode): 19:40.80",
			""));

		assertEquals(4, records.size());
		assertAdventureRecord(records.get(0), "Hard Mode", 3, "ROOM", 1221.60);
		assertAdventureRecord(records.get(1), "Hard Mode", 3, "OVERALL", 1339.30);
		assertAdventureRecord(records.get(2), "Hard Mode", 4, "ROOM", 1033.20);
		assertAdventureRecord(records.get(3), "Hard Mode", 4, "OVERALL", 1180.80);
	}

	@Test
	public void requiresTeamSizeForGroupPbSources()
	{
		assertTrue(ClanMessagesPlugin.requiresExplicitPbTeamSize("Chambers of Xeric"));
		assertTrue(ClanMessagesPlugin.requiresExplicitPbTeamSize("Theatre of Blood"));
		assertTrue(ClanMessagesPlugin.requiresExplicitPbTeamSize("Tombs of Amascut"));
		assertTrue(ClanMessagesPlugin.requiresExplicitPbTeamSize("The Nightmare"));
		assertFalse(ClanMessagesPlugin.requiresExplicitPbTeamSize("Phosani's Nightmare"));
		assertFalse(ClanMessagesPlugin.requiresExplicitPbTeamSize("Vorkath"));
	}

	@Test
	public void validatesAdventureLogOwner()
	{
		assertEquals("Cita", ClanMessagesPlugin.adventureLogOwnerFromLines(Arrays.asList(
			"", "<col=800000>The Exploits of Cita</col>", "Tombs of Amascut")));
		assertFalse(ClanMessagesPlugin.isOwnAdventureLog("Milico", "Milico", "Old owner"));
		assertTrue(ClanMessagesPlugin.isOwnAdventureLog("Milico", "Raids", "Milico"));
		assertFalse(ClanMessagesPlugin.isOwnAdventureLog("Milico", "Milico", "Other player"));
		assertTrue(ClanMessagesPlugin.isOwnAdventureLog("Milico", "", "Milico"));
	}

	@Test
	public void preservesEveryCoxTeamSizeFromAdventureLog()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseAdventureLogPbs(Arrays.asList(
			"Chambers of Xeric - Challenge mode",
			"Fastest run - (Team size: Solo): 37:59.40",
			"Fastest run - (Team size: 2 players): 30:25.20",
			"Fastest run - (Team size: 3 players): 25:23.40",
			"Fastest run - (Team size: 4 players): 26:03.60",
			"Fastest run - (Team size: 5 players): 22:33.00",
			"Fastest run - (Team size: 6 players): 27:13.80",
			""));

		assertEquals(6, records.size());
		assertEquals(0, records.get(0).get("teamSize"));
		assertEquals(2, records.get(1).get("teamSize"));
		assertEquals(5, records.get(4).get("teamSize"));
		assertEquals(1353.0, (Double) records.get(4).get("seconds"), 0.001);
		for (Map<String, Object> record : records)
		{
			assertEquals("Chambers of Xeric", record.get("boss"));
			assertEquals("Challenge Mode", record.get("mode"));
		}
	}

	@Test
	public void readsAdventureLogWithoutBlankLinesBetweenOwnerAndBoss()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseAdventureLogPbs(Arrays.asList(
			"Stuartzin",
			"Theatre of Blood - Hard",
			"Fastest Room time - (Team size: Solo): 25:05",
			"Fastest Overall time - (Team size: Solo): 28:33",
			"Fastest Room time - (Team size: 2 player): 20:43"));

		assertEquals(3, records.size());
		assertAdventureRecord(records.get(0), "Hard Mode", 0, "ROOM", 1505.0);
		assertAdventureRecord(records.get(1), "Hard Mode", 0, "OVERALL", 1713.0);
		assertAdventureRecord(records.get(2), "Hard Mode", 2, "ROOM", 1243.0);
	}

	@Test
	public void readsAllSupportedCounterTimeLayouts()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseAdventureLogPbs(Arrays.asList(
			"The Exploits of Cita",
			"Vorkath",
			"Fastest kill: 1:21.00",
			"Chambers of Xeric",
			"Fastest run - (Team size: 2 players):",
			"30:25.20",
			"Tombs of Amascut",
			"Fastest Room time - (Team size: Solo): 30:16",
			"Fastest Overall time - (Team size: Solo): 34:28",
			"Tombs of Amascut - Expert",
			"Fastest Room time - (Team size: 3 player): 32:15",
			"Fastest Overall time - (Team size: 3 player): 35:23"));

		assertEquals(6, records.size());
		assertEquals("Vorkath", records.get(0).get("boss"));
		assertEquals(81.0, (Double) records.get(0).get("seconds"), 0.001);
		assertEquals("Chambers of Xeric", records.get(1).get("boss"));
		assertEquals(2, records.get(1).get("teamSize"));
		assertEquals("Tombs of Amascut", records.get(2).get("boss"));
		assertEquals("Normal", records.get(2).get("mode"));
		assertEquals(0, records.get(2).get("teamSize"));
		assertEquals("ROOM", records.get(2).get("timeType"));
		assertEquals(1816.0, (Double) records.get(2).get("seconds"), 0.001);
		assertEquals("Normal", records.get(3).get("mode"));
		assertEquals("OVERALL", records.get(3).get("timeType"));
		assertEquals(2068.0, (Double) records.get(3).get("seconds"), 0.001);
		assertEquals("Expert Mode", records.get(4).get("mode"));
		assertEquals(3, records.get(4).get("teamSize"));
		assertEquals("ROOM", records.get(4).get("timeType"));
		assertEquals(1935.0, (Double) records.get(4).get("seconds"), 0.001);
		assertEquals("Expert Mode", records.get(5).get("mode"));
		assertEquals("OVERALL", records.get(5).get("timeType"));
		assertEquals(2123.0, (Double) records.get(5).get("seconds"), 0.001);
	}

	@Test
	public void identifiesCoxChallengeModeFromHeadingAndRequiresTeamSize()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseAdventureLogPbs(Arrays.asList(
			"Chambers of Xeric - Challenge mode",
			"Fastest run: 25:00",
			"Fastest run - (Team size: 5 players): 22:33"));

		assertEquals(1, records.size());
		assertEquals("Chambers of Xeric", records.get(0).get("boss"));
		assertEquals("Challenge Mode", records.get(0).get("mode"));
		assertEquals(5, records.get(0).get("teamSize"));
	}

	@Test
	public void readsExactCoxRaidsCounterLayout()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseAdventureLogPbs(Arrays.asList(
			"Raids",
			"Chambers of Xeric",
			"Fastest run - (Team size: 3 players): 36:29",
			"Fastest run - (Team size: 24+ players): 1:14:15",
			"Chambers of Xeric - Challenge mode",
			"Fastest run: -",
			"Theatre of Blood - Entry"));

		assertEquals(2, records.size());
		assertEquals("Chambers of Xeric", records.get(0).get("boss"));
		assertEquals("Normal", records.get(0).get("mode"));
		assertEquals(3, records.get(0).get("teamSize"));
		assertEquals(2189.0, (Double) records.get(0).get("seconds"), 0.001);
		assertEquals(24, records.get(1).get("teamSize"));
		assertEquals(4455.0, (Double) records.get(1).get("seconds"), 0.001);
	}

	@Test
	public void ignoresUnsupportedFastestRowsInsteadOfCreatingCategories()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseAdventureLogPbs(Arrays.asList(
			"Jad Challenge",
			"Fastest Wave time (former): 17:14.40",
			"Fastest Wave time (former): 21:11",
			"TzHaar Fight Cave",
			"Fastest run: 35:00"));

		assertEquals(1, records.size());
		assertEquals("TzHaar Fight Cave", records.get(0).get("boss"));
	}

	@Test
	public void readsOverallTimesFromSelectedToaScoreboardTab()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseToaScoreboardPbs(
			"Expert Mode", Arrays.asList(
				"22:44.40", "24:56.40", "27:36.00", "25:56.40",
				"28:22.80", "-", "", "Not completed"));

		assertEquals(5, records.size());
		assertEquals("Tombs of Amascut", records.get(0).get("boss"));
		assertEquals("Expert Mode", records.get(0).get("mode"));
		assertEquals(0, records.get(0).get("teamSize"));
		assertEquals(1364.4, (Double) records.get(0).get("seconds"), 0.001);
		assertEquals("OVERALL", records.get(0).get("timeType"));
		assertEquals(5, records.get(4).get("teamSize"));
		assertEquals(1702.8, (Double) records.get(4).get("seconds"), 0.001);
	}

	@Test
	public void readsRoomAndOverallTimesFromSelectedTobScoreboardTab()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseTobScoreboardPbs(
			"Hard Mode",
			Arrays.asList("-", "-", "20:21.60", "17:13.20", "16:48.60"),
			Arrays.asList("-", "-", "22:19.80", "19:40.80", "18:30.60"));

		assertEquals(6, records.size());
		assertAdventureRecord(records.get(0), "Hard Mode", 3, "ROOM", 1221.60);
		assertAdventureRecord(records.get(1), "Hard Mode", 3, "OVERALL", 1339.80);
		assertAdventureRecord(records.get(4), "Hard Mode", 5, "ROOM", 1008.60);
		assertAdventureRecord(records.get(5), "Hard Mode", 5, "OVERALL", 1110.60);
	}

	@Test
	public void preservesTobCategoryWhenPreparingScoreboardSubmission()
	{
		Map<String, Object> parsed = ClanMessagesPlugin.parseTobScoreboardPbs(
			"Normal", Arrays.asList("-", "26:05", "-", "-", "-"),
			Arrays.asList("-", "28:54", "-", "-", "-")).get(0);
		Map<String, Object> payload = ClanMessagesPlugin.scoreboardPbPayload(parsed);

		assertEquals("Theatre of Blood", payload.get("boss"));
		assertEquals("Normal", payload.get("mode"));
		assertEquals(2, payload.get("teamSize"));
		assertEquals("ROOM", payload.get("timeType"));
		assertEquals(1565.0, (Double) payload.get("seconds"), 0.001);
	}

	@Test
	public void readsOfficialCombatAchievementBossPage()
	{
		Map<String, Object> parsed = ClanMessagesPlugin.parseCombatAchievementBossPb(Arrays.asList(
			"Tasks Completed: 9/9",
			"Combat Achievements - Maggot King",
			"Kill Count: 313",
			"Personal Best: 1:03.00"));
		assertEquals("Maggot King", parsed.get("boss"));
		assertEquals(63.0, (Double) parsed.get("seconds"), 0.001);
	}

	@Test
	public void readsPersonalBestInsideCombinedBossDetails()
	{
		Map<String, Object> parsed = ClanMessagesPlugin.parseCombatAchievementBossPb(Arrays.asList(
			"Combat Achievements - Maggot King",
			"Combat Level: 741 Kill Count: 313 Personal Best: 1:03.00"));
		assertEquals("Maggot King", parsed.get("boss"));
		assertEquals(63.0, (Double) parsed.get("seconds"), 0.001);
	}

	@Test
	public void readsJadFromOfficialCombatAchievementWidgets()
	{
		Map<String, Object> parsed = ClanMessagesPlugin.parseCombatAchievementBossWidgets(
			"TzTok-Jad",
			Arrays.asList("Combat Level: 702", "Kill Count: 1", "Personal Best: 1:06:13.20"));
		assertEquals("TzTok-Jad", parsed.get("boss"));
		assertEquals(3973.20, (Double) parsed.get("seconds"), 0.001);
	}

	@Test
	public void readsVorkathFromOfficialCombatAchievementWidgets()
	{
		Map<String, Object> parsed = ClanMessagesPlugin.parseCombatAchievementBossWidgets(
			"Combat Achievements - Vorkath",
			Arrays.asList("Combat Level: 732 Kill Count: 702 Personal Best: 1:21.00"));
		assertEquals("Vorkath", parsed.get("boss"));
		assertEquals(81.0, (Double) parsed.get("seconds"), 0.001);
	}

	@Test
	public void rejectsIncompleteCombatAchievementPage()
	{
		assertEquals(null, ClanMessagesPlugin.parseCombatAchievementBossPb(Arrays.asList(
			"Combat Achievements - Maggot King", "Kill Count: 313")));
		assertEquals(null, ClanMessagesPlugin.parseCombatAchievementBossPb(Arrays.asList(
			"Personal Best: 1:03.00")));
	}

	@Test
	public void readsPhysicalBossStatisticsBoard()
	{
		Map<String, Object> parsed = ClanMessagesPlugin.parseBossStatisticsBoardPb(Arrays.asList(
			"Maggot King Statistics",
			"Personal Killcount<br>313",
			"Global Killcount<br>17,149,976",
			"Personal Best Time<br>1:03.00",
			"Global Best Time<br>0:44.80"));
		assertEquals("Maggot King", parsed.get("boss"));
		assertEquals(63.0, (Double) parsed.get("seconds"), 0.001);
	}

	@Test
	public void rejectsGlobalOnlyBossStatisticsBoard()
	{
		assertEquals(null, ClanMessagesPlugin.parseBossStatisticsBoardPb(Arrays.asList(
			"Maggot King Statistics", "Global Best Time<br>0:44.80")));
	}

	@Test
	public void readsNormalAndAwakenedTimesFromBossStatisticsBoard()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseBossStatisticsBoardPbs(Arrays.asList(
			"The Whisperer Statistics",
			"Personal Best Time<br>2:05.40",
			"Awakened Personal Best Time<br>3:48.20"));
		assertEquals(2, records.size());
		assertEquals("", records.get(0).get("mode"));
		assertEquals(125.4, (Double) records.get(0).get("seconds"), 0.001);
		assertEquals("Awakened", records.get(1).get("mode"));
		assertEquals(228.2, (Double) records.get(1).get("seconds"), 0.001);
	}

	@Test
	public void readsExactAwakenedLeviathanBoardFormat()
	{
		Map<String, Object> parsed = ClanMessagesPlugin.parseBossStatisticsBoardPb(Arrays.asList(
			"Leviathan (Awakened) Statistics",
			"Personal Killcount<br>2",
			"Personal Best Time<br>5:37.20"));
		Map<String, Object> payload = ClanMessagesPlugin.pbPayload(
			(String) parsed.get("boss"), 0, (Double) parsed.get("seconds"));
		assertEquals("The Leviathan", payload.get("boss"));
		assertEquals("Awakened", payload.get("mode"));
		assertEquals(337.2, (Double) payload.get("seconds"), 0.001);
	}

	@Test
	public void dedupSignatureIgnoresTimeTypeSoBothPbSourcesMatch()
	{
		// The personal ToA "total completion time" message resolves to boss/mode via
		// pbPayload(...) and submits with timeType "OVERALL". The clan-wide PB announcement
		// for the exact same completion resolves the same raw text through the same
		// pbPayload(...) and submits with timeType "". Both must collapse to the same dedup
		// signature or the plugin fires two requests for a single PB.
		Map<String, Object> fromGameMessage = ClanMessagesPlugin.pbPayload("Tombs of Amascut: Expert Mode", 3, 1500.0);
		Map<String, Object> fromClanAnnouncement = ClanMessagesPlugin.pbPayload("Tombs of Amascut: Expert Mode", 3, 1500.0);
		String gameSignature = ClanMessagesPlugin.pbDedupSignature("Tamzz",
			(String) fromGameMessage.get("boss"), (String) fromGameMessage.get("mode"),
			(Integer) fromGameMessage.get("teamSize"), 1500.0);
		String announcementSignature = ClanMessagesPlugin.pbDedupSignature("Tamzz",
			(String) fromClanAnnouncement.get("boss"), (String) fromClanAnnouncement.get("mode"),
			(Integer) fromClanAnnouncement.get("teamSize"), 1500.0);
		assertEquals(gameSignature, announcementSignature);
	}

	@Test
	public void dedupSignatureStillDistinguishesDifferentRecords()
	{
		String base = ClanMessagesPlugin.pbDedupSignature("Tamzz", "Theatre of Blood", "Hard Mode", 5, 900.0);
		assertFalse(base.equals(ClanMessagesPlugin.pbDedupSignature("Tamzz", "Theatre of Blood", "Hard Mode", 5, 901.0)));
		assertFalse(base.equals(ClanMessagesPlugin.pbDedupSignature("Tamzz", "Theatre of Blood", "Hard Mode", 4, 900.0)));
		assertFalse(base.equals(ClanMessagesPlugin.pbDedupSignature("Tamzz", "Theatre of Blood", "Normal", 5, 900.0)));
		assertFalse(base.equals(ClanMessagesPlugin.pbDedupSignature("Noujain", "Theatre of Blood", "Hard Mode", 5, 900.0)));
	}

	// Real message from a Theatre of Blood Entry Mode run: unlike ToA's total-completion line,
	// ToB's completion time line never repeats the difficulty - only the wave-complete line in
	// the same chat message does. The plugin must recover "Entry Mode" from here, or it silently
	// defaults to Normal when the pending PB is later submitted.
	@Test
	public void capturesDifficultyFromTheatreOfBloodWaveCompleteLine()
	{
		Map<String, Object> parsed = ClanMessagesPlugin.parseChatNewPb(
			"Wave 'The Final Challenge' (Entry Mode) complete!<br>Duration: 2:16.80<br>"
				+ "Theatre of Blood completion time: 12:41.40 (new personal best)");
		assertEquals(761.4, (Double) parsed.get("seconds"), 0.001);
		assertEquals("Entry Mode", parsed.get("mode"));
		assertFalse("this message never names the raid itself", parsed.containsKey("boss"));

		Map<String, Object> payload = ClanMessagesPlugin.pbPayload(
			"Theatre of Blood " + parsed.get("mode"), 0, (Double) parsed.get("seconds"));
		assertEquals("Theatre of Blood", payload.get("boss"));
		assertEquals("Entry Mode", payload.get("mode"));
	}

	private static void assertRaid(String recorded, String boss, String mode)
	{
		Map<String, Object> payload = ClanMessagesPlugin.pbPayload(recorded, 2, 123.45);
		assertEquals(boss, payload.get("boss"));
		assertEquals(mode, payload.get("mode"));
		assertEquals(2, payload.get("teamSize"));
		assertEquals(123.45, (Double) payload.get("seconds"), 0.001);
	}

	private static void assertAdventureRecord(Map<String, Object> record, String mode, int teamSize,
		String timeType, double seconds)
	{
		assertEquals("Theatre of Blood", record.get("boss"));
		assertEquals(mode, record.get("mode"));
		assertEquals(teamSize, record.get("teamSize"));
		assertEquals(timeType, record.get("timeType"));
		assertEquals(seconds, (Double) record.get("seconds"), 0.001);
	}
}
