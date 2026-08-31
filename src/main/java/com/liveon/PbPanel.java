package com.liveon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JToggleButton;

final class PbPanel extends JPanel
{
	private static final Color ORANGE = new Color(255, 152, 0);
	private static final Color BLUE = new Color(90, 190, 245);
	private final javax.swing.JTextField globalSearch = new javax.swing.JTextField("Pesquisar");
	private final JButton clearSearch = new JButton("×");
	private final javax.swing.JPopupMenu searchSuggestions = new javax.swing.JPopupMenu();
	private final JComboBox<String> raids = new JComboBox<>();
	private final JComboBox<String> bosses = new JComboBox<>();
	private final JComboBox<String> modes = new JComboBox<>();
	private final JComboBox<String> teams = new JComboBox<>();
	private final JComboBox<String> timeTypes = new JComboBox<>();
	private final JPanel filters = new JPanel(new GridLayout(1, 2, 5, 0));
	private final JPanel filterStack = new JPanel();
	private final JPanel raidsGroup = new JPanel();
	private final JPanel bossesGroup = new JPanel();
	private final List<PbCategory> availableCategories = new ArrayList<>();
	private final JPanel ranking = new VerticalRankingPanel();
	private final JScrollPane rankingScroll = new JScrollPane(ranking);
	private final JLabel ownPb = new JLabel("Nenhum PB sincronizado", SwingConstants.CENTER);
	private final JPanel tutorial = new JPanel();
	private final JButton tutorialToggle = new JButton("▾ Como registrar seus PBs?");
	private final JPanel participationNotice = new JPanel(new BorderLayout());
	private final JButton refresh = new JButton("↻");
	private final Consumer<PbCategory> selectionAction;
	private boolean updatingFilters;
	private boolean updatingGlobalSearch;
	private volatile long latestRankingRequestGeneration;

	PbPanel(Runnable refreshAction, Consumer<PbCategory> selectionAction)
	{
		this.selectionAction = selectionAction;
		setLayout(new BorderLayout(0, 7));
		setBorder(BorderFactory.createEmptyBorder(7, 5, 5, 5));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		configureTutorial();
		top.add(tutorialToggle);
		top.add(tutorial);
		top.add(Box.createVerticalStrut(7));
		configureParticipationNotice();
		top.add(participationNotice);
		top.add(Box.createVerticalStrut(7));
		JLabel title = new JLabel("MELHORES TEMPOS DO CLAN");
		title.setForeground(ORANGE);
		JPanel titleRow = new JPanel(new BorderLayout(4, 0));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		titleRow.add(title, BorderLayout.CENTER);
		refresh.setToolTipText("Atualizar classificação");
		refresh.setMargin(new java.awt.Insets(1, 7, 1, 7));
		refresh.setPreferredSize(new Dimension(32, 26));
		titleRow.add(refresh, BorderLayout.EAST);
		top.add(titleRow);
		top.add(Box.createVerticalStrut(6));
		refresh.addActionListener(event ->
		{
			refreshAction.run();
		});

		globalSearch.setToolTipText("Pesquise em bosses, desafios e raids");
		configureSearchPrompt(globalSearch, "Pesquisar");
		configureGlobalAutocomplete();
		clearSearch.setToolTipText("Limpar pesquisa");
		clearSearch.setMargin(new java.awt.Insets(1, 7, 1, 7));
		clearSearch.setPreferredSize(new Dimension(30, 28));
		clearSearch.setVisible(false);
		clearSearch.addActionListener(event -> clearGlobalSearch());
		JPanel searchRow = new JPanel(new BorderLayout(3, 0));
		searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		searchRow.add(globalSearch, BorderLayout.CENTER);
		searchRow.add(clearSearch, BorderLayout.EAST);
		top.add(searchRow);
		top.add(Box.createVerticalStrut(6));
		JLabel selectionLabel = new JLabel("Ou selecione nos menus abaixo");
		selectionLabel.setForeground(new Color(145, 145, 145));
		selectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(selectionLabel);
		top.add(Box.createVerticalStrut(3));
		raids.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		raids.setAlignmentX(Component.LEFT_ALIGNMENT);
		raids.setToolTipText("Selecione uma raid");
		configureComboPlaceholder(raids, "Raids");
		raids.addActionListener(event ->
		{
			if (updatingFilters || raids.getSelectedItem() == null) return;
			updatingFilters = true;
			bosses.setSelectedItem(null);
			updatingFilters = false;
			clearSearchAfterMenuSelection();
			positionFilters(true);
			rebuildCategoryFilters();
		});
		raidsGroup.setLayout(new BoxLayout(raidsGroup, BoxLayout.Y_AXIS));
		raidsGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
		raidsGroup.add(raids);
		top.add(raidsGroup);
		top.add(Box.createVerticalStrut(6));
		javax.swing.JSeparator categorySeparator = new javax.swing.JSeparator();
		categorySeparator.setForeground(new Color(62, 62, 62));
		categorySeparator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		categorySeparator.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(categorySeparator);
		top.add(Box.createVerticalStrut(6));

		bosses.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		bosses.setAlignmentX(Component.LEFT_ALIGNMENT);
		bosses.setToolTipText("Selecione um boss ou desafio");
		configureComboPlaceholder(bosses, "Bosses e desafios");
		bosses.addActionListener(event -> {
			if (updatingFilters || bosses.getSelectedItem() == null) return;
			updatingFilters = true;
			raids.setSelectedItem(null);
			updatingFilters = false;
			clearSearchAfterMenuSelection();
			positionFilters(false);
			rebuildCategoryFilters();
		});
		modes.addActionListener(event -> { if (!updatingFilters) { rebuildTeamFilter(); fireSelection(); } });
		teams.addActionListener(event ->
		{
			if (updatingFilters) return;
			String boss = selectedActivity();
			String mode = modes.isVisible() ? selectedText(modes) : singleMode(boss);
			rebuildTimeTypeFilter(boss, mode, parseTeamLabel(selectedText(teams)));
			fireSelection();
		});
		bossesGroup.setLayout(new BoxLayout(bossesGroup, BoxLayout.Y_AXIS));
		bossesGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
		bossesGroup.add(bosses);
		top.add(bossesGroup);
		filterStack.setLayout(new BoxLayout(filterStack, BoxLayout.Y_AXIS));
		filterStack.setAlignmentX(Component.LEFT_ALIGNMENT);
		filterStack.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
		filterStack.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
		filters.setAlignmentX(Component.LEFT_ALIGNMENT);
		filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		filters.add(modes);
		filters.add(teams);
		filters.setVisible(false);
		timeTypes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		timeTypes.setAlignmentX(Component.LEFT_ALIGNMENT);
		timeTypes.setVisible(false);
		timeTypes.addActionListener(event -> { if (!updatingFilters) fireSelection(); });
		filterStack.add(filters);
		filterStack.add(Box.createVerticalStrut(5));
		filterStack.add(timeTypes);
		filterStack.setVisible(false);
		bossesGroup.add(filterStack);
		ownPb.setForeground(BLUE);
		ownPb.setAlignmentX(Component.LEFT_ALIGNMENT);
		ownPb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
		ownPb.setBorder(BorderFactory.createEmptyBorder(5, 2, 4, 2));
		top.add(ownPb);
		tutorialToggle.addActionListener(event -> {
			tutorial.setVisible(!tutorial.isVisible());
			tutorialToggle.setText((tutorial.isVisible() ? "▾ " : "▸ ") + "Como registrar seus PBs?");
			revalidate();
		});
		add(top, BorderLayout.NORTH);

		ranking.setLayout(new BoxLayout(ranking, BoxLayout.Y_AXIS));
		rankingScroll.setBorder(BorderFactory.createEmptyBorder());
		rankingScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		add(rankingScroll, BorderLayout.CENTER);

	}

	private void configureParticipationNotice()
	{
		participationNotice.setAlignmentX(Component.LEFT_ALIGNMENT);
		participationNotice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
		participationNotice.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BLUE),
			BorderFactory.createEmptyBorder(5, 7, 5, 7)));
		JLabel message = new JLabel("<html><b>Participação desativada</b><br>"
			+ "Ative nas configurações<br>para registrar seus PBs.</html>");
		message.setForeground(BLUE);
		participationNotice.add(message, BorderLayout.CENTER);
	}

	void setParticipationEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() ->
		{
			participationNotice.setVisible(!enabled);
			participationNotice.getParent().revalidate();
			participationNotice.getParent().repaint();
		});
	}

	private static void configureFittedButton(javax.swing.AbstractButton button, String text)
	{
		button.setText(text);
		button.setToolTipText(text);
		button.addComponentListener(new java.awt.event.ComponentAdapter()
		{
			@Override
			public void componentResized(java.awt.event.ComponentEvent event)
			{
				fitButtonText(button, text);
			}
		});
	}

	private static void configureSearchPrompt(javax.swing.JTextField field, String prompt)
	{
		Color normalColor = field.getForeground();
		Color promptColor = new Color(145, 145, 145);
		field.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusGained(java.awt.event.FocusEvent event)
			{
				if (prompt.equals(field.getText()))
				{
					field.setText("");
					field.setForeground(normalColor);
				}
			}

			@Override
			public void focusLost(java.awt.event.FocusEvent event)
			{
				if (field.getText().trim().isEmpty())
				{
					field.setText(prompt);
					field.setForeground(promptColor);
				}
			}
		});
		field.setForeground(promptColor);
		field.setText(prompt);
	}

	private static void configureComboPlaceholder(JComboBox<String> combo, String prompt)
	{
		combo.setRenderer((list, value, index, selected, focused) ->
		{
			javax.swing.JLabel label = (javax.swing.JLabel) new javax.swing.DefaultListCellRenderer()
				.getListCellRendererComponent(list, value, index, selected, focused);
			if (value == null)
			{
				label.setText(prompt);
				label.setForeground(new Color(155, 155, 155));
			}
			return label;
		});
	}

	private void configureGlobalAutocomplete()
	{
		searchSuggestions.setFocusable(false);
		globalSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			private void changed()
			{
				if (updatingGlobalSearch) return;
				String query = globalSearch.getText();
				if ("Pesquisar".equals(query)) return;
				SwingUtilities.invokeLater(() -> filterGlobalSuggestions(query));
			}
			@Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
			@Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
			@Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
		});
		globalSearch.addActionListener(event -> selectGlobalSearchResult(globalSearch.getText()));
	}

	private void filterGlobalSuggestions(String query)
	{
		if (updatingGlobalSearch) return;
		String normalized = normalizeSearch(query);
		searchSuggestions.setVisible(false);
		searchSuggestions.removeAll();
		clearSearch.setVisible(!normalized.isEmpty());
		if (normalized.isEmpty()) return;
		String alias = bossAlias(normalized);
		java.util.Set<String> suggestions = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (PbCategory category : availableCategories)
		{
			String candidate = safe(category.boss);
			String normalizedCandidate = normalizeSearch(candidate);
			if (normalized.isEmpty() || normalizedCandidate.contains(normalized)
				|| normalizedCandidate.contains(alias)) suggestions.add(candidate);
			if (suggestions.size() >= 5) break;
		}
		for (String suggestion : suggestions)
		{
			javax.swing.JMenuItem item = new javax.swing.JMenuItem(suggestion);
			item.setFocusable(false);
			item.setRequestFocusEnabled(false);
			item.addActionListener(event -> selectGlobalSearchResult(suggestion));
			searchSuggestions.add(item);
		}
		if (!suggestions.isEmpty() && globalSearch.isShowing())
		{
			searchSuggestions.show(globalSearch, 0, globalSearch.getHeight());
			globalSearch.requestFocusInWindow();
		}
	}

	private void selectGlobalSearchResult(String query)
	{
		if (updatingGlobalSearch) return;
		String resolved = resolveBossName(query);
		if (resolved.isEmpty() || !containsBoss(resolved)) return;
		updatingGlobalSearch = true;
		globalSearch.setForeground(javax.swing.UIManager.getColor("TextField.foreground"));
		globalSearch.setText(resolved);
		updatingGlobalSearch = false;
		clearSearch.setVisible(true);
		searchSuggestions.setVisible(false);
		updatingFilters = true;
		if (isRaid(resolved))
		{
			raids.setSelectedItem(resolved);
			bosses.setSelectedItem(null);
		}
		else
		{
			bosses.setSelectedItem(resolved);
			raids.setSelectedItem(null);
		}
		updatingFilters = false;
		positionFilters(isRaid(resolved));
		rebuildCategoryFilters();
	}

	private void positionFilters(boolean belowRaids)
	{
		java.awt.Container parent = filterStack.getParent();
		if (parent != null) parent.remove(filterStack);
		JPanel target = belowRaids ? raidsGroup : bossesGroup;
		target.add(filterStack);
		target.revalidate();
		target.repaint();
	}

	private void clearGlobalSearch()
	{
		updatingGlobalSearch = true;
		globalSearch.setText("");
		globalSearch.requestFocusInWindow();
		updatingGlobalSearch = false;
		clearSearch.setVisible(false);
		searchSuggestions.setVisible(false);
	}

	private void clearSearchAfterMenuSelection()
	{
		updatingGlobalSearch = true;
		if (globalSearch.hasFocus())
		{
			globalSearch.setText("");
			globalSearch.setForeground(javax.swing.UIManager.getColor("TextField.foreground"));
		}
		else
		{
			globalSearch.setText("Pesquisar");
			globalSearch.setForeground(new Color(145, 145, 145));
		}
		updatingGlobalSearch = false;
		clearSearch.setVisible(false);
		searchSuggestions.setVisible(false);
	}

	private boolean containsBoss(String boss)
	{
		for (PbCategory category : availableCategories)
			if (safe(category.boss).equalsIgnoreCase(boss)) return true;
		return false;
	}

	private static void fitButtonText(javax.swing.AbstractButton button, String text)
	{
		Font base = javax.swing.UIManager.getFont("Button.font");
		if (base == null) base = button.getFont();
		int available = Math.max(0, button.getWidth() - button.getInsets().left
			- button.getInsets().right - 6);
		float size = base.getSize2D();
		Font fitted = base;
		while (size > 9f)
		{
			fitted = base.deriveFont(size);
			FontMetrics metrics = button.getFontMetrics(fitted);
			if (metrics.stringWidth(text) <= available) break;
			size -= 0.5f;
		}
		button.setFont(fitted);
	}

	private void configureTutorial()
	{
		tutorial.setLayout(new BoxLayout(tutorial, BoxLayout.Y_AXIS));
		tutorial.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, BLUE),
			BorderFactory.createEmptyBorder(6, 8, 6, 5)));
		JLabel instructions = new JLabel("<html><div style='width:160px'>"
			+ "1. Abra o <b>Adventure Log</b> da sua POH para importar todos os seus tempos.<br><br>"
			+ "2. Nos <b>Combat Achievements</b>, abra a página do boss que quiser registrar.<br><br>"
			+ "3. Scoreboards também são reconhecidos.<br><br>"
			+ "Com <b>Participar do ranking de PBs</b> ativado, seus novos PBs serão registrados automaticamente."
			+ "</div></html>");
		instructions.setAlignmentX(Component.LEFT_ALIGNMENT);
		instructions.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		tutorial.add(instructions);
		tutorial.setVisible(true);
		tutorialToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		tutorialToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
	}

	void updateCategories(List<PbCategory> values)
	{
		SwingUtilities.invokeLater(() -> {
			availableCategories.clear();
			if (values != null) availableCategories.addAll(values);
			filterGlobalSuggestions("");
			rebuildBossLists();
		});
	}

	void setRefreshEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() -> refresh.setEnabled(enabled));
	}

	private void rebuildBossLists()
	{
		String previousBoss = selectedText(bosses);
		String previousRaid = selectedText(raids);
		java.util.Set<String> bossNames = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		java.util.Set<String> raidNames = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (PbCategory value : availableCategories)
		{
			if (value.boss == null) continue;
			if (isRaid(value.boss)) raidNames.add(value.boss);
			else bossNames.add(value.boss);
		}
		updatingFilters = true;
		bosses.setModel(new DefaultComboBoxModel<>(bossNames.toArray(new String[0])));
		raids.setModel(new DefaultComboBoxModel<>(raidNames.toArray(new String[0])));
		bosses.setSelectedItem(bossNames.contains(previousBoss) ? previousBoss : null);
		raids.setSelectedItem(raidNames.contains(previousRaid) ? previousRaid : null);
		updatingFilters = false;
		if (bossNames.isEmpty() && raidNames.isEmpty())
		{
			ranking.removeAll();
			ranking.add(centered("Nenhum PB sincronizado nesta categoria."));
			setTutorialExpanded(true);
			ownPb.setText("<html><div style='text-align:center'>Abra o Adventure Log<br>para importar seus PBs</div></html>");
			ranking.revalidate();
			ranking.repaint();
		}
		rebuildCategoryFilters();
	}

	private static boolean isRaid(String boss)
	{
		String normalized = safe(boss).toLowerCase(java.util.Locale.ROOT);
		return normalized.contains("chambers of xeric")
			|| normalized.contains("theatre of blood")
			|| normalized.contains("tombs of amascut");
	}

	PbCategory selectedCategory()
	{
		String boss = selectedActivity();
		String mode = modes.isVisible() ? selectedText(modes) : singleMode(boss);
		int teamSize = teams.isVisible() ? parseTeamLabel(selectedText(teams)) : singleTeam(boss, mode);
		String timeType = timeTypes.isVisible() ? selectedTimeType() : singleTimeType(boss, mode, teamSize);
		for (PbCategory value : availableCategories)
		{
			if (safe(value.boss).equalsIgnoreCase(boss) && safe(value.mode).equalsIgnoreCase(mode)
				&& value.team_size == teamSize && safe(value.time_type).equalsIgnoreCase(timeType)) return value;
		}
		return null;
	}

	private void rebuildCategoryFilters()
	{
		String boss = selectedActivity();
		java.util.Set<String> values = new java.util.LinkedHashSet<>();
		for (PbCategory category : availableCategories)
			if (safe(category.boss).equalsIgnoreCase(boss)) values.add(safe(category.mode));
		updatingFilters = true;
		modes.setModel(new DefaultComboBoxModel<>(values.toArray(new String[0])));
		modes.setVisible(values.size() > 1 || (values.size() == 1 && !values.iterator().next().isEmpty()));
		updatingFilters = false;
		rebuildTeamFilter();
		filterStack.setVisible(filters.isVisible() || timeTypes.isVisible());
		fireSelection();
		revalidate();
	}

	private void rebuildTeamFilter()
	{
		String boss = selectedActivity();
		String mode = modes.isVisible() ? selectedText(modes) : singleMode(boss);
		java.util.Set<Integer> sizes = new java.util.TreeSet<>();
		for (PbCategory category : availableCategories)
			if (safe(category.boss).equalsIgnoreCase(boss) && safe(category.mode).equalsIgnoreCase(mode)) sizes.add(category.team_size);
		List<String> labels = new ArrayList<>();
		for (Integer size : sizes) if (size > 0) labels.add(size == 1 ? "Solo" : size + " jogadores");
		updatingFilters = true;
		teams.setModel(new DefaultComboBoxModel<>(labels.toArray(new String[0])));
		teams.setVisible(labels.size() > 1);
		updatingFilters = false;
		filters.setVisible(modes.isVisible() || teams.isVisible());
		rebuildTimeTypeFilter(boss, mode, teams.isVisible() ? parseTeamLabel(selectedText(teams)) : singleTeam(boss, mode));
		filterStack.setVisible(filters.isVisible() || timeTypes.isVisible());
	}

	private void rebuildTimeTypeFilter(String boss, String mode, int teamSize)
	{
		java.util.Set<String> types = new java.util.LinkedHashSet<>();
		for (PbCategory category : availableCategories)
			if (safe(category.boss).equalsIgnoreCase(boss) && safe(category.mode).equalsIgnoreCase(mode)
				&& category.team_size == teamSize && !safe(category.time_type).isEmpty())
				types.add(category.time_type);
		List<String> labels = new ArrayList<>();
		if (types.contains("ROOM")) labels.add("Room time");
		if (types.contains("OVERALL")) labels.add("Overall time");
		updatingFilters = true;
		timeTypes.setModel(new DefaultComboBoxModel<>(labels.toArray(new String[0])));
		timeTypes.setVisible("Theatre of Blood".equalsIgnoreCase(boss) && labels.size() > 1);
		updatingFilters = false;
	}

	private String selectedTimeType()
	{
		return "Overall time".equals(selectedText(timeTypes)) ? "OVERALL" : "ROOM";
	}

	private void fireSelection()
	{
		PbCategory selected = selectedCategory();
		if (selected != null) selectionAction.accept(selected);
	}

	private String selectedActivity()
	{
		String raid = selectedText(raids);
		return raid.isEmpty() ? selectedText(bosses) : raid;
	}

	private String resolveBossName(String query)
	{
		String normalizedQuery = normalizeSearch(query);
		if (normalizedQuery.isEmpty()) return "";
		String alias = bossAlias(normalizedQuery);
		for (PbCategory category : availableCategories)
		{
			String candidate = safe(category.boss);
			String normalizedCandidate = normalizeSearch(candidate);
			if (normalizedCandidate.equals(normalizedQuery)
				|| normalizedCandidate.equals(alias)
				|| normalizedCandidate.contains(alias)
				|| normalizedCandidate.contains(normalizedQuery)) return candidate;
		}
		return query;
	}

	private static String bossAlias(String query)
	{
			switch (query)
		{
			case "dusk": case "dawn": case "gargs": case "ggs": case "gg": return "grotesque guardians";
			case "jad": case "tztok jad": return "tzhaar fight cave";
			case "zuk": case "tzkal zuk": return "inferno";
			case "cox": case "xeric": case "chambers": case "olm": case "raids": return "chambers of xeric";
			case "tob": case "theatre": case "verzik": case "verzik vitur": case "raids 2": return "theatre of blood";
			case "toa": case "tombs": case "amascut": case "warden": case "wardens": case "raids 3": return "tombs of amascut";
			case "cg": case "cgaunt": case "cgauntlet": case "the corrupted gauntlet": return "corrupted gauntlet";
			case "gaunt": case "gauntlet": case "the gauntlet": return "gauntlet";
			case "sire": return "abyssal sire";
			case "cerb": return "cerberus";
			case "thermy": case "smoke devil": return "thermonuclear smoke devil";
			case "hydra": return "alchemical hydra";
			case "amox": return "amoxliatl";
			case "huey": case "the hueycoatl": return "hueycoatl";
			case "deranged arch": return "deranged archaeologist";
			case "crazy arch": return "crazy archaeologist";
			case "chaos ele": return "chaos elemental";
			case "vetion": return "vet ion";
			case "calv": case "calvarion": return "calvar ion";
			case "vene": return "venenatis";
			case "kbd": return "king black dragon";
			case "corp": return "corporeal beast";
			case "kq": return "kalphite queen";
			case "vork": return "vorkath";
			case "mole": return "giant mole";
			case "phantom": case "muspah": case "pm": return "phantom muspah";
			case "nm": case "tnm": case "nmare": case "the nightmare": return "nightmare";
			case "pnm": case "phosani": case "phosanis": case "phosani nm": return "phosani s nightmare";
			case "sara": case "saradomin": case "zily": case "zilyana": return "commander zilyana";
			case "zammy": case "zamorak": case "kril": return "k ril tsutsaroth";
			case "arma": case "kree": case "kreearra": case "armadyl": return "kree arra";
			case "bandos": case "bando": case "graardor": return "general graardor";
			case "supreme": return "dagannoth supreme";
			case "rex": return "dagannoth rex";
			case "prime": return "dagannoth prime";
			case "duke": case "duke awakened": case "duke sucellus awakened": return "duke sucellus";
			case "levi": case "the leviathan": case "levi awakened": case "leviathan awakened": case "the leviathan awakened": return "the leviathan";
			case "vard": case "vard awakened": case "vardorvis awakened": return "vardorvis";
			case "wisp": case "whisp": case "the whisperer": case "wisp awakened": case "whisp awakened": case "whisperer awakened": return "the whisperer";
			case "sol": case "colo": case "colosseum": case "fortis colosseum": return "sol heredit";
			case "barrows": return "barrows chests";
			case "lunar chests": case "moons of peril": case "perilous moon": case "perilous moons": return "lunar chest";
			default: return query;
		}
	}

	private static String normalizeSearch(String value)
	{
		return safe(value).trim().toLowerCase(java.util.Locale.ROOT)
			.replaceAll("[^a-z0-9]+", " ").trim();
	}

	private String singleMode(String boss)
	{
		for (PbCategory value : availableCategories)
			if (safe(value.boss).equalsIgnoreCase(boss)) return safe(value.mode);
		return "";
	}

	private int singleTeam(String boss, String mode)
	{
		for (PbCategory value : availableCategories)
			if (safe(value.boss).equalsIgnoreCase(boss) && safe(value.mode).equalsIgnoreCase(mode)) return value.team_size;
		return 0;
	}

	private String singleTimeType(String boss, String mode, int teamSize)
	{
		for (PbCategory value : availableCategories)
			if (safe(value.boss).equalsIgnoreCase(boss) && safe(value.mode).equalsIgnoreCase(mode)
				&& value.team_size == teamSize) return safe(value.time_type);
		return "";
	}

	private static String selectedText(JComboBox<String> combo)
	{
		return combo.getSelectedItem() == null ? "" : combo.getSelectedItem().toString();
	}

	private static int parseTeamLabel(String label)
	{
		if ("Solo".equalsIgnoreCase(label)) return 1;
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d+)").matcher(label);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
	}

	void beginRankingRequest(long generation)
	{
		latestRankingRequestGeneration = generation;
		SwingUtilities.invokeLater(() -> {
			if (generation != latestRankingRequestGeneration) return;
			ranking.removeAll();
			ranking.add(centered("Carregando ranking..."));
			ranking.revalidate();
			ranking.repaint();
		});
	}

	void updateRanking(PbRankingResponse response, long generation)
	{
		SwingUtilities.invokeLater(() -> {
			if (generation != latestRankingRequestGeneration) return;
			ranking.removeAll();
			List<PbRankingResponse.Entry> values = response.ranking == null
				? new ArrayList<>() : response.ranking;
			if (values.isEmpty())
			{
				ranking.add(centered("Ainda não existem PBs nesta categoria."));
			}
			for (PbRankingResponse.Entry entry : values)
			{
				ranking.add(rankingRow(entry));
			}
			String ownText = response.own == null ? "Você ainda não possui PB<br>nesta categoria"
				: "Seu PB: " + formatTime(response.own.seconds) + " · " + response.own.position + "º lugar";
			ownPb.setText("<html><div style='text-align:center'>" + ownText + "</div></html>");
			ranking.revalidate();
			ranking.repaint();
			rankingScroll.getViewport().revalidate();
			revalidate();
			repaint();
			SwingUtilities.invokeLater(() ->
			{
				rankingScroll.getViewport().revalidate();
				revalidate();
				repaint();
			});
		});
	}

	private void setTutorialExpanded(boolean expanded)
	{
		tutorial.setVisible(expanded);
		tutorialToggle.setText((expanded ? "▾ " : "▸ ") + "Como registrar seus PBs?");
	}

	private JPanel rankingRow(PbRankingResponse.Entry entry)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, entry.position <= 3 ? 38 : 30));
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, entry.position <= 3 ? 3 : 1, 1, 0, medalColor(entry.position)),
			BorderFactory.createEmptyBorder(5, 6, 5, 6)));
		JLabel place = new JLabel(entry.position + "º");
		place.setPreferredSize(new Dimension(entry.position <= 3 ? 48 : 30, 24));
		if (entry.position <= 3)
		{
			place.setIcon(medalIcon(entry.position));
			place.setIconTextGap(3);
			place.setForeground(medalColor(entry.position));
		}
		JLabel player = new JLabel(entry.player_name == null ? "—" : entry.player_name);
		player.setToolTipText(entry.player_name);
		JLabel time = new JLabel(formatTime(entry.seconds), SwingConstants.RIGHT);
		if (entry.position <= 3) time.setForeground(medalColor(entry.position));
		row.add(place, BorderLayout.WEST); row.add(player, BorderLayout.CENTER); row.add(time, BorderLayout.EAST);
		return row;
	}

	private static JLabel centered(String text)
	{
		JLabel label = new JLabel("<html><div style='text-align:center;width:190px'>" + text + "</div></html>", SwingConstants.CENTER);
		label.setBorder(BorderFactory.createEmptyBorder(18, 3, 18, 3));
		return label;
	}

	private static String safe(String value) { return value == null ? "" : value; }
	private static Color medalColor(int position)
	{
		return position == 1 ? new Color(235, 190, 45) : position == 2 ? new Color(175, 185, 190)
			: position == 3 ? new Color(190, 110, 55) : new Color(70, 70, 70);
	}

	private static ImageIcon medalIcon(int position)
	{
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(16, 18,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
			java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		Color color = medalColor(position);
		graphics.setColor(color.darker());
		graphics.fillPolygon(new int[]{3, 7, 7, 5}, new int[]{1, 1, 9, 11}, 4);
		graphics.fillPolygon(new int[]{9, 13, 11, 9}, new int[]{1, 1, 11, 9}, 4);
		graphics.setColor(color);
		graphics.fillOval(3, 6, 10, 10);
		graphics.setColor(color.brighter());
		graphics.drawOval(4, 7, 8, 8);
		graphics.dispose();
		return new ImageIcon(image);
	}
	static String formatTime(double seconds)
	{
		long centiseconds = Math.round(seconds * 100.0);
		long hours = centiseconds / 360000;
		long minutes = (centiseconds / 6000) % 60;
		long secs = (centiseconds / 100) % 60;
		long fraction = centiseconds % 100;
		return hours > 0 ? String.format("%d:%02d:%02d.%02d", hours, minutes, secs, fraction)
			: String.format("%02d:%02d.%02d", minutes, secs, fraction);
	}

	private static final class VerticalRankingPanel extends JPanel implements Scrollable
	{
		@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		@Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 24; }
		@Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return Math.max(24, visibleRect.height - 24); }
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
		@Override public boolean getScrollableTracksViewportHeight() { return false; }
	}
}
