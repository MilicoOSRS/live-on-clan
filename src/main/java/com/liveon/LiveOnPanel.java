package com.liveon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.ImageUtil;

final class LiveOnPanel extends JPanel
{
	private static final Pattern RECORD_TIME_PATTERN = Pattern.compile(
		"(?:\\s*[·•|-]\\s*|\\s+)(\\d{1,2}(?::\\d{2}){1,2}(?:\\.\\d{1,2})?)$");
	private final JPanel onlineChannels = new JPanel();
	private final JPanel onlineSection = new JPanel(new BorderLayout(5, 5));
	private final JPanel staffManagement = new JPanel(new BorderLayout(5, 5));
	private final JTextField rsn = new JTextField();
	private final JTextField twitch = new JTextField();
	private final JLabel status = new JLabel(" ");
	private final JLabel pinnedNotice = new JLabel("<html><font color='#ff981f'>&#128204; Aviso</font><br><font color='#a0a0a0'>Nenhum aviso fixado.</font></html>");
	private final JPanel recentActivities = new JPanel();
	private final Set<String> expandedActivities = new HashSet<>();
	private List<RecentActivity> currentActivities = Collections.emptyList();
	private final DefaultTableModel model = new DefaultTableModel(new String[]{"RSN", "Twitch", "Status"}, 0)
	{
		@Override public boolean isCellEditable(int row, int column) { return false; }
	};
	private final JTable table = new JTable(model);
	private List<LiveChannel> managedChannels = new ArrayList<>();

	LiveOnPanel(Runnable refreshAction, BiConsumer<String, String> saveAction,
		Consumer<LiveChannel> deleteAction)
	{
		setLayout(new BorderLayout(6, 8));
		setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
		JPanel home = new JPanel();
		home.setLayout(new BoxLayout(home, BoxLayout.Y_AXIS));
		JPanel notice = new JPanel(new BorderLayout());
		notice.setAlignmentX(LEFT_ALIGNMENT);
		notice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
		notice.setBackground(LiveOnTheme.CARD);
		LiveOnTheme.roundCard(notice, LiveOnTheme.ORANGE, new java.awt.Insets(7, 8, 7, 6));
		notice.add(pinnedNotice);
		home.add(notice);
		home.add(javax.swing.Box.createVerticalStrut(8));
		home.add(createSectionDivider());
		home.add(javax.swing.Box.createVerticalStrut(8));
		JLabel liveSectionTitle = LiveOnTheme.sectionTitle("Ao vivo na Twitch");
		liveSectionTitle.setForeground(LiveOnTheme.GREEN);
		home.add(liveSectionTitle);
		home.add(javax.swing.Box.createVerticalStrut(5));
		onlineChannels.setLayout(new BoxLayout(onlineChannels, BoxLayout.Y_AXIS));
		onlineChannels.setAlignmentX(LEFT_ALIGNMENT);
		onlineChannels.setMinimumSize(new Dimension(0, 0));
		onlineChannels.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		home.add(onlineChannels);
		home.add(javax.swing.Box.createVerticalStrut(8));
		home.add(createSectionDivider());
		home.add(javax.swing.Box.createVerticalStrut(8));
		home.add(LiveOnTheme.sectionTitle("Atividade do clã"));
		recentActivities.setLayout(new BoxLayout(recentActivities, BoxLayout.Y_AXIS));
		recentActivities.setAlignmentX(LEFT_ALIGNMENT);
		home.add(recentActivities);
		onlineSection.add(home, BorderLayout.CENTER);

		JPanel fields = new JPanel(new GridLayout(0, 1, 3, 3));
		fields.setBorder(BorderFactory.createTitledBorder("Gerenciar canais"));
		fields.add(new JLabel("RSN"));
		fields.add(rsn);
		fields.add(new JLabel("Canal da Twitch"));
		fields.add(twitch);
		JButton save = new JButton("Associar / Atualizar");
		save.setBackground(LiveOnTheme.ACTION);
		save.setForeground(Color.WHITE);
		save.addActionListener(event -> saveAction.accept(rsn.getText().trim(), twitch.getText().trim()));
		fields.add(save);
		staffManagement.add(fields, BorderLayout.NORTH);

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		staffManagement.add(new JScrollPane(table), BorderLayout.CENTER);
		JButton refresh = new JButton("Atualizar");
		refresh.addActionListener(event -> refreshAction.run());
		JButton remove = new JButton("Remover");
		remove.setToolTipText("Remover canal selecionado");
		remove.addActionListener(event ->
		{
			int row = table.getSelectedRow();
			if (row < 0 || row >= managedChannels.size())
			{
				setStatus("Selecione um canal");
				return;
			}
			deleteAction.accept(managedChannels.get(row));
		});
		JPanel actions = new JPanel(new GridLayout(1, 2, 3, 0));
		actions.add(refresh);
		actions.add(remove);
		JPanel footer = new JPanel(new BorderLayout(3, 3));
		footer.add(actions, BorderLayout.NORTH);
		footer.add(status, BorderLayout.SOUTH);
		staffManagement.add(footer, BorderLayout.SOUTH);
		add(onlineSection, BorderLayout.CENTER);
		updatePinnedNotice(null);
		updateOnline(Collections.emptyList());
		updateRecent(Collections.emptyList());
	}

	JPanel managementPanel()
	{
		return staffManagement;
	}

	void updateOnline(List<LiveChannel> channels)
	{
		SwingUtilities.invokeLater(() ->
		{
			onlineChannels.removeAll();
			List<LiveChannel> displayed = channels == null ? new ArrayList<>() : new ArrayList<>(channels);
			if (displayed.isEmpty())
			{
				JLabel empty = new JLabel("Nenhuma live ativa no momento.");
				empty.setHorizontalAlignment(JLabel.CENTER);
				empty.setForeground(LiveOnTheme.TEXT_MUTED);
				empty.setBorder(BorderFactory.createEmptyBorder(14, 4, 14, 4));
				onlineChannels.add(empty);
			}
			else
			{
				for (LiveChannel channel : displayed)
				{
					onlineChannels.add(createLiveCard(channel));
				}
			}
			onlineChannels.revalidate();
			onlineChannels.repaint();
		});
	}

	void updatePinnedNotice(String message)
	{
		SwingUtilities.invokeLater(() -> pinnedNotice.setText(message == null || message.trim().isEmpty()
			? "<html><font color='#ff981f'>&#128204; Aviso</font><br><font color='#a0a0a0'>Nenhum aviso fixado.</font></html>"
			: "<html><font color='#ff981f'>&#128204; Aviso</font><br><div style='width:135px'>" + escapeHtml(message.trim()) + "</div></html>"));
	}

	private static String escapeHtml(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	void updateRecent(List<RecentActivity> activities)
	{
		SwingUtilities.invokeLater(() ->
		{
			recentActivities.removeAll();
			List<RecentActivity> displayed = activities == null ? new ArrayList<>() : new ArrayList<>(activities);
			displayed.removeIf(activity -> activity == null || !isFeedActivity(activity.type));
			if (displayed.size() > 10) displayed = new ArrayList<>(displayed.subList(0, 10));
			currentActivities = new ArrayList<>(displayed);
			Set<String> visibleKeys = new HashSet<>();
			for (RecentActivity activity : displayed) visibleKeys.add(activityKey(activity));
			expandedActivities.retainAll(visibleKeys);
			if (displayed.isEmpty())
			{
				JLabel empty = new JLabel("Nenhuma atividade recente.");
				empty.setForeground(LiveOnTheme.TEXT_MUTED);
				empty.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
				recentActivities.add(empty);
			}
			else
			{
			int rowIndex = 0;
			for (RecentActivity activity : displayed)
			{
				JPanel row = new JPanel(new BorderLayout(7, 0));
				row.setAlignmentX(LEFT_ALIGNMENT);
				Color accent = activityAccent(activity.type);
				// Subtle zebra striping so consecutive cards stay easy to tell apart.
				row.setBackground(rowIndex % 2 == 0 ? LiveOnTheme.CARD : LiveOnTheme.CARD_HOVER);
				rowIndex++;
				String icon = "MVP_LEADER".equals(activity.type) || "MVP_WINNER".equals(activity.type) ? "\u265B"
					: "DROP_MILESTONE".equals(activity.type) ? "\u25C6" : "\u25B2";
				boolean isClanRecord = "CLAN_RECORD".equals(activity.type);
				// The card border follows the activity accent, but the trophy itself always
				// stays gold - only the border should track the (now neutral) record color.
				JLabel marker = isClanRecord
					? new JLabel(LiveOnTheme.trophyIcon(LiveOnTheme.GOLD, 14))
					: new JLabel(icon);
				applyActivityIcon(marker, activity);
				if (!isClanRecord) marker.setForeground(accent);
				marker.setVerticalAlignment(JLabel.TOP);
				marker.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
				String player = activity.player_name == null ? "" : activity.player_name;
				String detail = activity.title == null ? "Atividade registrada" : activity.title;
				String recordTime = "";
				String mvpValue = "";
				if ("CLAN_RECORD".equals(activity.type))
				{
					Matcher timeMatcher = RECORD_TIME_PATTERN.matcher(detail);
					if (timeMatcher.find())
					{
						recordTime = timeMatcher.group(1);
						detail = detail.substring(0, timeMatcher.start()).trim();
					}
					detail = detail.replaceFirst("(?i)^novo recorde\\s+em\\s+",
						"Novo melhor tempo do clã em ");
					detail = detail.replaceFirst("(?i)^novo melhor tempo\\s+em\\s+",
						"Novo melhor tempo do clã em ");
				}
				else if ("MVP_LEADER".equals(activity.type) || "MVP_WINNER".equals(activity.type))
				{
					int valueSeparator = detail.lastIndexOf(" · ");
					if (valueSeparator > 0 && valueSeparator + 3 < detail.length())
					{
						mvpValue = detail.substring(valueSeparator + 3).trim();
						detail = detail.substring(0, valueSeparator).trim();
					}
				}
				boolean collective = player.isEmpty();
				boolean clanRecord = "CLAN_RECORD".equals(activity.type);
				String key = activityKey(activity);
				// Records and player-less announcements always wrap: the one-line label cut them at ~28 chars.
				boolean alwaysWrapped = clanRecord || collective;
				boolean expanded = alwaysWrapped || expandedActivities.contains(key);
				boolean expandable = !alwaysWrapped && activityNeedsExpansion(
					player, detail, mvpValue, collective, clanRecord);
				JPanel text = createActivityText(player, detail, recordTime, mvpValue,
					collective, clanRecord, expanded);
				int collapsedHeight = 49;
				int rowHeight = expanded ? Math.max(collapsedHeight, text.getPreferredSize().height + 14) : collapsedHeight;
				row.setPreferredSize(new Dimension(210, rowHeight));
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
				row.add(marker, BorderLayout.WEST);
				row.add(text, BorderLayout.CENTER);
				if (expandable)
				{
					Runnable toggleActivity = () ->
					{
						if (!expandedActivities.remove(key)) expandedActivities.add(key);
						updateRecent(currentActivities);
					};
					JButton toggle = new JButton(new ActivityToggleIcon(expanded));
					toggle.setToolTipText(expanded ? "Recolher atividade" : "Expandir atividade");
					toggle.setMargin(new java.awt.Insets(0, 1, 0, 1));
					toggle.setFocusable(false);
					toggle.setPreferredSize(new Dimension(22, 22));
					toggle.addActionListener(event -> toggleActivity.run());
					row.add(toggle, BorderLayout.EAST);
					makeClickable(row, toggleActivity);
				}
				LiveOnTheme.roundCard(row, accent, new java.awt.Insets(6, 8, 6, 5));
				recentActivities.add(row);
				recentActivities.add(javax.swing.Box.createVerticalStrut(4));
			}
			}
			recentActivities.revalidate();
			recentActivities.repaint();
		});
	}

	private static void makeClickable(Component component, Runnable action)
	{
		if (!(component instanceof AbstractButton))
		{
			component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			component.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent event)
				{
					if (event.getButton() == MouseEvent.BUTTON1) action.run();
				}
			});
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				makeClickable(child, action);
			}
		}
	}

	private static JPanel createActivityText(String player, String detail, String recordTime, String mvpValue,
		boolean collective, boolean clanRecord, boolean expanded)
	{
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		String timeSuffix = recordTime.isEmpty() ? "" : " · " + recordTime;
		String valueSuffix = mvpValue.isEmpty() ? "" : " · " + mvpValue;
		String tooltip = collective ? escapeHtml(detail + timeSuffix)
			: "<html><b>" + escapeHtml(player) + "</b><br>"
				+ escapeHtml(detail + timeSuffix + valueSuffix) + "</html>";
		if (clanRecord && !recordTime.isEmpty())
		{
			JLabel caption = new JLabel("Novo melhor tempo do clã");
			caption.setForeground(LiveOnTheme.TEXT_FAINT);
			caption.setFont(LiveOnTheme.small());
			caption.setAlignmentX(LEFT_ALIGNMENT);
			text.add(caption);
			String boss = detail.replaceFirst("(?i)^novo melhor tempo do clã em\\s+", "").trim();
			JLabel bossLabel = expanded
				? new JLabel("<html><div style='width:128px'><b>" + escapeHtml(boss) + "</b></div></html>")
				: new JLabel("<html><b>" + escapeHtml(abbreviate(boss, RECORD_BOSS_VISIBLE)) + "</b></html>");
			bossLabel.setForeground(LiveOnTheme.TEXT);
			bossLabel.setToolTipText("<html>Novo melhor tempo do clã · <b>" + escapeHtml(player) + "</b><br>"
				+ escapeHtml(detail + timeSuffix + valueSuffix) + "</html>");
			bossLabel.setAlignmentX(LEFT_ALIGNMENT);
			text.add(bossLabel);

			JLabel byline = new JLabel("<html><font color='" + LiveOnTheme.hex(LiveOnTheme.TEXT_MUTED) + "'>"
				+ escapeHtml(abbreviate(player, 16)) + " · " + escapeHtml(recordTime) + "</font></html>");
			byline.setToolTipText(tooltip);
			byline.setAlignmentX(LEFT_ALIGNMENT);
			text.add(byline);
			return text;
		}
		if (!collective && !mvpValue.isEmpty())
		{
			JLabel summary = expanded
				? new JLabel("<html><div style='width:128px'><b>" + escapeHtml(detail) + "</b></div></html>")
				: new JLabel("<html><b>" + escapeHtml(abbreviate(detail, 29)) + "</b></html>");
			summary.setForeground(LiveOnTheme.TEXT);
			summary.setToolTipText(tooltip);
			summary.setAlignmentX(LEFT_ALIGNMENT);
			text.add(summary);

			JLabel byline = new JLabel("<html><font color='" + LiveOnTheme.hex(LiveOnTheme.TEXT_MUTED) + "'>"
				+ escapeHtml(abbreviate(player, 15)) + " · </font><font color='"
				+ LiveOnTheme.hex(LiveOnTheme.GOLD) + "'>" + escapeHtml(mvpValue) + "</font></html>");
			byline.setToolTipText(tooltip);
			byline.setAlignmentX(LEFT_ALIGNMENT);
			text.add(byline);
			return text;
		}
		if (expanded)
		{
			JLabel full = new JLabel("<html><div style='width:125px'>"
				+ (collective ? "<b>" + escapeHtml(detail) + "</b>"
				: "<b>" + escapeHtml(player) + "</b><br>" + escapeHtml(detail))
				+ "</div></html>");
			full.setToolTipText(tooltip);
			text.add(full);
			return text;
		}

		if (!collective)
		{
			JLabel name = new JLabel("<html><b>" + escapeHtml(abbreviate(player, 22)) + "</b></html>");
			name.setForeground(LiveOnTheme.TEXT);
			name.setToolTipText(tooltip);
			text.add(name);
		}
		JLabel summary = new JLabel(abbreviate(detail, collective ? 50 : 29));
		summary.setForeground(collective ? LiveOnTheme.TEXT : LiveOnTheme.TEXT_MUTED);
		summary.setToolTipText(tooltip);
		text.add(summary);
		return text;
	}

	static boolean activityNeedsExpansion(String player, String detail, String mvpValue,
		boolean collective, boolean clanRecord)
	{
		if (collective)
		{
			return detail.length() > 50;
		}
		if (clanRecord)
		{
			String boss = detail.replaceFirst("(?i)^novo melhor tempo do clã em\\s+", "").trim();
			return player.length() > 16 || boss.length() > RECORD_BOSS_VISIBLE;
		}
		if (!mvpValue.isEmpty())
		{
			return player.length() > 15 || detail.length() > 29;
		}
		return player.length() > 22 || detail.length() > 29;
	}

	private static String activityKey(RecentActivity activity)
	{
		return String.valueOf(activity.type) + '\u0000' + String.valueOf(activity.player_name)
			+ '\u0000' + String.valueOf(activity.title);
	}

	// Long enough for "Tombs of Amascut: Expert Mode" so the raid mode is never cut off.
	private static final int RECORD_BOSS_VISIBLE = 30;

	private static Color activityAccent(String type)
	{
		if ("MVP_LEADER".equals(type) || "MVP_WINNER".equals(type)) return LiveOnTheme.GOLD;
		if ("DROP_MILESTONE".equals(type)) return LiveOnTheme.RED;
		// Green is reserved for live/online indicators; clan records get the same neutral
		// gray as promotions instead.
		return LiveOnTheme.CARD_EDGE;
	}

	private static JPanel createSectionDivider()
	{
		JPanel divider = new JPanel();
		divider.setAlignmentX(LEFT_ALIGNMENT);
		divider.setBackground(LiveOnTheme.DIVIDER);
		divider.setPreferredSize(new Dimension(210, 1));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return divider;
	}

	private static boolean isFeedActivity(String type)
	{
		return "PROMOTION".equals(type) || "CLAN_RECORD".equals(type)
			|| "MVP_LEADER".equals(type) || "MVP_WINNER".equals(type)
			|| "DROP_MILESTONE".equals(type);
	}

	private static String abbreviate(String value, int maximumLength)
	{
		if (value == null || value.length() <= maximumLength) return value;
		return value.substring(0, Math.max(0, maximumLength - 1)).trim() + "…";
	}

	private static final class ActivityToggleIcon implements javax.swing.Icon
	{
		private final boolean expanded;

		private ActivityToggleIcon(boolean expanded)
		{
			this.expanded = expanded;
		}

		@Override
		public void paintIcon(Component component, java.awt.Graphics graphics, int x, int y)
		{
			graphics.setColor(component.isEnabled() ? LiveOnTheme.TEXT_MUTED : LiveOnTheme.TEXT_FAINT);
			int[] xs = {x, x + 8, x + 4};
			int[] ys = expanded ? new int[]{y + 6, y + 6, y + 1} : new int[]{y + 1, y + 1, y + 6};
			graphics.fillPolygon(xs, ys, 3);
		}

		@Override public int getIconWidth() { return 9; }
		@Override public int getIconHeight() { return 8; }
	}

	private void applyActivityIcon(JLabel marker, RecentActivity activity)
	{
		String resource = null;
		if ("PROMOTION".equals(activity.type) && activity.title != null)
		{
			String normalized = activity.title.toLowerCase(java.util.Locale.ROOT);
			for (String rank : new String[]{"cabo", "aluno", "sargento", "cadete", "tenente", "capitao", "major", "coronel"})
			{
				String display = "capitao".equals(rank) ? "capitão" : rank;
				if (normalized.endsWith(display)) { resource = "/ranks/" + rank + ".png"; break; }
			}
		}
		if (resource != null)
		{
			java.awt.image.BufferedImage image = ImageUtil.loadImageResource(getClass(), resource);
			if (image != null) marker.setIcon(new javax.swing.ImageIcon(
				image.getScaledInstance(18, 18, java.awt.Image.SCALE_SMOOTH)));
			marker.setText("");
		}
	}

	private static JPanel createLiveCard(LiveChannel channel)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setPreferredSize(new Dimension(210, 54));
		card.setMinimumSize(new Dimension(0, 54));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
		card.setBackground(LiveOnTheme.CARD);

		JPanel heading = new JPanel(new BorderLayout(5, 0));
		heading.setOpaque(false);
		heading.setAlignmentX(LEFT_ALIGNMENT);
		heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 21));
		String playerName = channel.playerName == null ? "Streamer" : channel.playerName;
		JLabel name = new JLabel("<html><font color='" + LiveOnTheme.hex(LiveOnTheme.GREEN) + "'>\u25CF</font> <b>"
			+ escapeHtml(abbreviate(playerName, 18)) + "</b></html>");
		name.setToolTipText(playerName);
		name.setForeground(LiveOnTheme.TEXT);
		heading.add(name, BorderLayout.CENTER);
		JLabel liveBadge = new JLabel("AO VIVO");
		liveBadge.setForeground(LiveOnTheme.GREEN);
		liveBadge.setFont(LiveOnTheme.small());
		heading.add(liveBadge, BorderLayout.EAST);
		card.add(heading);

		JPanel details = new JPanel(new BorderLayout(5, 0));
		details.setOpaque(false);
		details.setAlignmentX(LEFT_ALIGNMENT);
		details.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		String fullUrl = channel.url == null ? "" : channel.url;
		String shortUrl = fullUrl.replaceFirst("(?i)^https?://(?:www\\.)?", "");
		JLabel url = new JLabel(abbreviate(shortUrl, 22));
		url.setForeground(LiveOnTheme.TEXT_MUTED);
		url.setToolTipText(fullUrl);
		details.add(url, BorderLayout.CENTER);
		JButton open = new JButton("Abrir");
		open.setBackground(LiveOnTheme.GREEN_SOFT);
		open.setForeground(LiveOnTheme.GREEN);
		open.setMargin(new java.awt.Insets(1, 8, 1, 8));
		open.setPreferredSize(new Dimension(54, 22));
		open.setToolTipText("Abrir " + fullUrl);
		open.addActionListener(event -> LinkBrowser.browse(fullUrl));
		details.add(open, BorderLayout.EAST);
		card.add(details);
		LiveOnTheme.roundCard(card, LiveOnTheme.GREEN, new java.awt.Insets(5, 8, 5, 6));
		return card;
	}

	void updateManaged(List<LiveChannel> channels)
	{
		SwingUtilities.invokeLater(() ->
		{
			managedChannels = channels == null ? new ArrayList<>() : new ArrayList<>(channels);
			model.setRowCount(0);
			for (LiveChannel channel : managedChannels)
			{
				model.addRow(new Object[]{channel.playerName, channel.twitchLogin, channel.online ? "AO VIVO" : "Offline"});
			}
			status.setText(managedChannels.size() + " canal(is)");
		});
	}

	void setStatus(String text)
	{
		SwingUtilities.invokeLater(() -> status.setText(text));
	}

	void clearFields()
	{
		SwingUtilities.invokeLater(() ->
		{
			rsn.setText("");
			twitch.setText("");
		});
	}
}
