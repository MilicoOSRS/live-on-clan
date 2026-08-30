package com.liveon;

import java.util.Map;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PbPayloadTest
{
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
