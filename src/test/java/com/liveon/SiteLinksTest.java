package com.liveon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SiteLinksTest
{
	@Test
	public void profileSlugMirrorsTheSite()
	{
		assertEquals("doido-man", ClanMessagesPlugin.playerProfileSlug("Doido Man"));
		assertEquals("doido-man", ClanMessagesPlugin.playerProfileSlug("Doido_Man"));
		assertEquals("doido", ClanMessagesPlugin.playerProfileSlug("<col=ffffff>Doido</col>".replaceAll("<[^>]+>", "")));
		assertEquals("doido-man", ClanMessagesPlugin.playerProfileSlug("Doido Man"));
		assertEquals("", ClanMessagesPlugin.playerProfileSlug(null));
		assertEquals("", ClanMessagesPlugin.playerProfileSlug("   "));
	}

	@Test
	public void profileSlugIgnoresTheLevelOfWorldPlayers()
	{
		assertEquals("doido", ClanMessagesPlugin.playerProfileSlug("Doido  (level-126)"));
		assertEquals("doido-man", ClanMessagesPlugin.playerProfileSlug("Doido Man (level-3)"));
	}

	@Test
	public void buildsProfileAddress()
	{
		assertEquals("https://liveon.gg/jogador/doido", LiveOnUrls.playerProfile("doido"));
		assertEquals("https://liveon.gg/recrutamento", LiveOnUrls.APPLY);
	}

	@Test
	public void formatsDropValuesWithoutRoundingIntoTheWrongUnit()
	{
		assertEquals("500 gp", RecentActivity.shortGp(500));
		assertEquals("50K", RecentActivity.shortGp(50_000));
		assertEquals("1.0M", RecentActivity.shortGp(999_600));
		assertEquals("1.2M", RecentActivity.shortGp(1_234_567));
		assertEquals("999.9M", RecentActivity.shortGp(999_900_000));
		assertEquals("1.0B", RecentActivity.shortGp(999_960_000));
		assertEquals("6.4B", RecentActivity.shortGp(6_440_000_000L));
	}

	@Test
	public void buildsTheDropOfTheDayFromTheServerRanking()
	{
		com.google.gson.Gson gson = new com.google.gson.Gson();
		MvpDropEntry[] ranking = gson.fromJson("[{\"player_name\":\"A\",\"top_drops\":[{\"item\":\"1x Ultor vestige\","
			+ "\"value\":50000000,\"source\":\"Vardorvis\"}]},{\"player_name\":\"B\",\"top_drops\":["
			+ "{\"item\":\"3x Coins\",\"value\":3000000,\"source\":null}]}]", MvpDropEntry[].class);
		RecentActivity activity = RecentActivity.dropOfDay(ranking);
		assertNotNull(activity);
		assertEquals(RecentActivity.DROP_OF_DAY, activity.type);
		assertEquals("A", activity.player_name);
		assertEquals("Drop do dia: Ultor vestige (50.0M)", activity.title);
		assertNull(RecentActivity.dropOfDay(new MvpDropEntry[0]));
	}

	@Test
	public void noDropMeansWaitingCardNotAnError()
	{
		assertNull(RecentActivity.dropOfDay((MvpDropEntry[]) null));
		RecentActivity waiting = RecentActivity.noDropToday();
		assertEquals(RecentActivity.DROP_OF_DAY, waiting.type);
		assertEquals("", waiting.player_name);
	}
}
