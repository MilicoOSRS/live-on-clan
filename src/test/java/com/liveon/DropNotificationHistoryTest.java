package com.liveon;

import org.junit.Test;
import static org.junit.Assert.*;

public class DropNotificationHistoryTest
{
	@Test
	public void fullLootSuppressesOnlyMatchingFallbackItems()
	{
		DropNotificationHistory history = new DropNotificationHistory(8);
		assertTrue(history.admit("player", "discord", "Ashes", 1, 100, false));
		assertTrue(history.admit("player", "discord", "Zenyte shard", 1, 100, false));
		assertFalse(history.admit("player", "discord", "Zenyte shard", 1, 103, true));
		assertTrue(history.admit("player", "discord", "Rune platebody", 1, 103, true));
	}

	@Test
	public void statsAcceptanceDoesNotBlockDiscordAfterPriceThresholdChanges()
	{
		DropNotificationHistory history = new DropNotificationHistory(8);
		// The GE value qualified for MVP, but was below the Discord threshold.
		assertTrue(history.admit("player", "stats", "Zenyte shard", 1, 100, false));
		assertTrue(history.admit("player", "discord", "Zenyte shard", 1, 103, true));
		assertFalse(history.admit("player", "stats", "Zenyte shard", 1, 103, true));
	}

	@Test
	public void deferredZeroValueItemHasNoNotificationHistory()
	{
		DropNotificationHistory history = new DropNotificationHistory(8);
		assertTrue(history.admit("player", "discord", "Ashes", 1, 100, false));
		assertTrue(history.admit("player", "discord", "Tokkul", 100, 103, true));
		assertFalse(history.admit("player", "discord", "Tokkul", 100, 104, true));
	}

	@Test
	public void fallbackFirstNeverBlocksLaterDirectLootOrAdditionalItems()
	{
		DropNotificationHistory history = new DropNotificationHistory(8);
		assertTrue(history.admit("player", "discord", "Zenyte shard", 1, 100, true));
		assertTrue(history.admit("player", "discord", "Zenyte shard", 1, 101, false));
		assertTrue(history.admit("player", "discord", "Rune platebody", 1, 101, false));
		assertTrue(history.admit("player", "discord", "Zenyte shard", 1, 102, false));
	}

	@Test
	public void accountsQuantitiesDestinationsAndLaterDropsRemainIndependent()
	{
		DropNotificationHistory history = new DropNotificationHistory(8);
		assertTrue(history.admit("player", "discord", "Zenyte shard", 1, 100, false));
		assertTrue(history.admit("alt", "discord", "Zenyte shard", 1, 103, true));
		assertTrue(history.admit("player", "discord", "Zenyte shard", 2, 103, true));
		assertTrue(history.admit("player", "stats", "Zenyte shard", 1, 103, true));
		assertTrue(history.admit("player", "discord", "Zenyte shard", 1, 109, true));
		history.clear();
		assertTrue(history.admit("player", "discord", "Zenyte shard", 1, 110, true));
	}
}
