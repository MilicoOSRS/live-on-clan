package com.liveon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

final class RanksPanel extends JPanel
{
	private static final int PANEL_WIDTH = 225;
	private static final int CARD_TEXT_WIDTH = 130;
	private static final int BODY_TEXT_WIDTH = 155;
	private final JPanel detected = new JPanel();
	private final JLabel currentRank = new JLabel(wrapped("n\u00e3o sincronizado", CARD_TEXT_WIDTH));
	private final JLabel currentRankIcon = new JLabel(new RankIcon(Color.GRAY));
	private final JLabel rankIcon = new JLabel(new RankIcon(Color.GRAY));
	private final JLabel possibleRank = new JLabel(wrapped("em an\u00e1lise", CARD_TEXT_WIDTH));
	private final JLabel missingRequirements = new JLabel();
	private final JLabel status = new JLabel();
	private String currentRankName = "";
	private String nextRankName = "";

	RanksPanel(Runnable refreshAction, Runnable resetAction, Runnable requestRankAction)
	{
		setLayout(new BorderLayout(5, 5));
		setPreferredSize(new Dimension(PANEL_WIDTH, 650));
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		JButton refresh = new JButton("Sincronizar");
		refresh.addActionListener(event -> refreshAction.run());
		JButton reset = new JButton("Reset");
		reset.addActionListener(event -> resetAction.run());
		JButton requestRank = new JButton("Solicitar rank");
		requestRank.addActionListener(event -> requestRankAction.run());

		JPanel header = new JPanel();
		header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel currentCard = card("Seu rank atual", currentRank);
		currentCard.add(currentRankIcon, BorderLayout.WEST);
		header.add(currentCard);
		JPanel possibleCard = card("Pr\u00f3ximo rank", possibleRank);
		possibleCard.add(rankIcon, BorderLayout.WEST);
		missingRequirements.setVisible(false);
		possibleCard.add(missingRequirements, BorderLayout.SOUTH);
		header.add(possibleCard);

		detected.setLayout(new javax.swing.BoxLayout(detected, javax.swing.BoxLayout.Y_AXIS));
		detected.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		JPanel dataContent = new JPanel(new BorderLayout(4, 4));
		JLabel syncHint = new JLabel(wrapped("Equipe os itens solicitados e abra os menus indicados para que o plugin possa calcular seu rank. Em seguida, clique em sincronizar.", BODY_TEXT_WIDTH));
		JPanel requirementsBox = new JPanel(new BorderLayout(3, 3));
		requirementsBox.setBorder(BorderFactory.createTitledBorder("Dados detectados / requisitos"));
		requirementsBox.add(syncHint, BorderLayout.NORTH);
		JScrollPane detectedScroll = new JScrollPane(detected);
		detectedScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		detectedScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		detectedScroll.setBorder(BorderFactory.createEmptyBorder());
		requirementsBox.add(detectedScroll, BorderLayout.CENTER);
		dataContent.add(requirementsBox, BorderLayout.CENTER);

		add(header, BorderLayout.NORTH);
		add(dataContent, BorderLayout.CENTER);
		JPanel actions = new JPanel(new BorderLayout(5, 5));
		JPanel buttons = new JPanel(new GridLayout(3, 1, 3, 3));
		buttons.add(refresh);
		buttons.add(reset);
		buttons.add(requestRank);
		actions.add(buttons, BorderLayout.CENTER);
		status.setHorizontalAlignment(JLabel.CENTER);
		actions.add(status, BorderLayout.SOUTH);
		add(actions, BorderLayout.SOUTH);
	}

	void update(String currentRankName, String possibleRankName, List<String> checks, String advice)
	{
		SwingUtilities.invokeLater(() ->
		{
			RanksPanel.this.currentRankName = currentRankName;
			RanksPanel.this.nextRankName = possibleRankName;
			currentRank.setText(wrapped(currentRankName, CARD_TEXT_WIDTH));
			currentRankIcon.setIcon(rankIconFor(currentRankName));
			possibleRank.setText(wrapped(possibleRankName, CARD_TEXT_WIDTH));
			String request = advice == null ? "" : advice;
			String missing = "";
			int extraText = request.indexOf("<br>");
			if (extraText >= 0)
			{
				String details = request.substring(extraText + 4);
				int secondBreak = details.indexOf("<br>");
				if (secondBreak >= 0) details = details.substring(secondBreak + 4);
				if (details.startsWith("Requisitos faltantes:")) missing = details;
				request = request.substring(0, extraText);
			}
			missingRequirements.setText(wrapped(missing, CARD_TEXT_WIDTH));
			missingRequirements.setVisible(!missing.isEmpty());
			rankIcon.setIcon(rankIconFor(possibleRankName));
			detected.removeAll();
			for (int i = 0; i < checks.size(); i++)
			{
				detected.add(line(checks.get(i), true));
				if (i + 1 < checks.size()) detected.add(Box.createVerticalStrut(3));
			}
			detected.revalidate();
			detected.repaint();
		});
	}

	void clearDetails()
	{
		SwingUtilities.invokeLater(() ->
		{
			detected.removeAll();
			detected.revalidate();
			detected.repaint();
		});
	}

	void reset()
	{
		SwingUtilities.invokeLater(() ->
		{
			currentRank.setText(wrapped("n\u00e3o sincronizado", CARD_TEXT_WIDTH));
			currentRankIcon.setIcon(new RankIcon(Color.GRAY));
			possibleRank.setText(wrapped("em an\u00e1lise", CARD_TEXT_WIDTH));
			rankIcon.setIcon(new RankIcon(Color.GRAY));
			missingRequirements.setText("");
			missingRequirements.setVisible(false);
			detected.removeAll();
			String[] resetLines = {
				"Total level: 0 — leitura pendente",
				"Quest points: 0 — leitura pendente",
				"Combat achievements: 0 pontos — leitura pendente",
				"Capas e slayer helmets: leitura pendente",
				"Diary cape: — leitura pendente"
			};
			for (int i = 0; i < resetLines.length; i++)
			{
				detected.add(line(resetLines[i], true));
				if (i + 1 < resetLines.length) detected.add(Box.createVerticalStrut(3));
			}
			detected.revalidate();
			detected.repaint();
		});
	}

	private static JPanel card(String title, JLabel label)
	{
		JPanel card = new JPanel(new BorderLayout(4, 2));
		card.setBorder(BorderFactory.createTitledBorder(title));
		card.add(label, BorderLayout.CENTER);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 115));
		return card;
	}

	private static String wrapped(String value, int width)
	{
		return "<html><body style='width: " + width + "px'>" + value + "</body></html>";
	}

	private static boolean isEligibleRank(String rankName)
	{
		String rank = rankName.toLowerCase();
		return !rank.contains("pendente") && !rank.contains("progresso")
			&& !rank.contains("análise") && !rank.contains("não sincronizado");
	}

	private static JLabel line(String text, boolean data)
	{
		JLabel area = new JLabel(wrapped(text, BODY_TEXT_WIDTH));
		area.setBorder(BorderFactory.createEmptyBorder(2, 3, 2, 3));
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		area.setVerticalAlignment(JLabel.TOP);
		area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		if (data)
		{
			String status = text.toLowerCase(java.util.Locale.ROOT);
			
			// Special logic for Combat Achievements
			if (status.contains("combat achievements"))
			{
				java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)\\s*pontos?");
				java.util.regex.Matcher matcher = pattern.matcher(text);
				if (matcher.find())
				{
					int points = Integer.parseInt(matcher.group(1));
					if (points == 2671)
					{
						area.setForeground(new Color(35, 145, 65)); // Green - Grandmaster
					}
					else if (points > 0)
					{
						area.setForeground(new Color(190, 130, 20)); // Orange - In progress
					}
					else
					{
						area.setForeground(new Color(190, 55, 55)); // Red - Not started
					}
				}
				return area;
			}
			
			boolean hasCheck = text.contains("\u2713");
			boolean alternativeComplete = status.contains("alternativa atendida");
			boolean hasMissing = status.contains("pendente") || status.contains("leitura")
				|| status.contains("equipe") || status.contains("não obtida") || status.contains("—");
			boolean partial = status.contains("requer") || (hasCheck && hasMissing)
				|| status.matches(".*\\d+\\s*/\\s*\\d+.*");
			if ((hasCheck && !hasMissing) || alternativeComplete) area.setForeground(new Color(35, 145, 65));
			else if (partial) area.setForeground(new Color(190, 130, 20));
			else area.setForeground(new Color(190, 55, 55));
		}
		return area;
	}

	private static Icon rankIconFor(String rankName)
	{
		Icon icon = RankVisuals.rankIconFor(rankName);
		return icon != null ? icon : new RankIcon(RankVisuals.rankColor(rankName));
	}

	private static final class RankIcon implements Icon
	{
		private final Color color;
		private RankIcon(Color color) { this.color = color; }
		@Override public void paintIcon(Component c, Graphics g, int x, int y)
		{
			g.setColor(color);
			g.fillOval(x + 2, y + 2, 20, 20);
			g.setColor(Color.WHITE);
			g.drawOval(x + 2, y + 2, 20, 20);
		}
		@Override public int getIconWidth() { return 24; }
		@Override public int getIconHeight() { return 24; }
	}

	String getCurrentRank() { return currentRankName; }
	String getNextRank() { return nextRankName; }
	void setStatus(String text) { SwingUtilities.invokeLater(() -> status.setText(text)); }
	void setStatusSuccess(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(text);
			status.setForeground(new Color(80, 220, 80));
		});
	}
}

