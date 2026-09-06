package com.liveon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;

/** Read-only member board. Only explicit staff edits change progress. */
final class BingoPanel extends JPanel
{
	private final DefaultListModel<JsonObject> openTiles = new DefaultListModel<>();
	private final DefaultListModel<JsonObject> closedTiles = new DefaultListModel<>();
	private EmptyMessageList openTileList;
	private final JLabel openHeading = new JLabel("EM ANDAMENTO · 0");
	private final JLabel closedHeading = new JLabel("CONCLUÍDOS · 0");
	private final JPanel closedBody;
	private boolean closedExpanded;
	private final JLabel score = new JLabel("Pontuação aguardando servidor");
	private final JProgressBar progressBar = new JProgressBar(0, 100);
	private final java.util.Set<Integer> expandedTiles = new java.util.HashSet<>();
	private final JLabel summary = new JLabel("Aguardando cartela");
	private final JLabel status = new JLabel(" ");
	private final JComboBox<String> staffTileSelection = new JComboBox<>();
	private final JLabel selectedTileStatus = new JLabel("Selecione uma cartela");
	private final JButton toggleTileStateButton = new JButton("Concluir tile");
	private final JButton editTileButton = new JButton("Editar tile");
	private final JButton settingsButton = new JButton("Configurar Bingo");
	private JsonObject board;
	private final JPanel administration = new JPanel(new BorderLayout(0, 5));
	private Consumer<JsonObject> write = ignored -> { };
	private Runnable refresh = () -> { };
	private Supplier<Boolean> deputyOwner = () -> false;

	BingoPanel()
	{
		super(new BorderLayout(0, 5));
		setMinimumSize(new Dimension(0, 0));
		administration.setMinimumSize(new Dimension(0, 0));
		administration.setPreferredSize(new Dimension(190, 285));
		status.setMinimumSize(new Dimension(0, 0));
		JPanel heading = new JPanel(new java.awt.GridLayout(0, 1, 0, 3));
		heading.setBorder(BorderFactory.createEmptyBorder(7, 6, 7, 6));
		score.setForeground(new Color(166, 226, 46));
		score.setFont(score.getFont().deriveFont(java.awt.Font.BOLD, 15f));
		progressBar.setForeground(new Color(166, 226, 46));
		heading.add(score); heading.add(summary); heading.add(progressBar);
		add(heading, BorderLayout.NORTH);
		add(section(openHeading, openTiles), BorderLayout.CENTER);
		closedBody = section(null, closedTiles);
		closedBody.setVisible(false);
		closedBody.setPreferredSize(new Dimension(190, 160));
		closedHeading.setForeground(new Color(166, 226, 46));
		closedHeading.setBorder(BorderFactory.createEmptyBorder(5, 5, 4, 0));
		closedHeading.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		closedHeading.setToolTipText("Clique para mostrar ou ocultar os tiles concluídos");
		closedHeading.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mouseClicked(java.awt.event.MouseEvent event) { toggleClosedTiles(); }
		});
		JPanel completed = new JPanel(new BorderLayout(0, 0));
		completed.add(closedHeading, BorderLayout.NORTH);
		completed.add(closedBody, BorderLayout.CENTER);
		add(completed, BorderLayout.SOUTH);
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.add(staffSection("PROGRESSO", staffTileSelection, selectedTileStatus,
			toggleTileStateButton, editTileButton));
		controls.add(Box.createVerticalStrut(6));
		controls.add(staffSection("EVENTO", settingsButton));
		controls.add(Box.createVerticalStrut(6));
		JButton reload = new JButton("Atualizar cartela");
		controls.add(staffSection("SINCRONIZAÇÃO", reload));
		staffTileSelection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		toggleTileStateButton.addActionListener(event -> quickToggleSelectedTile());
		editTileButton.addActionListener(event -> editSelectedTile());
		settingsButton.addActionListener(event -> settings());
		reload.addActionListener(event -> refresh.run());
		staffTileSelection.addActionListener(event -> updateSelectedTileStatus());
		administration.add(controls, BorderLayout.CENTER);
		administration.add(status, BorderLayout.SOUTH);
	}

	void configure(Consumer<JsonObject> write, Runnable refresh, Supplier<Boolean> deputyOwner)
	{
		this.write = write;
		this.refresh = refresh;
		this.deputyOwner = deputyOwner;
		updateStaffControls();
	}
	JPanel administration() { return administration; }
	void refreshPermissions() { updateStaffControls(); }
	void status(String text) { SwingUtilities.invokeLater(() -> { status.setText(text); status.setToolTipText(text); }); }

	private static JPanel staffSection(String title, java.awt.Component... components)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		JLabel heading = new JLabel(title);
		heading.setForeground(new Color(166, 226, 46));
		heading.setAlignmentX(LEFT_ALIGNMENT);
		section.add(heading);
		section.add(Box.createVerticalStrut(3));
		for (java.awt.Component component : components)
		{
			component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component instanceof JLabel ? 20 : 28));
			if (component instanceof JComponent) ((JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
			section.add(component);
		}
		return section;
	}

	private static JTextArea text(String value)
	{
		JTextArea text = new JTextArea(value) {
			@Override public Dimension getPreferredSize() {
				int width = getParent() != null && getParent().getWidth() > 0 ? Math.max(40, getParent().getWidth() - 12) : 180;
				java.awt.Insets insets = getInsets();
				javax.swing.text.View view = getUI().getRootView(this);
				view.setSize(Math.max(1, width - insets.left - insets.right), Float.MAX_VALUE);
				int height = (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS));
				return new Dimension(width, height + insets.top + insets.bottom);
			}
		};
		text.setMinimumSize(new Dimension(0, 0));
		text.setFont(UIManager.getFont("Label.font"));
		text.setEditable(false); text.setOpaque(false); text.setLineWrap(true); text.setWrapStyleWord(true);
		return text;
	}

	private JPanel section(JLabel heading, DefaultListModel<JsonObject> model)
	{
		JPanel section = new JPanel(new BorderLayout(0, 4));
		section.setMinimumSize(new Dimension(0, 75));
		if (heading != null)
		{
			heading.setForeground(new Color(166, 226, 46));
			heading.setBorder(BorderFactory.createEmptyBorder(4, 5, 2, 0));
		}
		EmptyMessageList list = new EmptyMessageList(model);
		if (model == openTiles) openTileList = list;
		list.setFixedCellHeight(-1);
		list.setFixedCellWidth(180);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new TileRenderer());
		list.addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override public void componentResized(java.awt.event.ComponentEvent event) { list.setFixedCellWidth(Math.max(1, list.getWidth())); }
		});
		list.setToolTipText("Clique para expandir ou recolher os itens que faltam");
		list.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mouseClicked(java.awt.event.MouseEvent event) {
				int index = list.locationToIndex(event.getPoint());
				if (index >= 0 && list.getCellBounds(index, index).contains(event.getPoint())) toggleTile(model, index);
			}
		});
		list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "details");
		list.getActionMap().put("details", new AbstractAction() {
			@Override public void actionPerformed(java.awt.event.ActionEvent event) {
				if (list.getSelectedValue() != null) toggleTile(model, list.getSelectedIndex());
			}
		});
		JScrollPane scroll = new JScrollPane(list);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setPreferredSize(new Dimension(190, 160)); scroll.setMinimumSize(new Dimension(0, 0));
		scroll.getVerticalScrollBar().setUnitIncrement(46);
		if (heading != null) section.add(heading, BorderLayout.NORTH);
		section.add(scroll, BorderLayout.CENTER);
		return section;
	}

	private static final class EmptyMessageList extends JList<JsonObject>
	{
		private String emptyText = "";

		private EmptyMessageList(DefaultListModel<JsonObject> model)
		{
			super(model);
		}

		private void setEmptyText(String text)
		{
			emptyText = text == null ? "" : text;
			repaint();
		}

		@Override protected void paintComponent(java.awt.Graphics graphics)
		{
			super.paintComponent(graphics);
			if (getModel().getSize() != 0 || emptyText.isEmpty()) return;
			graphics.setColor(new Color(185, 185, 185));
			java.awt.FontMetrics metrics = graphics.getFontMetrics(getFont());
			int x = Math.max(6, (getWidth() - metrics.stringWidth(emptyText)) / 2);
			int y = Math.max(metrics.getAscent() + 8, getHeight() / 3);
			graphics.drawString(emptyText, x, y);
		}
	}

	private void toggleClosedTiles()
	{
		closedExpanded = !closedExpanded;
		closedBody.setVisible(closedExpanded);
		updateClosedHeading();
		revalidate();
		repaint();
	}

	private void updateClosedHeading()
	{
		closedHeading.setText((closedExpanded ? "▾ " : "▸ ") + "CONCLUÍDOS · " + closedTiles.size());
	}

	private void toggleTile(DefaultListModel<JsonObject> model, int index)
	{
		JsonObject tile = model.get(index);
		int id = tile.get("id").getAsInt();
		if (!expandedTiles.add(id)) expandedTiles.remove(id);
		// Replacing the row invalidates Swing's cached height without resetting scroll.
		model.set(index, tile);
	}

	private final class TileRenderer extends JPanel implements ListCellRenderer<JsonObject>
	{
		private final JLabel title = new JLabel();
		private final JLabel count = new JLabel();
		private final JLabel detail = new JLabel();
		private final JLabel arrow = new JLabel("▸", SwingConstants.CENTER);
		private final JTextArea fullDetail = new JTextArea();
		private TileRenderer()
		{
			super(new BorderLayout(4, 2));
			JPanel row = new JPanel(new BorderLayout(4, 0)); row.setOpaque(false);
			JPanel trailing = new JPanel(new BorderLayout(5, 0)); trailing.setOpaque(false);
			arrow.setPreferredSize(new Dimension(16, 18));
			trailing.add(count, BorderLayout.CENTER); trailing.add(arrow, BorderLayout.EAST);
			row.add(title, BorderLayout.CENTER); row.add(trailing, BorderLayout.EAST);
			fullDetail.setEditable(false); fullDetail.setOpaque(false);
			fullDetail.setLineWrap(true); fullDetail.setWrapStyleWord(true);
			fullDetail.setFont(UIManager.getFont("Label.font"));
			add(row, BorderLayout.NORTH); add(detail, BorderLayout.CENTER);
		}
		@Override public java.awt.Component getListCellRendererComponent(JList<? extends JsonObject> list, JsonObject tile, int index, boolean selected, boolean focus)
		{
			int obtained = tile.get("obtained").getAsInt(), total = tile.get("total").getAsInt();
			boolean done = obtained == total;
			Color accent = done ? new Color(166, 226, 46) : obtained > 0 ? new Color(230, 183, 70) : new Color(145, 145, 145);
			setBackground(selected ? new Color(48, 61, 36) : new Color(35, 35, 35));
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 1, 0, accent), BorderFactory.createEmptyBorder(5, 5, 4, 4)));
			title.setText("#" + tile.get("id").getAsInt() + " · " + tile.get("title").getAsString());
			title.setForeground(accent); count.setText(obtained + "/" + total); count.setForeground(accent);
			detail.setForeground(new Color(195, 195, 195));
			String missing = tile.get("missing").getAsString().replaceAll("\\s+", " ").trim();
			String description = done ? "✓ Concluído" : "Falta: " + (missing.isEmpty() ? "staff atualizando" : tile.get("missing").getAsString());
			boolean expanded = expandedTiles.contains(tile.get("id").getAsInt());
			int width = Math.max(40, (list.getWidth() > 0 ? list.getWidth() : 190) - 12);
			boolean overflow = description.contains("\n") || detail.getFontMetrics(detail.getFont()).stringWidth(description) > width;
			arrow.setText(overflow ? (expanded ? "▾" : "▸") : "");
			arrow.setForeground(accent);
			remove(detail); remove(fullDetail);
			int height = 46;
			if (expanded && overflow) {
				fullDetail.setText(description); fullDetail.setForeground(new Color(205, 205, 205));
				java.awt.Insets padding = fullDetail.getInsets();
				javax.swing.text.View view = fullDetail.getUI().getRootView(fullDetail);
				view.setSize(Math.max(1, width - padding.left - padding.right), Float.MAX_VALUE);
				int textHeight = (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS)) + padding.top + padding.bottom;
				height = Math.max(46, 30 + textHeight);
				add(fullDetail, BorderLayout.CENTER);
			} else {
				detail.setText(done ? "✓ Concluído" : "Falta: " + (missing.isEmpty() ? "staff atualizando" : missing));
				add(detail, BorderLayout.CENTER);
			}
			setPreferredSize(new Dimension(width + 12, height));
			return this;
		}
	}

	void update(JsonObject data)
	{
		if (board != null && data.get("revision").getAsInt() < board.get("revision").getAsInt()) return;
		board = data;
		openTiles.clear(); closedTiles.clear();
		int points = 0; boolean hasPoints = true;
		for (JsonElement element : data.getAsJsonArray("tiles")) {
			JsonObject tile = element.getAsJsonObject();
			boolean done = tile.get("obtained").getAsInt() == tile.get("total").getAsInt();
			(done ? closedTiles : openTiles).addElement(tile);
			if (!tile.has("points")) hasPoints = false;
			else if (done) points += tile.get("points").getAsInt();
		}
		int publishedTiles = openTiles.size() + closedTiles.size();
		int total = data.has("board_size") ? Math.max(publishedTiles, data.get("board_size").getAsInt()) : publishedTiles;
		double percent = total == 0 ? 0 : 100.0 * closedTiles.size() / total;
		String message = data.has("message") ? data.get("message").getAsString().trim() : "";
		if (openTileList != null) openTileList.setEmptyText(message);
		score.setText(hasPoints ? points + " pontos" : "Pontos: atualize o servidor");
		summary.setText(String.format(java.util.Locale.forLanguageTag("pt-BR"),
			"%d/%d concluídos · %.1f%%", closedTiles.size(), total, percent));
		progressBar.setVisible(true);
		progressBar.setValue((int) percent);
		openHeading.setText(publishedTiles == 0 && !message.isEmpty() ? "CARTELA" : "EM ANDAMENTO · " + openTiles.size());
		updateClosedHeading();
		updateStaffControls();
	}

	private void updateStaffControls()
	{
		int selected = staffTileSelection.getSelectedIndex();
		staffTileSelection.removeAllItems();
		if (board != null && board.has("tiles"))
		{
			for (JsonElement element : board.getAsJsonArray("tiles"))
			{
				JsonObject tile = element.getAsJsonObject();
				staffTileSelection.addItem("#" + tile.get("id").getAsInt() + " · "
					+ tile.get("title").getAsString());
			}
		}
		if (staffTileSelection.getItemCount() > 0)
			staffTileSelection.setSelectedIndex(Math.max(0, Math.min(selected, staffTileSelection.getItemCount() - 1)));
		boolean canConfigure = deputyOwner.get();
		settingsButton.setVisible(canConfigure);
		if (settingsButton.getParent() != null) settingsButton.getParent().setVisible(canConfigure);
		updateSelectedTileStatus();
	}

	private void updateSelectedTileStatus()
	{
		JsonObject tile = selectedStaffTile();
		boolean available = tile != null;
		toggleTileStateButton.setEnabled(available);
		editTileButton.setEnabled(available);
		if (!available)
		{
			selectedTileStatus.setText("Cartela indisponível");
			return;
		}
		int obtained = tile.get("obtained").getAsInt();
		int total = tile.get("total").getAsInt();
		boolean done = obtained == total;
		selectedTileStatus.setText((done ? "Concluído" : "Em andamento") + " · " + obtained + "/" + total);
		toggleTileStateButton.setText(done ? "Reativar tile" : "Concluir tile");
	}

	private JsonObject action(String action)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("action", action); payload.add("revision", board.get("revision"));
		return payload;
	}

	private void settings()
	{
		if (!deputyOwner.get()) { status("Somente Deputy Owner pode configurar o evento"); return; }
		if (board == null) { status("Recarregue a cartela primeiro"); return; }
		JsonObject payload = action("settings");
		JCheckBox visible = new JCheckBox("Mostrar aba aos membros", board.get("visible").getAsBoolean());
		JCheckBox sending = new JCheckBox("Enviar drops ao Discord", board.get("sending").getAsBoolean());
		JPanel form = new JPanel(new java.awt.GridLayout(0, 1)); form.add(visible); form.add(sending);
		if (JOptionPane.showConfirmDialog(administration, form, "Bingo", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
		payload.addProperty("visible", visible.isSelected()); payload.addProperty("sending", sending.isSelected()); write.accept(payload);
	}

	static JScrollPane boundedEditor(JPanel form)
	{
		JScrollPane scroll = new JScrollPane(form);
		scroll.setPreferredSize(new Dimension(360, 360));
		scroll.setMinimumSize(new Dimension(240, 200));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private JsonObject selectedStaffTile()
	{
		if (board == null || !board.has("tiles")) return null;
		int index = staffTileSelection.getSelectedIndex();
		JsonArray tiles = board.getAsJsonArray("tiles");
		return index >= 0 && index < tiles.size() ? tiles.get(index).getAsJsonObject().deepCopy() : null;
	}

	private void quickToggleSelectedTile()
	{
		JsonObject tile = selectedStaffTile();
		if (tile == null) { status("Selecione um tile"); return; }
		boolean done = tile.get("obtained").getAsInt() == tile.get("total").getAsInt();
		String action = done ? "reativar" : "concluir";
		if (JOptionPane.showConfirmDialog(administration,
			"Deseja " + action + " o tile #" + tile.get("id").getAsInt() + "?",
			(done ? "Reativar" : "Concluir") + " tile", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
		int total = tile.get("total").getAsInt();
		tile.addProperty("obtained", done ? Math.max(0, total - 1) : total);
		JsonObject payload = action("tile");
		payload.add("tile", tile);
		write.accept(payload);
		status(done ? "Reativando tile..." : "Concluindo tile...");
	}

	private void editSelectedTile()
	{
		JsonObject tile = selectedStaffTile();
		if (tile == null) { status("Selecione um tile"); return; }
		editTile(tile);
	}

	private void editTile(JsonObject tile)
	{
		JsonObject payload = action("tile");
		JTextField title = new JTextField(tile.get("title").getAsString(), 28);
		JTextField points = new JTextField(tile.has("points") ? tile.get("points").getAsString() : "0", 8);
		JTextField progress = new JTextField(tile.get("obtained").getAsInt() + "/" + tile.get("total").getAsInt(), 8);
		JTextArea missing = new JTextArea(tile.get("missing").getAsString(), 5, 28);
		StringBuilder accepted = new StringBuilder(); for (JsonElement item : tile.getAsJsonArray("items")) accepted.append(item.getAsString()).append('\n');
		JTextArea items = new JTextArea(accepted.toString(), 5, 28);
		missing.setLineWrap(true); missing.setWrapStyleWord(true);
		items.setLineWrap(true); items.setWrapStyleWord(true);
		JPanel form = new JPanel(); form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.add(new JLabel("Título")); form.add(title); form.add(new JLabel("Progresso: obtidos/total (ex.: 1/3)")); form.add(progress);
		form.add(new JLabel("Pontos ao concluir")); form.add(points);
		form.add(new JLabel("Itens que faltam (texto livre)")); form.add(new JScrollPane(missing));
		form.add(new JLabel("<html>Itens para Discord<br>Nome exato, um por linha</html>")); form.add(new JScrollPane(items));
		if (JOptionPane.showConfirmDialog(administration, boundedEditor(form), "Editar tile", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
		try {
			String[] values = progress.getText().trim().split("/");
			if (values.length != 2) throw new NumberFormatException();
			tile.addProperty("obtained", Integer.parseInt(values[0].trim())); tile.addProperty("total", Integer.parseInt(values[1].trim()));
			if (tile.has("points")) tile.addProperty("points", Integer.parseInt(points.getText().trim()));
			tile.addProperty("title", title.getText().trim()); tile.addProperty("missing", missing.getText().trim());
			JsonArray list = new JsonArray(); for (String item : items.getText().split("\\R")) if (!item.trim().isEmpty()) list.add(item.trim()); tile.add("items", list);
			payload.add("tile", tile); write.accept(payload);
		} catch (NumberFormatException e) { status("Use progresso no formato 1/3"); }
	}
}
