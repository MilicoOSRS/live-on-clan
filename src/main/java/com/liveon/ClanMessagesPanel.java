package com.liveon;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
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
	private final javax.swing.JCheckBox pinBroadcast = new javax.swing.JCheckBox("Fixar broadcast");
	private final JLabel status = new JLabel("Desconectado");
	private final JTabbedPane tabs = new JTabbedPane();
	private final JPanel accessTab = new JPanel(new BorderLayout(5, 5));
	private final JPanel chatTab = new JPanel(new BorderLayout(5, 5));
	private final JPanel staffTab = new JPanel(new BorderLayout(5, 5));
	private final MvpPanel mvpTab = new MvpPanel();
	private final RanksPanel ranksTab;
	private final RankRequestsPanel rankRequestsTab;
	private final LiveOnPanel liveOnTab;
	private final MvpManagementPanel mvpManagementTab;
	private final DefaultTableModel sentMessagesModel = new DefaultTableModel(new String[]{"Tipo", "Fixada", "Mensagem"}, 0)
	{
		@Override public boolean isCellEditable(int row, int column) { return false; }
	};
	private final JTable sentMessagesTable = new JTable(sentMessagesModel);
	private final JLabel sentMessagesStatus = new JLabel(" ");
	private java.util.List<StaffSentMessage> currentSentMessages = new java.util.ArrayList<>();
	// Message label shown in the Access tab under the verify button
	private final JLabel accessMessage = new JLabel();

	ClanMessagesPanel(Runnable publishBroadcastAction, Runnable publishClanAction, Runnable verifyTokenAction, Runnable clearMessagesAction, Runnable refreshRanksAction, Runnable resetRanksAction, Runnable requestRankAction, Runnable refreshRankRequestsAction, java.util.function.Consumer<Integer> deleteRankRequestAction, java.util.function.Consumer<RankRequestsPanel.RankRequest> confirmRankRequestAction, java.util.function.Consumer<RankRequestsPanel.RankRequest> declineRankRequestAction, Runnable refreshSentMessagesAction, java.util.function.Consumer<StaffSentMessage> deleteSentMessageAction, java.util.function.Consumer<StaffSentMessage> resendSentMessageAction, java.util.function.Consumer<StaffSentMessage> togglePinnedMessageAction, Runnable refreshLivesAction, java.util.function.BiConsumer<String, String> saveLiveChannelAction, java.util.function.Consumer<LiveChannel> deleteLiveChannelAction, Runnable refreshMvpMembersAction, java.util.function.Consumer<String> saveMvpMemberAction, java.util.function.Consumer<MvpMember> deleteMvpMemberAction, String initialStaffAccessKey, java.util.function.Consumer<String> saveStaffAccessKeyAction)
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		createAccessTab(verifyTokenAction);
		createChatTab();
		mvpManagementTab = new MvpManagementPanel(refreshMvpMembersAction, saveMvpMemberAction, deleteMvpMemberAction);
		createStaffTab(publishBroadcastAction, publishClanAction, clearMessagesAction, refreshSentMessagesAction, deleteSentMessageAction, resendSentMessageAction, togglePinnedMessageAction, initialStaffAccessKey, saveStaffAccessKeyAction);
		ranksTab = new RanksPanel(refreshRanksAction, resetRanksAction, requestRankAction);
		rankRequestsTab = new RankRequestsPanel(refreshRankRequestsAction, deleteRankRequestAction, confirmRankRequestAction, declineRankRequestAction);
		liveOnTab = new LiveOnPanel(refreshLivesAction, saveLiveChannelAction, deleteLiveChannelAction);
		setAuthenticated(false, false);
		add(tabs, BorderLayout.CENTER);
		add(createLinksFooter(), BorderLayout.SOUTH);
		setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 650));
	}

	private JPanel createLinksFooter()
	{
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 3));
		footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(55, 55, 55)));

		JButton discord = new JButton("Discord");
		discord.setIcon(loadIcon("/links/discord.png"));
		discord.setToolTipText("Abrir Discord do Live ON");
		discord.setMargin(new java.awt.Insets(2, 7, 2, 7));
		discord.addActionListener(event -> LinkBrowser.browse("https://www.discord.gg/liveon"));

		JButton wom = new JButton("WOM");
		wom.setIcon(loadIcon("/links/wom.png"));
		wom.setToolTipText("Abrir grupo no Wise Old Man");
		wom.setMargin(new java.awt.Insets(2, 7, 2, 7));
		wom.addActionListener(event -> LinkBrowser.browse("https://wiseoldman.net/groups/1945"));

		footer.add(discord);
		footer.add(wom);
		return footer;
	}

	private ImageIcon loadIcon(String resource)
	{
		java.awt.image.BufferedImage image = ImageUtil.loadImageResource(getClass(), resource);
		return image == null ? null : new ImageIcon(image);
	}

	private JButton verifyButton;
	private void createAccessTab(Runnable verifyTokenAction)
	{
		JLabel logo = new JLabel();
		java.awt.image.BufferedImage logoImage = ImageUtil.loadImageResource(getClass(), "/live-on-logo.png");
		if (logoImage != null)
		{
			java.awt.Image scaled = logoImage.getScaledInstance(230, 230, java.awt.Image.SCALE_SMOOTH);
			logo.setIcon(new ImageIcon(scaled));
		}
		logo.setHorizontalAlignment(JLabel.CENTER);
		accessTab.add(logo, BorderLayout.NORTH);
		JPanel fields = new JPanel(new GridLayout(0, 1, 3, 3));
		// Authentication via RSN + WOM group verification. Use default button size so countdown fits.
		verifyButton = new JButton("Verificar agora");
		verifyButton.addActionListener(event -> verifyTokenAction.run());
		JPanel buttonWrap = new JPanel();
		buttonWrap.add(verifyButton);
		// Access status shown under the verification button.
		fields.add(buttonWrap);
		accessMessage.setHorizontalAlignment(JLabel.CENTER);
		accessMessage.setText("");
		accessMessage.setVisible(false);
		fields.add(accessMessage);
		accessTab.add(fields, BorderLayout.CENTER);
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

	private void createStaffTab(Runnable publishBroadcastAction, Runnable publishClanAction, Runnable clearMessagesAction, Runnable refreshSentMessagesAction, java.util.function.Consumer<StaffSentMessage> deleteSentMessageAction, java.util.function.Consumer<StaffSentMessage> resendSentMessageAction, java.util.function.Consumer<StaffSentMessage> togglePinnedMessageAction, String initialStaffAccessKey, java.util.function.Consumer<String> saveStaffAccessKeyAction)
	{
		JPanel publisher = new JPanel(new BorderLayout(5, 5));
		JPanel composerHeader = new JPanel(new BorderLayout());
		composerHeader.add(new JLabel("Mensagem para o clã:"), BorderLayout.NORTH);
		pinBroadcast.setToolTipText("Entregar este broadcast também aos próximos jogadores que entrarem");
		composerHeader.add(pinBroadcast, BorderLayout.SOUTH);
		publisher.add(composerHeader, BorderLayout.NORTH);
		composer.setLineWrap(true);
		composer.setWrapStyleWord(true);
		JScrollPane composerScrollPane = new JScrollPane(composer);
		composerScrollPane.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 100));
		publisher.add(composerScrollPane, BorderLayout.CENTER);
		publish.setText("Publicar broadcast");
		publish.addActionListener(event -> publishBroadcastAction.run());
		JButton clanPublish = new JButton("Publicar clan channel");
		clanPublish.addActionListener(event -> publishClanAction.run());
		JButton clear = new JButton("Limpar mensagens");
		clear.addActionListener(event ->
		{
			int result = JOptionPane.showConfirmDialog(this, "Apagar todas as mensagens do clã?", "Confirmar limpeza", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result == JOptionPane.YES_OPTION) clearMessagesAction.run();
		});
		JPanel publishActions = new JPanel(new GridLayout(3, 1, 3, 3));
		publishActions.add(publish);
		publishActions.add(clanPublish);
		publishActions.add(clear);
		publisher.add(publishActions, BorderLayout.SOUTH);

		JPanel history = new JPanel(new BorderLayout(5, 5));
		history.setBorder(BorderFactory.createTitledBorder("Mensagens enviadas"));
		sentMessagesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		sentMessagesTable.getColumnModel().getColumn(0).setPreferredWidth(75);
		sentMessagesTable.getColumnModel().getColumn(0).setMaxWidth(90);
		sentMessagesTable.getColumnModel().getColumn(1).setPreferredWidth(50);
		sentMessagesTable.getColumnModel().getColumn(1).setMaxWidth(60);
		history.add(new JScrollPane(sentMessagesTable), BorderLayout.CENTER);
		JButton refresh = new JButton("Atualizar");
		refresh.addActionListener(event -> refreshSentMessagesAction.run());
		JButton resend = new JButton("Reenviar");
		resend.addActionListener(event -> withSelectedSentMessage(resendSentMessageAction));
		JButton remove = new JButton("Remover");
		remove.addActionListener(event -> withSelectedSentMessage(deleteSentMessageAction));
		JButton togglePinned = new JButton("Fixar / Desfixar");
		togglePinned.addActionListener(event -> withSelectedSentMessage(togglePinnedMessageAction));
		JPanel historyActions = new JPanel(new GridLayout(4, 1, 3, 3));
		historyActions.add(refresh);
		historyActions.add(resend);
		historyActions.add(togglePinned);
		historyActions.add(remove);
		JPanel historyFooter = new JPanel(new BorderLayout(3, 3));
		historyFooter.add(historyActions, BorderLayout.NORTH);
		historyFooter.add(sentMessagesStatus, BorderLayout.SOUTH);
		history.add(historyFooter, BorderLayout.SOUTH);

		javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, publisher, history);
		split.setResizeWeight(0.48);
		split.setBorder(null);
		JTabbedPane staffSections = new JTabbedPane();
		staffSections.addTab("Mensagens", split);
		staffSections.addTab("MVP", mvpManagementTab);
		staffTab.add(staffSections, BorderLayout.CENTER);

		JPanel security = new JPanel(new BorderLayout(4, 4));
		security.setBorder(BorderFactory.createTitledBorder("Seguranca da staff"));
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
		security.add(staffAccessKey, BorderLayout.CENTER);
		security.add(securityFooter, BorderLayout.SOUTH);
		staffTab.add(security, BorderLayout.SOUTH);
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
			liveOnTab.setStaff(authenticated && staff);
			tabs.removeAll();
			if (!authenticated)
			{
				tabs.addTab("Acesso", accessTab);
				return;
			}
			tabs.addTab("MVP's", mvpTab);
			tabs.addTab("Ranks", ranksTab);
			tabs.addTab("Live ON", liveOnTab);
			if (staff)
			{
				tabs.addTab("Staff", staffTab);
				tabs.addTab("Solicitações", rankRequestsTab);
			}
		});
	}

	void setAccessMessage(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
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
	void setMvpEfficiency(java.util.List<MvpEfficiencyEntry> ehb, java.util.List<MvpEfficiencyEntry> ehp)
	{
		mvpTab.updateEfficiencyRankings(ehb, ehp);
	}
	void updateRanks(String currentRank, String possibleRank, java.util.List<String> checks, String advice) { ranksTab.update(currentRank, possibleRank, checks, advice); }
	void clearRankDetails() { ranksTab.clearDetails(); }
	void resetRanks() { ranksTab.reset(); }
	void setStatus(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(text);
			ranksTab.setStatus(text);
		});
	}
	void setStatusSuccess(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(text);
			ranksTab.setStatusSuccess(text);
		});
	}
	void addMessage(ClanMessage message)
	{
		SwingUtilities.invokeLater(() ->
		{
			messages.append("[" + (message.getAuthor() == null ? "Clã" : message.getAuthor()) + "] " + message.getMessage() + "\n");
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
				sentMessagesModel.addRow(new Object[]{sentMessage.mode, sentMessage.isPinned() ? "Sim" : "", sentMessage.message});
			}
			sentMessagesStatus.setText(sentMessages.size() + " mensagem(ns)");
		});
	}
	void setSentMessagesStatus(String text)
	{
		SwingUtilities.invokeLater(() -> sentMessagesStatus.setText(text));
	}
	void updateOnlineLives(java.util.List<LiveChannel> channels) { liveOnTab.updateOnline(channels); }
	void updateManagedLives(java.util.List<LiveChannel> channels) { liveOnTab.updateManaged(channels); }
	void setLivesStatus(String text) { liveOnTab.setStatus(text); }
	void clearLiveFields() { liveOnTab.clearFields(); }
	void updateMvpMembers(java.util.List<MvpMember> members) { mvpManagementTab.update(members); }
	void setMvpMembersStatus(String text) { mvpManagementTab.setStatus(text); }
	void clearMvpMemberField() { mvpManagementTab.clearField(); }

	boolean isPinSelected()
	{
		return pinBroadcast.isSelected();
	}

	static final class StaffSentMessage
	{
		String id;
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
