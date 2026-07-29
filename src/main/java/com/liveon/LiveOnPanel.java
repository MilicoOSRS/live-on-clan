package com.liveon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.util.LinkBrowser;

final class LiveOnPanel extends JPanel
{
	private final JPanel onlineChannels = new JPanel();
	private final JPanel onlineSection = new JPanel(new BorderLayout(5, 5));
	private final JPanel content = new JPanel(new BorderLayout());
	private final JPanel staffManagement = new JPanel(new BorderLayout(5, 5));
	private final JSplitPane staffSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
	private final JTextField rsn = new JTextField();
	private final JTextField twitch = new JTextField();
	private final JLabel status = new JLabel(" ");
	private final DefaultTableModel model = new DefaultTableModel(new String[]{"RSN", "Twitch", "Status"}, 0)
	{
		@Override public boolean isCellEditable(int row, int column) { return false; }
	};
	private final JTable table = new JTable(model);
	private List<LiveChannel> managedChannels = new ArrayList<>();

	LiveOnPanel(Runnable refreshAction, BiConsumer<String, String> saveAction, Consumer<LiveChannel> deleteAction)
	{
		setLayout(new BorderLayout(6, 8));
		setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
		JLabel title = new JLabel("LIVES ONLINE");
		title.setHorizontalAlignment(JLabel.CENTER);
		onlineSection.add(title, BorderLayout.NORTH);
		onlineChannels.setLayout(new BoxLayout(onlineChannels, BoxLayout.Y_AXIS));
		JScrollPane onlineScrollPane = new JScrollPane(onlineChannels);
		onlineScrollPane.setBorder(null);
		onlineSection.add(onlineScrollPane, BorderLayout.CENTER);

		JPanel fields = new JPanel(new GridLayout(0, 1, 3, 3));
		fields.setBorder(BorderFactory.createTitledBorder("Gerenciar canais"));
		fields.add(new JLabel("RSN"));
		fields.add(rsn);
		fields.add(new JLabel("Canal da Twitch"));
		fields.add(twitch);
		JButton save = new JButton("Associar / Atualizar");
		save.addActionListener(event -> saveAction.accept(rsn.getText().trim(), twitch.getText().trim()));
		fields.add(save);
		staffManagement.add(fields, BorderLayout.NORTH);

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		staffManagement.add(new JScrollPane(table), BorderLayout.CENTER);
		JButton refresh = new JButton("Atualizar");
		refresh.addActionListener(event -> refreshAction.run());
		JButton remove = new JButton("Remover selecionado");
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
		JPanel actions = new JPanel(new GridLayout(2, 1, 3, 3));
		actions.add(refresh);
		actions.add(remove);
		JPanel footer = new JPanel(new BorderLayout(3, 3));
		footer.add(actions, BorderLayout.NORTH);
		footer.add(status, BorderLayout.SOUTH);
		staffManagement.add(footer, BorderLayout.SOUTH);
		staffSplit.setTopComponent(onlineSection);
		staffSplit.setBottomComponent(staffManagement);
		staffSplit.setResizeWeight(0.34);
		staffSplit.setDividerSize(6);
		staffSplit.setBorder(null);
		content.add(staffSplit, BorderLayout.CENTER);
		add(content, BorderLayout.CENTER);
		setStaff(false);
		updateOnline(Collections.emptyList());
	}

	void setStaff(boolean staff)
	{
		SwingUtilities.invokeLater(() ->
		{
			content.removeAll();
			if (staff)
			{
				staffSplit.setTopComponent(onlineSection);
				staffSplit.setBottomComponent(staffManagement);
				content.add(staffSplit, BorderLayout.CENTER);
				staffSplit.setDividerLocation(0.34);
			}
			else
			{
				content.add(onlineSection, BorderLayout.CENTER);
			}
			content.revalidate();
			content.repaint();
		});
	}

	void updateOnline(List<LiveChannel> channels)
	{
		SwingUtilities.invokeLater(() ->
		{
			onlineChannels.removeAll();
			if (channels == null || channels.isEmpty())
			{
				JLabel empty = new JLabel("Nenhuma live ativa no momento.");
				empty.setHorizontalAlignment(JLabel.CENTER);
				empty.setForeground(new Color(155, 155, 155));
				empty.setBorder(BorderFactory.createEmptyBorder(18, 4, 18, 4));
				onlineChannels.add(empty);
			}
			else
			{
				for (LiveChannel channel : channels)
				{
					onlineChannels.add(createLiveCard(channel));
				}
			}
			onlineChannels.revalidate();
			onlineChannels.repaint();
		});
	}

	private static JPanel createLiveCard(LiveChannel channel)
	{
		JPanel card = new JPanel(new BorderLayout(5, 5));
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setPreferredSize(new Dimension(210, 82));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(40, 200, 80)),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		JLabel name = new JLabel("●  " + channel.playerName);
		name.setForeground(new Color(70, 220, 100));
		card.add(name, BorderLayout.NORTH);
		card.add(new JLabel(channel.url), BorderLayout.CENTER);
		JButton open = new JButton("Abrir live");
		open.addActionListener(event -> LinkBrowser.browse(channel.url));
		card.add(open, BorderLayout.SOUTH);
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
