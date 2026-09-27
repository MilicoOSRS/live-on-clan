package com.liveon;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.runelite.api.GameState;
import net.runelite.client.game.ItemStack;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import static org.junit.Assert.*;

public class NpcLootEventGateTest
{
	private static final List<ItemStack> COW_LOOT = Arrays.asList(new ItemStack(526, 1),
		new ItemStack(2132, 1), new ItemStack(1739, 1));

	@Test
	public void splitStacksMatchTheirGroupedTrackerEvent()
	{
		NpcLootEventGate gate = new NpcLootEventGate();
		gate.recordPrimary("Milico", "Cow", Arrays.asList(new ItemStack(526, 1), new ItemStack(526, 2)), 100);
		assertTrue(gate.consumePrimary("Milico", "Cow", Arrays.asList(new ItemStack(526, 3)), 100));
		assertFalse(gate.consumePrimary("Milico", "Cow", Arrays.asList(new ItemStack(526, 3)), 100));
	}

	@Test
	public void trackerOnlyLootWaitsForLoadingThenSendsOnce()
	{
		assertTrue(ClanMessagesPlugin.usesNpcTrackerFallback(LootRecordType.NPC, "Cow"));
		NpcLootEventGate gate = new NpcLootEventGate();
		AtomicReference<GameState> game = new AtomicReference<>(GameState.LOADING);
		AtomicInteger deliveries = new AtomicInteger();
		BooleanSupplier fallback = fallback(gate, game, deliveries, "Milico", "Cow", COW_LOOT, 100);
		assertFalse(fallback.getAsBoolean());
		assertEquals(0, deliveries.get());
		game.set(GameState.LOGGED_IN);
		assertTrue(fallback.getAsBoolean());
		assertEquals(1, deliveries.get());
	}

	@Test
	public void primaryBeforeTrackerSuppressesOnlyItsMatchingFallback()
	{
		NpcLootEventGate gate = new NpcLootEventGate();
		AtomicReference<GameState> game = new AtomicReference<>(GameState.LOGGED_IN);
		AtomicInteger deliveries = new AtomicInteger();
		gate.recordPrimary("Milico", "Cow", COW_LOOT, 100);
		assertTrue(fallback(gate, game, deliveries, "Milico", "Cow", COW_LOOT, 100).getAsBoolean());
		assertEquals(0, deliveries.get());
		// A later kill with the same loot and no primary event still goes through the fallback.
		assertTrue(fallback(gate, game, deliveries, "Milico", "Cow", COW_LOOT, 105).getAsBoolean());
		assertEquals(1, deliveries.get());
	}

	@Test
	public void extraCopiesFromOtherPluginsAreIgnoredForOneTick()
	{
		NpcLootEventGate gate = new NpcLootEventGate();
		AtomicReference<GameState> game = new AtomicReference<>(GameState.LOGGED_IN);
		AtomicInteger deliveries = new AtomicInteger();
		gate.recordPrimary("MeHZAO", "Hellhound", COW_LOOT, 100);
		for (int tick : new int[] {100, 100, 101})
		{
			assertTrue(fallback(gate, game, deliveries, "MeHZAO", "Hellhound", COW_LOOT, tick).getAsBoolean());
		}
		assertEquals(0, deliveries.get());
		assertFalse(gate.isRepeatOfConsumed("MeHZAO", "Hellhound", COW_LOOT, 102));
	}

	@Test
	public void trackerCopyOneTickLateOrEarlyIsSentOnce()
	{
		AtomicReference<GameState> game = new AtomicReference<>(GameState.LOGGED_IN);
		AtomicInteger deliveries = new AtomicInteger();
		NpcLootEventGate late = new NpcLootEventGate();
		primary(late, deliveries, "Milico", "Cow", COW_LOOT, 100);
		fallback(late, game, deliveries, "Milico", "Cow", COW_LOOT, 101).getAsBoolean();
		NpcLootEventGate early = new NpcLootEventGate();
		fallback(early, game, deliveries, "Milico", "Cow", COW_LOOT, 100).getAsBoolean();
		primary(early, deliveries, "Milico", "Cow", COW_LOOT, 101);
		assertEquals(2, deliveries.get());
	}

	@Test
	public void identicalKillsAreEachSentOnceInEitherOrder()
	{
		NpcLootEventGate gate = new NpcLootEventGate();
		AtomicReference<GameState> game = new AtomicReference<>(GameState.LOGGED_IN);
		AtomicInteger deliveries = new AtomicInteger();
		fallback(gate, game, deliveries, "Milico", "Cow", COW_LOOT, 100).getAsBoolean();
		fallback(gate, game, deliveries, "Milico", "Cow", COW_LOOT, 100).getAsBoolean();
		primary(gate, deliveries, "Milico", "Cow", COW_LOOT, 101);
		primary(gate, deliveries, "Milico", "Cow", COW_LOOT, 101);
		assertEquals(2, deliveries.get());
		primary(gate, deliveries, "Milico", "Cow", COW_LOOT, 110);
		assertEquals(3, deliveries.get());
	}

	@Test
	public void trackerBeforePrimaryWaitsAndThenAvoidsDuplicate()
	{
		NpcLootEventGate gate = new NpcLootEventGate();
		AtomicReference<GameState> game = new AtomicReference<>(GameState.LOADING);
		AtomicInteger deliveries = new AtomicInteger();
		BooleanSupplier fallback = fallback(gate, game, deliveries, "Milico", "Cow", COW_LOOT, 100);
		assertFalse(fallback.getAsBoolean());
		gate.recordPrimary("Milico", "Cow", COW_LOOT, 100);
		game.set(GameState.LOGGED_IN);
		assertTrue(fallback.getAsBoolean());
		assertEquals(0, deliveries.get());
	}

	@Test
	public void identicalKillsKeepTheirOwnPrimaryMatches()
	{
		NpcLootEventGate gate = new NpcLootEventGate();
		gate.recordPrimary("Milico", "Cow", COW_LOOT, 100);
		gate.recordPrimary("Milico", "Cow", COW_LOOT, 100);
		assertTrue(gate.consumePrimary("Milico", "Cow", COW_LOOT, 100));
		assertTrue(gate.consumePrimary("Milico", "Cow", COW_LOOT, 100));
		assertFalse(gate.consumePrimary("Milico", "Cow", COW_LOOT, 100));
	}

	@Test
	public void accountTickAndItemsDoNotStealAnotherDrop()
	{
		NpcLootEventGate gate = new NpcLootEventGate();
		gate.recordPrimary("Milico", "Cow", COW_LOOT, 100);
		assertFalse(gate.consumePrimary("Other", "Cow", COW_LOOT, 100));
		assertFalse(gate.consumePrimary("Milico", "Cow", COW_LOOT, 102));
		// Loot that shares no item with the primary belongs to another kill.
		assertFalse(gate.consumePrimary("Milico", "Cow", Arrays.asList(new ItemStack(995, 2)), 100));
		assertTrue(gate.consumePrimary("Milico", "Cow", COW_LOOT, 100));
	}

	@Test
	public void bonecrusherBonesAreTheOnlyItemLeftInEitherOrder()
	{
		// The ground shows only the drop; the server loot behind the tracker copy still lists the bones.
		List<ItemStack> ground = Arrays.asList(new ItemStack(21270, 1));
		List<ItemStack> tracker = Arrays.asList(new ItemStack(21270, 1), new ItemStack(526, 1));
		NpcLootEventGate gate = new NpcLootEventGate();
		gate.recordPrimary("Boramosso", "Greater abyssal demon", ground, 100);
		List<ItemStack> extra = gate.takePrimary("Boramosso", "Greater abyssal demon", tracker, 100);
		assertEquals(1, extra.size());
		assertEquals(526, extra.get(0).getId());
		gate.recordFallback("MeHZAO", "Hellhound", tracker, 100);
		assertTrue(gate.takeFallback("MeHZAO", "Hellhound", ground, 101).isEmpty());
	}

	@Test
	public void sharedItemMatchPrefersTheKillWithMostItemsInCommon()
	{
		NpcLootEventGate gate = new NpcLootEventGate();
		gate.recordPrimary("Milico", "Cow", Arrays.asList(new ItemStack(526, 1), new ItemStack(2132, 1)), 100);
		gate.recordPrimary("Milico", "Cow", Arrays.asList(new ItemStack(526, 1), new ItemStack(1739, 1)), 100);
		assertTrue(gate.consumePrimary("Milico", "Cow", Arrays.asList(new ItemStack(526, 1), new ItemStack(1739, 1)), 100));
		assertTrue(gate.consumePrimary("Milico", "Cow", Arrays.asList(new ItemStack(526, 1), new ItemStack(2132, 1)), 100));
	}

	@Test
	public void logoutCancelsWaitingFallbackAndSpecialNpcKeepsExistingRoute()
	{
		assertFalse(ClanMessagesPlugin.usesNpcTrackerFallback(LootRecordType.NPC, "Araxxor"));
		assertFalse(ClanMessagesPlugin.usesNpcTrackerFallback(LootRecordType.EVENT, "Chambers of Xeric"));
		NpcLootEventGate gate = new NpcLootEventGate();
		AtomicReference<GameState> game = new AtomicReference<>(GameState.LOADING);
		AtomicInteger deliveries = new AtomicInteger();
		BooleanSupplier fallback = fallback(gate, game, deliveries, "Milico", "Cow", COW_LOOT, 100);
		assertFalse(fallback.getAsBoolean());
		game.set(GameState.LOGIN_SCREEN);
		assertTrue(fallback.getAsBoolean());
		assertEquals(0, deliveries.get());
	}

	private static void primary(NpcLootEventGate gate, AtomicInteger deliveries, String account,
		String source, List<ItemStack> items, int tick)
	{
		List<ItemStack> uncovered = gate.takeFallback(account, source, items, tick);
		if (uncovered != null && uncovered.isEmpty()) return;
		gate.recordPrimary(account, source, items, tick);
		deliveries.incrementAndGet();
	}

	private static BooleanSupplier fallback(NpcLootEventGate gate, AtomicReference<GameState> game,
		AtomicInteger deliveries, String account, String source, List<ItemStack> items, int tick)
	{
		return DropSessionGate.task(() -> DropSessionGate.evaluate(1, 1, account, account, true, game.get()),
			() -> {
				if (gate.consumePrimary(account, source, items, tick)
					|| gate.isRepeatOfConsumed(account, source, items, tick)) return;
				gate.recordFallback(account, source, items, tick);
				deliveries.incrementAndGet();
			});
	}
}
