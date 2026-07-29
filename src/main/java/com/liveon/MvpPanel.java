package com.liveon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

final class MvpPanel extends JPanel
{
	private static final Color GOLD = new Color(214, 174, 52);
	private static final Color SILVER = new Color(170, 176, 185);
	private static final Color BRONZE = new Color(190, 112, 48);
	private static final Color ROW_BACKGROUND = new Color(38, 38, 38);
	private final JPanel dropEntries = new JPanel();
	private final JPanel ehbEntries = new JPanel();
	private final JPanel ehpEntries = new JPanel();
	private final JLabel dropSummary = new JLabel("<html><center>Ranking mensal<br>Drops individuais de 1m+</center></html>");
	private final JButton previewButton = new JButton("Ver prévia Top 10");
	private List<MvpDropEntry> liveRanking = Collections.emptyList();
	private boolean previewActive;

	MvpPanel()
	{
		setLayout(new BorderLayout());
		JTabbedPane sections = new JTabbedPane();
		sections.addTab("MVP Drops", createDropsSection());
		sections.addTab("MVP EHB", createEfficiencySection("TOP 10 • MVP EHB", ehbEntries));
		sections.addTab("MVP EHP", createEfficiencySection("TOP 10 • MVP EHP", ehpEntries));
		add(sections, BorderLayout.CENTER);
		updateDropRanking(Collections.emptyList());
	}

	private JPanel createDropsSection()
	{
		JPanel section = new JPanel(new BorderLayout(6, 8));
		section.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
		JLabel title = new JLabel("TOP 10 • MVP DROPS", SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
		dropSummary.setHorizontalAlignment(SwingConstants.CENTER);
		dropSummary.setForeground(new Color(170, 170, 170));
		JPanel header = new JPanel(new GridLayout(0, 1, 2, 2));
		header.add(title);
		header.add(dropSummary);
		previewButton.setVisible(false);
		previewButton.addActionListener(event ->
		{
			previewActive = !previewActive;
			previewButton.setText(previewActive ? "Voltar ao ranking real" : "Ver prévia Top 10");
			renderDropRanking(previewActive ? previewRanking() : liveRanking);
		});
		header.add(previewButton);
		section.add(header, BorderLayout.NORTH);
		dropEntries.setLayout(new BoxLayout(dropEntries, BoxLayout.Y_AXIS));
		JScrollPane rankingScrollPane = new JScrollPane(dropEntries);
		rankingScrollPane.setBorder(null);
		rankingScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		rankingScrollPane.getVerticalScrollBar().setUnitIncrement(12);
		section.add(rankingScrollPane, BorderLayout.CENTER);
		return section;
	}

	private static JPanel createEfficiencySection(String titleText, JPanel entries)
	{
		JPanel section = new JPanel(new BorderLayout(6, 8));
		section.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
		JLabel title = new JLabel(titleText, SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
		JLabel summary = new JLabel("<html><center>Ranking mensal<br>Dados do Wise Old Man</center></html>", SwingConstants.CENTER);
		summary.setForeground(new Color(170, 170, 170));
		JPanel header = new JPanel(new GridLayout(0, 1, 2, 2));
		header.add(title);
		header.add(summary);
		section.add(header, BorderLayout.NORTH);
		entries.setLayout(new BoxLayout(entries, BoxLayout.Y_AXIS));
		JScrollPane scrollPane = new JScrollPane(entries);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(12);
		section.add(scrollPane, BorderLayout.CENTER);
		return section;
	}

	void updateDropRanking(List<MvpDropEntry> ranking)
	{
		liveRanking = ranking == null ? Collections.emptyList() : new ArrayList<>(ranking);
		if (!previewActive)
		{
			renderDropRanking(liveRanking);
		}
	}

	void setStaff(boolean staff)
	{
		SwingUtilities.invokeLater(() ->
		{
			previewButton.setVisible(staff);
			if (!staff && previewActive)
			{
				previewActive = false;
				previewButton.setText("Ver prévia Top 10");
				renderDropRanking(liveRanking);
			}
		});
	}

	private void renderDropRanking(List<MvpDropEntry> ranking)
	{
		List<MvpDropEntry> topTen = ranking == null
			? Collections.emptyList()
			: ranking.subList(0, Math.min(10, ranking.size()));
		SwingUtilities.invokeLater(() ->
		{
			dropEntries.removeAll();
			if (topTen.isEmpty())
			{
				JLabel empty = new JLabel("Nenhum drop de 1m+ registrado.", SwingConstants.CENTER);
				empty.setForeground(new Color(160, 160, 160));
				empty.setBorder(BorderFactory.createEmptyBorder(24, 4, 4, 4));
				dropEntries.add(empty);
			}
			else
			{
				long leaderValue = Math.max(1L, topTen.get(0).getTotalValue());
				for (int index = 0; index < topTen.size(); index++)
				{
					dropEntries.add(createDropRow(index + 1, topTen.get(index), leaderValue));
					dropEntries.add(Box.createRigidArea(new Dimension(0, 5)));
				}
			}
			dropEntries.revalidate();
			dropEntries.repaint();
		});
	}

	private static List<MvpDropEntry> previewRanking()
	{
		return Arrays.asList(
			new MvpDropEntry("Milico", 286_400_000L, 14),
			new MvpDropEntry("Devil Dragon", 234_800_000L, 11),
			new MvpDropEntry("Cloud Iron", 174_100_000L, 9),
			new MvpDropEntry("Boga Rosa", 108_700_000L, 6),
			new MvpDropEntry("Uwu Zesteh", 68_300_000L, 4),
			new MvpDropEntry("Timner", 51_900_000L, 5),
			new MvpDropEntry("Jessse", 43_250_000L, 3),
			new MvpDropEntry("Gm Bearmike", 31_800_000L, 4),
			new MvpDropEntry("Live On One", 22_600_000L, 2),
			new MvpDropEntry("Rune Hunter", 12_150_000L, 1)
		);
	}

	void updateEfficiencyRankings(List<MvpEfficiencyEntry> ehb, List<MvpEfficiencyEntry> ehp)
	{
		renderEfficiencyRanking(ehbEntries, ehb);
		renderEfficiencyRanking(ehpEntries, ehp);
	}

	private static void renderEfficiencyRanking(JPanel target, List<MvpEfficiencyEntry> ranking)
	{
		List<MvpEfficiencyEntry> topTen = ranking == null
			? Collections.emptyList()
			: ranking.subList(0, Math.min(10, ranking.size()));
		SwingUtilities.invokeLater(() ->
		{
			target.removeAll();
			if (topTen.isEmpty())
			{
				JLabel empty = new JLabel("Nenhum ganho mensal disponível no WOM.", SwingConstants.CENTER);
				empty.setForeground(new Color(160, 160, 160));
				empty.setBorder(BorderFactory.createEmptyBorder(24, 4, 4, 4));
				target.add(empty);
			}
			else
			{
				double leaderValue = Math.max(0.0001d, topTen.get(0).getGained());
				for (int index = 0; index < topTen.size(); index++)
				{
					target.add(createEfficiencyRow(index + 1, topTen.get(index), leaderValue));
					target.add(Box.createRigidArea(new Dimension(0, 5)));
				}
			}
			target.revalidate();
			target.repaint();
		});
	}

	private static JPanel createEfficiencyRow(int position, MvpEfficiencyEntry entry, double leaderValue)
	{
		Color accent = position == 1 ? GOLD : position == 2 ? SILVER : position == 3 ? BRONZE : new Color(95, 95, 95);
		JPanel row = new JPanel(new BorderLayout(7, 3));
		row.setBackground(ROW_BACKGROUND);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
			BorderFactory.createEmptyBorder(9, 7, 9, 7)));
		JLabel positionLabel = new JLabel(Integer.toString(position), SwingConstants.CENTER);
		positionLabel.setFont(positionLabel.getFont().deriveFont(Font.BOLD, 16f));
		positionLabel.setForeground(accent);
		positionLabel.setPreferredSize(new Dimension(22, 28));
		row.add(positionLabel, BorderLayout.WEST);
		JLabel name = new JLabel(entry.getPlayerName());
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		row.add(name, BorderLayout.CENTER);
		JLabel value = new JLabel(formatHours(entry.getGained()), SwingConstants.RIGHT);
		value.setFont(value.getFont().deriveFont(Font.BOLD));
		value.setForeground(accent);
		row.add(value, BorderLayout.EAST);
		JProgressBar progress = new JProgressBar(0, 1000);
		progress.setValue((int) Math.min(1000d, entry.getGained() * 1000d / leaderValue));
		progress.setForeground(accent);
		progress.setBackground(new Color(55, 55, 55));
		progress.setBorderPainted(false);
		progress.setPreferredSize(new Dimension(10, 3));
		row.add(progress, BorderLayout.SOUTH);
		return row;
	}

	private static String formatHours(double value)
	{
		return String.format(Locale.ROOT, "%.2f h", value);
	}

	private static JPanel createDropRow(int position, MvpDropEntry entry, long leaderValue)
	{
		Color accent = position == 1 ? GOLD : position == 2 ? SILVER : position == 3 ? BRONZE : new Color(95, 95, 95);
		JPanel row = new JPanel(new BorderLayout(7, 3));
		row.setBackground(ROW_BACKGROUND);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
			BorderFactory.createEmptyBorder(7, 7, 7, 7)));
		JLabel positionLabel = new JLabel(Integer.toString(position), SwingConstants.CENTER);
		positionLabel.setFont(positionLabel.getFont().deriveFont(Font.BOLD, 16f));
		positionLabel.setForeground(accent);
		positionLabel.setPreferredSize(new Dimension(22, 34));
		row.add(positionLabel, BorderLayout.WEST);

		JLabel name = new JLabel(entry.getPlayerName());
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		JLabel details = new JLabel(entry.getDropCount() + (entry.getDropCount() == 1 ? " drop válido" : " drops válidos"));
		details.setForeground(new Color(155, 155, 155));
		JPanel identity = new JPanel(new GridLayout(0, 1));
		identity.setOpaque(false);
		identity.add(name);
		identity.add(details);
		row.add(identity, BorderLayout.CENTER);

		JLabel value = new JLabel(formatValue(entry.getTotalValue()), SwingConstants.RIGHT);
		value.setFont(value.getFont().deriveFont(Font.BOLD));
		value.setForeground(accent);
		row.add(value, BorderLayout.EAST);

		JProgressBar progress = new JProgressBar(0, 1000);
		progress.setValue((int) Math.min(1000L, entry.getTotalValue() * 1000L / leaderValue));
		progress.setForeground(accent);
		progress.setBackground(new Color(55, 55, 55));
		progress.setBorderPainted(false);
		progress.setPreferredSize(new Dimension(10, 3));
		row.add(progress, BorderLayout.SOUTH);
		return row;
	}

	private static String formatValue(long value)
	{
		if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000d);
		if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000d);
		return String.format(Locale.ROOT, "%,d", value);
	}
}
