package com.liveon;

import com.google.gson.annotations.SerializedName;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

final class RankRequestsPanel extends JPanel
{
	private final JTable requestsTable;
	private final DefaultTableModel tableModel;
	private final JLabel statusLabel;
	private final JTextArea activityLog = new JTextArea(7, 20);
	private java.util.List<RankRequest> currentRequests = new java.util.ArrayList<>();

	RankRequestsPanel(Runnable refreshAction, java.util.function.Consumer<Integer> deleteAction, java.util.function.Consumer<RankRequest> confirmAction, java.util.function.Consumer<RankRequest> declineAction)
	{
		setLayout(new BorderLayout(5, 5));
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		JPanel header = new JPanel(new BorderLayout(5, 5));
		statusLabel = new JLabel("");
		header.add(statusLabel, BorderLayout.CENTER);
		JButton refresh = new JButton("Atualizar");
		refresh.addActionListener(event -> refreshAction.run());
		header.add(refresh, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		tableModel = new DefaultTableModel(new String[]{"Jogador", "Rank", "Data"}, 0)
		{
			@Override
			public boolean isCellEditable(int row, int column)
			{
				return false;
			}
		};
		requestsTable = new JTable(tableModel);
		requestsTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer()
		{
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
			{
				JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				String rankName = value == null ? "" : value.toString();
				label.setText(rankName);
				label.setIcon(RankVisuals.rankIconFor(rankName));
				return label;
			}
		});
		requestsTable.setPreferredScrollableViewportSize(new Dimension(220, 175));
		JScrollPane scrollPane = new JScrollPane(requestsTable);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		add(scrollPane, BorderLayout.CENTER);

		JPanel actions = new JPanel(new GridLayout(1, 3, 3, 0));
		JButton confirm = new JButton("Aceitar");
		confirm.setMargin(new java.awt.Insets(3, 2, 3, 2));
		confirm.setBackground(new Color(190, 104, 0));
		confirm.setForeground(Color.WHITE);
		confirm.setToolTipText("Aceitar solicitação selecionada");
		confirm.addActionListener(event ->
		{
			int selectedRow = requestsTable.getSelectedRow();
			if (selectedRow >= 0 && selectedRow < currentRequests.size())
			{
				confirmAction.accept(currentRequests.get(selectedRow));
			}
			else
			{
				setStatus("Selecione uma solicitação");
			}
		});
		JButton delete = new JButton("Excluir");
		delete.setMargin(new java.awt.Insets(3, 2, 3, 2));
		delete.setToolTipText("Excluir solicitação selecionada");
		delete.addActionListener(event ->
		{
			int selectedRow = requestsTable.getSelectedRow();
			if (selectedRow >= 0 && selectedRow < currentRequests.size())
			{
				deleteAction.accept(currentRequests.get(selectedRow).id);
			}
			else
			{
				setStatus("Selecione uma solicitação");
			}
		});
		JButton decline = new JButton("Recusar");
		decline.setMargin(new java.awt.Insets(3, 2, 3, 2));
		decline.setToolTipText("Recusar solicitação selecionada");
		decline.addActionListener(event ->
		{
			int selectedRow = requestsTable.getSelectedRow();
			if (selectedRow >= 0 && selectedRow < currentRequests.size())
			{
				declineAction.accept(currentRequests.get(selectedRow));
			}
			else
			{
				setStatus("Selecione uma solicitação");
			}
		});
		actions.add(confirm);
		actions.add(decline);
		actions.add(delete);

		activityLog.setEditable(false);
		activityLog.setLineWrap(true);
		activityLog.setWrapStyleWord(true);
		JScrollPane activityScrollPane = new JScrollPane(activityLog);
		activityScrollPane.setBorder(BorderFactory.createTitledBorder("Atualizações recentes"));
		activityScrollPane.setPreferredSize(new Dimension(220, 105));

		JPanel footer = new JPanel(new BorderLayout(5, 5));
		footer.add(actions, BorderLayout.NORTH);
		footer.add(activityScrollPane, BorderLayout.CENTER);
		add(footer, BorderLayout.SOUTH);
	}

	void update(List<RankRequest> requests)
	{
		SwingUtilities.invokeLater(() ->
		{
			currentRequests = new java.util.ArrayList<>(requests);
			tableModel.setRowCount(0);
			for (RankRequest req : requests)
			{
				tableModel.addRow(new Object[]{req.playerName, req.rankName, formatDate(req.createdAt)});
			}
			// Do not display total count next to refresh button; keep statusLabel for transient messages
statusLabel.setText("");
		});
	}

	void setStatus(String text)
	{
		SwingUtilities.invokeLater(() -> statusLabel.setText(text));
	}

	void updateActivity(List<RankRequestActivity> activities)
	{
		SwingUtilities.invokeLater(() ->
		{
			StringBuilder text = new StringBuilder();
			for (RankRequestActivity activity : activities)
			{
				String decision = "ACCEPTED".equalsIgnoreCase(activity.decision) ? "ACEITO" : "RECUSADO";
				text.append(activity.playerName)
					.append(" / ").append(activity.rankName)
					.append(" — ").append(decision)
					.append(" por ").append(activity.staffName)
					.append('\n');
			}
			activityLog.setText(text.toString());
			activityLog.setCaretPosition(0);
		});
	}

	private static String formatDate(String isoDate)
	{
		try
		{
			if (isoDate == null || isoDate.isEmpty()) return "";
			return isoDate.substring(5, 10); // MM-DD
		}
		catch (Exception e)
		{
			return isoDate;
		}
	}

	static final class RankRequest
	{
		int id;
		@SerializedName("player_name")
		String playerName;
		@SerializedName("rank_name")
		String rankName;
		@SerializedName("created_at")
		String createdAt;

		RankRequest() { }

		RankRequest(int id, String playerName, String rankName, String createdAt)
		{
			this.id = id;
			this.playerName = playerName;
			this.rankName = rankName;
			this.createdAt = createdAt;
		}
	}

	static final class RankRequestActivity
	{
		@SerializedName("player_name")
		String playerName;
		@SerializedName("rank_name")
		String rankName;
		String decision;
		@SerializedName("staff_name")
		String staffName;
		@SerializedName("created_at")
		String createdAt;
	}
}
