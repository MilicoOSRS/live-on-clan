package com.liveon;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.runelite.api.GameState;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropSessionGateTest
{
	@Test
	public void distinguishesLoadingFromRevokedSessions()
	{
		assertEquals(DropSessionGate.State.READY, state(GameState.LOGGED_IN));
		assertEquals(DropSessionGate.State.WAIT, state(GameState.LOADING));
		assertEquals(DropSessionGate.State.WAIT, state(GameState.CONNECTION_LOST));
		assertEquals(DropSessionGate.State.CANCEL, state(GameState.LOGIN_SCREEN));
		assertEquals(DropSessionGate.State.CANCEL, state(GameState.HOPPING));
		assertEquals(DropSessionGate.State.CANCEL,
			DropSessionGate.evaluate(1, 2, "Milico", "Milico", true, GameState.LOADING));
		assertEquals(DropSessionGate.State.CANCEL,
			DropSessionGate.evaluate(1, 1, "Milico", "Other", true, GameState.LOGGED_IN));
		assertEquals(DropSessionGate.State.CANCEL,
			DropSessionGate.evaluate(1, 1, "Milico", "Milico", false, GameState.LOADING));
	}

	@Test
	public void frameAndTimeoutWaitThenDeliverOnlyOnce()
	{
		AtomicReference<GameState> game = new AtomicReference<>(GameState.LOADING);
		AtomicInteger deliveries = new AtomicInteger();
		AtomicBoolean claimed = new AtomicBoolean();
		Runnable delivery = () -> { if (claimed.compareAndSet(false, true)) deliveries.incrementAndGet(); };
		BooleanSupplier frame = DropSessionGate.task(() -> state(game.get()), delivery);
		BooleanSupplier timeout = DropSessionGate.task(() -> state(game.get()), delivery);
		for (int tick = 0; tick < 100; tick++)
		{
			assertFalse(frame.getAsBoolean());
			assertFalse(timeout.getAsBoolean());
		}
		assertEquals(0, deliveries.get());
		game.set(GameState.LOGGED_IN);
		assertTrue(frame.getAsBoolean());
		assertTrue(timeout.getAsBoolean());
		assertEquals(1, deliveries.get());
	}

	@Test
	public void leavingSessionCancelsWaitingCapture()
	{
		AtomicReference<GameState> game = new AtomicReference<>(GameState.LOADING);
		BooleanSupplier capture = DropSessionGate.task(() -> state(game.get()), () -> fail("Must cancel"));
		assertFalse(capture.getAsBoolean());
		game.set(GameState.LOGIN_SCREEN);
		assertTrue(capture.getAsBoolean());
	}

	private static DropSessionGate.State state(GameState game)
	{
		return DropSessionGate.evaluate(1, 1, "Milico", "Milico", true, game);
	}
}
