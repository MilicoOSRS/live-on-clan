package com.liveon;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.GameState;

/** Separates temporary loading from revocation of an accepted drop's session. */
final class DropSessionGate
{
	enum State { READY, WAIT, CANCEL }

	static State evaluate(long originalGeneration, long currentGeneration,
		String originalAccount, String currentAccount, boolean enabled, GameState gameState)
	{
		if (!enabled || originalGeneration != currentGeneration || originalAccount == null
			|| originalAccount.isEmpty() || !originalAccount.equals(currentAccount)) return State.CANCEL;
		if (gameState == GameState.LOADING || gameState == GameState.CONNECTION_LOST) return State.WAIT;
		return gameState == GameState.LOGGED_IN ? State.READY : State.CANCEL;
	}

	/** ClientThread retains a false-returning task without blocking the game thread. */
	static BooleanSupplier task(Supplier<State> state, Runnable action)
	{
		return () -> {
			State current = state.get();
			if (current == State.WAIT) return false;
			if (current == State.READY) action.run();
			return true;
		};
	}
}
