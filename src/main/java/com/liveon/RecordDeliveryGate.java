package com.liveon;

import net.runelite.api.GameState;

/** A persisted request remains bound to its original account and destination. */
final class RecordDeliveryGate
{
	static DropSessionGate.State evaluate(String owner, String local, String authenticated,
		boolean enabled, boolean temporaryWorld, boolean rejected, GameState gameState)
	{
		if (!enabled || temporaryWorld || rejected || owner == null || owner.isEmpty())
			return DropSessionGate.State.CANCEL;
		if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING)
			return DropSessionGate.State.CANCEL;
		if (local == null || local.isEmpty()) return DropSessionGate.State.WAIT;
		if (!owner.equalsIgnoreCase(local)) return DropSessionGate.State.CANCEL;
		if (gameState != GameState.LOGGED_IN || !owner.equalsIgnoreCase(authenticated))
			return DropSessionGate.State.WAIT;
		return DropSessionGate.State.READY;
	}
}
