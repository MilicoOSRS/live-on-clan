package com.liveon;

import java.util.Arrays;
import javax.swing.SwingUtilities;
import org.junit.Assert;
import org.junit.Test;

public class RanksPanelTest
{
	@Test
	public void requestButtonStaysInHeaderNearVerificationActions()
	{
		RanksPanel panel = new RanksPanel(() -> { }, () -> { }, () -> { });
		Assert.assertTrue(panel.isRequestButtonInHeader());
		Assert.assertTrue(panel.isRequestButtonImmediatelyAfterAvailableRank());
	}

	@Test
	public void recruitCanRequestHighestEligibleRank() throws Exception
	{
		RanksPanel panel = new RanksPanel(() -> { }, () -> { }, () -> { });
		panel.update("Player", "Recruta", "Major", "Coronel",
			Arrays.asList("Quest cape: ✓"), "");
		SwingUtilities.invokeAndWait(() -> { });
		Assert.assertEquals("Major", panel.getCurrentRank());
	}

	@Test
	public void helperUsesRecruitProgressionWithoutChangingDisplayedClanRank() throws Exception
	{
		RanksPanel panel = new RanksPanel(() -> { }, () -> { }, () -> { });
		panel.update("Drizer", "Helper", "Cabo", "Aluno",
			Arrays.asList("Quest points: ✓ 236", "Fire cape: ✓"), "");
		SwingUtilities.invokeAndWait(() -> { });

		Assert.assertEquals("Cabo", panel.getDisplayedAvailableRank());
		Assert.assertEquals("Cabo", panel.getCurrentRank());
		Assert.assertEquals("Rank atual • Recruta", panel.getDisplayedCurrentClanRank());
	}

	@Test
	public void englishClanTitlesUseConfiguredPortugueseProgressionNames() throws Exception
	{
		RanksPanel panel = new RanksPanel(() -> { }, () -> { }, () -> { });
		panel.update("Player", "Major", "Major", "Coronel",
			Arrays.asList("2300 total level: ✓"), "");
		SwingUtilities.invokeAndWait(() -> { });

		Assert.assertEquals("Rank atual • Major", panel.getDisplayedCurrentClanRank());
		Assert.assertEquals("Conclua as pendências abaixo e verifique novamente.", panel.getDisplayedAvailableRank());
	}

	@Test
	public void specialClanRankCannotRequestAutomatically() throws Exception
	{
		RanksPanel panel = new RanksPanel(() -> { }, () -> { }, () -> { });
		panel.update("Player", "MVP Drops", "Major", "Coronel",
			Arrays.asList("Quest cape: ✓"), "");
		SwingUtilities.invokeAndWait(() -> { });
		Assert.assertEquals("", panel.getCurrentRank());
		Assert.assertTrue(panel.isSpecialNoticeVisible());
		Assert.assertFalse(panel.isProgressionVisible());
	}

	@Test
	public void specialClanTitlesAreRenamedButRemainOutsideProgression() throws Exception
	{
		String[][] ranks = {
			{"Completionist", "Diary 50%+"},
			{"Quester", "Diary 100%"},
			{"Beast", "Colaborador"},
			{"Berserker", "MVP EHB"},
			{"Skiller", "MVP EHP"},
			{"Gold", "MVP Drops"}
		};

		for (String[] rank : ranks)
		{
			RanksPanel panel = new RanksPanel(() -> { }, () -> { }, () -> { });
			panel.update("Player", rank[0], "Major", "Coronel",
				Arrays.asList("Quest cape: ✓"), "");
			SwingUtilities.invokeAndWait(() -> { });

			Assert.assertEquals("Rank atual • " + rank[1], panel.getDisplayedCurrentClanRank());
			Assert.assertEquals("", panel.getCurrentRank());
			Assert.assertTrue(panel.isSpecialNoticeVisible());
			Assert.assertFalse(panel.isProgressionVisible());
		}
	}

	@Test
	public void currentColonelDoesNotRequestColonelAgain() throws Exception
	{
		RanksPanel panel = new RanksPanel(() -> { }, () -> { }, () -> { });
		panel.update("Player", "Coronel", "Coronel", "Rank máximo atingido",
			Arrays.asList("Grandmaster: ✓"), "");
		SwingUtilities.invokeAndWait(() -> { });
		Assert.assertEquals("", panel.getCurrentRank());
	}

	@Test
	public void currentGeneralShowsMaximumRankNotice() throws Exception
	{
		RanksPanel panel = new RanksPanel(() -> { }, () -> { }, () -> { });
		panel.update("Player", "General", "Coronel", "Rank máximo atingido",
			Arrays.asList("Grandmaster: ✓"), "");
		SwingUtilities.invokeAndWait(() -> { });

		Assert.assertEquals("Rank atual • General", panel.getDisplayedCurrentClanRank());
		Assert.assertEquals("", panel.getCurrentRank());
		Assert.assertTrue(panel.isSpecialNoticeVisible());
		Assert.assertFalse(panel.isProgressionVisible());
	}

	@Test
	public void alunoWithOnlyCaboRequirementsShowsSargentoAsNextRank() throws Exception
	{
		RanksPanel panel = new RanksPanel(() -> { }, () -> { }, () -> { });
		panel.update("Player", "Aluno", "Cabo", "Sargento",
			Arrays.asList("Quest points: ✓ 250", "Fire cape: ✓", "Combat achievements: Easy"), "");
		SwingUtilities.invokeAndWait(() -> { });

		Assert.assertEquals("Conclua as pendências abaixo e verifique novamente.", panel.getDisplayedAvailableRank());
		Assert.assertEquals("Sargento", panel.getDisplayedNextRank());
		Assert.assertEquals("", panel.getCurrentRank());
	}
}
