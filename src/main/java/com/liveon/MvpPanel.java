package com.liveon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.hiscore.HiscorePlugin;
import net.runelite.client.util.ImageUtil;

final class MvpPanel extends JPanel
{
	private static final Color GOLD = new Color(214, 174, 52);
	private static final Color SILVER = new Color(170, 176, 185);
	private static final Color BRONZE = new Color(190, 112, 48);
	private static final Color DROP_GROUP_DARK = new Color(35, 35, 35);
	private static final Color DROP_GROUP_LIGHT = new Color(44, 44, 44);
	private static final Color PANEL_BACKGROUND = new Color(36, 36, 36);
	private static final Color NOTICE_BLUE = new Color(90, 190, 245);
	private static final Icon IRONMAN_ICON = officialIronIcon("ironman.png");
	private static final Icon HARDCORE_IRONMAN_ICON = officialIronIcon("hardcore_ironman.png");
	private static final Icon ULTIMATE_IRONMAN_ICON = officialIronIcon("ultimate_ironman.png");
	private final JPanel dropEntries = new WidthTrackingPanel();
	private final JPanel ehbEntries = new WidthTrackingPanel();
	private final JPanel ehpEntries = new WidthTrackingPanel();
	private final JPanel participationNotice = new JPanel(new BorderLayout());
	private List<MvpDropEntry> liveRanking = Collections.emptyList();
	private String expandedDropPlayer;

	MvpPanel()
	{
		setLayout(new BorderLayout());
		JTabbedPane sections = new JTabbedPane();
		sections.addTab("Drops", createDropsSection());
		sections.addTab("EHB", createEfficiencySection("TOP 10 • MVP EHB", ehbEntries));
		sections.addTab("EHP", createEfficiencySection("TOP 10 • MVP EHP", ehpEntries));
		configureSectionTabs(sections);
		add(sections, BorderLayout.CENTER);
		updateDropRanking(Collections.emptyList());
	}

	private static void configureSectionTabs(JTabbedPane sections)
	{
		sections.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		String[] titles = {"Drops", "EHB", "EHP"};
		String[] icons = {"drop", "hammer", "bars"};
		for (int index = 0; index < titles.length; index++)
		{
			JButton label = new JButton(titles[index], sectionIcon(icons[index]));
			label.setIconTextGap(3);
			label.setContentAreaFilled(false);
			label.setFocusPainted(false);
			label.setMargin(new java.awt.Insets(0, 0, 0, 0));
			label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			final int tabIndex = index;
			label.addActionListener(event -> sections.setSelectedIndex(tabIndex));
			sections.setTabComponentAt(index, label);
		}
		Runnable update = () ->
		{
			for (int index = 0; index < sections.getTabCount(); index++)
			{
				JButton label = (JButton) sections.getTabComponentAt(index);
				boolean selected = index == sections.getSelectedIndex();
				label.setForeground(selected ? new Color(255, 152, 0) : new Color(210, 210, 210));
				label.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, selected ? 2 : 0, 0, new Color(255, 152, 0)),
					BorderFactory.createEmptyBorder(5, 3, selected ? 3 : 5, 3)));
			}
		};
		sections.addChangeListener(event -> update.run());
		update.run();
	}

	private static javax.swing.Icon sectionIcon(String type)
	{
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(14, 14, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(new Color(220, 170, 45));
		graphics.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
		if ("drop".equals(type))
		{
			graphics.fillOval(3, 3, 8, 8);
			graphics.setColor(new Color(90, 65, 15));
			graphics.drawOval(5, 5, 4, 4);
		}
		else if ("hammer".equals(type))
		{
			graphics.drawLine(3, 12, 10, 5);
			graphics.drawLine(7, 2, 12, 7);
			graphics.drawLine(8, 1, 13, 6);
		}
		else
		{
			graphics.fillRect(2, 7, 2, 5);
			graphics.fillRect(6, 3, 2, 9);
			graphics.fillRect(10, 5, 2, 7);
		}
		graphics.dispose();
		return new javax.swing.ImageIcon(image);
	}

	private JPanel createDropsSection()
	{
		JPanel section = new JPanel(new BorderLayout());
		section.setBackground(PANEL_BACKGROUND);
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(PANEL_BACKGROUND);
		top.add(createDropsHeader());
		configureParticipationNotice();
		top.add(participationNotice);
		section.add(top, BorderLayout.NORTH);
		dropEntries.setLayout(new BoxLayout(dropEntries, BoxLayout.Y_AXIS));
		dropEntries.setBackground(PANEL_BACKGROUND);
		dropEntries.setBorder(BorderFactory.createEmptyBorder(8, 7, 10, 7));
		JScrollPane rankingScrollPane = new JScrollPane(dropEntries);
		rankingScrollPane.setBorder(null);
		rankingScrollPane.getViewport().setBackground(PANEL_BACKGROUND);
		rankingScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		rankingScrollPane.getVerticalScrollBar().setUnitIncrement(12);
		section.add(rankingScrollPane, BorderLayout.CENTER);
		return section;
	}

	private void configureParticipationNotice()
	{
		participationNotice.setAlignmentX(Component.LEFT_ALIGNMENT);
		participationNotice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
		participationNotice.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(7, 7, 0, 7, PANEL_BACKGROUND),
			BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(NOTICE_BLUE),
				BorderFactory.createEmptyBorder(5, 7, 5, 7))));
		JLabel message = new JLabel("<html><b>Participação desativada</b><br>"
			+ "Ative nas configurações<br>para registrar seus drops.</html>");
		message.setForeground(NOTICE_BLUE);
		participationNotice.add(message, BorderLayout.CENTER);
	}

	void setParticipationEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() ->
		{
			participationNotice.setVisible(!enabled);
			participationNotice.revalidate();
			participationNotice.repaint();
		});
	}

	private static JPanel createDropsHeader()
	{
		GradientPanel header = new GradientPanel(new Color(47, 44, 34), PANEL_BACKGROUND);
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBorder(BorderFactory.createEmptyBorder(11, 12, 10, 12));
		JLabel kicker = new JLabel("MVP DROPS");
		kicker.setForeground(new Color(255, 176, 0));
		kicker.setFont(kicker.getFont().deriveFont(Font.BOLD, 13f));
		JLabel title = new JLabel("Ranking mensal");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		JLabel meta = new JLabel(monthLabel() + "  •  Drops de 1m+");
		meta.setForeground(new Color(180, 180, 180));
		meta.setFont(meta.getFont().deriveFont(Font.PLAIN, 13f));
		kicker.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		meta.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(kicker);
		header.add(Box.createVerticalStrut(3));
		header.add(title);
		header.add(Box.createVerticalStrut(3));
		header.add(meta);
		return header;
	}

	private static JPanel createEfficiencySection(String titleText, JPanel entries)
	{
		JPanel section = new JPanel(new BorderLayout());
		section.setBackground(PANEL_BACKGROUND);
		String category = titleText.endsWith("EHP") ? "EHP" : "EHB";
		section.add(createEfficiencyHeader(category), BorderLayout.NORTH);
		entries.setLayout(new BoxLayout(entries, BoxLayout.Y_AXIS));
		entries.setBackground(PANEL_BACKGROUND);
		entries.setBorder(BorderFactory.createEmptyBorder(8, 7, 10, 7));
		JScrollPane scrollPane = new JScrollPane(entries);
		scrollPane.setBorder(null);
		scrollPane.getViewport().setBackground(PANEL_BACKGROUND);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(12);
		section.add(scrollPane, BorderLayout.CENTER);
		return section;
	}

	private static JPanel createEfficiencyHeader(String category)
	{
		GradientPanel header = new GradientPanel(new Color(47, 44, 34), PANEL_BACKGROUND);
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBorder(BorderFactory.createEmptyBorder(11, 12, 10, 12));
		JLabel kicker = new JLabel("MVP " + category);
		kicker.setForeground(new Color(255, 176, 0));
		kicker.setFont(kicker.getFont().deriveFont(Font.BOLD, 13f));
		JLabel title = new JLabel("Ranking mensal");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		JLabel meta = new JLabel(monthLabel() + "  •  Wise Old Man");
		meta.setForeground(new Color(180, 180, 180));
		meta.setFont(meta.getFont().deriveFont(Font.PLAIN, 13f));
		kicker.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		meta.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(kicker);
		header.add(Box.createVerticalStrut(3));
		header.add(title);
		header.add(Box.createVerticalStrut(3));
		header.add(meta);
		return header;
	}

	private static String monthLabel()
	{
		YearMonth month = YearMonth.now();
		String name = month.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
		return name.substring(0, 1).toUpperCase(new Locale("pt", "BR")) + name.substring(1) + " de " + month.getYear();
	}

	void updateDropRanking(List<MvpDropEntry> ranking)
	{
		liveRanking = ranking == null ? Collections.emptyList() : new ArrayList<>(ranking);
		renderDropRanking(liveRanking);
	}

	void setStaff(boolean staff)
	{
		// Kept for the shared panel contract; the ranking has no staff-only controls.
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
				dropEntries.add(createDropLeader(topTen.get(0), leaderValue));
				dropEntries.add(Box.createRigidArea(new Dimension(0, 7)));
				if (topTen.size() > 1)
				{
					for (int index = 1; index < Math.min(3, topTen.size()); index++)
					{
						dropEntries.add(createDropPodium(index + 1, topTen.get(index), index == 1 ? SILVER : BRONZE));
						if (index + 1 < Math.min(3, topTen.size()))
						{
							dropEntries.add(Box.createRigidArea(new Dimension(0, 5)));
						}
					}
					dropEntries.add(Box.createRigidArea(new Dimension(0, 11)));
				}
				if (topTen.size() > 3)
				{
					JLabel classification = new JLabel("CLASSIFICAÇÃO");
					classification.setForeground(new Color(155, 155, 155));
					classification.setFont(classification.getFont().deriveFont(Font.BOLD, 13f));
					classification.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 0));
					classification.setAlignmentX(Component.LEFT_ALIGNMENT);
					dropEntries.add(classification);
					for (int index = 3; index < topTen.size(); index++)
					{
						dropEntries.add(createDropListRow(index + 1, topTen.get(index), leaderValue));
						if (index + 1 < topTen.size()) dropEntries.add(Box.createVerticalStrut(3));
					}
				}
			}
			dropEntries.revalidate();
			dropEntries.repaint();
		});
	}

	void updateEfficiencyRankings(List<MvpEfficiencyEntry> ehb, List<MvpEfficiencyEntry> ehp)
	{
		renderEfficiencyRanking(ehbEntries, ehb, "EHB");
		renderEfficiencyRanking(ehpEntries, ehp, "EHP");
	}

	private static void renderEfficiencyRanking(JPanel target, List<MvpEfficiencyEntry> ranking, String metricType)
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
				target.add(createEfficiencyLeader(topTen.get(0), leaderValue, metricType));
				target.add(Box.createRigidArea(new Dimension(0, 7)));
				if (topTen.size() > 1)
				{
					JPanel podium = new JPanel(new GridLayout(0, 1, 0, 5));
					podium.setOpaque(false);
					podium.setAlignmentX(Component.LEFT_ALIGNMENT);
					podium.setMaximumSize(new Dimension(Integer.MAX_VALUE, topTen.size() > 2 ? 104 : 50));
					podium.add(createEfficiencyPodium(2, topTen.get(1), SILVER));
					podium.add(topTen.size() > 2
						? createEfficiencyPodium(3, topTen.get(2), BRONZE)
						: new JPanel());
					target.add(podium);
					target.add(Box.createRigidArea(new Dimension(0, 11)));
				}
				if (topTen.size() > 3)
				{
					JLabel classification = new JLabel("CLASSIFICAÇÃO");
					classification.setForeground(new Color(155, 155, 155));
					classification.setFont(classification.getFont().deriveFont(Font.BOLD, 13f));
					classification.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 0));
					classification.setAlignmentX(Component.LEFT_ALIGNMENT);
					target.add(classification);
					for (int index = 3; index < topTen.size(); index++)
					{
						target.add(createEfficiencyListRow(index + 1, topTen.get(index)));
						if (index + 1 < topTen.size()) target.add(Box.createVerticalStrut(3));
					}
				}
			}
			target.revalidate();
			target.repaint();
		});
	}

	private static JPanel createEfficiencyLeader(MvpEfficiencyEntry entry, double leaderValue, String metricType)
	{
		GradientPanel row = new GradientPanel(new Color(58, 47, 22), new Color(41, 38, 29));
		row.setLayout(new BorderLayout(8, 6));
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, GOLD),
			BorderFactory.createEmptyBorder(10, 9, 9, 9)));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		MvpEfficiencyContribution[] breakdown = entry.getBreakdown();
		boolean hasBreakdown = breakdown != null && breakdown.length > 0;
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, hasBreakdown ? 151 : 82));
		JLabel crown = new JLabel("♛", SwingConstants.CENTER);
		crown.setFont(crown.getFont().deriveFont(Font.BOLD, 20f));
		crown.setForeground(GOLD);
		crown.setPreferredSize(new Dimension(25, 30));
		row.add(crown, BorderLayout.WEST);
		JLabel name = new JLabel(entry.getPlayerName());
		applyAccountIcon(name, entry.getAccountType());
		name.setFont(name.getFont().deriveFont(Font.BOLD, 16f));
		JLabel caption = new JLabel("Líder do mês");
		caption.setFont(caption.getFont().deriveFont(Font.BOLD, 15f));
		caption.setForeground(new Color(180, 161, 110));
		JPanel identity = new JPanel(new GridLayout(0, 1, 0, 1));
		identity.setOpaque(false);
		identity.add(name);
		identity.add(caption);
		JLabel value = new JLabel(formatHours(entry.getGained()), SwingConstants.RIGHT);
		value.setFont(value.getFont().deriveFont(Font.BOLD, 14f));
		value.setForeground(GOLD);
		JPanel header = new JPanel(new BorderLayout(5, 0));
		header.setOpaque(false);
		header.add(identity, BorderLayout.CENTER);
		header.add(value, BorderLayout.EAST);
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.add(header);
		if (hasBreakdown)
		{
			content.add(Box.createVerticalStrut(6));
			int count = Math.min(3, breakdown.length);
			for (int index = 0; index < count; index++)
			{
				MvpEfficiencyContribution contribution = breakdown[index];
				JPanel detail = new JPanel(new BorderLayout(4, 0));
				detail.setOpaque(false);
				JLabel activity = new JLabel(metricLabel(contribution.getMetric()));
				activity.setForeground(new Color(195, 195, 190));
				activity.setFont(activity.getFont().deriveFont(13f));
				JLabel hours = new JLabel(String.format(Locale.ROOT, "+%.2f %s", contribution.getGained(), metricType));
				hours.setForeground(new Color(180, 161, 110));
				hours.setFont(hours.getFont().deriveFont(Font.BOLD, 13f));
				detail.add(activity, BorderLayout.CENTER);
				detail.add(hours, BorderLayout.EAST);
				content.add(detail);
			}
		}
		row.add(content, BorderLayout.CENTER);
		JProgressBar progress = new JProgressBar(0, 1000);
		progress.setValue((int) Math.min(1000d, entry.getGained() * 1000d / leaderValue));
		progress.setForeground(GOLD);
		progress.setBackground(new Color(74, 68, 54));
		progress.setBorderPainted(false);
		progress.setPreferredSize(new Dimension(10, 3));
		row.add(progress, BorderLayout.SOUTH);
		return row;
	}

	private static String metricLabel(String metric)
	{
		if (metric == null || metric.isEmpty())
		{
			return "";
		}
		String[] words = metric.replace('_', ' ').split(" ");
		StringBuilder label = new StringBuilder();
		for (String word : words)
		{
			if (word.isEmpty()) continue;
			if (label.length() > 0) label.append(' ');
			label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return shortName(label.toString(), 20);
	}

	private static JPanel createEfficiencyPodium(int position, MvpEfficiencyEntry entry, Color accent)
	{
		JPanel card = new JPanel(new BorderLayout(7, 0));
		card.setBackground(new Color(43, 43, 43));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			BorderFactory.createEmptyBorder(7, 8, 7, 8)));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 61));
		JLabel place = new JLabel(position + "º");
		place.setForeground(new Color(160, 160, 160));
		place.setFont(place.getFont().deriveFont(Font.BOLD, 12f));
		place.setPreferredSize(new Dimension(28, 20));
		JLabel name = new JLabel(shortName(entry.getPlayerName(), 15));
		name.setToolTipText(entry.getPlayerName());
		applyAccountIcon(name, entry.getAccountType());
		name.setForeground(new Color(225, 225, 225));
		name.setFont(name.getFont().deriveFont(Font.BOLD, 15f));
		JLabel value = new JLabel(formatHours(entry.getGained()), SwingConstants.RIGHT);
		value.setForeground(accent);
		value.setFont(value.getFont().deriveFont(Font.BOLD, 13f));
		card.add(place, BorderLayout.WEST);
		card.add(name, BorderLayout.CENTER);
		card.add(value, BorderLayout.EAST);
		return card;
	}

	private static JPanel createEfficiencyListRow(int position, MvpEfficiencyEntry entry)
	{
		Color background = dropGroupBackground(position);
		JPanel row = new JPanel(new BorderLayout(7, 0));
		row.setBackground(background);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(82, 82, 82)),
			BorderFactory.createEmptyBorder(9, 5, 9, 7)));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
		JLabel place = new JLabel(Integer.toString(position), SwingConstants.CENTER);
		place.setForeground(new Color(155, 155, 155));
		place.setFont(place.getFont().deriveFont(13f));
		place.setPreferredSize(new Dimension(22, 20));
		JLabel name = new JLabel(entry.getPlayerName());
		name.setToolTipText(entry.getPlayerName());
		applyAccountIcon(name, entry.getAccountType());
		name.setForeground(new Color(225, 225, 225));
		name.setFont(name.getFont().deriveFont(Font.BOLD, 15f));
		JLabel value = new JLabel(formatHours(entry.getGained()));
		value.setForeground(new Color(205, 205, 205));
		value.setFont(value.getFont().deriveFont(Font.BOLD, 14f));
		JPanel identity = new JPanel();
		identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
		identity.setOpaque(false);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		value.setAlignmentX(Component.LEFT_ALIGNMENT);
		identity.add(name);
		identity.add(Box.createVerticalStrut(2));
		identity.add(value);
		row.add(place, BorderLayout.WEST);
		row.add(identity, BorderLayout.CENTER);
		return row;
	}

	private static String shortName(String value, int length)
	{
		if (value == null) return "";
		return value.length() <= length ? value : value.substring(0, Math.max(1, length - 1)) + "…";
	}

	private static final class GradientPanel extends JPanel
	{
		private final Color start;
		private final Color end;
		private GradientPanel(Color start, Color end)
		{
			this.start = start;
			this.end = end;
			setOpaque(false);
		}
		@Override protected void paintComponent(Graphics graphics)
		{
			Graphics2D copy = (Graphics2D) graphics.create();
			copy.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			copy.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
			copy.fillRect(0, 0, getWidth(), getHeight());
			copy.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Keeps ranking cards pinned to the sidebar width instead of their text width. */
	private static final class WidthTrackingPanel extends JPanel implements Scrollable
	{
		@Override public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 12;
		}

		@Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return Math.max(12, visibleRect.height - 24);
		}

		@Override public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private static String formatHours(double value)
	{
		return String.format(Locale.ROOT, "%.2f h", value);
	}

	private JPanel createDropLeader(MvpDropEntry entry, long leaderValue)
	{
		GradientPanel card = new GradientPanel(new Color(58, 47, 22), new Color(41, 38, 29));
		card.setLayout(new BorderLayout(8, 6));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, GOLD),
			BorderFactory.createEmptyBorder(10, 9, 9, 9)));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 173));
		JLabel crown = new JLabel("♛", SwingConstants.CENTER);
		crown.setFont(crown.getFont().deriveFont(Font.BOLD, 20f));
		crown.setForeground(GOLD);
		crown.setPreferredSize(new Dimension(25, 30));
		card.add(crown, BorderLayout.WEST);
		JLabel name = new JLabel(entry.getPlayerName());
		applyAccountIcon(name, entry.getAccountType());
		name.setFont(name.getFont().deriveFont(Font.BOLD, 16f));
		JLabel caption = new JLabel("Líder do mês");
		caption.setFont(caption.getFont().deriveFont(Font.BOLD, 15f));
		caption.setForeground(new Color(180, 161, 110));
		JPanel identity = new JPanel(new GridLayout(0, 1, 0, 1));
		identity.setOpaque(false);
		identity.add(name);
		identity.add(caption);
		JLabel value = new JLabel(formatValue(entry.getTotalValue()), SwingConstants.RIGHT);
		value.setFont(value.getFont().deriveFont(Font.BOLD, 14f));
		value.setForeground(GOLD);
		JPanel leaderHeader = new JPanel(new BorderLayout(5, 0));
		leaderHeader.setOpaque(false);
		leaderHeader.add(identity, BorderLayout.CENTER);
		leaderHeader.add(value, BorderLayout.EAST);
		JPanel content = new JPanel(new BorderLayout(0, 6));
		content.setOpaque(false);
		content.add(leaderHeader, BorderLayout.NORTH);
		content.add(leftPinnedDropDetails(entry), BorderLayout.CENTER);
		card.add(content, BorderLayout.CENTER);
		JProgressBar progress = new JProgressBar(0, 1000);
		progress.setValue((int) Math.min(1000L, entry.getTotalValue() * 1000L / leaderValue));
		progress.setForeground(GOLD);
		progress.setBackground(new Color(74, 68, 54));
		progress.setBorderPainted(false);
		progress.setPreferredSize(new Dimension(10, 3));
		card.add(progress, BorderLayout.SOUTH);
		return card;
	}

	private JPanel createDropPodium(int position, MvpDropEntry entry, Color accent)
	{
		Color background = dropGroupBackground(position);
		boolean expanded = normalizePlayer(entry.getPlayerName()).equals(expandedDropPlayer);
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(background);
		card.setBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, accent));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, expanded ? 156 : 61));
		JPanel summary = new JPanel(new BorderLayout(7, 0));
		summary.setBackground(background);
		summary.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
		JLabel place = new JLabel(position + "º");
		place.setForeground(new Color(160, 160, 160));
		place.setFont(place.getFont().deriveFont(Font.BOLD, 12f));
		place.setPreferredSize(new Dimension(28, 20));
		JLabel name = clickableDropName(entry, 16, 15f);
		name.setForeground(new Color(225, 225, 225));
		JLabel value = new JLabel(formatValue(entry.getTotalValue()));
		value.setForeground(accent);
		value.setFont(value.getFont().deriveFont(Font.BOLD, 13f));
		JPanel identity = new JPanel();
		identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
		identity.setOpaque(false);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		value.setAlignmentX(Component.LEFT_ALIGNMENT);
		identity.add(name);
		identity.add(Box.createVerticalStrut(2));
		identity.add(value);
		summary.add(place, BorderLayout.WEST);
		summary.add(identity, BorderLayout.CENTER);
		card.add(summary, BorderLayout.NORTH);
		if (expanded)
		{
			card.add(createInlineDropDetails(entry, background, 43), BorderLayout.CENTER);
		}
		makeDropGroupClickable(card, entry);
		return card;
	}

	private JPanel createDropListRow(int position, MvpDropEntry entry, long leaderValue)
	{
		Color groupBackground = dropGroupBackground(position);
		boolean expanded = normalizePlayer(entry.getPlayerName()).equals(expandedDropPlayer);
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(groupBackground);
		wrapper.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(82, 82, 82)));
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, expanded ? 151 : 56));
		JPanel row = new JPanel(new BorderLayout(7, 0));
		row.setBackground(groupBackground);
		row.setBorder(BorderFactory.createEmptyBorder(9, 5, 9, 7));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
		JLabel place = new JLabel(Integer.toString(position), SwingConstants.CENTER);
		place.setForeground(new Color(155, 155, 155));
		place.setFont(place.getFont().deriveFont(13f));
		place.setPreferredSize(new Dimension(22, 20));
		JLabel name = clickableDropName(entry, 16, 15f);
		name.setForeground(new Color(225, 225, 225));
		JLabel value = new JLabel(formatValue(entry.getTotalValue()));
		value.setForeground(new Color(205, 205, 205));
		value.setFont(value.getFont().deriveFont(Font.BOLD, 14f));
		JPanel identity = new JPanel();
		identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
		identity.setOpaque(false);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		value.setAlignmentX(Component.LEFT_ALIGNMENT);
		identity.add(name);
		identity.add(Box.createVerticalStrut(2));
		identity.add(value);
		row.add(place, BorderLayout.WEST);
		row.add(identity, BorderLayout.CENTER);
		wrapper.add(row, BorderLayout.NORTH);
		if (expanded)
		{
			wrapper.add(createInlineDropDetails(entry, groupBackground, 34), BorderLayout.CENTER);
		}
		makeDropGroupClickable(wrapper, entry);
		return wrapper;
	}

	private static Color dropGroupBackground(int position)
	{
		return position % 2 == 0 ? DROP_GROUP_LIGHT : DROP_GROUP_DARK;
	}

	private JLabel clickableDropName(MvpDropEntry entry, int length, float size)
	{
		boolean expanded = normalizePlayer(entry.getPlayerName()).equals(expandedDropPlayer);
		JLabel name = new JLabel(shortName(entry.getPlayerName(), length) + (expanded ? "  ▾" : "  ›"));
		applyAccountIcon(name, entry.getAccountType());
		name.setToolTipText(entry.getPlayerName());
		name.setFont(name.getFont().deriveFont(Font.BOLD, size));
		name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return name;
	}

	private void makeDropGroupClickable(Component component, MvpDropEntry entry)
	{
		if (component instanceof javax.swing.JComponent)
		{
			javax.swing.JComponent clickable = (javax.swing.JComponent) component;
			clickable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			clickable.setToolTipText("Exibir os três maiores drops");
			clickable.addMouseListener(dropToggleListener(entry));
		}
		if (component instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) component).getComponents())
			{
				makeDropGroupClickable(child, entry);
			}
		}
	}

	private MouseAdapter dropToggleListener(MvpDropEntry entry)
	{
		return new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent event)
			{
				String player = normalizePlayer(entry.getPlayerName());
				expandedDropPlayer = player.equals(expandedDropPlayer) ? null : player;
				renderDropRanking(liveRanking);
			}
		};
	}

	private static JPanel createInlineDropDetails(MvpDropEntry entry, Color background, int leftPadding)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(background);
		panel.setBorder(BorderFactory.createEmptyBorder(7, leftPadding, 8, 5));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
		panel.add(leftPinnedDropDetails(entry), BorderLayout.WEST);
		return panel;
	}

	private static JPanel leftPinnedDropDetails(MvpDropEntry entry)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);
		holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
		holder.add(createDropDetails(entry), BorderLayout.WEST);
		return holder;
	}

	private static JPanel createDropDetails(MvpDropEntry entry)
	{
		JPanel details = new JPanel(new GridLayout(0, 1, 0, 4));
		details.setOpaque(false);
		details.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
		MvpDropDetail[] drops = entry.getTopDrops();
		if (drops == null || drops.length == 0)
		{
			JLabel unavailable = new JLabel("Top 3 ainda sem registros");
			unavailable.setForeground(new Color(175, 175, 175));
			unavailable.setFont(unavailable.getFont().deriveFont(Font.BOLD, 14f));
			unavailable.setHorizontalAlignment(SwingConstants.LEFT);
			details.add(unavailable);
			details.setPreferredSize(new Dimension(175, 22));
			return details;
		}
		int displayed = 0;
		for (MvpDropDetail drop : drops)
		{
			if (drop.getValue() < 1_000_000L || displayed >= 3) continue;
			String readableItem = drop.getItem().replaceFirst("^(\\d+)x\\s*", "$1x ");
			JLabel item = new JLabel(readableItem);
			item.setToolTipText(drop.getItem() + (drop.getSource() == null || drop.getSource().isEmpty()
				? "" : " • " + drop.getSource()));
			item.setForeground(new Color(230, 210, 145));
			item.setFont(item.getFont().deriveFont(Font.BOLD, 15f));
			item.setHorizontalAlignment(SwingConstants.LEFT);
			details.add(item);
			displayed++;
		}
		details.setPreferredSize(new Dimension(175, Math.max(24, displayed * 24)));
		return details;
	}

	private static String normalizePlayer(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static void applyAccountIcon(JLabel label, String accountType)
	{
		Icon icon = accountTypeIcon(accountType);
		if (icon != null)
		{
			label.setIcon(icon);
			label.setIconTextGap(4);
			String typeLabel = accountTypeLabel(accountType);
			String currentTooltip = label.getToolTipText();
			label.setToolTipText(currentTooltip == null || currentTooltip.isEmpty()
				? typeLabel : currentTooltip + " • " + typeLabel);
		}
	}

	private static Icon accountTypeIcon(String accountType)
	{
		String normalized = accountType == null ? "" : accountType.trim().toLowerCase(Locale.ROOT);
		switch (normalized)
		{
			case "ironman": return IRONMAN_ICON;
			case "hardcore":
			case "hardcore_ironman": return HARDCORE_IRONMAN_ICON;
			case "ultimate":
			case "ultimate_ironman": return ULTIMATE_IRONMAN_ICON;
			default: return null;
		}
	}

	private static Icon officialIronIcon(String resource)
	{
		BufferedImage image = ImageUtil.loadImageResource(HiscorePlugin.class, resource);
		return image == null ? null : new ImageIcon(ImageUtil.resizeImage(image, 13, 13));
	}

	private static String accountTypeLabel(String accountType)
	{
		String normalized = accountType == null ? "" : accountType.trim().toLowerCase(Locale.ROOT);
		switch (normalized)
		{
			case "ironman": return "Ironman";
			case "hardcore":
			case "hardcore_ironman": return "Hardcore Ironman";
			case "ultimate":
			case "ultimate_ironman": return "Ultimate Ironman";
			default: return "Conta normal";
		}
	}

	private static String formatValue(long value)
	{
		if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000d);
		if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000d);
		return String.format(Locale.ROOT, "%,d", value);
	}
}
