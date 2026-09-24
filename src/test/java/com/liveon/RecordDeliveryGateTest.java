package com.liveon;

import net.runelite.api.GameState;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordDeliveryGateTest
{
	@Test public void unverifiedAccountWaitsThenSendsOnlyAfterSameAccountIsVerified()
	{
		assertEquals(DropSessionGate.State.WAIT, gate("Tamzz", "Tamzz", "", true, false));
		assertEquals(DropSessionGate.State.WAIT, gate("Tamzz", "Tamzz", "Other", true, false));
		assertEquals(DropSessionGate.State.READY, gate("Tamzz", "Tamzz", "tamzz", true, false));
	}
	@Test public void switchedAccountsAndRevokedConsentNeverSend()
	{
		assertEquals(DropSessionGate.State.CANCEL, gate("Tamzz", "Other", "Other", true, false));
		assertEquals(DropSessionGate.State.CANCEL, gate("Tamzz", "Tamzz", "Tamzz", false, false));
		assertEquals(DropSessionGate.State.CANCEL, gate("Tamzz", "Tamzz", "", true, true));
	}
	@Test public void loadingWaitsWithoutRevokingIdentity()
	{
		assertEquals(DropSessionGate.State.WAIT, RecordDeliveryGate.evaluate("Tamzz", "Tamzz", "Tamzz",
			true, false, false, GameState.LOADING));
		assertEquals(DropSessionGate.State.WAIT, RecordDeliveryGate.evaluate("Tamzz", "", "Tamzz",
			true, false, false, GameState.LOADING));
	}
	private DropSessionGate.State gate(String owner, String local, String auth, boolean enabled, boolean rejected)
	{
		return RecordDeliveryGate.evaluate(owner, local, auth, enabled, false, rejected, GameState.LOGGED_IN);
	}
}
