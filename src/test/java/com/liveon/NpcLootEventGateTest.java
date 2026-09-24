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
		assertFalse(gate.consumePrimary("Milico", "Cow", Arrays.asList(new ItemStack(526, 3)), 101));
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
		assertTrue(fallback(gate, game, deliveries, "Milico", "Cow", COW_LOOT, 100).getAsBoolean());
		assertEquals(1, deliveries.get());
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
		assertFalse(gate.consumePrimary("Milico", "Cow", COW_LOOT, 101));
		assertFalse(gate.consumePrimary("Milico", "Cow", Arrays.asList(new ItemStack(526, 2)), 100));
		assertTrue(gate.consumePrimary("Milico", "Cow", COW_LOOT, 100));
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

	private static BooleanSupplier fallback(NpcLootEventGate gate, AtomicReference<GameState> game,
		AtomicInteger deliveries, String account, String source, List<ItemStack> items, int tick)
	{
		return DropSessionGate.task(() -> DropSessionGate.evaluate(1, 1, account, account, true, game.get()),
			() -> { if (!gate.consumePrimary(account, source, items, tick)) deliveries.incrementAndGet(); });
	}
}
