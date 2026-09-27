package com.liveon;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RankNotificationTest
{
	@Test
	public void notifiesForHigherRank()
	{
		assertEquals(true, ClanMessagesPlugin.shouldNotifyAvailableRank(1, 2, -1, false));
	}

	@Test
	public void neverRepeatsRankAlreadyAnnouncedThisSession()
	{
		assertEquals(false, ClanMessagesPlugin.shouldNotifyAvailableRank(1, 2, 2, false));
	}

	@Test
	public void neverAnnouncesLowerRankAfterHigherOne()
	{
		// Cadete (4) was announced; a late recalculation to Aluno (2) must stay silent.
		assertEquals(false, ClanMessagesPlugin.shouldNotifyAvailableRank(1, 2, 4, false));
	}

	@Test
	public void stillAnnouncesGenuinelyHigherRankLater()
	{
		assertEquals(true, ClanMessagesPlugin.shouldNotifyAvailableRank(1, 5, 4, false));
	}

	@Test
	public void suppressesCurrentAndPendingRanks()
	{
		assertEquals(false, ClanMessagesPlugin.shouldNotifyAvailableRank(2, 2, -1, false));
		assertEquals(false, ClanMessagesPlugin.shouldNotifyAvailableRank(1, 2, -1, true));
	}
}
