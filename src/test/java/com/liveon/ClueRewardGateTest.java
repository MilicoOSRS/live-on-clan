package com.liveon;

import java.util.Arrays;
import java.util.List;
import net.runelite.client.game.ItemStack;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClueRewardGateTest
{
	private static final List<ItemStack> REWARD = Arrays.asList(
		new ItemStack(1, 1), new ItemStack(2, 2));

	@Test
	public void officialEventBeforeWidgetUsesOnlyOfficialEvent()
	{
		ClueRewardGate gate = new ClueRewardGate();
		gate.rememberOfficial(REWARD, 100);
		assertFalse(gate.shouldSendWidget(Arrays.asList(
			new ItemStack(2, 1), new ItemStack(1, 1), new ItemStack(2, 1)), 100));
	}

	@Test
	public void officialEventAfterWidgetCancelsOnlyThatWidgetFallback()
	{
		ClueRewardGate gate = new ClueRewardGate();
		assertTrue(gate.shouldSendWidget(REWARD, 100));
		gate.rememberOfficial(REWARD, 101);
		assertFalse(gate.shouldSendWidget(REWARD, 100));
		assertTrue(gate.shouldSendWidget(Arrays.asList(new ItemStack(3, 1)), 100));
	}

	@Test
	public void anotherCompletionWithIdenticalItemsIsAllowed()
	{
		ClueRewardGate gate = new ClueRewardGate();
		gate.rememberOfficial(REWARD, 100);
		assertTrue(gate.shouldSendWidget(REWARD, 102));
		gate.clear();
		assertTrue(gate.shouldSendWidget(REWARD, 100));
	}
}
