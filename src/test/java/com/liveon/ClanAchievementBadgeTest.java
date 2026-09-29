package com.liveon;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClanAchievementBadgeTest
{
	@Test
	public void recognizesAutomaticClanAchievements()
	{
		assertTrue(ClanMessagesPlugin.isDecoratableClanAchievement(
			"player received a new collection log item: araxyte fang"));
		assertTrue(ClanMessagesPlugin.isDecoratableClanAchievement(
			"player received a drop: noxious blade (10,000,000 coins) from araxxor."));
		assertTrue(ClanMessagesPlugin.isDecoratableClanAchievement(
			"player received special loot from a raid: twisted bow (1,100,056,752 coins)."));
		assertTrue(ClanMessagesPlugin.isDecoratableClanAchievement(
			"player has achieved a new phosani's nightmare personal best: 5:19.20"));
		assertTrue(ClanMessagesPlugin.isDecoratableClanAchievement(
			"player has a funny feeling like they're being followed"));
	}

	@Test
	public void ignoresOrdinaryClanConversation()
	{
		assertFalse(ClanMessagesPlugin.isDecoratableClanAchievement("player: hello clan"));
	}

	@Test
	public void badgesKeepTheMessageColourOpenAfterThem()
	{
		String message = "<col=9a39ff>Hoag B received a drop: Inquisitor's plateskirt</col>";
		int afterName = "<col=9a39ff>Hoag B".length();
		org.junit.Assert.assertEquals("<col=9a39ff>", ClanMessagesPlugin.activeColorTag(message, afterName));
		org.junit.Assert.assertNull(ClanMessagesPlugin.activeColorTag("Hoag B received a drop", 6));
		org.junit.Assert.assertEquals("<col=9a39ff>", ClanMessagesPlugin.activeColorTag(
			"<col=9a39ff><col=ffffff>Hoag</col> B received", "<col=9a39ff><col=ffffff>Hoag</col> B".length()));
	}

	@Test
	public void findsTheNameAfterAnAccountIconAndItsSpace()
	{
		String raw = "<col=8000ff><img=2> Akazudo received a new collection log item: Big bass (892/1717)</col>";
		org.junit.Assert.assertEquals("<col=8000ff><img=2> Akazudo".length(),
			ClanMessagesPlugin.originalIndexAfterVisiblePrefix(raw, "akazudo"));
		org.junit.Assert.assertEquals("Pirozinha".length(),
			ClanMessagesPlugin.originalIndexAfterVisiblePrefix("Pirozinha received a drop:", "pirozinha"));
	}
}
