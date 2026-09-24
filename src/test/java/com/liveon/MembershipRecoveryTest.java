package com.liveon;

import org.junit.Test;
import static org.junit.Assert.*;

public class MembershipRecoveryTest
{
    @Test public void transientFailureRetriesSameAccountWithBackoff()
    {
        MembershipRecovery recovery = new MembershipRecovery();
        recovery.begin();
        recovery.failed(1000);
        assertFalse(recovery.rejected());
        assertFalse(recovery.due(5999));
        assertTrue(recovery.due(6000));
        recovery.begin();
        recovery.failed(6000);
        assertFalse(recovery.due(15999));
        assertTrue(recovery.due(16000));
        recovery.resolved(true, 999999);
        assertFalse(recovery.rejected());
        assertFalse(recovery.due(Long.MAX_VALUE));
    }

    @Test public void confirmedNonMemberIsNotTreatedAsTransportFailure()
    {
        MembershipRecovery recovery = new MembershipRecovery();
        recovery.resolved(false, 61000);
        assertTrue(recovery.rejected());
        assertFalse(recovery.due(60000));
        assertTrue(recovery.due(61000));
        recovery.accountChanged();
        assertFalse(recovery.rejected());
        assertFalse(recovery.due(Long.MAX_VALUE));
    }

    // onGameTick calls begin() synchronously, right before verifyToken(), specifically so that
    // due() stops returning true on the very next tick instead of waiting for verifyToken()'s
    // own clientThread.invokeLater body to run and clear it a tick later. Without this, the
    // same due()==true condition keeps re-triggering verifyToken() every tick, cancelling and
    // restarting the WOM request each time.
    @Test public void beginImmediatelyStopsDueFromRetriggeringVerification()
    {
        MembershipRecovery recovery = new MembershipRecovery();
        recovery.failed(1000);
        assertTrue(recovery.due(6000));
        recovery.begin();
        assertFalse(recovery.due(6000));
        assertFalse(recovery.due(Long.MAX_VALUE));
    }

    @Test public void repeatedFailuresRemainBoundedAndRecoverable()
    {
        MembershipRecovery recovery = new MembershipRecovery();
        for (int i = 0; i < 1000; i++) {
            recovery.failed(1000);
            assertTrue(recovery.due(61000));
            assertFalse(recovery.rejected());
        }
        recovery.stale(1000);
        assertTrue(recovery.due(6000));
    }
}
