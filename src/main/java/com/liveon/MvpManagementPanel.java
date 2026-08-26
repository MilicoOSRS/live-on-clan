package com.liveon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

final class MvpManagementPanel extends JPanel
{
	private final JTextField rsn = new JTextField();
	private final JLabel status = new JLabel(" ");
	private final DefaultTableModel model = new DefaultTableModel(new String[]{"Membro MVP"}, 0)
	{
		@Override public boolean isCellEditable(int row, int column) { return false; }
	};
	private final JTable table = new JTable(model);
	private List<MvpMember> members = new ArrayList<>();

	MvpManagementPanel(Runnable refreshAction, Consumer<String> saveAction, Consumer<MvpMember> deleteAction)
	{
		setLayout(new BorderLayout(5, 5));
		setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));

		JPanel form = new JPanel(new GridLayout(0, 1, 3, 3));
		form.setBorder(BorderFactory.createTitledBorder("Cargo MVP"));
		form.add(new JLabel("Nome do membro"));
		form.add(rsn);
		JButton save = new JButton("Adicionar MVP");
		save.setBackground(new Color(190, 104, 0));
		save.setForeground(Color.WHITE);
		save.addActionListener(event -> saveAction.accept(rsn.getText().trim()));
		form.add(save);
		add(form, BorderLayout.NORTH);

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		add(new JScrollPane(table), BorderLayout.CENTER);

		JButton refresh = new JButton("Atualizar");
		refresh.addActionListener(event -> refreshAction.run());
		JButton remove = new JButton("Remover");
		remove.setToolTipText("Remover membro MVP selecionado");
		remove.addActionListener(event ->
		{
			int row = table.getSelectedRow();
			if (row < 0 || row >= members.size())
			{
				setStatus("Selecione um membro");
				return;
			}
			deleteAction.accept(members.get(row));
		});
		JPanel actions = new JPanel(new GridLayout(1, 2, 3, 0));
		actions.add(refresh);
		actions.add(remove);
		JPanel footer = new JPanel(new BorderLayout(3, 3));
		footer.add(actions, BorderLayout.NORTH);
		footer.add(status, BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
	}

	void update(List<MvpMember> updated)
	{
		SwingUtilities.invokeLater(() ->
		{
			members = updated == null ? new ArrayList<>() : new ArrayList<>(updated);
			model.setRowCount(0);
			for (MvpMember member : members)
			{
				model.addRow(new Object[]{member.playerName});
			}
			status.setText(members.size() + " MVP(s)");
		});
	}

	void setStatus(String text)
	{
		SwingUtilities.invokeLater(() -> status.setText(text));
	}

	void clearField()
	{
		SwingUtilities.invokeLater(() -> rsn.setText(""));
	}
}
