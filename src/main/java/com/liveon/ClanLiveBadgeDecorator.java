package com.liveon;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Keeps LIVE/MVP decorations inside the native clan member name widget. */
final class ClanLiveBadgeDecorator
{
	private static final int REFRESH_INTERVAL_TICKS = 2;
	private static final String MVP_MARKUP = " <col=ffc628>MVP</col>";
	private static final String LIVE_MARKUP = " <col=96ffaa>LIVE</col>";

	private final Client client;
	private final ClanMessagesPlugin plugin;
	private int lastRefreshTick = Integer.MIN_VALUE;
	private Set<String> clearingTagMarkup;

	ClanLiveBadgeDecorator(Client client, ClanMessagesPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
	}

	void refresh()
	{
		refresh(false);
	}

	void refreshAfterRedraw()
	{
		refresh(true);
	}

	private void refresh(boolean force)
	{
		int currentTick = client.getTickCount();
		if (!force && lastRefreshTick != Integer.MIN_VALUE
			&& currentTick >= lastRefreshTick
			&& currentTick - lastRefreshTick < REFRESH_INTERVAL_TICKS) return;
		lastRefreshTick = currentTick;
		Widget playerList = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		if (playerList == null || playerList.isHidden()) return;
		Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		decorateWidgetTree(playerList, plugin.isLiveStatusVisible(), visited, false);
	}

	/**
	 * Removes every badge. The tag markup is passed in because shutdown clears the plugin's known
	 * tags before this runs on the client thread.
	 */
	void clearDecorations(Set<String> tagMarkup)
	{
		Widget playerList = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		if (playerList == null) return;
		Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		clearingTagMarkup = tagMarkup;
		try
		{
			decorateWidgetTree(playerList, false, visited, true);
		}
		finally
		{
			clearingTagMarkup = null;
		}
	}

	private void decorateWidgetTree(Widget widget, boolean enabled, Set<Widget> visited, boolean includeHidden)
	{
		if (widget == null || (!includeHidden && widget.isHidden()) || !visited.add(widget)) return;
		String rawText = widget.getText();
		if (rawText != null && !rawText.isEmpty())
		{
			String baseText = clearingTagMarkup != null
				? removeMarkup(removeOwnMarkup(rawText), clearingTagMarkup)
				: plugin.removeKnownClanTagMarkup(removeOwnMarkup(rawText));
			String displayedText = Text.removeTags(baseText).trim();
			String playerName = enabled ? plugin.decoratedPlayerNameIn(displayedText) : null;
			String decoratedText = baseText;
			if (playerName != null)
			{
				if (plugin.isPlayerMvp(playerName)) decoratedText += MVP_MARKUP;
				if (plugin.isPlayerLive(playerName)) decoratedText += LIVE_MARKUP;
				decoratedText += plugin.clanTagBadges(playerName);
			}
			if (!rawText.equals(decoratedText)) widget.setText(decoratedText);
		}
		decorateChildren(widget.getChildren(), enabled, visited, includeHidden);
		decorateChildren(widget.getDynamicChildren(), enabled, visited, includeHidden);
		decorateChildren(widget.getStaticChildren(), enabled, visited, includeHidden);
		decorateChildren(widget.getNestedChildren(), enabled, visited, includeHidden);
	}

	private void decorateChildren(Widget[] children, boolean enabled, Set<Widget> visited, boolean includeHidden)
	{
		if (children == null) return;
		for (Widget child : children) decorateWidgetTree(child, enabled, visited, includeHidden);
	}

	static String removeMarkup(String text, Set<String> markup)
	{
		String cleaned = text;
		for (String item : markup) cleaned = cleaned.replace(item, "");
		return cleaned;
	}

	private static String removeOwnMarkup(String text)
	{
		return text.replace(MVP_MARKUP, "").replace(LIVE_MARKUP, "");
	}
}
