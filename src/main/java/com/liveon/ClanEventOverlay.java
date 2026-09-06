package com.liveon;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class ClanEventOverlay extends Overlay
{
	private static final int MAX_WIDTH = 235;
	private static final int PADDING_X = 5;
	private static final int PADDING_Y = 3;
	private static final Color BACKGROUND = new Color(0, 0, 0, 55);
	private static final Color TITLE = new Color(124, 238, 74);
	private static final Color MESSAGE = new Color(230, 230, 230);
	private final ClanMessagesPlugin plugin;
	private static final java.time.format.DateTimeFormatter CLOCK = java.time.format.DateTimeFormatter
		.ofPattern("dd/MM HH:mm 'BRT'").withZone(java.time.ZoneId.of("America/Sao_Paulo"));
	private long cachedSecond = Long.MIN_VALUE;
	private String clock = "Hora indisponível";

	ClanEventOverlay(ClanMessagesPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ClanMessagesPlugin.EventOverlayState state = plugin.getEventOverlayState();
		if (!plugin.isEventOverlayVisible() || state == null || !state.enabled)
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		long elapsed = state.synchronizedNanos == 0 ? Long.MAX_VALUE : System.nanoTime() - state.synchronizedNanos;
		if (state.serverTime > 0 && elapsed >= 0 && elapsed < java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) {
			long second = (long) (state.serverTime + elapsed / 1_000_000_000.0);
			if (second != cachedSecond) { cachedSecond = second; clock = CLOCK.format(java.time.Instant.ofEpochSecond(second)); }
		} else { clock = "Hora não sincronizada"; cachedSecond = Long.MIN_VALUE; }
		String firstLine = fit(state.header(), metrics, MAX_WIDTH - PADDING_X * 2 - metrics.stringWidth("  " + clock)) + "  " + clock;
		String secondLine = fit(state.staffMessage, metrics, MAX_WIDTH - PADDING_X * 2);
		boolean hasSecondLine = !secondLine.isEmpty();
		int textWidth = Math.max(metrics.stringWidth(firstLine), metrics.stringWidth(secondLine));
		int width = Math.min(MAX_WIDTH, Math.max(70, textWidth + PADDING_X * 2));
		int lineHeight = metrics.getHeight();
		int height = PADDING_Y * 2 + lineHeight * (hasSecondLine ? 2 : 1);

		graphics.setColor(BACKGROUND);
		graphics.fillRect(0, 0, width, height);
		int baseline = PADDING_Y + metrics.getAscent();
		graphics.setColor(Color.BLACK);
		graphics.drawString(firstLine, PADDING_X + 1, baseline + 1);
		graphics.setColor(TITLE);
		graphics.drawString(firstLine, PADDING_X, baseline);
		if (hasSecondLine)
		{
			graphics.setColor(Color.BLACK);
			graphics.drawString(secondLine, PADDING_X + 1, baseline + lineHeight + 1);
			graphics.setColor(MESSAGE);
			graphics.drawString(secondLine, PADDING_X, baseline + lineHeight);
		}
		return new Dimension(width, height);
	}

	private static String fit(String value, FontMetrics metrics, int maxWidth)
	{
		String text = value == null ? "" : value.trim();
		if (metrics.stringWidth(text) <= maxWidth) return text;
		String suffix = "...";
		int end = text.length();
		while (end > 0 && metrics.stringWidth(text.substring(0, end) + suffix) > maxWidth) end--;
		return end == 0 ? suffix : text.substring(0, end).trim() + suffix;
	}
}
