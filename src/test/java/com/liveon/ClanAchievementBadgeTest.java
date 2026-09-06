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
}
