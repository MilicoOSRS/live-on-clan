package com.liveon;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveOnPanelTest
{
	@Test
	public void onlyExpandsClanRecordsWhenVisibleTextIsTruncated()
	{
		assertFalse(LiveOnPanel.activityNeedsExpansion(
			"TOSTAS", "Novo melhor tempo do clã em Corrupted Hunllef", "", false, true));
		assertTrue(LiveOnPanel.activityNeedsExpansion(
			"Very Long Player Name", "Novo melhor tempo do clã em Corrupted Hunllef", "", false, true));
		assertTrue(LiveOnPanel.activityNeedsExpansion(
			"TOSTAS", "Novo melhor tempo do clã em An Extremely Long Encounter Name", "", false, true));
	}
}
