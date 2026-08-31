package com.liveon;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.Scrollable;
import javax.swing.text.BadLocationException;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

final class ClanMessagesPanel extends PluginPanel
{
	private static final int MAX_CHAT_LINES = 500;
	private static final int RETAIN_CHAT_LINES = 350;
	private final JTextArea messages = new JTextArea();
	private final JTextArea composer = new JTextArea(2, 20);
	private final JButton publish = new JButton("Publicar");
	private final JButton publishNotice = new JButton("Publicar aviso fixado");
	private final JButton removeNotice = new JButton("Remover aviso atual");
	private final JTextArea noticeComposer = new JTextArea(4, 20);
	private final JLabel noticeStatus = new JLabel("Nenhum aviso carregado");
	private final javax.swing.JCheckBox pinBroadcast = new javax.swing.JCheckBox("Fixar broadcast");
	private final javax.swing.JToggleButton broadcastMode = new javax.swing.JToggleButton("Broadcast", true);
	private final javax.swing.JToggleButton clanMode = new javax.swing.JToggleButton("Clan channel");
	private final JLabel status = new JLabel("Desconectado");
	private final JLabel footerStatus = new JLabel("Desconectado", SwingConstants.CENTER);
	private final java.awt.CardLayout mainCardsLayout = new java.awt.CardLayout();
	private final JPanel mainCards = new JPanel(mainCardsLayout);
	private final JPanel navigationGrid = new JPanel(new java.awt.GridBagLayout());
	private final JPanel mainArea = new JPanel(new BorderLayout(0, 4));
	private final java.util.List<JButton> navigationButtons = new java.util.ArrayList<>();
	private String selectedPage = "";
	private final JPanel accessTab = new JPanel(new BorderLayout(5, 5));
	private final JPanel chatTab = new JPanel(new BorderLayout(5, 5));
	private final JPanel staffTab = new JPanel(new BorderLayout(5, 5));
	private final MvpPanel mvpTab = new MvpPanel();
	private final PbPanel pbTab;
	private final RanksPanel ranksTab;
	private final RankRequestsPanel rankRequestsTab;
	private final LiveOnPanel liveOnTab;
	private final MvpManagementPanel mvpManagementTab;
	private final ClanTagsPanel clanTagsTab;
	private final JTabbedPane staffSections = new JTabbedPane();
	private JPanel messagesStaffSection;
	private JPanel noticesStaffSection;
	private JPanel livesStaffSection;
	private final DefaultTableModel sentMessagesModel = new DefaultTableModel(new String[]{"Staff", "Tipo", "Mensagem"}, 0)
	{
		@Override public boolean isCellEditable(int row, int column) { return false; }
	};
	private final JTable sentMessagesTable = new JTable(sentMessagesModel);
	private final JLabel sentMessagesStatus = new JLabel(" ");
	private java.util.List<StaffSentMessage> currentSentMessages = new java.util.ArrayList<>();
	// Message label shown in the Access tab under the verify button
	private final JLabel accessMessage = new JLabel();
	private final JPanel connectionWarningPanel = new JPanel(new BorderLayout());
	private final JLabel connectionWarningLabel = new JLabel("", SwingConstants.CENTER);
	private int connectionWarningAttempts;

	ClanMessagesPanel(Runnable publishBroadcastAction, Runnable publishClanAction, Runnable verifyTokenAction, Runnable clearMessagesAction, Runnable refreshRanksAction, Runnable resetRanksAction, Runnable requestRankAction, Runnable refreshRankRequestsAction, java.util.function.Consumer<Integer> deleteRankRequestAction, java.util.function.Consumer<RankRequestsPanel.RankRequest> confirmRankRequestAction, java.util.function.Consumer<RankRequestsPanel.RankRequest> declineRankRequestAction, Runnable refreshSentMessagesAction, java.util.function.Consumer<StaffSentMessage> deleteSentMessageAction, java.util.function.Consumer<StaffSentMessage> resendSentMessageAction, java.util.function.Consumer<StaffSentMessage> togglePinnedMessageAction, java.util.function.Consumer<String> publishPanelNoticeAction, Runnable removePanelNoticeAction, Runnable refreshLivesAction, java.util.function.BiConsumer<String, String> saveLiveChannelAction, java.util.function.Consumer<LiveChannel> deleteLiveChannelAction, Runnable refreshMvpMembersAction, java.util.function.Consumer<String> saveMvpMemberAction, java.util.function.Consumer<MvpMember> deleteMvpMemberAction, Runnable refreshClanTagsAction, java.util.function.BiConsumer<String, String> createClanTagAction, java.util.function.BiConsumer<ClanTag, String> addClanTagMemberAction, java.util.function.Consumer<ClanTag> deleteClanTagAction, java.util.function.BiConsumer<ClanTag, ClanTagMember> removeClanTagMemberAction, Runnable refreshPbCategoriesAction, java.util.function.Consumer<PbCategory> selectPbCategoryAction, String initialStaffAccessKey, java.util.function.Consumer<String> saveStaffAccessKeyAction)
	{
		super(false);
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		createAccessTab(verifyTokenAction);
		createChatTab();
		mvpManagementTab = new MvpManagementPanel(refreshMvpMembersAction, saveMvpMemberAction, deleteMvpMemberAction);
		clanTagsTab = new ClanTagsPanel(refreshClanTagsAction, createClanTagAction,
			addClanTagMemberAction, deleteClanTagAction, removeClanTagMemberAction);
		ranksTab = new RanksPanel(refreshRanksAction, resetRanksAction, requestRankAction);
		rankRequestsTab = new RankRequestsPanel(refreshRankRequestsAction, deleteRankRequestAction, confirmRankRequestAction, declineRankRequestAction);
		liveOnTab = new LiveOnPanel(refreshLivesAction, saveLiveChannelAction, deleteLiveChannelAction);
		pbTab = new PbPanel(refreshPbCategoriesAction, selectPbCategoryAction);
		createStaffTab(publishBroadcastAction, publishClanAction, clearMessagesAction, refreshSentMessagesAction,
			deleteSentMessageAction, resendSentMessageAction, togglePinnedMessageAction,
			publishPanelNoticeAction, removePanelNoticeAction, initialStaffAccessKey, saveStaffAccessKeyAction);
		setAuthenticated(false, false);
		add(mainArea, BorderLayout.CENTER);
		add(createLinksFooter(), BorderLayout.SOUTH);
		setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 650));
	}

	private JPanel createLinksFooter()
	{
		JPanel footer = new JPanel(new BorderLayout());
		footer.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(55, 55, 55)),
			BorderFactory.createEmptyBorder(6, 10, 2, 10)));
		JPanel links = new JPanel(new GridLayout(1, 2, 5, 0));

		JButton discord = new JButton("Discord");
		discord.setIcon(loadIcon("/links/discord.png", 16));
		discord.setToolTipText("Abrir Discord do Live ON");
		discord.setMargin(new java.awt.Insets(3, 6, 3, 6));
		discord.addActionListener(event -> LinkBrowser.browse("https://www.discord.gg/liveon"));

		JButton wom = new JButton("WOM");
		wom.setIcon(loadIcon("/links/wom.png", 16));
		wom.setToolTipText("Abrir grupo no Wise Old Man");
		wom.setMargin(new java.awt.Insets(3, 6, 3, 6));
		wom.addActionListener(event -> LinkBrowser.browse("https://wiseoldman.net/groups/1945"));

		links.add(discord);
		links.add(wom);
		footerStatus.setForeground(new java.awt.Color(165, 165, 165));
		footerStatus.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		footer.add(links, BorderLayout.CENTER);
		footer.add(footerStatus, BorderLayout.SOUTH);
		return footer;
	}

	private ImageIcon loadIcon(String resource, int size)
	{
		java.awt.image.BufferedImage image = ImageUtil.loadImageResource(getClass(), resource);
		if (image == null)
		{
			return null;
		}
		java.awt.Image scaled = image.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH);
		return new ImageIcon(scaled);
	}

	private ImageIcon createUiIcon(String type)
	{
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor("live".equals(type) ? new java.awt.Color(50, 210, 90)
			: ("home".equals(type) || "crown".equals(type) || "trophy".equals(type) || "star".equals(type) || "ranks".equals(type) || "key".equals(type) || "tag".equals(type))
				? new java.awt.Color(225, 170, 45) : new java.awt.Color(210, 210, 210));
		graphics.setStroke(new java.awt.BasicStroke(1.7f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
		if ("refresh".equals(type))
		{
			graphics.drawArc(2, 2, 11, 11, 35, 285);
			graphics.fillPolygon(new int[]{11, 15, 14}, new int[]{1, 3, 6}, 3);
		}
		else if ("resend".equals(type))
		{
			graphics.drawLine(2, 12, 8, 6);
			graphics.drawLine(8, 6, 13, 6);
			graphics.fillPolygon(new int[]{8, 8, 14}, new int[]{2, 10, 6}, 3);
		}
		else if ("pin".equals(type))
		{
			graphics.drawLine(4, 3, 12, 11);
			graphics.drawLine(8, 8, 3, 13);
			graphics.drawLine(7, 2, 13, 8);
			graphics.drawLine(9, 1, 14, 6);
		}
		else if ("trash".equals(type))
		{
			graphics.drawRect(4, 5, 8, 9);
			graphics.drawLine(3, 4, 13, 4);
			graphics.drawLine(6, 2, 10, 2);
			graphics.drawLine(7, 7, 7, 12);
			graphics.drawLine(9, 7, 9, 12);
		}
		else if ("key".equals(type))
		{
			graphics.drawOval(2, 2, 7, 7);
			graphics.drawLine(8, 8, 14, 14);
			graphics.drawLine(11, 11, 13, 9);
			graphics.drawLine(13, 13, 15, 11);
		}
		else if ("trophy".equals(type))
		{
			graphics.drawRect(5, 2, 6, 7);
			graphics.drawArc(1, 3, 6, 6, 80, 200);
			graphics.drawArc(9, 3, 6, 6, -100, 200);
			graphics.drawLine(8, 9, 8, 13);
			graphics.drawLine(5, 14, 11, 14);
		}
		else if ("crown".equals(type))
		{
			graphics.fillPolygon(new int[]{2, 4, 7, 8, 9, 12, 14, 13, 3},
				new int[]{5, 9, 4, 9, 4, 9, 5, 12, 12}, 9);
			graphics.fillRect(3, 12, 10, 2);
		}
		else if ("home".equals(type))
		{
			graphics.drawPolygon(new int[]{2, 8, 14}, new int[]{8, 2, 8}, 3);
			graphics.drawRect(4, 7, 8, 7);
			graphics.drawRect(7, 10, 3, 4);
		}
		else if ("ranks".equals(type))
		{
			graphics.drawPolygon(new int[]{8, 14, 12, 8, 4, 2}, new int[]{1, 4, 11, 15, 11, 4}, 6);
			graphics.drawLine(5, 6, 11, 6);
			graphics.drawLine(6, 9, 10, 9);
		}
		else if ("star".equals(type))
		{
			graphics.fillPolygon(new int[]{8, 10, 15, 11, 12, 8, 4, 5, 1, 6},
				new int[]{1, 6, 6, 9, 14, 11, 14, 9, 6, 6}, 10);
		}
		else if ("live".equals(type))
		{
			graphics.fillOval(4, 4, 8, 8);
		}
		else if ("message".equals(type))
		{
			graphics.drawRoundRect(2, 3, 12, 8, 2, 2);
			graphics.drawLine(5, 11, 4, 14);
			graphics.drawLine(5, 11, 8, 11);
		}
		else if ("requests".equals(type))
		{
			graphics.drawRect(3, 3, 10, 11);
			graphics.drawLine(6, 1, 10, 1);
			graphics.drawLine(5, 6, 11, 6);
			graphics.drawLine(5, 9, 11, 9);
		}
		else if ("tag".equals(type))
		{
			graphics.drawPolygon(new int[]{2, 9, 14, 14, 9, 2}, new int[]{4, 2, 6, 10, 14, 12}, 6);
			graphics.fillOval(5, 6, 2, 2);
		}
		graphics.dispose();
		return new ImageIcon(image);
	}

	private void addNavigationButton(String title, String iconType, JPanel component, String pageKey, String tooltip)
	{
		component.setMinimumSize(new Dimension(0, 0));
		if (component instanceof MvpPanel || component == staffTab)
		{
			// MVP sections already own their scrolling. Wrapping them in another
			// scroll pane makes the inner pane consume wheel events without moving.
			mainCards.add(component, pageKey);
		}
		else
		{
			ViewportWidthPanel viewportContent = new ViewportWidthPanel();
			viewportContent.add(component, BorderLayout.CENTER);
			JScrollPane pageScroll = new JScrollPane(viewportContent);
			pageScroll.setBorder(BorderFactory.createEmptyBorder());
			pageScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			pageScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			pageScroll.getVerticalScrollBar().setUnitIncrement(16);
			pageScroll.setMinimumSize(new Dimension(0, 0));
			mainCards.add(pageScroll, pageKey);
		}
		JButton button = new JButton(title, createUiIcon(iconType));
		button.setName(pageKey);
		button.setToolTipText(tooltip);
		button.setIconTextGap(5);
		button.setMargin(new java.awt.Insets(5, 4, 5, 4));
		button.setFocusPainted(false);
		button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		button.addActionListener(event -> selectPage(pageKey));
		navigationButtons.add(button);
		int index = navigationButtons.size() - 1;
		boolean fullWidth = "staff".equals(pageKey);
		java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
		constraints.gridx = fullWidth ? 0 : index % 2;
		constraints.gridy = index / 2;
		constraints.gridwidth = fullWidth ? 2 : 1;
		constraints.weightx = 1.0;
		constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
		constraints.insets = new java.awt.Insets(0, index % 2 == 1 ? 2 : 0, 3,
			index % 2 == 0 && !fullWidth ? 2 : 0);
		navigationGrid.add(button, constraints);
	}

	private static final class ViewportWidthPanel extends JPanel implements Scrollable
	{
		private ViewportWidthPanel()
		{
			super(new BorderLayout());
			setMinimumSize(new Dimension(0, 0));
		}

		@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		@Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
		@Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return Math.max(16, visibleRect.height - 16); }
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
		@Override public boolean getScrollableTracksViewportHeight() { return false; }
	}

	private void selectPage(String pageKey)
	{
		selectedPage = pageKey;
		mainCardsLayout.show(mainCards, pageKey);
		for (JButton button : navigationButtons)
		{
			boolean selected = pageKey.equals(button.getName());
			button.setForeground(selected ? new java.awt.Color(255, 152, 0) : new java.awt.Color(210, 210, 210));
			button.setBackground(selected ? new java.awt.Color(50, 50, 50) : new java.awt.Color(35, 35, 35));
			button.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, selected ? 2 : 1, 0,
					selected ? new java.awt.Color(255, 152, 0) : new java.awt.Color(55, 55, 55)),
				BorderFactory.createEmptyBorder(3, 3, selected ? 2 : 3, 3)));
		}
	}

	private static void updateNavigationStyle(JTabbedPane tabbedPane)
	{
		for (int index = 0; index < tabbedPane.getTabCount(); index++)
		{
			java.awt.Component component = tabbedPane.getTabComponentAt(index);
			if (!(component instanceof javax.swing.AbstractButton)) continue;
			javax.swing.AbstractButton label = (javax.swing.AbstractButton) component;
			boolean selected = index == tabbedPane.getSelectedIndex();
			label.setForeground(selected ? new java.awt.Color(255, 152, 0) : new java.awt.Color(210, 210, 210));
			label.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, selected ? 2 : 0, 0, new java.awt.Color(255, 152, 0)),
				BorderFactory.createEmptyBorder(5, 3, selected ? 3 : 5, 3)));
		}
	}

	private void configureSectionTabs(JTabbedPane sections, String[] titles, String[] iconTypes)
	{
		sections.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		for (int index = 0; index < sections.getTabCount(); index++)
		{
			JButton label = new JButton(titles[index], createUiIcon(iconTypes[index]));
			label.setIconTextGap(3);
			label.setContentAreaFilled(false);
			label.setFocusPainted(false);
			label.setMargin(new java.awt.Insets(0, 0, 0, 0));
			label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			final int tabIndex = index;
			label.addActionListener(event -> sections.setSelectedIndex(tabIndex));
			sections.setTabComponentAt(index, label);
		}
		sections.addChangeListener(event -> updateNavigationStyle(sections));
		updateNavigationStyle(sections);
	}

	private static void updateStaffTabStyle(JTabbedPane sections)
	{
		for (int index = 0; index < sections.getTabCount(); index++)
		{
			java.awt.Component component = sections.getTabComponentAt(index);
			if (!(component instanceof JButton)) continue;
			JButton button = (JButton) component;
			boolean selected = index == sections.getSelectedIndex();
			button.setBackground(selected ? new java.awt.Color(71, 52, 20) : new java.awt.Color(35, 33, 30));
			button.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, selected ? 2 : 1, 0,
					selected ? new java.awt.Color(255, 152, 0) : new java.awt.Color(60, 57, 52)),
				BorderFactory.createEmptyBorder(3, 8, selected ? 2 : 3, 8)));
		}
	}

	private JButton verifyButton;
	private void createAccessTab(Runnable verifyTokenAction)
	{
		accessTab.setBorder(BorderFactory.createEmptyBorder(18, 8, 12, 8));
		JPanel welcome = new JPanel();
		welcome.setLayout(new BoxLayout(welcome, BoxLayout.Y_AXIS));
		welcome.add(Box.createVerticalGlue());
		JLabel logo = new JLabel();
		java.awt.image.BufferedImage logoImage = ImageUtil.loadImageResource(getClass(), "/live-on-logo.png");
		if (logoImage != null)
		{
			java.awt.Image scaled = logoImage.getScaledInstance(180, 180, java.awt.Image.SCALE_SMOOTH);
			logo.setIcon(new ImageIcon(scaled));
		}
		logo.setHorizontalAlignment(JLabel.CENTER);
		logo.setAlignmentX(Component.CENTER_ALIGNMENT);
		welcome.add(logo);
		welcome.add(Box.createVerticalStrut(8));
		JLabel title = new JLabel("Bem-vindo ao Live On Clan", SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		welcome.add(title);
		welcome.add(Box.createVerticalStrut(12));
		// Authentication via RSN + WOM group verification. Use default button size so countdown fits.
		verifyButton = new JButton("Verificar agora");
		verifyButton.setBackground(new java.awt.Color(190, 104, 0));
		verifyButton.setForeground(java.awt.Color.WHITE);
		verifyButton.addActionListener(event -> verifyTokenAction.run());
		verifyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		verifyButton.setMaximumSize(new Dimension(190, 30));
		welcome.add(verifyButton);
		welcome.add(Box.createVerticalStrut(8));
		connectionWarningPanel.setBackground(new java.awt.Color(57, 43, 20));
		connectionWarningPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new java.awt.Color(205, 126, 15)),
			BorderFactory.createEmptyBorder(7, 8, 7, 8)));
		connectionWarningPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		connectionWarningPanel.setMaximumSize(new Dimension(205, 58));
		connectionWarningLabel.setForeground(new java.awt.Color(255, 193, 72));
		connectionWarningPanel.add(connectionWarningLabel, BorderLayout.CENTER);
		connectionWarningPanel.setVisible(false);
		welcome.add(connectionWarningPanel);
		accessMessage.setHorizontalAlignment(JLabel.CENTER);
		accessMessage.setForeground(new java.awt.Color(235, 178, 55));
		accessMessage.setAlignmentX(Component.CENTER_ALIGNMENT);
		accessMessage.setText("");
		accessMessage.setVisible(false);
		welcome.add(accessMessage);
		welcome.add(Box.createVerticalStrut(14));
		JLabel description = new JLabel(
			"<html><center>Acompanhe a disputa pelos MVPs<br>"
				+ "Confira os melhores PBs do clã<br>"
				+ "Saiba quem está ao vivo na Twitch<br>"
				+ "Atualize seu rank automaticamente<br>"
				+ "Receba os comunicados oficiais do clã.</center></html>",
			SwingConstants.CENTER);
		description.setForeground(new java.awt.Color(185, 185, 185));
		description.setAlignmentX(Component.CENTER_ALIGNMENT);
		welcome.add(description);
		welcome.add(Box.createVerticalStrut(8));
		JLabel membersOnly = new JLabel("Exclusivo para membros", SwingConstants.CENTER);
		membersOnly.setForeground(new java.awt.Color(210, 160, 55));
		membersOnly.setAlignmentX(Component.CENTER_ALIGNMENT);
		welcome.add(membersOnly);
		welcome.add(Box.createVerticalGlue());
		accessTab.add(welcome, BorderLayout.CENTER);
	}

	void setVerifyEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (verifyButton != null)
			{
				verifyButton.setEnabled(enabled);
				if (enabled) verifyButton.setText("Verificar agora");
			}
		});
	}

	void startVerifyCooldown(int seconds)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (verifyButton == null) return;
			verifyButton.setEnabled(false);
			final int[] remaining = { seconds };
			javax.swing.Timer timer = new javax.swing.Timer(1000, null);
			timer.addActionListener(e ->
			{
				remaining[0]--;
				if (remaining[0] <= 0)
				{
					timer.stop();
					verifyButton.setEnabled(true);
					verifyButton.setText("Verificar agora");
				}
				else
				{
					verifyButton.setText("Verificar em " + remaining[0] + "s");
				}
			});
			verifyButton.setText("Verificar em " + remaining[0] + "s");
			timer.setInitialDelay(1000);
			timer.start();
		});
	}

	private void createChatTab()
	{
		messages.setEditable(false);
		messages.setLineWrap(true);
		messages.setWrapStyleWord(true);
		chatTab.add(new JScrollPane(messages), BorderLayout.CENTER);
		chatTab.add(status, BorderLayout.SOUTH);
	}

	private void createStaffTab(Runnable publishBroadcastAction, Runnable publishClanAction, Runnable clearMessagesAction, Runnable refreshSentMessagesAction, java.util.function.Consumer<StaffSentMessage> deleteSentMessageAction, java.util.function.Consumer<StaffSentMessage> resendSentMessageAction, java.util.function.Consumer<StaffSentMessage> togglePinnedMessageAction, java.util.function.Consumer<String> publishPanelNoticeAction, Runnable removePanelNoticeAction, String initialStaffAccessKey, java.util.function.Consumer<String> saveStaffAccessKeyAction)
	{
		staffTab.setBackground(new java.awt.Color(28, 26, 23));
		staffTab.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(122, 82, 24)));
		JPanel publisher = new JPanel(new BorderLayout(5, 5));
		JPanel composerHeader = new JPanel(new BorderLayout());
		JLabel composerTitle = new JLabel("Mensagem para o clã");
		JButton expandComposer = new JButton("Expandir");
		expandComposer.setMargin(new java.awt.Insets(1, 5, 1, 5));
		JPanel composerTitleRow = new JPanel(new BorderLayout(4, 0));
		composerTitleRow.add(composerTitle, BorderLayout.CENTER);
		composerTitleRow.add(expandComposer, BorderLayout.EAST);
		composerHeader.add(composerTitleRow, BorderLayout.NORTH);
		javax.swing.ButtonGroup publishModes = new javax.swing.ButtonGroup();
		publishModes.add(broadcastMode);
		publishModes.add(clanMode);
		JPanel modeSelector = new JPanel(new GridLayout(1, 2, 2, 0));
		modeSelector.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
		modeSelector.add(broadcastMode);
		modeSelector.add(clanMode);
		pinBroadcast.setToolTipText("Entregar este broadcast também aos próximos jogadores que entrarem");
		JPanel composerOptions = new JPanel(new BorderLayout());
		composerOptions.add(modeSelector, BorderLayout.NORTH);
		composerOptions.add(pinBroadcast, BorderLayout.SOUTH);
		composerHeader.add(composerOptions, BorderLayout.SOUTH);
		publisher.add(composerHeader, BorderLayout.NORTH);
		broadcastMode.addActionListener(event -> pinBroadcast.setVisible(true));
		clanMode.addActionListener(event -> pinBroadcast.setVisible(false));
		composer.setLineWrap(true);
		composer.setWrapStyleWord(true);
		JScrollPane composerScrollPane = new JScrollPane(composer);
		composerScrollPane.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 48));
		expandComposer.addActionListener(event ->
		{
			boolean expanded = "Expandir".equals(expandComposer.getText());
			expandComposer.setText(expanded ? "Recolher" : "Expandir");
			composerScrollPane.setPreferredSize(new Dimension(
				PluginPanel.PANEL_WIDTH - 20, expanded ? 120 : 48));
			publisher.setMaximumSize(new Dimension(Integer.MAX_VALUE, publisher.getPreferredSize().height));
			publisher.revalidate();
		});
		publisher.add(composerScrollPane, BorderLayout.CENTER);
		publish.setText("Enviar mensagem");
		publish.setBackground(new java.awt.Color(190, 104, 0));
		publish.setForeground(java.awt.Color.WHITE);
		publish.addActionListener(event ->
		{
			if (broadcastMode.isSelected()) publishBroadcastAction.run();
			else publishClanAction.run();
		});
		JButton clear = new JButton(createUiIcon("trash"));
		clear.setToolTipText("Limpar todas as mensagens");
		clear.setPreferredSize(new Dimension(36, 26));
		clear.addActionListener(event ->
		{
			int result = JOptionPane.showConfirmDialog(this, "Apagar todas as mensagens do clã?", "Confirmar limpeza", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result == JOptionPane.YES_OPTION) clearMessagesAction.run();
		});
		JPanel publishActions = new JPanel(new BorderLayout(4, 0));
		publishActions.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		publishActions.add(publish, BorderLayout.CENTER);
		publishActions.add(clear, BorderLayout.EAST);
		publisher.add(publishActions, BorderLayout.SOUTH);

		JPanel history = new JPanel(new BorderLayout(5, 5));
		history.setBorder(BorderFactory.createTitledBorder("Mensagens enviadas"));
		sentMessagesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		sentMessagesTable.getColumnModel().getColumn(0).setPreferredWidth(55);
		sentMessagesTable.getColumnModel().getColumn(0).setMaxWidth(70);
		sentMessagesTable.getColumnModel().getColumn(1).setPreferredWidth(58);
		sentMessagesTable.getColumnModel().getColumn(1).setMaxWidth(72);
		history.add(new JScrollPane(sentMessagesTable), BorderLayout.CENTER);
		JButton refresh = new JButton(createUiIcon("refresh"));
		refresh.setToolTipText("Atualizar mensagens");
		refresh.addActionListener(event -> refreshSentMessagesAction.run());
		JButton resend = new JButton(createUiIcon("resend"));
		resend.setToolTipText("Reenviar mensagem selecionada");
		resend.addActionListener(event -> withSelectedSentMessage(resendSentMessageAction));
		JButton remove = new JButton(createUiIcon("trash"));
		remove.setToolTipText("Remover mensagem selecionada");
		remove.addActionListener(event -> withSelectedSentMessage(deleteSentMessageAction));
		JButton togglePinned = new JButton(createUiIcon("pin"));
		togglePinned.setToolTipText("Fixar ou desfixar broadcast selecionado");
		togglePinned.addActionListener(event -> withSelectedSentMessage(togglePinnedMessageAction));
		JPanel historyActions = new JPanel(new GridLayout(1, 4, 3, 0));
		historyActions.add(refresh);
		historyActions.add(resend);
		historyActions.add(togglePinned);
		historyActions.add(remove);
		JPanel historyFooter = new JPanel(new BorderLayout(3, 3));
		historyFooter.add(historyActions, BorderLayout.NORTH);
		historyFooter.add(sentMessagesStatus, BorderLayout.SOUTH);
		history.add(historyFooter, BorderLayout.SOUTH);

		publisher.setMaximumSize(new Dimension(Integer.MAX_VALUE, publisher.getPreferredSize().height));
		history.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 12, 175));
		history.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
		messagesStaffSection = new JPanel();
		messagesStaffSection.setLayout(new BoxLayout(messagesStaffSection, BoxLayout.Y_AXIS));
		publisher.setAlignmentX(Component.LEFT_ALIGNMENT);
		history.setAlignmentX(Component.LEFT_ALIGNMENT);
		messagesStaffSection.add(publisher);
		messagesStaffSection.add(Box.createVerticalStrut(5));
		messagesStaffSection.add(history);
		messagesStaffSection.add(Box.createVerticalGlue());
		noticesStaffSection = createNoticesManagement(publishPanelNoticeAction, removePanelNoticeAction);
		livesStaffSection = liveOnTab.managementPanel();
		staffSections.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
		staffSections.setBackground(new java.awt.Color(28, 26, 23));
		rebuildStaffSections(false);
		staffSections.addChangeListener(event -> updateStaffTabStyle(staffSections));
		staffTab.add(staffSections, BorderLayout.CENTER);

		JPanel security = new JPanel(new BorderLayout(4, 4));
		security.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(55, 55, 55)));
		JButton securityToggle = new JButton("▸ Segurança da staff");
		securityToggle.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
		JPasswordField staffAccessKey = new JPasswordField(
			initialStaffAccessKey == null ? "" : initialStaffAccessKey);
		staffAccessKey.setToolTipText("Chave administrativa configurada no servidor");
		JButton saveStaffKey = new JButton("Salvar chave");
		JLabel staffKeyStatus = new JLabel(" ");
		saveStaffKey.addActionListener(event ->
		{
			char[] password = staffAccessKey.getPassword();
			try
			{
				saveStaffAccessKeyAction.accept(new String(password).trim());
				staffKeyStatus.setText(password.length == 0 ? "Chave removida" : "Chave salva");
			}
			finally
			{
				java.util.Arrays.fill(password, '\0');
			}
		});
		JPanel securityFooter = new JPanel(new BorderLayout(4, 4));
		securityFooter.add(saveStaffKey, BorderLayout.NORTH);
		securityFooter.add(staffKeyStatus, BorderLayout.SOUTH);
		JPanel securityContent = new JPanel(new BorderLayout(4, 4));
		securityContent.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		securityContent.add(staffAccessKey, BorderLayout.CENTER);
		securityContent.add(securityFooter, BorderLayout.SOUTH);
		securityContent.setVisible(false);
		securityToggle.addActionListener(event ->
		{
			boolean show = !securityContent.isVisible();
			securityContent.setVisible(show);
			securityToggle.setText(show ? "▾ Segurança da staff" : "▸ Segurança da staff");
			security.revalidate();
		});
		security.add(securityToggle, BorderLayout.NORTH);
		security.add(securityContent, BorderLayout.CENTER);
		staffTab.add(security, BorderLayout.SOUTH);
	}

	private JPanel createNoticesManagement(java.util.function.Consumer<String> publishPanelNoticeAction,
		Runnable removePanelNoticeAction)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
		JLabel explanation = new JLabel("<html><div style='width:175px'><b>Aviso fixo no painel</b><br><br>"
			+ "Este aviso ficará visível<br>"
			+ "no topo da aba Painel.<br><br>"
			+ "Ao publicar um novo aviso,<br>"
			+ "o atual será substituído.</div></html>");
		explanation.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(explanation);
		panel.add(Box.createVerticalStrut(7));
		noticeComposer.setLineWrap(true);
		noticeComposer.setWrapStyleWord(true);
		noticeComposer.setToolTipText("Texto que será exibido na seção Avisos da página Início");
		JScrollPane noticeScroll = new JScrollPane(noticeComposer);
		noticeScroll.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 72));
		noticeScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
		noticeScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(noticeScroll);
		panel.add(Box.createVerticalStrut(7));
		publishNotice.setBackground(new java.awt.Color(190, 104, 0));
		publishNotice.setForeground(java.awt.Color.WHITE);
		publishNotice.addActionListener(event ->
		{
			String text = noticeComposer.getText().trim();
			if (text.isEmpty())
			{
				noticeStatus.setText("Digite o aviso antes de publicar");
				return;
			}
			noticeStatus.setText("Publicando...");
			publishPanelNoticeAction.accept(text);
		});
		removeNotice.addActionListener(event ->
		{
			noticeStatus.setText("Removendo...");
			removePanelNoticeAction.run();
		});
		JPanel actions = new JPanel(new GridLayout(0, 1, 4, 0));
		actions.add(publishNotice);
		actions.add(removeNotice);
		actions.add(noticeStatus);
		actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, actions.getPreferredSize().height));
		actions.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(actions);
		panel.add(Box.createVerticalGlue());
		return panel;
	}


	private void addStaffSection(String title, java.awt.Component component, String tooltip, String iconType)
	{
		staffSections.addTab(title, component);
		JButton button = new JButton(createUiIcon(iconType));
		button.setToolTipText(tooltip);
		button.setPreferredSize(new Dimension(44, 28));
		button.setFocusPainted(false);
		button.setMargin(new java.awt.Insets(3, 12, 3, 12));
		button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		button.addActionListener(event ->
		{
			int index = staffSections.indexOfComponent(component);
			if (index >= 0) staffSections.setSelectedIndex(index);
		});
		staffSections.setTabComponentAt(staffSections.indexOfComponent(component), button);
		updateStaffTabStyle(staffSections);
	}

	void setDeputyOwner(boolean deputyOwner)
	{
		SwingUtilities.invokeLater(() ->
		{
			rebuildStaffSections(deputyOwner);
			staffTab.revalidate();
			staffTab.repaint();
		});
	}

	private void rebuildStaffSections(boolean deputyOwner)
	{
		java.awt.Component selected = staffSections.getSelectedComponent();
		staffSections.removeAll();
		addStaffSection("Ranks", rankRequestsTab, "Solicitações de rank", "requests");
		addStaffSection("Broadcast", messagesStaffSection, "Enviar broadcasts e mensagens", "message");
		addStaffSection("Lives", livesStaffSection, "Gerenciar canais da Twitch", "live");
		if (deputyOwner)
		{
			addStaffSection("MVP", mvpManagementTab, "Gerenciar membros MVP", "trophy");
			addStaffSection("Tags", clanTagsTab, "Gerenciar etiquetas do clã", "tag");
		}
		addStaffSection("Aviso painel", noticesStaffSection, "Gerenciar aviso fixo do Painel", "pin");
		int selectedIndex = selected == null ? -1 : staffSections.indexOfComponent(selected);
		if (selectedIndex >= 0) staffSections.setSelectedIndex(selectedIndex);
	}

	private void withSelectedSentMessage(java.util.function.Consumer<StaffSentMessage> action)
	{
		int selectedRow = sentMessagesTable.getSelectedRow();
		if (selectedRow < 0 || selectedRow >= currentSentMessages.size())
		{
			sentMessagesStatus.setText("Selecione uma mensagem");
			return;
		}
		action.accept(currentSentMessages.get(selectedRow));
	}

	void setAuthenticated(boolean authenticated, boolean staff)
	{
		SwingUtilities.invokeLater(() ->
		{
			mvpTab.setStaff(authenticated && staff);
			publishNotice.setEnabled(authenticated && staff);
			removeNotice.setEnabled(authenticated && staff);
			mainArea.removeAll();
			mainCards.removeAll();
			navigationGrid.removeAll();
			navigationButtons.clear();
			if (!authenticated)
			{
				mainCards.add(accessTab, "access");
				mainArea.add(mainCards, BorderLayout.CENTER);
				mainCardsLayout.show(mainCards, "access");
				mainArea.revalidate();
				mainArea.repaint();
				return;
			}
			addNavigationButton("Painel", "home", liveOnTab, "home", "Painel principal do clã");
			addNavigationButton("Ranks", "star", ranksTab, "ranks", "Solicitação de ranks");
			addNavigationButton("MVPs", "crown", mvpTab, "mvp", "Rankings MVP");
			addNavigationButton("PBs", "trophy", pbTab, "pbs", "Recordes pessoais do clã");
			if (staff)
			{
				addNavigationButton("Staff", "key", staffTab, "staff", "Abrir painel da staff");
			}
			mainArea.add(navigationGrid, BorderLayout.NORTH);
			mainArea.add(mainCards, BorderLayout.CENTER);
			selectPage("home");
			mainArea.revalidate();
			mainArea.repaint();
		});
	}

	void setConnectionDisabled(boolean disabled)
	{
		SwingUtilities.invokeLater(() ->
		{
			verifyButton.setEnabled(true);
			verifyButton.setText("Verificar agora");
			if (disabled)
			{
				connectionWarningAttempts = 0;
				connectionWarningPanel.setVisible(false);
				accessMessage.setText("");
				accessMessage.setVisible(false);
				footerStatus.setText("Aguardando verificação");
			}
			else
			{
				connectionWarningAttempts = 0;
				connectionWarningPanel.setVisible(false);
				accessMessage.setText("");
				accessMessage.setVisible(false);
				footerStatus.setText("Conectando...");
			}
		});
	}

	void showConnectionRequired()
	{
		SwingUtilities.invokeLater(() ->
		{
			connectionWarningAttempts++;
			connectionWarningLabel.setText(connectionWarningAttempts == 1
				? "<html><center>Ative <b>Conectar ao clan</b><br>nas configurações do plugin.</center></html>"
				: "<html><center>A conexão continua desativada.<br>Ative a opção e tente novamente.</center></html>");
			connectionWarningPanel.setVisible(true);
			accessMessage.setVisible(false);
			footerStatus.setText("Conexão desativada");
			accessTab.revalidate();
			accessTab.repaint();
		});
	}

	void setAuthenticatedPlayer(String playerName)
	{
		SwingUtilities.invokeLater(() -> footerStatus.setText(
			playerName == null || playerName.trim().isEmpty()
				? "Desconectado"
				: "Autenticado como " + playerName.trim()));
	}

	void setBroadcastAllowed(boolean allowed)
	{
		SwingUtilities.invokeLater(() ->
		{
			broadcastMode.setEnabled(allowed);
			if (!allowed)
			{
				clanMode.setSelected(true);
				pinBroadcast.setSelected(false);
				pinBroadcast.setVisible(false);
			}
			else
			{
				pinBroadcast.setVisible(broadcastMode.isSelected());
			}
			staffTab.revalidate();
			staffTab.repaint();
		});
	}

	void setAccessMessage(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			connectionWarningAttempts = 0;
			connectionWarningPanel.setVisible(false);
			if (text == null || text.trim().isEmpty())
			{
				accessMessage.setText("");
				accessMessage.setVisible(false);
			}
			else
			{
				accessMessage.setText("<html><center>" + text + "</center></html>");
				accessMessage.setVisible(true);
			}
		});
	}

	String getDraft() { return composer.getText().trim(); }
	String getCurrentRank() { return ranksTab.getCurrentRank(); }
	void clearDraft() { composer.setText(""); }
	void setDraft(String text, boolean pinned)
	{
		SwingUtilities.invokeLater(() ->
		{
			composer.setText(text == null ? "" : text);
			pinBroadcast.setSelected(pinned);
		});
	}
	void clearMessages() { SwingUtilities.invokeLater(() -> messages.setText("")); }
	void setPublishing(boolean value) { publish.setEnabled(!value); }
	void setMvpDrops(java.util.List<MvpDropEntry> ranking) { mvpTab.updateDropRanking(ranking); }
	void setMvpParticipationEnabled(boolean enabled) { mvpTab.setParticipationEnabled(enabled); }
	void updatePbCategories(java.util.List<PbCategory> categories) { pbTab.updateCategories(categories); }
	void beginPbRankingRequest(long generation) { pbTab.beginRankingRequest(generation); }
	void updatePbRanking(PbRankingResponse response, long generation) { pbTab.updateRanking(response, generation); }
	void setPbRefreshEnabled(boolean enabled) { pbTab.setRefreshEnabled(enabled); }
	void setPbParticipationEnabled(boolean enabled) { pbTab.setParticipationEnabled(enabled); }
	PbCategory selectedPbCategory() { return pbTab.selectedCategory(); }
	void setMvpEfficiency(java.util.List<MvpEfficiencyEntry> ehb, java.util.List<MvpEfficiencyEntry> ehp)
	{
		mvpTab.updateEfficiencyRankings(ehb, ehp);
	}
	void updateRanks(String playerName, String clanRank, javax.swing.Icon clanRankIcon,
		String evaluatedRank, javax.swing.Icon evaluatedRankIcon,
		String possibleRank, javax.swing.Icon possibleRankIcon, java.util.List<String> nextChecks,
		java.util.List<String> overviewChecks, String advice)
	{
		ranksTab.update(playerName, clanRank, clanRankIcon, evaluatedRank, evaluatedRankIcon,
			possibleRank, possibleRankIcon,
			nextChecks, overviewChecks, advice);
	}
	void clearRankDetails() { ranksTab.clearDetails(); }
	void clearRanksStatus() { ranksTab.setStatus(" "); }
	void resetRanks() { ranksTab.reset(); }
	void setRankRequestState(boolean pending, int cooldownSeconds)
	{
		ranksTab.setRankRequestState(pending, cooldownSeconds);
	}
	void setStatus(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(text);
			footerStatus.setText(text);
			ranksTab.setStatus(text);
		});
	}
	void setStatusSuccess(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(text);
			footerStatus.setText(text);
			ranksTab.setStatusSuccess(text);
		});
	}
	void addMessage(ClanMessage message)
	{
		SwingUtilities.invokeLater(() ->
		{
			messages.append("[" + (message.getAuthor() == null ? "Clã" : message.getAuthor()) + "] " + message.getMessage() + "\n");
			if (Boolean.TRUE.equals(message.getPinned())
				|| (message.getPinned() instanceof Number && ((Number) message.getPinned()).intValue() != 0))
			{
				liveOnTab.updatePinnedNotice(message.getMessage());
			}
			trimMessageHistory();
		});
	}

	private void trimMessageHistory()
	{
		int lineCount = messages.getLineCount();
		if (lineCount <= MAX_CHAT_LINES)
		{
			return;
		}
		try
		{
			int removeUpToLine = Math.max(0, lineCount - RETAIN_CHAT_LINES);
			int removeEndOffset = messages.getLineStartOffset(removeUpToLine);
			messages.replaceRange("", 0, removeEndOffset);
		}
		catch (BadLocationException exception)
		{
			messages.setText("");
		}
	}
	void updateRankRequests(java.util.List<RankRequestsPanel.RankRequest> requests)
	{
		rankRequestsTab.update(requests);
	}
	void setRankRequestsStatus(String text)
	{
		rankRequestsTab.setStatus(text);
	}
	void updateRankRequestActivity(java.util.List<RankRequestsPanel.RankRequestActivity> activities)
	{
		rankRequestsTab.updateActivity(activities);
	}
	void updateSentMessages(java.util.List<StaffSentMessage> sentMessages)
	{
		SwingUtilities.invokeLater(() ->
		{
			currentSentMessages = new java.util.ArrayList<>(sentMessages);
			sentMessagesModel.setRowCount(0);
			for (StaffSentMessage sentMessage : sentMessages)
			{
				String compactMode = "CLAN".equalsIgnoreCase(sentMessage.mode) ? "Clan" : "Broadcast";
				if (sentMessage.isPinned()) compactMode += " •";
				sentMessagesModel.addRow(new Object[]{sentMessage.author == null ? "—" : sentMessage.author, compactMode, sentMessage.message});
			}
			sentMessagesStatus.setText(sentMessages.size() + " mensagem(ns)");
		});
	}
	void setSentMessagesStatus(String text)
	{
		SwingUtilities.invokeLater(() -> sentMessagesStatus.setText(text));
	}
	void updatePanelNotice(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			String value = message == null ? "" : message.trim();
			noticeComposer.setText(value);
			noticeStatus.setText(value.isEmpty() ? "Nenhum aviso publicado" : "Aviso atual carregado");
			liveOnTab.updatePinnedNotice(value.isEmpty() ? null : value);
		});
	}
	void setPanelNoticeStatus(String text)
	{
		SwingUtilities.invokeLater(() -> noticeStatus.setText(text));
	}
	void updateOnlineLives(java.util.List<LiveChannel> channels) { liveOnTab.updateOnline(channels); }
	void updateRecentActivities(java.util.List<RecentActivity> activities) { liveOnTab.updateRecent(activities); }
	void updateManagedLives(java.util.List<LiveChannel> channels) { liveOnTab.updateManaged(channels); }
	void setLivesStatus(String text) { liveOnTab.setStatus(text); }
	void clearLiveFields() { liveOnTab.clearFields(); }
	void updateMvpMembers(java.util.List<MvpMember> members) { mvpManagementTab.update(members); }
	void setMvpMembersStatus(String text) { mvpManagementTab.setStatus(text); }
	void clearMvpMemberField() { mvpManagementTab.clearField(); }
	void updateClanTags(ClanTagsResponse response) { clanTagsTab.update(response); }
	void setClanTagsStatus(String text) { clanTagsTab.setStatus(text); }
	void clearClanTagCode() { clanTagsTab.clearCode(); }
	void clearClanTagMember() { clanTagsTab.clearMember(); }

	boolean isPinSelected()
	{
		return pinBroadcast.isSelected();
	}

	static final class StaffSentMessage
	{
		String id;
		String author;
		String message;
		String mode;
		Object pinned;

		boolean isPinned()
		{
			return Boolean.TRUE.equals(pinned)
				|| (pinned instanceof Number && ((Number) pinned).intValue() != 0);
		}
	}
}
