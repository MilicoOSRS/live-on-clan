package com.liveon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
	public void buildsTheDropOfTheDayFromTheSitePayload()
	{
		JsonObject drop = new JsonParser().parse("{\"player_name\":\"Hahahaphael\",\"item_name\":\"Ultor vestige\","
			+ "\"quantity\":1,\"value\":50000,\"source\":\"Vardorvis\"}").getAsJsonObject();
		RecentActivity activity = RecentActivity.dropOfDay(drop);
		assertNotNull(activity);
		assertEquals(RecentActivity.DROP_OF_DAY, activity.type);
		assertEquals("Hahahaphael", activity.player_name);
		assertEquals("Drop do dia: Ultor vestige (50K) em Vardorvis", activity.title);

		JsonObject stack = new JsonParser().parse("{\"player_name\":\"A\",\"item_name\":\"Coins\","
			+ "\"quantity\":3,\"value\":0,\"source\":null}").getAsJsonObject();
		assertEquals("Drop do dia: 3x Coins", RecentActivity.dropOfDay(stack).title);
	}

	@Test
	public void noDropMeansWaitingCardNotAnError()
	{
		assertNull(RecentActivity.dropOfDay(null));
		assertNull(RecentActivity.dropOfDay(new JsonObject()));
		RecentActivity waiting = RecentActivity.noDropToday();
		assertEquals(RecentActivity.DROP_OF_DAY, waiting.type);
		assertEquals("", waiting.player_name);
	}
}
