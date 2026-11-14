package com.liveon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.hiscore.HiscorePlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.util.ImageUtil;

final class MvpPanel extends JPanel
{
	private static final Color GOLD = LiveOnTheme.GOLD;
	private static final Color SILVER = LiveOnTheme.SILVER;
	private static final Color BRONZE = LiveOnTheme.BRONZE;
	private static final Color PANEL_BACKGROUND = new Color(36, 36, 36);
	private static final Color NOTICE_BLUE = LiveOnTheme.GREEN;
	private static final Color MOVE_UP = new Color(126, 217, 87);
	private static final Color MOVE_DOWN = new Color(224, 108, 117);
	/** Rows shown per MVP ranking; older servers send ten and the panel shows what it gets. */
	static final int TOP_SIZE = 25;
	private static final Font META_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	private static final Icon IRONMAN_ICON = officialIronIcon("ironman.png");
	private static final Icon HARDCORE_IRONMAN_ICON = officialIronIcon("hardcore_ironman.png");
	private static final Icon ULTIMATE_IRONMAN_ICON = officialIronIcon("ultimate_ironman.png");
	private final JPanel dropEntries = new WidthTrackingPanel();
	private final JLabel monthTotalLabel = new JLabel();
	private final JPanel ehbEntries = new WidthTrackingPanel();
	private final JPanel ehpEntries = new WidthTrackingPanel();
	private final JPanel dropOwnPosition = new JPanel();
	private final JPanel ehbOwnPosition = new JPanel();
	private final JPanel ehpOwnPosition = new JPanel();
	private final JPanel participationNotice = new JPanel(new BorderLayout());
	private List<MvpDropEntry> liveRanking = Collections.emptyList();
	private MvpDropEntry liveOwnDrop;
	private List<MvpEfficiencyEntry> liveEhbRanking = Collections.emptyList();
	private List<MvpEfficiencyEntry> liveEhpRanking = Collections.emptyList();
	private MvpEfficiencyEntry liveOwnEhb;
	private MvpEfficiencyEntry liveOwnEhp;
	private String dropDataMonth;
	private String ownPlayerName = "";
	private boolean dropsReceived;
	private boolean efficiencyReceived;
	private String efficiencyDataMonth;
	private String expandedDropPlayer;
	private String expandedEhbPlayer;
	private String expandedEhpPlayer;
	private final Properties positionCache = new Properties();
	private boolean positionCacheLoaded;
	private boolean positionCacheDirty;
	private String positionCacheMonth;

	MvpPanel(net.runelite.client.util.AsyncBufferedImage dropIcon)
	{
		setLayout(new BorderLayout());
		JTabbedPane sections = new JTabbedPane();
		sections.addTab("Drops", createDropsSection());
		sections.addTab("EHB", createEfficiencySection("TOP " + TOP_SIZE + " • MVP EHB", ehbEntries, ehbOwnPosition));
		sections.addTab("EHP", createEfficiencySection("TOP " + TOP_SIZE + " • MVP EHP", ehpEntries, ehpOwnPosition));
		configureSectionTabs(sections, dropIcon);
		add(sections, BorderLayout.CENTER);
		renderDropRanking(Collections.emptyList(), null);
	}

	private static void configureSectionTabs(JTabbedPane sections, net.runelite.client.util.AsyncBufferedImage dropIcon)
	{
		sections.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		LiveOnTheme.styleTabs(sections, LiveOnTheme.ORANGE);
		String[] titles = {"Drops", "EHB", "EHP"};
		String[] icons = {"drop", "hammer", "bars"};
		for (int index = 0; index < titles.length; index++)
		{
			JButton label = new JButton(titles[index], sectionIcon(icons[index]));
			if (index == 0 && dropIcon != null)
			{
				dropIcon.onLoaded(() -> SwingUtilities.invokeLater(() -> label.setIcon(
					new ImageIcon(ImageUtil.resizeImage(dropIcon, 14, 14)))));
			}
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
				// The tab pane paints the rounded highlight and underline; the button only text.
				LiveOnTheme.styleTabButton(label, selected, LiveOnTheme.ORANGE);
			}
		};
		sections.addChangeListener(event -> update.run());
		update.run();
	}

	private static final int ICON_SIZE = 14;
	private static final int ICON_SUPERSAMPLE = 4;

	private static javax.swing.Icon sectionIcon(String type)
	{
		int scaled = ICON_SIZE * ICON_SUPERSAMPLE;
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(scaled, scaled, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
		graphics.scale(ICON_SUPERSAMPLE, ICON_SUPERSAMPLE);
		graphics.setColor(new Color(220, 170, 45));
		graphics.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
		if ("drop".equals(type))
		{
			graphics.fillOval(3, 4, 8, 6);
			graphics.drawOval(3, 2, 8, 6);
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
		java.awt.Image downscaled = image.getScaledInstance(ICON_SIZE, ICON_SIZE, java.awt.Image.SCALE_AREA_AVERAGING);
		return new javax.swing.ImageIcon(downscaled);
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
		configureOwnPositionFooter(dropOwnPosition);
		section.add(dropOwnPosition, BorderLayout.SOUTH);
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

	private JPanel createDropsHeader()
	{
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(PANEL_BACKGROUND);
		header.setBorder(BorderFactory.createEmptyBorder(10, 11, 4, 11));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel meta = new JLabel(monthName() + " · 1m+");
		meta.setForeground(LiveOnTheme.TEXT_MUTED);
		meta.setFont(LiveOnTheme.small());
		monthTotalLabel.setForeground(LiveOnTheme.TEXT_MUTED);
		monthTotalLabel.setFont(LiveOnTheme.small());
		monthTotalLabel.setVisible(false);
		header.add(meta, BorderLayout.CENTER);
		header.add(monthTotalLabel, BorderLayout.EAST);
		return header;
	}

	private static String monthName()
	{
		String name = clanMonth().getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
		return name.substring(0, 1).toUpperCase(new Locale("pt", "BR")) + name.substring(1);
	}

	private static JPanel createEfficiencySection(String titleText, JPanel entries, JPanel ownPosition)
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
		configureOwnPositionFooter(ownPosition);
		section.add(ownPosition, BorderLayout.SOUTH);
		return section;
	}

	private static void configureOwnPositionFooter(JPanel footer)
	{
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		footer.setBackground(PANEL_BACKGROUND);
		footer.setBorder(BorderFactory.createEmptyBorder(0, 7, 8, 7));
		footer.setVisible(false);
	}

	private static JPanel createEfficiencyHeader(String category)
	{
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(PANEL_BACKGROUND);
		header.setBorder(BorderFactory.createEmptyBorder(10, 11, 4, 11));
		JLabel meta = new JLabel(monthName() + " · ganhos no WOM");
		meta.setForeground(LiveOnTheme.TEXT_MUTED);
		meta.setFont(LiveOnTheme.small());
		JLabel unit = new JLabel(category);
		unit.setForeground(LiveOnTheme.TEXT_MUTED);
		unit.setFont(LiveOnTheme.small());
		header.add(meta, BorderLayout.CENTER);
		header.add(unit, BorderLayout.EAST);
		return header;
	}

	void setMonthTotal(long total)
	{
		String text = monthTotalText(total);
		SwingUtilities.invokeLater(() ->
		{
			monthTotalLabel.setText(text == null ? "" : text);
			monthTotalLabel.setVisible(text != null);
		});
	}

	/** Older servers do not send the clan total; the line stays hidden instead of showing zero. */
	static String monthTotalText(long total)
	{
		if (total <= 0) return null;
		return "<html>Total do mês: <font color='" + LiveOnTheme.hex(LiveOnTheme.TEXT) + "'>" + formatValue(total)
			+ "</font></html>";
	}

	synchronized void updateDropRanking(List<MvpDropEntry> ranking, MvpDropEntry own)
	{
		List<MvpDropEntry> updatedRanking = ranking == null
			? Collections.emptyList() : new ArrayList<>(ranking);
		dropsReceived = true;
		String month = clanMonth().toString();
		if (month.equals(dropDataMonth) && updatedRanking.equals(liveRanking)
			&& Objects.equals(own, liveOwnDrop)) return;
		dropDataMonth = month;
		liveRanking = updatedRanking;
		liveOwnDrop = own;
		applyDropPositionChanges(liveRanking, own);
		renderDropRanking(liveRanking, own);
	}

	void setStaff(boolean staff)
	{
		// Kept for the shared panel contract; the ranking has no staff-only controls.
	}

	private void renderDropRanking(List<MvpDropEntry> ranking, MvpDropEntry own)
	{
		List<MvpDropEntry> topTen = ranking == null
			? Collections.emptyList()
			: ranking.subList(0, Math.min(TOP_SIZE, ranking.size()));
		SwingUtilities.invokeLater(() ->
		{
			dropEntries.removeAll();
			if (topTen.isEmpty())
			{
				JLabel empty = new JLabel("Nenhum drop de 1m+ registrado.", SwingConstants.CENTER);
				empty.setForeground(LiveOnTheme.TEXT_MUTED);
				empty.setBorder(BorderFactory.createEmptyBorder(24, 4, 4, 4));
				dropEntries.add(empty);
			}
			else
			{
				long leaderValue = Math.max(1L, topTen.get(0).getTotalValue());
				dropEntries.add(createDropLeader(topTen.get(0), topTen.size() > 1 ? topTen.get(1) : null));
				if (topTen.size() > 1)
				{
					dropEntries.add(dropSectionLabel("Ranking"));
					JPanel list = new JPanel();
					list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
					list.setBackground(LiveOnTheme.CARD);
					list.setAlignmentX(Component.LEFT_ALIGNMENT);
					// Round the card before adding rows: roundCard clears child backgrounds, and the
					// rows paint their own hover/expanded highlight.
					LiveOnTheme.roundCard(list, new java.awt.Insets(3, 3, 3, 3));
					for (int index = 1; index < topTen.size(); index++)
					{
						list.add(createDropRow(index + 1, topTen.get(index), leaderValue));
					}
					dropEntries.add(list);
				}
			}
			renderOwnDropFooter(own);
			dropEntries.revalidate();
			dropEntries.repaint();
		});
	}

	synchronized void updateEfficiencyRankings(List<MvpEfficiencyEntry> ehb, MvpEfficiencyEntry ownEhb,
		List<MvpEfficiencyEntry> ehp, MvpEfficiencyEntry ownEhp)
	{
		List<MvpEfficiencyEntry> updatedEhb = ehb == null
			? Collections.emptyList() : new ArrayList<>(ehb);
		List<MvpEfficiencyEntry> updatedEhp = ehp == null
			? Collections.emptyList() : new ArrayList<>(ehp);
		efficiencyReceived = true;
		String month = clanMonth().toString();
		if (month.equals(efficiencyDataMonth) && updatedEhb.equals(liveEhbRanking)
			&& updatedEhp.equals(liveEhpRanking) && Objects.equals(ownEhb, liveOwnEhb)
			&& Objects.equals(ownEhp, liveOwnEhp)) return;
		efficiencyDataMonth = month;
		liveEhbRanking = updatedEhb;
		liveEhpRanking = updatedEhp;
		liveOwnEhb = ownEhb;
		liveOwnEhp = ownEhp;
		applyEfficiencyPositionChanges("ehb", liveEhbRanking, ownEhb);
		applyEfficiencyPositionChanges("ehp", liveEhpRanking, ownEhp);
		renderEfficiencyRanking(ehbEntries, liveEhbRanking, ownEhb, "EHB");
		renderEfficiencyRanking(ehpEntries, liveEhpRanking, ownEhp, "EHP");
	}

	private void renderEfficiencyRanking(JPanel target, List<MvpEfficiencyEntry> ranking,
		MvpEfficiencyEntry own, String metricType)
	{
		List<MvpEfficiencyEntry> top = ranking == null
			? Collections.emptyList()
			: ranking.subList(0, Math.min(TOP_SIZE, ranking.size()));
		SwingUtilities.invokeLater(() ->
		{
			target.removeAll();
			if (top.isEmpty())
			{
				JLabel empty = new JLabel("Nenhum ganho mensal disponível no WOM.", SwingConstants.CENTER);
				empty.setForeground(LiveOnTheme.TEXT_MUTED);
				empty.setBorder(BorderFactory.createEmptyBorder(24, 4, 4, 4));
				target.add(empty);
			}
			else
			{
				double leaderValue = Math.max(0.0001d, top.get(0).getGained());
				target.add(createEfficiencyLeader(top.get(0), top.size() > 1 ? top.get(1) : null, metricType));
				if (top.size() > 1)
				{
					target.add(dropSectionLabel("Ranking"));
					JPanel list = new JPanel();
					list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
					list.setBackground(LiveOnTheme.CARD);
					list.setAlignmentX(Component.LEFT_ALIGNMENT);
					LiveOnTheme.roundCard(list, new java.awt.Insets(3, 3, 3, 3));
					for (int index = 1; index < top.size(); index++)
					{
						list.add(createEfficiencyRow(index + 1, top.get(index), leaderValue, metricType));
					}
					target.add(list);
				}
			}
			JPanel footer = "EHB".equals(metricType) ? ehbOwnPosition : ehpOwnPosition;
			renderOwnEfficiencyFooter(footer, own, metricType);
			target.revalidate();
			target.repaint();
		});
	}

	private JPanel createEfficiencyLeader(MvpEfficiencyEntry entry, MvpEfficiencyEntry second, String metricType)
	{
		boolean expanded = isEfficiencyExpanded(entry, metricType);
		double lead = second == null ? 0 : entry.getGained() - second.getGained();
		JPanel card = createLeaderCard(entry.getPlayerName(), entry.getAccountType(), entry.getPositionChange(),
			formatHours(entry.getGained()), lead > 0 ? "+" + formatHours(lead) + " à frente" : null);
		if (expanded)
		{
			card.add(createEfficiencyDetails(entry, metricType, LiveOnTheme.GOLD_SOFT, 26), BorderLayout.CENTER);
		}
		makeEfficiencyGroupClickable(card, entry, metricType, efficiencyHint(metricType, expanded, false));
		LiveOnTheme.roundCard(card, GOLD, new java.awt.Insets(9, 8, 9, 10));
		return card;
	}

	private JPanel createEfficiencyRow(int position, MvpEfficiencyEntry entry, double leaderValue, String metricType)
	{
		boolean expanded = isEfficiencyExpanded(entry, metricType);
		DropRow row = new DropRow(Math.min(1d, entry.getGained() / leaderValue), expanded);
		row.add(createRankingSummary(position, entry.getPlayerName(), entry.getAccountType(),
			entry.getPositionChange(), formatHours(entry.getGained()), placeColor(position), LiveOnTheme.TEXT),
			BorderLayout.NORTH);
		if (expanded)
		{
			JPanel details = createEfficiencyDetails(entry, metricType, LiveOnTheme.CARD_HOVER, 55);
			details.setOpaque(false);
			row.add(details, BorderLayout.CENTER);
		}
		makeEfficiencyGroupClickable(row, entry, metricType, efficiencyHint(metricType, expanded, false));
		addHoverHighlight(row, row);
		return row;
	}

	private static final String NO_TOGGLE = "liveon.noToggle";

	/** Link to the member's site profile, shown inside an expanded ranking entry. */
	private static JPanel createProfileLink(String playerName)
	{
		String slug = ClanMessagesPlugin.playerProfileSlug(playerName);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);
		holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		if (slug.isEmpty()) return holder;
		JButton open = new JButton("Ver perfil Live ON ↗");
		open.putClientProperty(NO_TOGGLE, Boolean.TRUE);
		open.setFont(META_FONT);
		open.setFocusable(false);
		open.setMargin(new java.awt.Insets(2, 8, 2, 8));
		open.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		open.setToolTipText("Abrir " + LiveOnUrls.playerProfile(slug));
		open.addActionListener(event ->
			net.runelite.client.util.LinkBrowser.browse(LiveOnUrls.playerProfile(slug)));
		holder.add(open, BorderLayout.WEST);
		return holder;
	}

	private static JPanel createEfficiencyDetails(MvpEfficiencyEntry entry, String metricType,
		Color background, int leftPadding)
	{
		JPanel details = new JPanel();
		details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
		details.setBackground(background);
		details.setBorder(BorderFactory.createEmptyBorder(5, leftPadding, 8, 10));
		// Gains under 0.05h would show as "+0.0h"; drop them so a tiny month reads the same as an empty one.
		List<MvpEfficiencyContribution> shown = new ArrayList<>();
		if (entry.getBreakdown() != null)
		{
			for (MvpEfficiencyContribution contribution : entry.getBreakdown())
			{
				if (contribution.getGained() >= 0.05d && shown.size() < 5) shown.add(contribution);
			}
		}
		if (shown.isEmpty())
		{
			JLabel unavailable = new JLabel("Sem ganhos neste mês");
			unavailable.setFont(META_FONT);
			unavailable.setForeground(LiveOnTheme.TEXT_MUTED);
			details.add(unavailable);
			details.add(javax.swing.Box.createVerticalStrut(6));
			details.add(createProfileLink(entry.getPlayerName()));
			return details;
		}
		for (MvpEfficiencyContribution contribution : shown)
		{
			JPanel line = new JPanel(new BorderLayout(4, 0));
			line.setOpaque(false);
			String label = metricLabel(contribution.getMetric());
			JLabel activity = new JLabel(label);
			activity.setToolTipText(contribution.getMetric());
			activity.setFont(META_FONT);
			activity.setForeground(new Color(215, 215, 215));
			JLabel hours = new JLabel(String.format(Locale.ROOT, "+%.1fh", contribution.getGained()));
			hours.setFont(META_FONT);
			hours.setForeground(LiveOnTheme.GREEN);
			line.add(activity, BorderLayout.CENTER);
			line.add(hours, BorderLayout.EAST);
			line.setAlignmentX(Component.LEFT_ALIGNMENT);
			line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
			details.add(line);
		}
		details.add(javax.swing.Box.createVerticalStrut(6));
		details.add(createProfileLink(entry.getPlayerName()));
		return details;
	}

	private boolean isEfficiencyExpanded(MvpEfficiencyEntry entry, String metricType)
	{
		return isEfficiencyExpanded(entry.getPlayerName(), metricType);
	}

	boolean isEfficiencyExpanded(String playerName, String metricType)
	{
		String selected = "EHB".equals(metricType) ? expandedEhbPlayer : expandedEhpPlayer;
		return normalizePlayer(playerName).equals(selected);
	}

	void toggleEfficiencyExpansion(String playerName, String metricType)
	{
		String player = normalizePlayer(playerName);
		if ("EHB".equals(metricType))
		{
			expandedEhbPlayer = player.equals(expandedEhbPlayer) ? null : player;
			renderEfficiencyRanking(ehbEntries, liveEhbRanking, liveOwnEhb, "EHB");
		}
		else
		{
			expandedEhpPlayer = player.equals(expandedEhpPlayer) ? null : player;
			renderEfficiencyRanking(ehpEntries, liveEhpRanking, liveOwnEhp, "EHP");
		}
	}

	private void makeEfficiencyGroupClickable(Component component, MvpEfficiencyEntry entry, String metricType,
		String hint)
	{
		if (component instanceof javax.swing.JComponent
			&& Boolean.TRUE.equals(((javax.swing.JComponent) component).getClientProperty(NO_TOGGLE)))
		{
			return;
		}
		if (component instanceof javax.swing.JComponent)
		{
			javax.swing.JComponent clickable = (javax.swing.JComponent) component;
			clickable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			if (clickable.getToolTipText() == null)
			{
				clickable.setToolTipText(hint);
			}
			clickable.addMouseListener(new MouseAdapter()
			{
				@Override public void mouseClicked(MouseEvent event)
				{
					toggleEfficiencyExpansion(entry.getPlayerName(), metricType);
				}
			});
		}
		if (component instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) component).getComponents())
			{
				makeEfficiencyGroupClickable(child, entry, metricType, hint);
			}
		}
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

	private JPanel createOwnEfficiencyRow(MvpEfficiencyEntry entry, String metricType)
	{
		JPanel card = createOwnCard(createRankingSummary(entry.getPosition(), entry.getPlayerName(),
			entry.getAccountType(), entry.getPositionChange(), formatHours(entry.getGained()),
			LiveOnTheme.GREEN, LiveOnTheme.GREEN));
		if (isEfficiencyExpanded(entry, metricType))
		{
			JPanel details = createEfficiencyDetails(entry, metricType, LiveOnTheme.GREEN_SOFT,
				37 + placeWidth(entry.getPosition()));
			if (entry.getTotal() != null)
			{
				details.add(javax.swing.Box.createVerticalStrut(5));
				details.add(createAccountTotalBubble(entry.getTotal()));
			}
			card.add(details, BorderLayout.CENTER);
		}
		makeEfficiencyGroupClickable(card, entry, metricType,
			efficiencyHint(metricType, isEfficiencyExpanded(entry, metricType), true));
		LiveOnTheme.roundCard(card, LiveOnTheme.GREEN, new java.awt.Insets(7, 3, 7, 8));
		return card;
	}

	private static JPanel createAccountTotalBubble(double total)
	{
		JPanel bubble = new JPanel(new BorderLayout(4, 0));
		bubble.setBackground(LiveOnTheme.CARD);
		bubble.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel label = new JLabel("Total da conta");
		label.setFont(META_FONT);
		label.setForeground(LiveOnTheme.TEXT_MUTED);
		JLabel value = new JLabel(formatHours(total));
		value.setFont(META_FONT);
		value.setForeground(LiveOnTheme.TEXT);
		bubble.add(label, BorderLayout.CENTER);
		bubble.add(value, BorderLayout.EAST);
		LiveOnTheme.roundCard(bubble, new java.awt.Insets(4, 7, 4, 7));
		bubble.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		return bubble;
	}

	private JPanel createOwnDropRow(MvpDropEntry entry)
	{
		JPanel card = createOwnCard(createRankingSummary(entry.getPosition(), entry.getPlayerName(),
			entry.getAccountType(), entry.getPositionChange(), formatValue(entry.getTotalValue()),
			LiveOnTheme.GREEN, LiveOnTheme.GREEN));
		if (isDropExpanded(entry.getPlayerName()))
		{
			card.add(createInlineDropDetails(entry, LiveOnTheme.GREEN_SOFT,
				37 + placeWidth(entry.getPosition())), BorderLayout.CENTER);
		}
		makeDropGroupClickable(card, entry, dropHint(isDropExpanded(entry.getPlayerName()), true));
		LiveOnTheme.roundCard(card, LiveOnTheme.GREEN, new java.awt.Insets(7, 3, 7, 8));
		return card;
	}

	/** "Sua posição" for someone absent from the ranking: no position, current name, zero value, not clickable. */
	private JPanel createOwnPlaceholder(String zeroText)
	{
		JPanel summary = createRankingSummary(0, ownPlayerName, null, 0, zeroText, LiveOnTheme.GREEN, LiveOnTheme.GREEN);
		JPanel card = createOwnCard(summary);
		LiveOnTheme.roundCard(card, LiveOnTheme.GREEN, new java.awt.Insets(7, 3, 7, 8));
		return card;
	}

	void setOwnPlayerName(String playerName)
	{
		String name = playerName == null ? "" : playerName.trim();
		if (name.equals(ownPlayerName)) return;
		ownPlayerName = name;
		renderDropRanking(liveRanking, liveOwnDrop);
		renderEfficiencyRanking(ehbEntries, liveEhbRanking, liveOwnEhb, "EHB");
		renderEfficiencyRanking(ehpEntries, liveEhpRanking, liveOwnEhp, "EHP");
	}

	private static JPanel createOwnCard(JPanel summary)
	{
		summary.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(LiveOnTheme.GREEN_SOFT);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(summary, BorderLayout.NORTH);
		return card;
	}

	private void renderOwnDropFooter(MvpDropEntry own)
	{
		dropOwnPosition.removeAll();
		// Players with no 1m+ drop this month are absent from the ranking; still pin their row, at zero.
		boolean placeholder = own == null && dropsReceived && !ownPlayerName.isEmpty();
		boolean visible = placeholder || (own != null && own.getPosition() > 0);
		if (visible)
		{
			dropOwnPosition.add(dropSectionLabel("Sua posição"));
			dropOwnPosition.add(own != null ? createOwnDropRow(own) : createOwnPlaceholder(formatValue(0)));
		}
		dropOwnPosition.setVisible(visible);
		dropOwnPosition.revalidate();
		dropOwnPosition.repaint();
	}

	private void renderOwnEfficiencyFooter(JPanel footer, MvpEfficiencyEntry own, String metricType)
	{
		footer.removeAll();
		// Newer servers also send "own" for players with no gain this month (position 0); older ones
		// send null, so fall back to a zero row once the rankings have arrived.
		boolean placeholder = own == null && efficiencyReceived && !ownPlayerName.isEmpty();
		boolean visible = placeholder || own != null;
		if (visible)
		{
			footer.add(dropSectionLabel("Sua posição"));
			footer.add(own != null ? createOwnEfficiencyRow(own, metricType) : createOwnPlaceholder(formatHours(0)));
		}
		footer.setVisible(visible);
		footer.revalidate();
		footer.repaint();
	}

	private synchronized void applyDropPositionChanges(List<MvpDropEntry> ranking, MvpDropEntry own)
	{
		if ((ranking == null || ranking.isEmpty()) && own == null) return;
		loadPositionCache();
		if (ranking != null)
		{
			for (MvpDropEntry entry : ranking) applyPositionChange("drops", entry.getPlayerName(),
				entry.getPosition(), entry::setPositionChange);
		}
		if (own != null) applyPositionChange("drops", own.getPlayerName(), own.getPosition(), own::setPositionChange);
		savePositionCache();
	}

	private synchronized void applyEfficiencyPositionChanges(String rankingType,
		List<MvpEfficiencyEntry> ranking, MvpEfficiencyEntry own)
	{
		if ((ranking == null || ranking.isEmpty()) && own == null) return;
		loadPositionCache();
		if (ranking != null)
		{
			for (MvpEfficiencyEntry entry : ranking) applyPositionChange(rankingType, entry.getPlayerName(),
				entry.getPosition(), entry::setPositionChange);
		}
		if (own != null) applyPositionChange(rankingType, own.getPlayerName(), own.getPosition(), own::setPositionChange);
		savePositionCache();
	}

	private void applyPositionChange(String rankingType, String playerName, int currentPosition,
		java.util.function.IntConsumer setter)
	{
		if (currentPosition <= 0 || playerName == null) return;
		String key = positionCacheMonth + "." + rankingType + "." + normalizePlayer(playerName);
		int previous = parseCachedInt(positionCache.getProperty(key + ".position"));
		int change = previous > 0 && previous != currentPosition
			? previous - currentPosition : parseCachedInt(positionCache.getProperty(key + ".change"));
		if (previous <= 0) change = 0;
		if (previous != currentPosition
			|| change != parseCachedInt(positionCache.getProperty(key + ".change")))
		{
			positionCache.setProperty(key + ".position", Integer.toString(currentPosition));
			positionCache.setProperty(key + ".change", Integer.toString(change));
			positionCacheDirty = true;
		}
		setter.accept(change);
	}

	private void loadPositionCache()
	{
		String month = clanMonth().toString();
		if (positionCacheLoaded && month.equals(positionCacheMonth)) return;
		positionCacheMonth = month;
		if (positionCacheLoaded)
		{
			positionCache.clear();
			positionCacheDirty = true;
			return;
		}
		positionCacheLoaded = true;
		Path file = positionCacheFile();
		if (!Files.isRegularFile(file)) return;
		try (InputStream input = Files.newInputStream(file))
		{
			positionCache.load(input);
			positionCacheDirty = positionCache.keySet().removeIf(key -> !key.toString().startsWith(month + "."));
		}
		catch (IOException ignored)
		{
			positionCache.clear();
		}
	}

	private void savePositionCache()
	{
		if (!positionCacheDirty) return;
		Path file = positionCacheFile();
		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		try
		{
			Files.createDirectories(file.getParent());
			try (OutputStream output = Files.newOutputStream(temporary))
			{
				positionCache.store(output, "Live On MVP ranking positions");
			}
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			positionCacheDirty = false;
		}
		catch (IOException ignored)
		{
			// Ranking display continues normally when the optional cache cannot be saved.
		}
	}

	private static Path positionCacheFile()
	{
		return RuneLite.RUNELITE_DIR.toPath().resolve("live-on-clan").resolve("mvp-positions.properties");
	}

	private static YearMonth clanMonth()
	{
		return YearMonth.now(java.time.ZoneId.of("America/Sao_Paulo"));
	}

	private static int parseCachedInt(String value)
	{
		try { return value == null ? 0 : Integer.parseInt(value); }
		catch (NumberFormatException ignored) { return 0; }
	}

	private static String shortName(String value, int length)
	{
		if (value == null) return "";
		return value.length() <= length ? value : value.substring(0, Math.max(1, length - 1)) + "…";
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
		return String.format(Locale.ROOT, "%.1fh", value);
	}

	private JPanel createDropLeader(MvpDropEntry entry, MvpDropEntry second)
	{
		boolean expanded = isDropExpanded(entry.getPlayerName());
		long lead = second == null ? 0 : entry.getTotalValue() - second.getTotalValue();
		JPanel card = createLeaderCard(entry.getPlayerName(), entry.getAccountType(), entry.getPositionChange(),
			formatValue(entry.getTotalValue()), lead > 0 ? "+" + formatValue(lead) + " à frente" : null);
		if (expanded)
		{
			card.add(createInlineDropDetails(entry, LiveOnTheme.GOLD_SOFT, 26), BorderLayout.CENTER);
		}
		makeDropGroupClickable(card, entry, dropHint(expanded, false));
		LiveOnTheme.roundCard(card, GOLD, new java.awt.Insets(9, 8, 9, 10));
		return card;
	}

	/** Crown, name with movement, value and lead over the 2nd place; callers add details and round it. */
	private static JPanel createLeaderCard(String playerName, String accountType, int change, String valueText,
		String leadText)
	{
		JPanel card = new JPanel(new BorderLayout(0, 6));
		card.setBackground(LiveOnTheme.GOLD_SOFT);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel summary = new JPanel(new BorderLayout(8, 0));
		JLabel crown = new JLabel("♛", SwingConstants.CENTER);
		crown.setFont(crown.getFont().deriveFont(Font.BOLD, 18f));
		crown.setForeground(GOLD);
		JLabel name = new JLabel(shortName(playerName, 16));
		name.setToolTipText(playerName);
		applyAccountIcon(name, accountType);
		name.setForeground(LiveOnTheme.TEXT);
		name.setFont(LiveOnTheme.bold());
		JLabel caption = new JLabel("Líder do mês");
		caption.setForeground(LiveOnTheme.TEXT_MUTED);
		caption.setFont(LiveOnTheme.small());
		JPanel badge = new JPanel(new GridLayout(0, 1, 0, 1));
		badge.add(crown);
		if (change != 0)
		{
			// Under the crown there is spare width; next to the caption it wrapped out of view.
			JLabel movement = changeLabel(change);
			movement.setHorizontalAlignment(SwingConstants.CENTER);
			badge.add(movement);
		}
		JLabel value = new JLabel(valueText, SwingConstants.RIGHT);
		value.setForeground(GOLD);
		value.setFont(LiveOnTheme.bold());
		// Name shares its line only with the short value; the wider "+X à frente" goes under it,
		// so long names are not cut by a wide value column.
		JPanel nameLine = new JPanel(new BorderLayout(6, 0));
		nameLine.add(name, BorderLayout.CENTER);
		nameLine.add(value, BorderLayout.EAST);
		JPanel captionLine = new JPanel(new BorderLayout(6, 0));
		captionLine.add(caption, BorderLayout.WEST);
		if (leadText != null)
		{
			JLabel ahead = new JLabel(leadText, SwingConstants.RIGHT);
			ahead.setForeground(LiveOnTheme.TEXT_FAINT);
			ahead.setFont(LiveOnTheme.small());
			captionLine.add(ahead, BorderLayout.EAST);
		}
		JPanel identity = new JPanel(new GridLayout(0, 1, 0, 1));
		identity.add(nameLine);
		identity.add(captionLine);
		summary.add(badge, BorderLayout.WEST);
		summary.add(identity, BorderLayout.CENTER);
		card.add(summary, BorderLayout.NORTH);
		return card;
	}

	private static Color placeColor(int position)
	{
		return position == 2 ? SILVER : position == 3 ? BRONZE : LiveOnTheme.TEXT_MUTED;
	}

	private JPanel createDropRow(int position, MvpDropEntry entry, long leaderValue)
	{
		boolean expanded = isDropExpanded(entry.getPlayerName());
		DropRow row = new DropRow(Math.min(1d, entry.getTotalValue() / (double) leaderValue), expanded);
		row.add(createRankingSummary(position, entry.getPlayerName(), entry.getAccountType(),
			entry.getPositionChange(), formatValue(entry.getTotalValue()), placeColor(position), LiveOnTheme.TEXT),
			BorderLayout.NORTH);
		if (expanded)
		{
			JPanel details = createInlineDropDetails(entry, LiveOnTheme.CARD_HOVER, 55);
			details.setOpaque(false);
			row.add(details, BorderLayout.CENTER);
		}
		makeDropGroupClickable(row, entry, dropHint(expanded, false));
		addHoverHighlight(row, row);
		return row;
	}

	/** Position, movement, name and value in fixed columns, shared by every ranking row and "Sua posição". */
	private static JPanel createRankingSummary(int position, String playerName, String accountType, int change,
		String valueText, Color placeColor, Color nameColor)
	{
		JPanel summary = new JPanel(new BorderLayout(4, 0));
		summary.setOpaque(false);
		summary.setBorder(BorderFactory.createEmptyBorder(7, 4, 8, 8));
		JLabel place = new JLabel(position > 0 ? Integer.toString(position) : "–", SwingConstants.RIGHT);
		place.setForeground(placeColor);
		place.setFont(LiveOnTheme.bold());
		place.setPreferredSize(new Dimension(placeWidth(position), 16));
		JLabel movement = changeLabel(change);
		movement.setPreferredSize(new Dimension(26, 16));
		JPanel left = new JPanel(new BorderLayout(3, 0));
		left.setOpaque(false);
		left.add(place, BorderLayout.WEST);
		left.add(movement, BorderLayout.EAST);
		JLabel name = new JLabel(shortName(playerName, 16));
		name.setToolTipText(playerName);
		applyAccountIcon(name, accountType);
		name.setForeground(nameColor);
		name.setFont(LiveOnTheme.regular());
		JLabel value = new JLabel(valueText, SwingConstants.RIGHT);
		value.setForeground(LiveOnTheme.TEXT);
		value.setFont(LiveOnTheme.bold());
		summary.add(left, BorderLayout.WEST);
		summary.add(name, BorderLayout.CENTER);
		summary.add(value, BorderLayout.EAST);
		return summary;
	}

	/** Top-25 rows only need two digits; "Sua posição" can be 100+ and must not be cut to "...". */
	private static int placeWidth(int position)
	{
		return position >= 100 ? 26 : 18;
	}

	private static JLabel changeLabel(int change)
	{
		JLabel label = new JLabel(change > 0 ? "▲ " + change : change < 0 ? "▼ " + Math.abs(change) : "•");
		label.setFont(META_FONT);
		label.setForeground(change > 0 ? MOVE_UP : change < 0 ? MOVE_DOWN : LiveOnTheme.TEXT_FAINT);
		if (change != 0)
		{
			label.setToolTipText((change > 0 ? "Subiu " : "Caiu ") + Math.abs(change)
				+ (Math.abs(change) == 1 ? " posição" : " posições"));
		}
		return label;
	}

	private static JLabel dropSectionLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(LiveOnTheme.TEXT_MUTED);
		label.setFont(LiveOnTheme.small());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(11, 4, 5, 0));
		return label;
	}

	private static void addHoverHighlight(Component component, DropRow row)
	{
		component.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseEntered(MouseEvent event) { row.setHover(true); }

			@Override public void mouseExited(MouseEvent event)
			{
				// Moving onto a child label fires an exit on the parent; only clear when truly outside.
				java.awt.Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), row);
				if (!row.contains(point)) row.setHover(false);
			}
		});
		if (component instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) component).getComponents()) addHoverHighlight(child, row);
		}
	}

	/** A ranking row: rounded highlight on hover/expanded, and a thin bar showing its share of the leader. */
	private static final class DropRow extends JPanel
	{
		private final double share;
		private final boolean expanded;
		private boolean hover;

		private DropRow(double share, boolean expanded)
		{
			super(new BorderLayout());
			this.share = share;
			this.expanded = expanded;
			setOpaque(false);
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		private void setHover(boolean hover)
		{
			if (this.hover == hover) return;
			this.hover = hover;
			repaint();
		}

		@Override public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		@Override protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (hover || expanded)
			{
				g.setColor(LiveOnTheme.CARD_HOVER);
				g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
			}
			int barY = getHeight() - 3;
			int barWidth = Math.max(0, getWidth() - 16);
			g.setColor(LiveOnTheme.DIVIDER);
			g.fillRoundRect(8, barY, barWidth, 2, 2, 2);
			g.setColor(LiveOnTheme.CARD_EDGE);
			g.fillRoundRect(8, barY, (int) Math.round(barWidth * share), 2, 2, 2);
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	private static String dropHint(boolean expanded, boolean own)
	{
		if (expanded) return "Clique para fechar";
		return own ? "Clique para ver seus 5 maiores drops" : "Clique para ver os 5 maiores drops";
	}

	private static String efficiencyHint(String metricType, boolean expanded, boolean own)
	{
		if (expanded) return "Clique para fechar";
		if (own) return "Clique para ver seu resumo de " + metricType;
		boolean bosses = "EHB".equals(metricType);
		String subject = bosses ? "os bosses" : "as skills";
		return "Clique para ver " + subject + " com mais horas";
	}

	private void makeDropGroupClickable(Component component, MvpDropEntry entry, String hint)
	{
		if (component instanceof javax.swing.JComponent
			&& Boolean.TRUE.equals(((javax.swing.JComponent) component).getClientProperty(NO_TOGGLE)))
		{
			return;
		}
		if (component instanceof javax.swing.JComponent)
		{
			javax.swing.JComponent clickable = (javax.swing.JComponent) component;
			clickable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			if (clickable.getToolTipText() == null)
			{
				clickable.setToolTipText(hint);
			}
			clickable.addMouseListener(dropToggleListener(entry));
		}
		if (component instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) component).getComponents())
			{
				makeDropGroupClickable(child, entry, hint);
			}
		}
	}

	private MouseAdapter dropToggleListener(MvpDropEntry entry)
	{
		return new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent event)
			{
				toggleDropExpansion(entry.getPlayerName());
			}
		};
	}

	boolean isDropExpanded(String playerName)
	{
		return normalizePlayer(playerName).equals(expandedDropPlayer);
	}

	void toggleDropExpansion(String playerName)
	{
		String player = normalizePlayer(playerName);
		expandedDropPlayer = player.equals(expandedDropPlayer) ? null : player;
		renderDropRanking(liveRanking, liveOwnDrop);
	}

	private static JPanel createInlineDropDetails(MvpDropEntry entry, Color background, int leftPadding)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(background);
		panel.setBorder(BorderFactory.createEmptyBorder(7, leftPadding, 8, 5));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
		panel.add(leftPinnedDropDetails(entry));
		panel.add(javax.swing.Box.createVerticalStrut(6));
		panel.add(createProfileLink(entry.getPlayerName()));
		return panel;
	}

	private static JPanel leftPinnedDropDetails(MvpDropEntry entry)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);
		holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
		holder.add(createDropDetails(entry), BorderLayout.WEST);
		return holder;
	}

	private static JPanel createDropDetails(MvpDropEntry entry)
	{
		JPanel details = new JPanel(new GridLayout(0, 1, 0, 4));
		details.setOpaque(false);
		details.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
		MvpDropDetail[] drops = entry.getTopDrops();
		if (drops == null || drops.length == 0)
		{
			JLabel unavailable = new JLabel("Top 5 ainda sem registros");
			unavailable.setForeground(LiveOnTheme.TEXT_MUTED);
			unavailable.setFont(META_FONT);
			unavailable.setHorizontalAlignment(SwingConstants.LEFT);
			details.add(unavailable);
			details.setPreferredSize(new Dimension(175, 22));
			return details;
		}
		int displayed = 0;
		for (MvpDropDetail drop : drops)
		{
			if (drop.getValue() < 1_000_000L || displayed >= 5) continue;
			String readableItem = drop.getItem().replaceFirst("^(\\d+)x\\s*", "$1x ");
			JLabel item = new JLabel(readableItem);
			item.setToolTipText(drop.getItem() + (drop.getSource() == null || drop.getSource().isEmpty()
				? "" : " • " + drop.getSource()));
			item.setForeground(new Color(215, 215, 215));
			item.setFont(META_FONT);
			item.setHorizontalAlignment(SwingConstants.LEFT);
			details.add(item);
			displayed++;
		}
		details.setPreferredSize(new Dimension(175, Math.max(24, displayed * 18)));
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
