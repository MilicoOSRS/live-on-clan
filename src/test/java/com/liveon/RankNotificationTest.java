package com.liveon;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RankNotificationTest
{
	private static final long DAY = 24L * 60L * 60L * 1000L;

	@Test
	public void formatsAvailableRankMessage()
	{
		assertEquals(
			"[Live On] Promoção de rank disponível: Sargento! Solicite pelo plugin do clã.",
			ClanMessagesPlugin.rankNotificationMessage("Sargento"));
	}

	@Test
	public void notifiesImmediatelyForHigherRank()
	{
		assertEquals(true, ClanMessagesPlugin.shouldNotifyAvailableRank(1, 2, -1, 0L, 100L, false));
	}

	@Test
	public void waitsTwentyFourHoursBeforeRepeatingSameRank()
	{
		assertEquals(false, ClanMessagesPlugin.shouldNotifyAvailableRank(1, 2, 2, 100L, 100L + DAY - 1L, false));
		assertEquals(true, ClanMessagesPlugin.shouldNotifyAvailableRank(1, 2, 2, 100L, 100L + DAY, false));
	}

	@Test
	public void suppressesCurrentAndPendingRanks()
	{
		assertEquals(false, ClanMessagesPlugin.shouldNotifyAvailableRank(2, 2, -1, 0L, 100L, false));
		assertEquals(false, ClanMessagesPlugin.shouldNotifyAvailableRank(1, 2, -1, 0L, 100L, true));
	}
}
