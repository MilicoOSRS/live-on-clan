package com.liveon;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.game.ChatIconManager;

final class RankVisuals
{
	private static final int CHAT_ICON_SIZE = 11;
	private static final String[][] CUSTOM_RANKS = {
		{"coronel", "Coronel", "coronel.png"},
		{"major", "Major", "major.png"},
		{"capitão", "Capitão", "capitao.png"},
		{"capitao", "Capitão", "capitao.png"},
		{"tenente", "Tenente", "tenente.png"},
		{"cadete", "Cadete", "cadete.png"},
		{"sargento", "Sargento", "sargento.png"},
		{"aluno", "Aluno", "aluno.png"},
		{"cabo", "Cabo", "cabo.png"}
	};
	private static final Map<String, Integer> CHAT_ICON_IDS = new LinkedHashMap<>();
	private static final Pattern RANK_PATTERN = Pattern.compile("(?iu)(?<!\\p{L})(Coronel|Major|Capitão|Capitao|Tenente|Cadete|Sargento|Aluno|Cabo)(?!\\p{L})");

	private RankVisuals()
	{
	}

	static void registerChatIcons(ChatIconManager chatIconManager)
	{
		CHAT_ICON_IDS.clear();
		Map<String, Integer> iconIdsByFile = new HashMap<>();
		for (String[] rank : CUSTOM_RANKS)
		{
			Integer iconId = iconIdsByFile.get(rank[2]);
			if (iconId == null)
			{
				BufferedImage image = loadRankImage(rank[2]);
				if (image == null) continue;
				iconId = chatIconManager.registerChatIcon(scaleImage(image, CHAT_ICON_SIZE, CHAT_ICON_SIZE));
				iconIdsByFile.put(rank[2], iconId);
			}
			CHAT_ICON_IDS.put(rank[0], iconId);
		}
	}

	static void appendWithIcons(ChatIconManager chatIconManager, ChatMessageBuilder builder, String text)
	{
		appendWithIcons(chatIconManager, builder, null, text);
	}

	static void appendRankWithIcon(ChatIconManager chatIconManager, ChatMessageBuilder builder, String rankName)
	{
		appendRankWithIcon(chatIconManager, builder, null, rankName);
	}

	static void appendRankWithIcon(ChatIconManager chatIconManager, ChatMessageBuilder builder, Color color, String rankName)
	{
		if (rankName == null || rankName.isEmpty())
		{
			return;
		}
		Integer iconId = chatIconIndexFor(chatIconManager, rankName);
		if (iconId != null)
		{
			builder.img(iconId).append(" ");
		}
		appendText(builder, color, rankName);
	}

	static void appendWithIcons(ChatIconManager chatIconManager, ChatMessageBuilder builder, Color color, String text)
	{
		if (text == null || text.isEmpty())
		{
			return;
		}
		Matcher matcher = RANK_PATTERN.matcher(text);
		int lastEnd = 0;
		while (matcher.find())
		{
			if (matcher.start() > lastEnd)
			{
				appendText(builder, color, text.substring(lastEnd, matcher.start()));
			}
			String matchedRank = matcher.group();
			appendRankWithIcon(chatIconManager, builder, color, matchedRank);
			lastEnd = matcher.end();
		}
		if (lastEnd < text.length())
		{
			appendText(builder, color, text.substring(lastEnd));
		}
	}

	static Icon rankIconFor(String rankName)
	{
		String file = rankFile(rankName);
		if (file != null)
		{
			BufferedImage image = loadRankImage(file);
			if (image != null)
			{
				return new ImageIcon(image);
			}
		}
		return null;
	}

	static Color rankColor(String rankName)
	{
		String rank = normalize(rankName);
		if (rank.contains("owner") || rank.contains("jmod")) return new Color(202, 155, 56);
		if (rank.contains("administrator")) return new Color(177, 75, 220);
		if (rank.contains("deputy")) return new Color(220, 150, 60);
		if (rank.contains("guest")) return new Color(140, 140, 140);
		if (rank.contains("coronel")) return new Color(184, 135, 42);
		if (rank.contains("major")) return new Color(142, 98, 190);
		if (rank.contains("capit")) return new Color(205, 126, 42);
		if (rank.contains("tenente")) return new Color(70, 125, 190);
		if (rank.contains("cadete")) return new Color(80, 155, 95);
		if (rank.contains("sargento")) return new Color(110, 145, 175);
		if (rank.contains("aluno") || rank.contains("cabo")) return new Color(125, 125, 125);
		return new Color(70, 180, 90);
	}

	static Integer chatIconIdFor(String rankName)
	{
		String normalized = normalize(rankName);
		for (Map.Entry<String, Integer> entry : CHAT_ICON_IDS.entrySet())
		{
			if (normalized.contains(entry.getKey()))
			{
				return entry.getValue();
			}
		}
		return null;
	}

	static Integer chatIconIndexFor(ChatIconManager chatIconManager, String rankName)
	{
		Integer logicalId = chatIconIdFor(rankName);
		if (logicalId == null)
		{
			return null;
		}

		int index = chatIconManager.chatIconIndex(logicalId);
		return index >= 0 ? index : null;
	}

	private static String rankFile(String rankName)
	{
		String normalized = normalize(rankName);
		for (String[] rank : CUSTOM_RANKS)
		{
			if (normalized.contains(rank[0]))
			{
				return rank[2];
			}
		}
		return null;
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private static BufferedImage loadRankImage(String file)
	{
		try (InputStream stream = RankVisuals.class.getResourceAsStream("/ranks/" + file))
		{
			return stream == null ? null : ImageIO.read(stream);
		}
		catch (IOException exception)
		{
			return null;
		}
	}

	private static BufferedImage scaleImage(BufferedImage source, int width, int height)
	{
		Image scaled = source.getScaledInstance(width, height, Image.SCALE_SMOOTH);
		BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = buffered.createGraphics();
		graphics.drawImage(scaled, 0, 0, null);
		graphics.dispose();
		return buffered;
	}

	private static void appendText(ChatMessageBuilder builder, Color color, String text)
	{
		if (text.isEmpty())
		{
			return;
		}
		if (color == null)
		{
			builder.append(text);
			return;
		}
		builder.append(color, text);
	}

	/**
	 * Ensure a chat icon is registered for the given rank resource and return its id.
	 * Returns null if no resource exists or registration failed.
	 */
	static Integer registerChatIconForRank(ChatIconManager chatIconManager, String rankName)
	{
		Integer existing = chatIconIndexFor(chatIconManager, rankName);
		if (existing != null)
		{
			return existing;
		}
		String file = rankFile(rankName);
		if (file == null) return null;
		BufferedImage image = loadRankImage(file);
		if (image == null) return null;
		try
		{
			int id = chatIconManager.registerChatIcon(scaleImage(image, CHAT_ICON_SIZE, CHAT_ICON_SIZE));
			if (id < 0) return null;
			// choose canonical key for the mapping (first matching CUSTOM_RANKS entry)
			String key = null;
			for (String[] r : CUSTOM_RANKS)
			{
				if (r[2].equals(file)) { key = r[0]; break; }
			}
			if (key == null) key = normalize(rankName);
			CHAT_ICON_IDS.put(key, id);
			int index = chatIconManager.chatIconIndex(id);
			return index >= 0 ? index : null;
		}
		catch (RuntimeException | Error ex)
		{
			return null;
		}
	}
}

