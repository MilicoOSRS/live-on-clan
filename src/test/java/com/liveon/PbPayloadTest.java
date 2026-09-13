package com.liveon;

import java.util.Map;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PbPayloadTest
{
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
	public void readsOverallTimesFromSelectedToaScoreboardTab()
	{
		java.util.List<Map<String, Object>> records = ClanMessagesPlugin.parseToaScoreboardPbs(
			"Expert Mode", Arrays.asList(
				"22:44.40", "24:56.40", "27:36.00", "25:56.40",
				"28:22.80", "-", "", "Not completed"));

		assertEquals(5, records.size());
		assertEquals("Tombs of Amascut", records.get(0).get("boss"));
		assertEquals("Expert Mode", records.get(0).get("mode"));
		assertEquals(1, records.get(0).get("teamSize"));
		assertEquals(1364.4, (Double) records.get(0).get("seconds"), 0.001);
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
