package com.liveon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

final class RanksPanel extends JPanel
{
	private static final int PANEL_WIDTH = 225;
	private static final int BODY_TEXT_WIDTH = 172;
	private static final int ICON_TEXT_WIDTH = 132;
	private static final int REQUIREMENT_TEXT_WIDTH = 158;
	private static final int NEXT_REQUIREMENT_TEXT_WIDTH = 148;
	private static final int REQUIREMENT_NOTICE_TEXT_WIDTH = 174;
	private static final int REQUIREMENT_NOTICE_WIDTH = PANEL_WIDTH - 30;
	private static final Color ORANGE = new Color(190, 104, 0);
	private static final String REQUEST_PENDING_TEXT = "Aguardando aprovação";
	private static final String REQUEST_PENDING_STATUS = "Solicitação enviada para a staff.";
	private static final List<String> PROGRESSION = Arrays.asList(
		"recruta", "soldado", "cabo", "aluno", "sargento", "cadete",
		"tenente", "capitão", "major", "coronel");

	private final JLabel playerName = new JLabel("Jogador");
	private final JLabel actualRank = new JLabel("Rank atual • carregando…");
	private final JLabel actualRankIcon = new JLabel(new RankIcon(Color.GRAY));
	private final JLabel availableTitle = new JLabel("Rank disponível");
	private final JLabel availableRank = new JLabel("Ainda não verificado");
	private final JLabel availableRankIcon = new JLabel(new RankIcon(Color.GRAY));
	private final JPanel availableCard = new JPanel(new BorderLayout());
	private final JLabel nextRank = new JLabel("Aguardando verificação");
	private final JLabel nextRankIcon = new JLabel(new RankIcon(Color.GRAY));
	private final JLabel nextMissing = new JLabel(" ");
	private final JPanel nextCard = new JPanel(new BorderLayout());
	private final JLabel specialTitle = new JLabel("Cargo especial");
	private final JLabel specialNotice = new JLabel(wrapped("", BODY_TEXT_WIDTH));
	private final JPanel specialCard = new JPanel(new BorderLayout());
	private final JLabel requirementsTitle = new JLabel("Requisitos");
	private final JLabel helper = new JLabel(wrapped("Equipe os itens exigidos e abra os menus necessários antes de verificar.", BODY_TEXT_WIDTH));
	private final JPanel detected = new JPanel();
	private final JPanel requirementsCard = new JPanel(new BorderLayout());
	private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
	private final JButton verify;
	private final JButton requestRank;
	private String requestCandidate = "";
	private boolean requestPending;
	private int requestCooldownSeconds;

	RanksPanel(Runnable refreshAction, Runnable resetAction, Runnable requestRankAction)
	{
		setLayout(new BorderLayout(5, 5));
		setPreferredSize(new Dimension(PANEL_WIDTH, 650));
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		verify = new JButton("Verificar");
		verify.setToolTipText("Atualizar itens, pontos e requisitos do rank");
		verify.setBackground(ORANGE);
		verify.setForeground(Color.WHITE);
		verify.addActionListener(event -> refreshAction.run());

		playerName.setFont(playerName.getFont().deriveFont(Font.BOLD, 14f));
		actualRank.setForeground(new Color(170, 170, 170));
		JPanel identityText = new JPanel(new GridLayout(0, 1, 1, 1));
		identityText.setOpaque(false);
		identityText.add(playerName);
		identityText.add(actualRank);
		JPanel identity = new JPanel(new BorderLayout(8, 0));
		identity.setBorder(BorderFactory.createEmptyBorder(4, 7, 6, 7));
		identity.add(actualRankIcon, BorderLayout.WEST);
		identity.add(identityText, BorderLayout.CENTER);

		availableTitle.setFont(availableTitle.getFont().deriveFont(Font.BOLD));
		availableRank.setFont(availableRank.getFont().deriveFont(Font.BOLD, 14f));
		JPanel availableHeader = new JPanel(new BorderLayout());
		availableHeader.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		availableHeader.add(availableTitle, BorderLayout.CENTER);
		JPanel availableBody = new JPanel(new BorderLayout(7, 0));
		availableBody.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		availableBody.add(availableRankIcon, BorderLayout.WEST);
		availableBody.add(availableRank, BorderLayout.CENTER);
		availableCard.setBorder(BorderFactory.createLineBorder(new Color(58, 58, 58)));
		availableCard.add(availableHeader, BorderLayout.NORTH);
		availableCard.add(availableBody, BorderLayout.CENTER);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		identity.setAlignmentX(Component.LEFT_ALIGNMENT);
		availableCard.setAlignmentX(Component.LEFT_ALIGNMENT);
		identity.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
		availableCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
		header.add(identity);
		header.add(Box.createVerticalStrut(3));
		JButton reset = new JButton("Resetar");
		reset.addActionListener(event -> resetAction.run());
		JPanel verifyRow = new JPanel(new GridLayout(1, 2, 3, 0));
		verifyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		verifyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		verifyRow.add(verify);
		verifyRow.add(reset);
		header.add(verifyRow);
		header.add(Box.createVerticalStrut(4));
		specialTitle.setFont(specialTitle.getFont().deriveFont(Font.BOLD));
		specialNotice.setForeground(new Color(180, 180, 180));
		JPanel specialBody = new JPanel();
		specialBody.setOpaque(false);
		specialBody.setLayout(new BoxLayout(specialBody, BoxLayout.Y_AXIS));
		specialBody.setBorder(BorderFactory.createEmptyBorder(5, 8, 6, 8));
		specialBody.add(specialTitle);
		specialBody.add(Box.createVerticalStrut(4));
		specialBody.add(specialNotice);
		specialCard.setBorder(BorderFactory.createLineBorder(new Color(78, 67, 42)));
		specialCard.add(specialBody, BorderLayout.CENTER);
		specialCard.setAlignmentX(Component.LEFT_ALIGNMENT);
		specialCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));
		specialCard.setVisible(false);
		header.add(specialCard);
		header.add(availableCard);
		header.add(Box.createVerticalStrut(3));

		JLabel nextTitle = new JLabel("Próximo objetivo");
		nextTitle.setForeground(new Color(155, 155, 155));
		nextRank.setFont(nextRank.getFont().deriveFont(Font.BOLD));
		nextMissing.setForeground(new Color(165, 165, 165));
		JPanel nextBody = new JPanel();
		nextBody.setOpaque(false);
		nextBody.setLayout(new BoxLayout(nextBody, BoxLayout.Y_AXIS));
		nextBody.setBorder(BorderFactory.createEmptyBorder(4, 7, 5, 7));
		nextBody.add(nextTitle);
		nextBody.add(Box.createVerticalStrut(2));
		JPanel nextValue = new JPanel(new BorderLayout(7, 0));
		nextValue.setOpaque(false);
		nextValue.setAlignmentX(Component.LEFT_ALIGNMENT);
		nextValue.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		nextValue.add(nextRankIcon, BorderLayout.WEST);
		nextValue.add(nextRank, BorderLayout.CENTER);
		nextBody.add(nextValue);
		nextBody.add(Box.createVerticalStrut(3));
		nextMissing.setAlignmentX(Component.LEFT_ALIGNMENT);
		nextMissing.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
		nextBody.add(nextMissing);
		nextCard.setBorder(BorderFactory.createLineBorder(new Color(52, 52, 52)));
		nextCard.add(nextBody, BorderLayout.CENTER);
		nextCard.setAlignmentX(Component.LEFT_ALIGNMENT);
		nextCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 145));
		header.add(nextCard);

		detected.setLayout(new BoxLayout(detected, BoxLayout.Y_AXIS));
		detected.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		JPanel requirementsHeader = new JPanel(new BorderLayout());
		requirementsTitle.setFont(requirementsTitle.getFont().deriveFont(Font.BOLD));
		requirementsHeader.setBorder(BorderFactory.createEmptyBorder(4, 6, 3, 6));
		requirementsHeader.add(requirementsTitle, BorderLayout.NORTH);
		helper.setForeground(new Color(155, 155, 155));
		requirementsHeader.add(helper, BorderLayout.SOUTH);
		JScrollPane detectedScroll = new JScrollPane(detected);
		detectedScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		detectedScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		detectedScroll.setBorder(BorderFactory.createEmptyBorder());
		requirementsCard.setBorder(BorderFactory.createLineBorder(new Color(58, 58, 58)));
		requirementsCard.add(requirementsHeader, BorderLayout.NORTH);
		requirementsCard.add(detectedScroll, BorderLayout.CENTER);

		requestRank = new JButton("Solicitar novo rank");
		requestRank.setBackground(ORANGE);
		requestRank.setForeground(Color.WHITE);
		requestRank.setEnabled(false);
		requestRank.setVisible(false);
		requestRank.addActionListener(event -> requestRankAction.run());
		JPanel actions = new JPanel(new BorderLayout(3, 3));
		actions.add(requestRank, BorderLayout.CENTER);
		actions.add(status, BorderLayout.SOUTH);

		add(header, BorderLayout.NORTH);
		add(requirementsCard, BorderLayout.CENTER);
		add(actions, BorderLayout.SOUTH);
	}

	void update(String accountName, String clanRankName, String evaluatedRank, String nextRank,
		List<String> checks, String advice)
	{
		update(accountName, clanRankName, null, evaluatedRank, null, nextRank, null, checks, checks, advice);
	}

	void update(String accountName, String clanRankName, Icon clanRankIcon, String evaluatedRank, String nextRank,
		List<String> checks, String advice)
	{
		update(accountName, clanRankName, clanRankIcon, evaluatedRank, null, nextRank, null, checks, checks, advice);
	}

	void update(String accountName, String clanRankName, Icon clanRankIcon,
		String evaluatedRank, Icon evaluatedRankIcon, String nextRankName, Icon nextRankSuppliedIcon,
		List<String> nextChecks, List<String> overviewChecks, String advice)
	{
		SwingUtilities.invokeLater(() ->
		{
			playerName.setText(accountName == null || accountName.trim().isEmpty() ? "Jogador" : accountName);
			String safeClanRank = clanRankName == null || clanRankName.trim().isEmpty() ? "Não identificado" : clanRankName;
			actualRank.setText("Rank atual • " + displayedClanRank(safeClanRank));
			actualRankIcon.setIcon(clanRankIcon != null ? clanRankIcon : rankIconFor(safeClanRank));
			availableTitle.setFont(availableTitle.getFont().deriveFont(Font.BOLD, 13f));
			availableRank.setFont(availableRank.getFont().deriveFont(Font.BOLD, 14f));

			boolean synchronizedData = isEligibleRank(evaluatedRank) || (evaluatedRank != null && !evaluatedRank.contains("não sincronizado"));
			boolean unknownRank = normalize(safeClanRank).contains("não identificado") || normalize(safeClanRank).contains("carregando");
			boolean maximumRank = isMaximumRank(safeClanRank);
			boolean specialRank = synchronizedData && !maximumRank && !isNormalRank(safeClanRank);
			boolean noticeOnly = specialRank || maximumRank;
			int currentIndex = progressionIndex(safeClanRank);
			int eligibleIndex = progressionIndex(evaluatedRank);
			specialCard.setVisible(noticeOnly);
			availableCard.setVisible(!noticeOnly);
			nextCard.setVisible(!noticeOnly);
			requirementsCard.setVisible(!noticeOnly);
			availableRankIcon.setVisible(true);
			requestCandidate = "";
			String requestTarget = "";

			if (!synchronizedData)
			{
				availableTitle.setText("Melhor rank disponível");
				setWrappedText(availableRank, "Ainda não verificado");
				availableRankIcon.setIcon(new RankIcon(Color.GRAY));
				nextRankIcon.setIcon(new RankIcon(Color.GRAY));
				requirementsTitle.setText("Como começar");
				helper.setText(wrapped("Clique em Verificar. Se algum item não for detectado, abra o banco ou equipe-o e verifique novamente.", REQUIREMENT_TEXT_WIDTH));
				setWrappedText(nextRank, "Aguardando verificação");
				nextMissing.setText(" ");
			}
			else if (unknownRank)
			{
				availableTitle.setText("Rank atual indisponível");
				setWrappedText(availableRank, "Aguarde o Clan Chat carregar");
				availableRankIcon.setIcon(new RankIcon(Color.GRAY));
				nextRankIcon.setIcon(new RankIcon(Color.GRAY));
				requirementsTitle.setText("Informação");
				helper.setText(wrapped("A solicitação será liberada quando o cargo atual for confirmado pelo Clan Chat.", REQUIREMENT_TEXT_WIDTH));
				setWrappedText(nextRank, "Aguardando Clan Chat");
				nextMissing.setText(" ");
			}
			else if (maximumRank)
			{
				specialTitle.setText("Rank máximo atingido");
				specialNotice.setText(wrapped("Parabéns! Você chegou ao rank máximo do clã. Não há novos ranks para solicitar.", BODY_TEXT_WIDTH));
			}
			else if (specialRank)
			{
				specialTitle.setText("Cargo especial");
				specialNotice.setText(wrapped("Você está com um cargo especial no momento, então a troca de rank não pode ser feita por aqui. Se quiser mudar, é só pedir para a staff pelo Discord.", BODY_TEXT_WIDTH));
			}
			else if (eligibleIndex > currentIndex)
			{
				requestCandidate = evaluatedRank;
				requestTarget = evaluatedRank;
				availableTitle.setText("Melhor rank disponível");
				setWrappedText(availableRank, evaluatedRank);
				availableRankIcon.setIcon(evaluatedRankIcon != null ? evaluatedRankIcon : rankIconFor(evaluatedRank));
				requirementsTitle.setText("Dados verificados");
				helper.setText(wrapped("Confira abaixo os dados usados para calcular seu rank.", REQUIREMENT_TEXT_WIDTH));
				setWrappedText(nextRank, nextRankName);
				nextRankIcon.setIcon(nextRankSuppliedIcon != null ? nextRankSuppliedIcon : rankIconFor(nextRankName));
				nextMissing.setText(missingSummary(nextChecks));
			}
			else if (currentIndex == 0 && eligibleIndex < 2)
			{
				availableTitle.setText("Melhor rank disponível");
				setWrappedText(availableRank, "Soldado • promoção automática");
				availableRankIcon.setIcon(nextRankSuppliedIcon != null ? nextRankSuppliedIcon : rankIconFor("Soldado"));
				nextRankIcon.setIcon(nextRankSuppliedIcon != null ? nextRankSuppliedIcon : rankIconFor("Soldado"));
				requirementsTitle.setText("Promoção automática");
				helper.setText(wrapped("Soldado é concedido após 30 dias no clã. Não é necessário solicitar.", REQUIREMENT_TEXT_WIDTH));
				setWrappedText(nextRank, "Soldado • promoção automática");
				nextMissing.setText(wrapped("Concedido após 30 dias no clã.", REQUIREMENT_TEXT_WIDTH));
			}
			else if (currentIndex >= progressionIndex("Coronel"))
			{
				availableTitle.setText("Próximo cargo");
				setWrappedText(availableRank, "General • somente via Discord");
				Icon generalIcon = nextRankSuppliedIcon != null ? nextRankSuppliedIcon : rankIconFor("General");
				availableRankIcon.setIcon(generalIcon);
				nextRankIcon.setIcon(generalIcon);
				requirementsTitle.setText("Progressão concluída");
				helper.setText(wrapped("General é solicitado diretamente à staff pelo Discord.", REQUIREMENT_TEXT_WIDTH));
				setWrappedText(nextRank, "General • somente via Discord");
				nextMissing.setText(" ");
			}
			else
			{
				String target = nextRankName == null || nextRankName.trim().isEmpty()
					? nextProgressionRank(safeClanRank) : nextRankName;
				availableTitle.setText("Nenhum rank novo disponível");
				availableRank.setFont(availableRank.getFont().deriveFont(Font.PLAIN, 13f));
				setWrappedText(availableRank, "Conclua as pendências abaixo e verifique novamente.", BODY_TEXT_WIDTH);
				availableRankIcon.setIcon(new RankIcon(Color.GRAY));
				availableRankIcon.setVisible(false);
				requirementsTitle.setText("Dados verificados");
				helper.setText(wrapped("Confira o que foi detectado. Se algo estiver pendente, siga a instrução exibida.", REQUIREMENT_TEXT_WIDTH));
				setWrappedText(nextRank, target == null ? "Próximo rank" : target);
				nextRankIcon.setIcon(nextRankSuppliedIcon != null ? nextRankSuppliedIcon : rankIconFor(target));
				nextMissing.setText(missingSummary(nextChecks));
			}

			detected.removeAll();
			for (int index = 0; index < overviewChecks.size(); index++)
			{
				detected.add(line(overviewChecks.get(index)));
				if (index + 1 < overviewChecks.size()) detected.add(Box.createVerticalStrut(3));
			}
			detected.revalidate();
			detected.repaint();
			boolean canRequest = !requestCandidate.isEmpty();
			boolean automaticSoldier = currentIndex == 0 && eligibleIndex < 2;
			boolean showRequest = synchronizedData && !unknownRank && !noticeOnly
				&& currentIndex >= 0 && currentIndex < progressionIndex("Coronel") && !automaticSoldier
				&& !requestTarget.isEmpty();
			requestRank.setVisible(showRequest);
			if (requestPending && showRequest)
			{
				requestRank.setEnabled(false);
				requestRank.setText(REQUEST_PENDING_TEXT);
				requestRank.setToolTipText("Aguarde a análise da staff");
				status.setText(REQUEST_PENDING_STATUS);
			}
			else if (requestCooldownSeconds > 0 && showRequest)
			{
				requestRank.setEnabled(false);
				requestRank.setText("Aguarde " + cooldownLabel(requestCooldownSeconds));
				requestRank.setToolTipText("Uma solicitação foi resolvida recentemente");
			}
			else
			{
				requestRank.setEnabled(canRequest);
				requestRank.setText("Solicitar " + (canRequest ? requestCandidate : requestTarget));
				requestRank.setToolTipText(canRequest ? "Enviar solicitação para a staff"
					: "Complete ou verifique os requisitos destacados antes de solicitar");
			}
			verify.setBackground(canRequest ? new Color(52, 52, 52) : ORANGE);
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
			requestCandidate = "";
			specialCard.setVisible(false);
			availableCard.setVisible(true);
			nextCard.setVisible(true);
			requirementsCard.setVisible(true);
			availableTitle.setText("Melhor rank disponível");
			setWrappedText(availableRank, "Ainda não verificado");
			availableRankIcon.setIcon(new RankIcon(Color.GRAY));
			availableRankIcon.setVisible(true);
			nextRankIcon.setIcon(new RankIcon(Color.GRAY));
			requirementsTitle.setText("Como começar");
			helper.setText(wrapped("Clique em Verificar. Se algum item não for detectado, abra o banco ou equipe-o e verifique novamente.", REQUIREMENT_TEXT_WIDTH));
			setWrappedText(nextRank, "Aguardando verificação");
			nextMissing.setText(" ");
			detected.removeAll();
			requestRank.setVisible(false);
			requestRank.setEnabled(false);
			verify.setBackground(ORANGE);
			status.setText(" ");
			detected.revalidate();
			detected.repaint();
		});
	}

	private static int progressionIndex(String rankName)
	{
		String normalized = normalize(rankName);
		if (normalized.equals("helper") || normalized.equals("membro") || normalized.equals("member")) return 0;
		if (normalized.equals("recruit") || normalized.equals("soldier") || normalized.equals("private")) return 1;
		if (normalized.equals("corporal")) return 2;
		if (normalized.equals("novice")) return 3;
		if (normalized.equals("sergeant")) return 4;
		if (normalized.equals("cadet")) return 5;
		if (normalized.equals("lieutenant")) return 6;
		if (normalized.equals("captain")) return 7;
		if (normalized.equals("general")) return 8;
		if (normalized.equals("colonel")) return 9;
		if (normalized.equals("brigadier")) return 10;
		for (int index = 0; index < PROGRESSION.size(); index++)
		{
			if (normalized.equals(PROGRESSION.get(index)) || normalized.contains(PROGRESSION.get(index))) return index;
		}
		return -1;
	}

	private static String displayedClanRank(String rankName)
	{
		switch (normalize(rankName))
		{
			case "helper": return "Recruta";
			case "recruit": return "Soldado";
			case "corporal": return "Cabo";
			case "novice": return "Aluno";
			case "sergeant": return "Sargento";
			case "cadet": return "Cadete";
			case "lieutenant": return "Tenente";
			case "captain": return "Capitão";
			case "general": return "General";
			case "colonel": return "Coronel";
			case "brigadier": return "General";
			case "completionist": return "Diary 50%+";
			case "quester": return "Diary 100%";
			case "beast": return "Colaborador";
			case "berserker": return "MVP EHB";
			case "skiller": return "MVP EHP";
			case "gold": return "MVP Drops";
			default: return rankName;
		}
	}

	private static String nextProgressionRank(String currentRank)
	{
		int index = progressionIndex(currentRank);
		if (index < 0 || index + 1 >= PROGRESSION.size()) return null;
		String next = PROGRESSION.get(index + 1);
		return next.substring(0, 1).toUpperCase(Locale.ROOT) + next.substring(1);
	}

	private static String requirementsDescription(String rank)
	{
		switch (normalize(rank))
		{
			case "cabo": return "Necessário: 200 Quest points e Fire cape.";
			case "aluno": return "Necessário: 250 Quest points, Fire cape e Easy Combat Achievements.";
			case "sargento": return "Necessário: 300 Quest points, Fire cape e Medium Combat Achievements.";
			case "cadete": return "Necessário: Quest cape, Fire cape e Hard Combat Achievements.";
			case "tenente": return "Necessário: Quest cape, Dizana's quiver ou Infernal cape e Elite Combat Achievements.";
			case "capitão": return "Necessário: Diary cape, Dizana's quiver, Infernal cape e Master Combat Achievements.";
			case "major": return "Necessário: requisitos de Capitão e 2300 total level.";
			case "coronel": return "Necessário: Diary cape, Max cape e Grandmaster Combat Achievements.";
			default: return "Verifique os requisitos pendentes antes de solicitar.";
		}
	}

	private static boolean isNormalRank(String rankName)
	{
		return progressionIndex(rankName) >= 0 || normalize(rankName).contains("não identificado")
			|| normalize(rankName).contains("carregando");
	}

	private static boolean isMaximumRank(String rankName)
	{
		String normalized = normalize(rankName);
		return normalized.equals("general") || normalized.equals("brigadier");
	}

	private static boolean isEligibleRank(String rankName)
	{
		return progressionIndex(rankName) >= 2;
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static JLabel line(String text)
	{
		JLabel label = new JLabel(wrapped(text, REQUIREMENT_TEXT_WIDTH));
		label.setBorder(BorderFactory.createEmptyBorder(2, 3, 2, 3));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setVerticalAlignment(JLabel.TOP);
		String normalized = normalize(text);
		if (normalized.startsWith("! abra o banco"))
		{
			label.setText(wrapped(text, REQUIREMENT_NOTICE_TEXT_WIDTH));
			label.setForeground(new Color(115, 195, 255));
			label.setBackground(new Color(25, 45, 61));
			label.setOpaque(true);
			label.setFont(label.getFont().deriveFont(Font.BOLD));
			label.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(55, 105, 140)),
				BorderFactory.createEmptyBorder(5, 6, 5, 6)));
			Dimension noticeSize = new Dimension(REQUIREMENT_NOTICE_WIDTH, label.getPreferredSize().height);
			label.setPreferredSize(noticeSize);
			label.setMaximumSize(noticeSize);
		}
		else if (text.trim().startsWith("✓"))
			label.setForeground(new Color(55, 170, 75));
		else if (text.trim().startsWith("✕"))
			label.setForeground(new Color(195, 70, 60));
		else if (text.trim().startsWith("!"))
			label.setForeground(new Color(220, 160, 45));
		else label.setForeground(new Color(175, 175, 175));
		return label;
	}

	private static String missingSummary(List<String> missing)
	{
		if (missing == null || missing.isEmpty())
			return wrapped("Nenhuma pendência detectada.", NEXT_REQUIREMENT_TEXT_WIDTH);
		StringBuilder text = new StringBuilder("<html><body><table cellspacing='0' cellpadding='0' width='")
			.append(NEXT_REQUIREMENT_TEXT_WIDTH).append("'><tr><td colspan='2'>Falta:</td></tr>");
		for (int index = 0; index < missing.size(); index++)
		{
			String value = missing.get(index).replaceFirst("^[!✕—]\\s*", "");
			text.append("<tr><td valign='top' width='10'>•</td><td width='")
				.append(NEXT_REQUIREMENT_TEXT_WIDTH - 10).append("'>")
				.append(value).append("</td></tr>");
		}
		return text.append("</table></body></html>").toString();
	}

	private static String wrapped(String value, int width)
	{
		return "<html><body style='width: " + width + "px; overflow-wrap: break-word'>" + value + "</body></html>";
	}

	private static void setWrappedText(JLabel label, String value)
	{
		setWrappedText(label, value, ICON_TEXT_WIDTH);
	}

	private static void setWrappedText(JLabel label, String value, int width)
	{
		String safeValue = value == null || value.trim().isEmpty() ? " " : value;
		label.putClientProperty("plainText", safeValue);
		label.setText(wrapped(safeValue, width));
	}

	private static String plainText(JLabel label)
	{
		Object value = label.getClientProperty("plainText");
		return value == null ? label.getText() : value.toString();
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
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			graphics.setColor(color);
			graphics.fillOval(x + 3, y + 3, 18, 18);
			graphics.setColor(Color.WHITE);
			graphics.drawOval(x + 3, y + 3, 18, 18);
		}
		@Override public int getIconWidth() { return 24; }
		@Override public int getIconHeight() { return 24; }
	}

	String getCurrentRank() { return requestCandidate; }
	String getNextRank() { return requestCandidate; }
	String getDisplayedAvailableRank() { return plainText(availableRank); }
	String getDisplayedNextRank() { return plainText(nextRank); }
	String getDisplayedNextRequirements() { return nextMissing.getText(); }
	String getDisplayedCurrentClanRank() { return actualRank.getText(); }
	boolean isSpecialNoticeVisible() { return specialCard.isVisible(); }
	boolean isProgressionVisible() { return availableCard.isVisible() && nextCard.isVisible() && requirementsCard.isVisible(); }
	String getRequirementsDescription() { return helper.getText(); }
	void setStatus(String text) { SwingUtilities.invokeLater(() -> status.setText(text)); }
	void setRankRequestState(boolean pending, int cooldownSeconds)
	{
		requestPending = pending;
		requestCooldownSeconds = Math.max(0, cooldownSeconds);
		SwingUtilities.invokeLater(() ->
		{
			if (!requestRank.isVisible()) return;
			if (requestPending)
			{
				requestRank.setEnabled(false);
				requestRank.setText(REQUEST_PENDING_TEXT);
				requestRank.setToolTipText("Aguarde a análise da staff");
				status.setText(REQUEST_PENDING_STATUS);
			}
			else if (requestCooldownSeconds > 0)
			{
				requestRank.setEnabled(false);
				requestRank.setText("Aguarde " + cooldownLabel(requestCooldownSeconds));
				requestRank.setToolTipText("Uma solicitação foi resolvida recentemente");
			}
		});
	}

	private static String cooldownLabel(int seconds)
	{
		int minutes = Math.max(1, (seconds + 59) / 60);
		return minutes + (minutes == 1 ? " min" : " min");
	}
	void setStatusSuccess(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(text);
			status.setForeground(new Color(80, 220, 80));
		});
	}
}
