package com.liveon;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Draws the MVP / LIVE / custom tag badges next to the names in the native clan list. They are drawn
 * as an overlay on purpose: rewriting the name widget's text made the game lose the member's
 * right-click menu.
 *
 * <p>Finding which rows get a badge means walking the whole clan list, so that is done once per game
 * tick (and after a redraw). Between ticks only the text widgets found by that walk are looked at again,
 * and only the ones whose text changed are re-evaluated; every frame draws the few cached badges at the
 * widgets' live positions.
 */
final class ClanBadgeOverlay extends Overlay
{
	private static final Pattern TAG = Pattern.compile("<col=([0-9a-fA-F]{6})>([^<]*)</col>");
	private static final Color MVP = new Color(0xffc628);
	private static final Color LIVE = new Color(0x96ffaa);

	private static final class Badge
	{
		final String text;
		final Color color;

		Badge(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}
	}

	private static final class Row
	{
		final Widget widget;
		final String name;
		final String rawText;
		final List<Badge> badges;

		Row(Widget widget, String name, String rawText, List<Badge> badges)
		{
			this.widget = widget;
			this.name = name;
			this.rawText = rawText;
			this.badges = badges;
		}
	}

	/** A text widget of the clan list, with the text it had when its badges were last worked out. */
	private static final class Candidate
	{
		final Widget widget;
		String rawText;
		Row row;

		Candidate(Widget widget)
		{
			this.widget = widget;
		}
	}

	private final Client client;
	private final ClanMessagesPlugin plugin;
	private List<Candidate> candidates = Collections.emptyList();
	private List<Row> rows = Collections.emptyList();
	private int scannedTick = Integer.MIN_VALUE;
	private int scannedScroll = Integer.MIN_VALUE;

	ClanBadgeOverlay(Client client, ClanMessagesPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_LOW);
	}

	/** The clan list was redrawn, so the cached widgets may be gone: scan again on the next frame. */
	void invalidate()
	{
		scannedTick = Integer.MIN_VALUE;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isLiveStatusVisible())
		{
			clear();
			return null;
		}
		Widget playerList = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		if (playerList == null || playerList.isHidden())
		{
			clear();
			return null;
		}
		int tick = client.getTickCount();
		int scroll = playerList.getScrollY();
		if (tick != scannedTick)
		{
			// Once per tick: walk the whole list and work every text widget out again, so changes in who is
			// MVP / live / tagged show up too.
			scannedTick = tick;
			scannedScroll = scroll;
			List<Candidate> found = new ArrayList<>();
			scan(playerList, Collections.newSetFromMap(new IdentityHashMap<>()), found);
			candidates = found;
			collectRows(true);
		}
		else if (scroll != scannedScroll || rowsOutdated())
		{
			// While scrolling the list reuses its row widgets for other members. Walking the tree again on
			// every frame is expensive, so only the known text widgets whose text changed are re-evaluated.
			scannedScroll = scroll;
			collectRows(false);
		}
		if (rows.isEmpty())
		{
			return null;
		}
		Rectangle clip = playerList.getBounds();
		java.awt.Shape oldClip = graphics.getClip();
		graphics.setClip(clip);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		try
		{
			for (Row row : rows) draw(graphics, row, clip);
		}
		finally
		{
			graphics.setClip(oldClip);
		}
		return null;
	}

	private boolean rowsOutdated()
	{
		for (Row row : rows)
		{
			if (!java.util.Objects.equals(row.widget.getText(), row.rawText)) return true;
		}
		return false;
	}

	private void clear()
	{
		candidates = Collections.emptyList();
		rows = Collections.emptyList();
		scannedTick = Integer.MIN_VALUE;
	}

	/** Collects every visible text widget under the clan list; which of them get a badge is decided later. */
	private void scan(Widget widget, Set<Widget> visited, List<Candidate> found)
	{
		if (widget == null || widget.isHidden() || !visited.add(widget))
		{
			return;
		}
		if (widget.getType() == WidgetType.TEXT)
		{
			found.add(new Candidate(widget));
		}
		for (Widget[] group : new Widget[][]{widget.getChildren(), widget.getDynamicChildren(),
			widget.getStaticChildren(), widget.getNestedChildren()})
		{
			if (group == null) continue;
			for (Widget child : group) scan(child, visited, found);
		}
	}

	/** Rebuilds the badge rows; unless forced, a widget whose text did not change keeps its previous result. */
	private void collectRows(boolean force)
	{
		List<Row> found = new ArrayList<>();
		for (Candidate candidate : candidates)
		{
			String raw = candidate.widget.getText();
			if (force || !java.util.Objects.equals(raw, candidate.rawText))
			{
				candidate.rawText = raw;
				candidate.row = badgeRow(candidate.widget, raw);
			}
			if (candidate.row != null) found.add(candidate.row);
		}
		rows = found;
	}

	private Row badgeRow(Widget widget, String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return null;
		}
		String displayed = Text.removeTags(raw).trim();
		String playerName = plugin.decoratedPlayerNameIn(displayed);
		if (playerName == null)
		{
			return null;
		}
		List<Badge> badges = new ArrayList<>();
		if (plugin.isPlayerMvp(playerName)) badges.add(new Badge("MVP", MVP));
		if (plugin.isPlayerLive(playerName)) badges.add(new Badge("LIVE", LIVE));
		Matcher tags = TAG.matcher(plugin.clanTagBadges(playerName));
		while (tags.find())
		{
			badges.add(new Badge(tags.group(2), new Color(Integer.parseInt(tags.group(1), 16))));
		}
		return badges.isEmpty() ? null : new Row(widget, displayed, raw, badges);
	}

	/**
	 * The game only refreshes a widget's stored screen position after a scroll has finished, so reading
	 * getBounds() while scrolling places the badge one frame behind its name. The vertical position is
	 * rebuilt from the scroll container's fixed position and its current scroll offset instead.
	 */
	private static Rectangle liveBounds(Widget widget)
	{
		Rectangle stored = widget.getBounds();
		if (stored == null)
		{
			return null;
		}
		Widget container = widget.getParent();
		while (container != null && container.getScrollHeight() <= container.getHeight())
		{
			container = container.getParent();
		}
		if (container == null)
		{
			return stored;
		}
		int relativeY = 0;
		Widget step = widget;
		while (step != null && step != container)
		{
			relativeY += step.getRelativeY();
			step = step.getParent();
		}
		if (step == null)
		{
			return stored;
		}
		Rectangle fixed = container.getBounds();
		if (fixed == null)
		{
			return stored;
		}
		return new Rectangle(stored.x, fixed.y + relativeY - container.getScrollY(), stored.width, stored.height);
	}

	private void draw(Graphics2D graphics, Row row, Rectangle clip)
	{
		Widget widget = row.widget;
		if (widget.isHidden())
		{
			return;
		}
		Rectangle bounds = liveBounds(widget);
		if (bounds == null || !bounds.intersects(clip))
		{
			return;
		}
		int nameWidth = widget.getFont() == null ? 0 : widget.getFont().getTextWidth(row.name);
		// Text starts at the left edge, or is centered; right-aligned names have no room for a badge.
		int x;
		switch (widget.getXTextAlignment())
		{
			case 0: x = bounds.x + nameWidth; break;
			case 1: x = bounds.x + (bounds.width + nameWidth) / 2; break;
			default: return;
		}
		x += 4;
		FontMetrics metrics = graphics.getFontMetrics();
		int y = bounds.y + (bounds.height + metrics.getAscent() - metrics.getDescent()) / 2;
		for (Badge badge : row.badges)
		{
			graphics.setColor(Color.BLACK);
			graphics.drawString(badge.text, x + 1, y + 1);
			graphics.setColor(badge.color);
			graphics.drawString(badge.text, x, y);
			x += metrics.stringWidth(badge.text) + 4;
		}
	}
}
