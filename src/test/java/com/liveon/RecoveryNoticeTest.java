package com.liveon;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecoveryNoticeTest
{
	@Test public void repairedFailureCannotWarnForANewSuccessfulRecord()
	{
		RecoveryNotice notice = new RecoveryNotice();
		notice.failed("first");
		notice.accepted("first");
		notice.queued("unrelated");
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(1000, false, true));
	}
	@Test public void startupLoadingAndEmptyFailuresStaySilent()
	{
		RecoveryNotice notice = new RecoveryNotice();
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(0, true, false));
		notice.queued("one");
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(1000, false, false));
		notice.accepted("one");
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(2000, false, true));
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(10000, false, true));
	}

	@Test public void manyRecordsAndRepeatedFailuresProduceOnlyOneWarning()
	{
		RecoveryNotice notice = new RecoveryNotice();
		notice.queued("one");
		assertEquals(RecoveryNotice.Message.WAITING, notice.poll(0, true, false));
		for (int i = 0; i < 1000; i++)
		{
			notice.failed("record-" + i);
			assertEquals(RecoveryNotice.Message.NONE, notice.poll(i * 1000L, true, false));
		}
	}

	@Test public void recoveryWaitsForEveryAcknowledgementAndQuietPeriod()
	{
		RecoveryNotice notice = new RecoveryNotice();
		notice.failed("discord");
		notice.queued("mvp");
		assertEquals(RecoveryNotice.Message.WAITING, notice.poll(0, false, true));
		notice.accepted("mvp");
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(10000, false, true));
		notice.accepted("discord");
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(11000, false, true));
		assertEquals(RecoveryNotice.Message.RECOVERED, notice.poll(16000, false, true));
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(17000, false, true));
	}

	@Test public void briefFlappingDoesNotCreateMoreChatMessages()
	{
		RecoveryNotice notice = new RecoveryNotice();
		notice.failed("one");
		assertEquals(RecoveryNotice.Message.WAITING, notice.poll(0, false, true));
		notice.accepted("one");
		notice.poll(1000, false, true);
		assertEquals(RecoveryNotice.Message.RECOVERED, notice.poll(6000, false, true));
		notice.failed("two");
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(10000, false, true));
		notice.accepted("two");
		notice.poll(11000, false, true);
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(16000, false, true));
	}

	@Test public void cancellationAndAccountSwitchNeverClaimDelivery()
	{
		RecoveryNotice notice = new RecoveryNotice();
		notice.failed("one");
		notice.queued("two");
		notice.poll(0, false, true);
		notice.accepted("one");
		notice.cancelled("two");
		notice.poll(1000, false, true);
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(6000, false, true));
		notice.reset();
		notice.accepted("stale-one");
		assertEquals(RecoveryNotice.Message.NONE, notice.poll(20000, false, true));
	}
}
