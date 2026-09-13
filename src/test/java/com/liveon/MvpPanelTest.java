package com.liveon;

import javax.swing.SwingUtilities;
import org.junit.Assert;
import org.junit.Test;

public class MvpPanelTest
{
	@Test
	public void rankingEntriesCompareDisplayedDataAndIgnorePositionChange()
	{
		com.google.gson.Gson gson = new com.google.gson.Gson();
		MvpDropEntry first = gson.fromJson("{\"player_name\":\"Leader\",\"total_value\":12000000,"
			+ "\"position\":1,\"top_drops\":[{\"item\":\"1x Fang\",\"value\":12000000,\"source\":\"Nex\"}]}",
			MvpDropEntry.class);
		MvpDropEntry same = gson.fromJson("{\"player_name\":\"Leader\",\"total_value\":12000000,"
			+ "\"position\":1,\"top_drops\":[{\"item\":\"1x Fang\",\"value\":12000000,\"source\":\"Nex\"}]}",
			MvpDropEntry.class);
		MvpDropEntry changed = gson.fromJson("{\"player_name\":\"Leader\",\"total_value\":13000000,"
			+ "\"position\":1,\"top_drops\":[{\"item\":\"1x Fang\",\"value\":13000000,\"source\":\"Nex\"}]}",
			MvpDropEntry.class);
		first.setPositionChange(2);
		Assert.assertEquals(first, same);
		Assert.assertNotEquals(first, changed);
	}

	@Test
	public void populatedDropsKeepSourceTooltipsAndLimitToFive() throws Exception
	{
		StringBuilder json = new StringBuilder("{\"player_name\":\"Leader\",\"total_value\":12000000,\"top_drops\":[");
		for (int index = 1; index <= 6; index++)
		{
			if (index > 1) json.append(',');
			json.append("{\"item\":\"Long example drop ").append(index)
				.append("\",\"source\":\"Example boss\",\"value\":2000000}");
		}
		MvpDropEntry entry = new com.google.gson.Gson().fromJson(json.append("]}").toString(), MvpDropEntry.class);
		MvpPanel[] panel = new MvpPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new MvpPanel(null);
			panel[0].updateDropRanking(java.util.Collections.singletonList(entry), null);
			panel[0].toggleDropExpansion("Leader");
		});
		SwingUtilities.invokeAndWait(() -> {
			java.util.List<String> tips = new java.util.ArrayList<>();
			collectTips(panel[0], tips);
			for (int index = 1; index <= 5; index++)
			{
				Assert.assertTrue(tips.contains("Long example drop " + index + " • Example boss"));
			}
			Assert.assertFalse(tips.contains("Long example drop 6 • Example boss"));
		});
	}

	@Test
	public void populatedEfficiencyDetailsKeepTooltipsAndLimitToFive() throws Exception
	{
		com.google.gson.Gson gson = new com.google.gson.Gson();
		MvpEfficiencyEntry entry = gson.fromJson("{\"player_name\":\"Leader\",\"gained\":21,\"breakdown\":["
			+ "{\"metric\":\"chambers_of_xeric_challenge_mode\",\"gained\":6},"
			+ "{\"metric\":\"nex\",\"gained\":5},{\"metric\":\"zulrah\",\"gained\":4},"
			+ "{\"metric\":\"vorkath\",\"gained\":3},{\"metric\":\"the_mimic\",\"gained\":2},"
			+ "{\"metric\":\"excluded_sixth\",\"gained\":1}]}", MvpEfficiencyEntry.class);
		MvpPanel[] panel = new MvpPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new MvpPanel(null);
			panel[0].updateEfficiencyRankings(java.util.Collections.singletonList(entry), null,
				java.util.Collections.emptyList(), null);
			panel[0].toggleEfficiencyExpansion("Leader", "EHB");
		});
		SwingUtilities.invokeAndWait(() -> {
			java.util.List<String> tips = new java.util.ArrayList<>();
			collectTips(panel[0], tips);
			for (String metric : new String[]{"chambers_of_xeric_challenge_mode", "nex", "zulrah", "vorkath", "the_mimic"})
			{
				Assert.assertTrue(metric, tips.contains(metric));
			}
			Assert.assertFalse(tips.contains("excluded_sixth"));
		});
	}

	private static void collectTips(java.awt.Component component, java.util.List<String> tips)
	{
		if (component instanceof javax.swing.JComponent)
		{
			tips.add(((javax.swing.JComponent) component).getToolTipText());
		}
		if (component instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) component).getComponents()) collectTips(child, tips);
		}
	}

	@Test
	public void dropExpansionStartsClosedAndKeepsOnlyOnePlayerOpen() throws Exception
	{
		MvpPanel panel = new MvpPanel(null);
		Assert.assertFalse(panel.isDropExpanded("Leader"));

		panel.toggleDropExpansion("Leader");
		Assert.assertTrue(panel.isDropExpanded("leader"));

		panel.toggleDropExpansion("Second");
		Assert.assertFalse(panel.isDropExpanded("Leader"));
		Assert.assertTrue(panel.isDropExpanded("Second"));

		panel.toggleDropExpansion("Second");
		Assert.assertFalse(panel.isDropExpanded("Second"));
		SwingUtilities.invokeAndWait(() -> { });
	}

	@Test
	public void efficiencyExpansionIsIndependentForEhbAndEhp() throws Exception
	{
		MvpPanel panel = new MvpPanel(null);
		panel.toggleEfficiencyExpansion("Boss Player", "EHB");
		panel.toggleEfficiencyExpansion("Skill Player", "EHP");

		Assert.assertTrue(panel.isEfficiencyExpanded("Boss Player", "EHB"));
		Assert.assertTrue(panel.isEfficiencyExpanded("Skill Player", "EHP"));
		Assert.assertFalse(panel.isEfficiencyExpanded("Boss Player", "EHP"));

		panel.toggleEfficiencyExpansion("Other Boss Player", "EHB");
		Assert.assertFalse(panel.isEfficiencyExpanded("Boss Player", "EHB"));
		Assert.assertTrue(panel.isEfficiencyExpanded("Other Boss Player", "EHB"));
		Assert.assertTrue(panel.isEfficiencyExpanded("Skill Player", "EHP"));
		SwingUtilities.invokeAndWait(() -> { });
	}
}
