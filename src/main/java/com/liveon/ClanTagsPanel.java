package com.liveon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

final class ClanTagsPanel extends JPanel
{
	private final JTextField code = new JTextField();
	private final JComboBox<String> color = new JComboBox<>(new String[]{"Dourado", "Vermelho", "Azul", "Verde", "Roxo", "Branco"});
	private final JComboBox<ClanTag> tag = new JComboBox<>();
	private final JTextField rsn = new JTextField();
	private final JButton create = new JButton("Criar etiqueta");
	private final JButton addMember = new JButton("Adicionar membro");
	private final JButton deleteTag = new JButton("Excluir etiqueta");
	private final JButton removeMember = new JButton("Remover membro");
	private final JLabel status = new JLabel(" ");
	private final DefaultTableModel model = new DefaultTableModel(new String[]{"Etiqueta", "Membro"}, 0)
	{
		@Override public boolean isCellEditable(int row, int column) { return false; }
	};
	private final JTable table = new JTable(model);
	private List<Row> rows = new ArrayList<>();

	ClanTagsPanel(Runnable refreshAction, BiConsumer<String, String> createAction,
		BiConsumer<ClanTag, String> addMemberAction, Consumer<ClanTag> deleteTagAction,
		BiConsumer<ClanTag, ClanTagMember> removeMemberAction)
	{
		setLayout(new BorderLayout(5, 5));
		setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));

		JPanel forms = new JPanel(new GridLayout(0, 1, 3, 3));
		JPanel createForm = new JPanel(new GridLayout(0, 1, 3, 3));
		createForm.setBorder(BorderFactory.createTitledBorder("Criar etiqueta"));
		createForm.add(new JLabel("Sigla (máximo 5 caracteres)"));
		createForm.add(code);
		createForm.add(new JLabel("Cor"));
		createForm.add(color);
		create.setBackground(new Color(190, 104, 0));
		create.setForeground(Color.WHITE);
		create.addActionListener(event -> createAction.accept(code.getText().trim(), selectedColor()));
		createForm.add(create);

		JPanel memberForm = new JPanel(new GridLayout(0, 1, 3, 3));
		memberForm.setBorder(BorderFactory.createTitledBorder("Adicionar membro"));
		memberForm.add(new JLabel("Etiqueta"));
		memberForm.add(tag);
		memberForm.add(new JLabel("Nome do membro"));
		memberForm.add(rsn);
		addMember.setBackground(new Color(190, 104, 0));
		addMember.setForeground(Color.WHITE);
		addMember.addActionListener(event ->
		{
			ClanTag selected = (ClanTag) tag.getSelectedItem();
			if (selected == null) setStatus("Crie ou selecione uma etiqueta");
			else addMemberAction.accept(selected, rsn.getText().trim());
		});
		memberForm.add(addMember);
		forms.add(createForm);
		forms.add(memberForm);
		add(forms, BorderLayout.NORTH);

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		add(new JScrollPane(table), BorderLayout.CENTER);

		JButton refresh = new JButton("Atualizar");
		refresh.addActionListener(event -> refreshAction.run());
		deleteTag.addActionListener(event ->
		{
			ClanTag selected = (ClanTag) tag.getSelectedItem();
			if (selected == null) setStatus("Selecione uma etiqueta");
			else deleteTagAction.accept(selected);
		});
		deleteTag.setToolTipText("Excluir a etiqueta selecionada e suas associações");
		removeMember.addActionListener(event ->
		{
			int selectedRow = table.getSelectedRow();
			if (selectedRow < 0 || selectedRow >= rows.size()) setStatus("Selecione um membro");
			else removeMemberAction.accept(rows.get(selectedRow).tag, rows.get(selectedRow).member);
		});
		removeMember.setToolTipText("Remover o membro selecionado da etiqueta");
		JPanel actions = new JPanel(new GridLayout(2, 2, 3, 3));
		actions.add(refresh);
		actions.add(removeMember);
		actions.add(deleteTag);
		JPanel actionFiller = new JPanel();
		actionFiller.setOpaque(false);
		actions.add(actionFiller);
		JPanel footer = new JPanel(new BorderLayout(3, 3));
		footer.add(actions, BorderLayout.NORTH);
		footer.add(status, BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
	}

	void update(ClanTagsResponse response)
	{
		SwingUtilities.invokeLater(() ->
		{
			List<ClanTag> tags = response == null || response.tags == null ? new ArrayList<>() : response.tags;
			tag.removeAllItems();
			model.setRowCount(0);
			rows = new ArrayList<>();
			for (ClanTag clanTag : tags)
			{
				tag.addItem(clanTag);
				if (clanTag.members == null || clanTag.members.isEmpty())
				{
					model.addRow(new Object[]{clanTag.code, "—"});
					continue;
				}
				for (ClanTagMember member : clanTag.members)
				{
					rows.add(new Row(clanTag, member));
					model.addRow(new Object[]{clanTag.code, member.playerName});
				}
			}
			boolean canManage = response != null && response.canManage;
			create.setEnabled(canManage);
			addMember.setEnabled(canManage);
			deleteTag.setEnabled(canManage);
			removeMember.setEnabled(canManage);
			status.setText(canManage ? tags.size() + " etiqueta(s)" : "Somente Owner e Deputy Owner podem alterar");
		});
	}

	void setStatus(String text) { SwingUtilities.invokeLater(() -> status.setText(text)); }
	void clearCode() { SwingUtilities.invokeLater(() -> code.setText("")); }
	void clearMember() { SwingUtilities.invokeLater(() -> rsn.setText("")); }

	private String selectedColor()
	{
		String[] values = {"gold", "red", "blue", "green", "purple", "white"};
		int selected = color.getSelectedIndex();
		return selected < 0 || selected >= values.length ? "gold" : values[selected];
	}

	private static final class Row
	{
		private final ClanTag tag;
		private final ClanTagMember member;
		private Row(ClanTag tag, ClanTagMember member) { this.tag = tag; this.member = member; }
	}
}
