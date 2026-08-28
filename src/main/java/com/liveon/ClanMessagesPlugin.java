package com.liveon;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.ScriptID;
import net.runelite.api.WorldType;
import net.runelite.api.ItemContainer;
import net.runelite.api.Item;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.events.ConfigChanged;
import javax.swing.JOptionPane;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.plugins.loottracker.LootTrackerConfig;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.Notifier;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MultipartBody;
import net.runelite.http.api.loottracker.LootRecordType;

@Slf4j
@PluginDescriptor(name = "Live On Clan")
public class ClanMessagesPlugin extends Plugin
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final Pattern RANK_REQUEST_MESSAGE_PATTERN = Pattern.compile("(?<player>.+) solicitou um rank: (?<rank>.+)");
	private static final Pattern PROMOTION_MESSAGE_PATTERN = Pattern.compile("(?:Promo\u00E7\u00E3o: )?(?<player>.+?) foi promovido para (?<rank>.+)!");
	private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>]+");
	private static final Pattern PET_TRIGGER_PATTERN = Pattern.compile(
		"You (?:have a funny feeling like you|feel something weird sneaking).*", Pattern.CASE_INSENSITIVE);
	private static final Pattern PET_CLAN_PATTERN = Pattern.compile(
		"\\b(?<user>[\\w\\s]+) (?:has a funny feeling like .+ followed|feels something weird sneaking into .+ backpack|feels like .+ acquired something special): (?:(?<pet>.+) at (?<milestone>.+)|(?<petOnly>.+))",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PET_UNTRADEABLE_PATTERN = Pattern.compile("Untradeable drop: (.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern PET_COLLECTION_PATTERN = Pattern.compile(
		"(?:New item added to your collection log|Collection log):\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern VALUABLE_DROP_PATTERN = Pattern.compile(
		"(?:Valuable drop|Untradeable drop):\\s*(?:(\\d+)\\s*x\\s*)?(.+?)\\s*\\(([0-9,]+)\\s+coins?\\)\\s*\\.?$",
		Pattern.CASE_INSENSITIVE);
	private static final int PET_DETAILS_WAIT_TICKS = 5;
	// Internal script called by rebuildchatbox after the vanilla clan rank is resolved.
	private static final int ADD_CHATBOX_MESSAGE_SCRIPT = 4483;
	private static final String WOM_USER_AGENT = "Live-On-RuneLite-Plugin";
	private static final long RANK_NOTIFICATION_COOLDOWN_MILLIS = 24L * 60L * 60L * 1000L;
	// Drop exceptions modelled after Dink's loot filters. A trailing '*' matches
	// item variants. Collection-log messages provide a fallback when RuneLite
	// does not emit a normal loot event for one of these items.
	private static final List<String> DROP_ITEM_ALLOWLIST = java.util.Arrays.asList(
		"enhanced crystal weapon seed",
		"crystal armour seed",
		"mokhaiotl cloth",
		"venator vestige",
		"ultor vestige",
		"bellator vestige",
		"magus vestige",
		"elder venator*",
		"crimson kisten",
		"araxyte fang*"
	);
	private static final List<String> DISCORD_ITEM_DENYLIST = java.util.Collections.emptyList();
	private static final List<String> DISCORD_SOURCE_DENYLIST = java.util.Arrays.asList(
		"loot chest",
		"bird nest"
	);

	// Mapping Portuguese rank names (lowercase) to clan title names used in-game
	private static final Map<String, String> RANK_TITLE_ALIASES = new LinkedHashMap<>();
	static {
		RANK_TITLE_ALIASES.put("recruta", "Helper");
		RANK_TITLE_ALIASES.put("soldado", "Recruit");
		RANK_TITLE_ALIASES.put("cabo", "Corporal");
		RANK_TITLE_ALIASES.put("aluno", "Novice");
		RANK_TITLE_ALIASES.put("sargento", "Sergeant");
		RANK_TITLE_ALIASES.put("cadete", "Cadet");
		RANK_TITLE_ALIASES.put("tenente", "Lieutenant");
		RANK_TITLE_ALIASES.put("capitão", "Captain");
		RANK_TITLE_ALIASES.put("capitao", "Captain");
		RANK_TITLE_ALIASES.put("major", "General");
		RANK_TITLE_ALIASES.put("coronel", "Colonel");
	}

	@Inject private ClientToolbar clientToolbar;
	@Inject private Client client;
	@Inject private ChatIconManager chatIconManager;
	@Inject private ClientThread clientThread;
	@Inject private ItemManager itemManager;
	@Inject private DropRarityService dropRarityService;
	@Inject private DrawManager drawManager;
	@Inject private ChatMessageBuilder chatMessageBuilder;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private OkHttpClient okHttpClient;
	@Inject private Gson gson;
	@Inject private ClanMessagesConfig config;
	@Inject private ConfigManager configManager;
	@Inject private Notifier notifier;

	private ScheduledExecutorService executor;
	private ClanMessagesPanel panel;
	private NavigationButton navigationButton;
	// WOM membership cache: rsn (lowercase) -> CacheEntry
	private final java.util.Map<String, CacheEntry> womCache = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long WOM_CACHE_TTL_SECONDS = 3600; // 1 hour
	private static final long WOM_NEGATIVE_CACHE_TTL_SECONDS = 60;
	private volatile okhttp3.Call currentWomCall = null;
	private static final int VERIFY_COOLDOWN_SECONDS = 30;

	private static final class CacheEntry { final boolean member; final String role; final long expiresAtMillis; CacheEntry(boolean m, String r, long e) { member=m; role=r; expiresAtMillis=e; } }
	private ScheduledFuture<?> pollingTask;
	private String lastMessageId = "";
	private String messageCursorAccount = "";
	private final java.util.Map<String, String> messageCursorByAccount = new java.util.concurrent.ConcurrentHashMap<>();
	private String lastClearMarker = "";
	private final AtomicBoolean messageFetchInFlight = new AtomicBoolean(false);
	private final java.util.Set<String> locallyDisplayedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.Set<String> deliveredPinnedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.Set<String> displayedPendingRankRequests = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private boolean isStaff = false;
	private boolean canPublishBroadcast = false;
	private String verifiedAccount = "";
	private volatile String authenticatedPlayerName = "";
	private int lastCombatAchievementPoints = -1;
	private String combatAchievementAccount = "";
	private int lastQuestPoints = -1;
	private int lastMaximumQuestPoints = -1;
	private boolean pendingPet;
	private String pendingPetName;
	private String pendingPetMilestone;
	private String pendingPetGameMessage;
	private boolean pendingPetDuplicate;
	private boolean pendingPetBackpack;
	private Boolean pendingPetPreviouslyOwned;
	private int pendingPetTicks;
	private String questAccount = "";
	private boolean rankSyncCompleted;
	private boolean rankWidgetRefreshPending;
	private final java.util.Set<String> rankBankItems = new java.util.HashSet<>();
	private final java.util.Set<Integer> rankBankItemIds = new java.util.HashSet<>();
	private String rankBankAccount = "";
	private boolean rankBankLoaded;
	private int lastObservedRankTotalLevel = -1;
	private volatile boolean rankRequestStatusKnown;
	private volatile boolean rankRequestPending;
	private final java.util.Map<String, PendingAllowlistedDrop> pendingAllowlistedDrops = new java.util.HashMap<>();
	private final java.util.Map<String, Integer> recentAllowlistedLootTicks = new java.util.HashMap<>();

	static final class PendingAllowlistedDrop
	{
		final String itemName;
		int quantity;
		Long totalValue;

		private PendingAllowlistedDrop(String itemName, int quantity, Long totalValue)
		{
			this.itemName = itemName;
			this.quantity = Math.max(1, quantity);
			this.totalValue = totalValue;
		}
	}
	private ScheduledFuture<?> rankRequestsPollingTask;
	private ScheduledFuture<?> mvpDropsPollingTask;
	// If true, the user manually disconnected and auto-verification should be paused until they click Verify
	private final java.util.Map<String, LiveChannel> onlineLiveChannels = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<String> mvpMembers = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.Map<String, java.util.List<ClanTag>> clanTagsByPlayer = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<String> knownClanTagMarkup = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private volatile boolean isDeputyOwner;
	private ClanLiveBadgeDecorator clanLiveBadgeDecorator;

	@Override
	protected void startUp()
	{
		executor = Executors.newSingleThreadScheduledExecutor();
		RankVisuals.registerChatIcons(chatIconManager);
		clanLiveBadgeDecorator = new ClanLiveBadgeDecorator(client, this);
		panel = new ClanMessagesPanel(() -> publishDraft("BROADCAST"), () -> publishDraft("CLAN"), () -> verifyToken(true), this::clearMessages, this::refreshRanks, this::resetRanks, this::requestRank, this::fetchRankRequests, this::deleteRankRequest, this::confirmRankRequest, this::declineRankRequest, this::fetchSentMessages, this::deleteSentMessage, this::resendSentMessage, this::togglePinnedMessage, this::fetchLives, this::saveLiveChannel, this::deleteLiveChannel, this::fetchMvpMembers, this::saveMvpMember, this::deleteMvpMember, this::fetchClanTags, this::createClanTag, this::addClanTagMember, this::deleteClanTag, this::removeClanTagMember, config.staffAccessKey(), this::saveStaffAccessKey);
		panel.clearRankDetails();
		if (config.enabled())
		{
			panel.setConnectionDisabled(false);
			verifyToken();
		}
		else
		{
			panel.setConnectionDisabled(true);
		}
		rebuildNavigationButton();
		configurePolling();
	}

	@Override
	protected void shutDown()
	{
		resetPendingPet();
		pendingAllowlistedDrops.clear();
		recentAllowlistedLootTicks.clear();
		if (pollingTask != null)
		{
			pollingTask.cancel(false);
		}
		if (rankRequestsPollingTask != null)
		{
			rankRequestsPollingTask.cancel(false);
		}
		if (mvpDropsPollingTask != null)
		{
			mvpDropsPollingTask.cancel(false);
		}
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
		}
		if (clanLiveBadgeDecorator != null)
		{
			clanLiveBadgeDecorator.clearDecorations();
			clanLiveBadgeDecorator = null;
		}
		okhttp3.Call womCall = currentWomCall;
		if (womCall != null)
		{
			womCall.cancel();
			currentWomCall = null;
		}
		verifiedAccount = "";
		authenticatedPlayerName = "";
		isStaff = false;
		isDeputyOwner = false;
		canPublishBroadcast = false;
		onlineLiveChannels.clear();
		mvpMembers.clear();
		clanTagsByPlayer.clear();
		knownClanTagMarkup.clear();
		panel = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("live-on-clan-messages".equals(event.getGroup()))
		{
			if ("sidebarIconPriority".equals(event.getKey()))
			{
				rebuildNavigationButton();
			}
			if ("enabled".equals(event.getKey()))
			{
				if (config.enabled())
				{
					if (panel != null) panel.setConnectionDisabled(false);
					verifyToken(true);
				}
				else
				{
					okhttp3.Call womCall = currentWomCall;
					if (womCall != null)
					{
						womCall.cancel();
						currentWomCall = null;
					}
					authenticatedPlayerName = "";
					verifiedAccount = "";
					isStaff = false;
					isDeputyOwner = false;
					onlineLiveChannels.clear();
					mvpMembers.clear();
					clanTagsByPlayer.clear();
					if (panel != null)
					{
						panel.setAuthenticated(false, false);
						panel.setConnectionDisabled(true);
					}
				}
			}
			configurePolling();
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!"Report".equals(event.getOption()))
		{
			return;
		}

		int packedWidgetId = event.getActionParam1();
		int groupId = WidgetUtil.componentToInterface(packedWidgetId);
		int childId = WidgetUtil.componentToId(packedWidgetId);
		if (groupId != InterfaceID.CHATBOX)
		{
			return;
		}

		Widget menuWidget = client.getWidget(groupId, childId);
		Widget messageLines = menuWidget == null ? null : menuWidget.getParent();
		if (messageLines == null || messageLines.getId() != InterfaceID.Chatbox.SCROLLAREA)
		{
			return;
		}

		int messageChildIndex = (childId - (InterfaceID.Chatbox.LINE0 & 0xFFFF)) * 4 + 1;
		Widget messageWidget = messageChildIndex < 0 ? null : messageLines.getChild(messageChildIndex);
		if (messageWidget == null)
		{
			return;
		}

		String formattedText = messageWidget.getText();
		if (formattedText == null)
		{
			return;
		}
		String chatText = Text.removeTags(formattedText);
		if (chatText == null || !chatText.contains("[Live ON]"))
		{
			return;
		}

		Matcher urls = URL_PATTERN.matcher(chatText);
		List<String> addedUrls = new ArrayList<>();
		int menuPosition = 1;
		while (urls.find())
		{
			String url = validChatUrl(urls.group());
			if (url == null || addedUrls.contains(url))
			{
				continue;
			}
			addedUrls.add(url);
			client.getMenu().createMenuEntry(menuPosition++)
				.setOption("Open link")
				.setTarget(url)
				.setType(MenuAction.RUNELITE)
				.onClick(entry -> LinkBrowser.browse(url));
		}
	}

	private static String validChatUrl(String candidate)
	{
		String url = candidate;
		while (!url.isEmpty() && ".,;:!?)]}".indexOf(url.charAt(url.length() - 1)) >= 0)
		{
			url = url.substring(0, url.length() - 1);
		}
		HttpUrl parsed = HttpUrl.parse(url);
		return parsed != null && ("http".equals(parsed.scheme()) || "https".equals(parsed.scheme())) ? url : null;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION
			&& event.getType() != ChatMessageType.CLAN_MESSAGE)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage()).replace('\u00A0', ' ').trim();
		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			PendingAllowlistedDrop valuableDrop = allowlistedValuableDrop(message);
			if (valuableDrop != null)
			{
				scheduleAllowlistedDropFallback(valuableDrop);
			}
			else
			{
				String collectionItem = allowlistedCollectionItem(message);
				if (collectionItem != null)
				{
					scheduleAllowlistedDropFallback(new PendingAllowlistedDrop(collectionItem, 1, null));
				}
			}
		}
		if (event.getType() == ChatMessageType.GAMEMESSAGE && PET_TRIGGER_PATTERN.matcher(message).matches())
		{
			if (config.discordDropsEnabled() && client.getLocalPlayer() != null)
			{
				pendingPet = true;
				pendingPetName = null;
				pendingPetMilestone = null;
				pendingPetGameMessage = message;
				pendingPetDuplicate = message.toLowerCase(java.util.Locale.ROOT).contains("would have been");
				pendingPetBackpack = message.toLowerCase(java.util.Locale.ROOT).contains("backpack");
				pendingPetPreviouslyOwned = pendingPetDuplicate ? Boolean.TRUE : null;
				pendingPetTicks = 0;
			}
			return;
		}
		if (!pendingPet)
		{
			return;
		}
		Matcher itemMatcher = PET_UNTRADEABLE_PATTERN.matcher(message);
		if (!itemMatcher.find())
		{
			itemMatcher = PET_COLLECTION_PATTERN.matcher(message);
		}
		if (itemMatcher.find(0))
		{
			pendingPetName = itemMatcher.group(1).trim();
			if (PET_COLLECTION_PATTERN.matcher(message).find())
			{
				pendingPetPreviouslyOwned = Boolean.FALSE;
			}
		}
		if (message.toLowerCase(java.util.Locale.ROOT).contains("automatically insured"))
		{
			pendingPetPreviouslyOwned = Boolean.FALSE;
		}
		Matcher clanMatcher = PET_CLAN_PATTERN.matcher(message);
		if (clanMatcher.find() && client.getLocalPlayer() != null
			&& WomMembership.normalizePlayerName(clanMatcher.group("user"))
				.equals(WomMembership.normalizePlayerName(client.getLocalPlayer().getName())))
		{
			String pet = clanMatcher.group("pet");
			pendingPetName = (pet == null ? clanMatcher.group("petOnly") : pet).trim();
			String milestone = clanMatcher.group("milestone");
			pendingPetMilestone = milestone == null ? null : milestone.replaceFirst("\\.$", "").trim();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != ADD_CHATBOX_MESSAGE_SCRIPT)
		{
			return;
		}
		Object[] objectStack = client.getObjectStack();
		int objectStackSize = client.getObjectStackSize();
		decorateAchievementMessage(objectStack, objectStackSize);
		if (objectStackSize < 2 || !(objectStack[1] instanceof String))
		{
			return;
		}
		String sender = (String) objectStack[1];
		String plainSender = Text.removeTags(sender).trim();
		if (!plainSender.endsWith(":"))
		{
			return;
		}
		String playerKey = normalizeChatPlayerName(
			plainSender.substring(0, plainSender.length() - 1));
		boolean isMvp = mvpMembers.contains(playerKey);
		boolean isLive = config.liveStatusEnabled() && onlineLiveChannels.containsKey(playerKey);
		String tagBadges = clanTagBadges(playerKey);
		if (!isMvp && !isLive && tagBadges.isEmpty())
		{
			return;
		}
		StringBuilder badges = new StringBuilder();
		if (isMvp)
		{
			badges.append(" <col=ffc628>MVP</col>");
		}
		if (isLive)
		{
			badges.append(" <col=96ffaa>LIVE</col>");
		}
		badges.append(tagBadges);
		int colonIndex = sender.lastIndexOf(':');
		if (badges.length() > 0 && colonIndex >= 0 && !sender.contains(badges.toString()))
		{
			// Keep the native clan rank at the beginning and place our optional
			// badges after the sender name, immediately before the chat colon.
			objectStack[1] = sender.substring(0, colonIndex) + badges
				+ sender.substring(colonIndex);
		}
	}

	private void decorateAchievementMessage(Object[] objectStack, int objectStackSize)
	{
		if (objectStackSize < 3 || !(objectStack[2] instanceof String))
		{
			return;
		}
		String message = (String) objectStack[2];
		String plainMessage = Text.removeTags(message).replace('\u00A0', ' ').trim();
		String lowerMessage = plainMessage.toLowerCase(java.util.Locale.ROOT);
		if (!(lowerMessage.contains(" received a new collection log item:")
			|| lowerMessage.contains(" has a funny feeling like")
			|| lowerMessage.contains(" feels something weird sneaking into")))
		{
			return;
		}

		java.util.Set<String> decoratedPlayers = new java.util.HashSet<>(mvpMembers);
		decoratedPlayers.addAll(clanTagsByPlayer.keySet());
		if (config.liveStatusEnabled())
		{
			decoratedPlayers.addAll(onlineLiveChannels.keySet());
		}
		for (String playerKey : decoratedPlayers)
		{
			if (!lowerMessage.startsWith(playerKey + " "))
			{
				continue;
			}
			boolean isMvp = mvpMembers.contains(playerKey);
			boolean isLive = config.liveStatusEnabled() && onlineLiveChannels.containsKey(playerKey);
			StringBuilder badges = new StringBuilder();
			if (isMvp) badges.append(" <col=ffc628>MVP</col>");
			if (isLive) badges.append(" <col=96ffaa>LIVE</col>");
			badges.append(clanTagBadges(playerKey));
			int insertionIndex = originalIndexAfterVisiblePrefix(message, playerKey);
			if (badges.length() > 0 && insertionIndex >= 0)
			{
				objectStack[2] = message.substring(0, insertionIndex) + badges
					+ message.substring(insertionIndex);
			}
			return;
		}
	}

	private static int originalIndexAfterVisiblePrefix(String text, String visiblePrefix)
	{
		int originalIndex = 0;
		int visibleIndex = 0;
		while (originalIndex < text.length() && visibleIndex < visiblePrefix.length())
		{
			if (text.charAt(originalIndex) == '<')
			{
				int tagEnd = text.indexOf('>', originalIndex);
				if (tagEnd < 0) return -1;
				originalIndex = tagEnd + 1;
				continue;
			}
			char actual = text.charAt(originalIndex) == '\u00A0' ? ' ' : text.charAt(originalIndex);
			if (Character.toLowerCase(actual) != Character.toLowerCase(visiblePrefix.charAt(visibleIndex)))
			{
				return -1;
			}
			originalIndex++;
			visibleIndex++;
		}
		return visibleIndex == visiblePrefix.length() ? originalIndex : -1;
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		notifyDiscordDrop(event.getNpc().getName(), event.getItems(), "NPC", event.getNpc().getId());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() != LootRecordType.NPC && event.getType() != LootRecordType.PLAYER)
		{
			notifyDiscordDrop(event.getName(), event.getItems(), event.getType().name(), null);
		}
	}

	private void notifyDiscordDrop(String source, Collection<ItemStack> items, String category, Integer npcId)
	{
		notifyDiscordDrop(source, items, category, npcId, null);
	}

	private void notifyDiscordDrop(String source, Collection<ItemStack> items, String category,
		Integer npcId, Long singleItemValueOverride)
	{
		if (items.isEmpty() || isTemporaryLootWorld()) return;
		long totalValue = 0;
		for (ItemStack item : items)
		{
			long value = effectiveDropValue(item, items.size(), singleItemValueOverride);
			totalValue += value;
			String itemName = itemManager.getItemComposition(item.getId()).getName();
			if (matchesDiscordFilter(DROP_ITEM_ALLOWLIST, itemName))
			{
				recentAllowlistedLootTicks.put(normalizeDropFilterValue(itemName), client.getTickCount());
			}
		}
		if (config.statsEnabled() && totalValue >= 1_000_000L)
		{
			submitDropStats(items, source, singleItemValueOverride);
		}
		if (matchesDiscordFilter(DISCORD_SOURCE_DENYLIST, source))
		{
			log.debug("Discord drop skipped for denied source: {}", source);
			return;
		}
		long minimumValue = Math.max(0, config.discordDropMinimumValue());
		if (!config.discordDropsEnabled())
		{
			return;
		}
		List<String> notableItems = new ArrayList<>();
		List<Map<String, Object>> dinkItems = new ArrayList<>();
		long notifiedValue = 0L;
		Double rarestProbability = null;
		int thumbnailItemId = -1;
		for (ItemStack item : items)
		{
			long value = effectiveDropValue(item, items.size(), singleItemValueOverride);
			String itemName = itemManager.getItemComposition(item.getId()).getName();
			boolean denied = matchesDiscordFilter(DISCORD_ITEM_DENYLIST, itemName);
			boolean allowed = matchesDiscordFilter(DROP_ITEM_ALLOWLIST, itemName);
			if (denied || (value < minimumValue && !allowed))
			{
				continue;
			}
			if (thumbnailItemId < 0)
			{
				thumbnailItemId = item.getId();
			}
			notifiedValue += value;
			java.util.OptionalDouble itemRarity = dropRarityService.getRarity(source, item.getId(), item.getQuantity());
			Double rarity = itemRarity.isPresent() ? itemRarity.getAsDouble() : null;
			if (rarity != null && (rarestProbability == null || rarity < rarestProbability))
			{
				rarestProbability = rarity;
			}
			String displayName = item.getQuantity() + "x " + itemName;
			notableItems.add(discordWikiLink(displayName, itemName) + " (" + formatDropValue(value) + ")");
			Map<String, Object> dinkItem = new LinkedHashMap<>();
			dinkItem.put("id", item.getId());
			dinkItem.put("quantity", item.getQuantity());
			dinkItem.put("priceEach", singleItemValueOverride != null && items.size() == 1
				? Math.max(0L, singleItemValueOverride) / Math.max(1, item.getQuantity())
				: itemManager.getItemPrice(item.getId()));
			dinkItem.put("name", itemName);
			List<String> criteria = new ArrayList<>();
			if (value >= minimumValue) criteria.add("VALUE");
			if (allowed) criteria.add("ALLOWLIST");
			dinkItem.put("criteria", criteria);
			dinkItem.put("rarity", rarity);
			dinkItems.add(dinkItem);
		}
		if (notableItems.isEmpty())
		{
			return;
		}
		String playerName = client.getLocalPlayer() == null ? "Jogador" : client.getLocalPlayer().getName();
		String sourceName = source == null ? "Loot" : source;
		String description = String.join("\n", notableItems) + "\n" + discordWikiLink(sourceName, sourceName);
		final int dropThumbnailItemId = thumbnailItemId;
		final long dropTotalValue = notifiedValue;
		final Double dropRarestProbability = rarestProbability;
		final Integer dropKillCount = readDropKillCount(category, sourceName);
		drawManager.requestNextFrameListener(image -> sendDiscordDrop(playerName, description,
			dropThumbnailItemId, sourceName, category, npcId, dinkItems, dropTotalValue,
			dropKillCount, dropRarestProbability, image));
	}

	private long effectiveDropValue(ItemStack item, int itemCount, Long singleItemValueOverride)
	{
		if (singleItemValueOverride != null && itemCount == 1)
		{
			return Math.max(0L, singleItemValueOverride);
		}
		return (long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
	}

	static boolean matchesDiscordFilter(List<String> filters, String value)
	{
		if (value == null) return false;
		String normalized = value.replace('\u00A0', ' ').trim().toLowerCase(java.util.Locale.ROOT);
		for (String filter : filters)
		{
			if (filter == null) continue;
			String rule = filter.trim().toLowerCase(java.util.Locale.ROOT);
			if (rule.endsWith("*") && normalized.startsWith(rule.substring(0, rule.length() - 1)))
			{
				return true;
			}
			if (normalized.equals(rule))
			{
				return true;
			}
		}
		return false;
	}

	static String allowlistedCollectionItem(String message)
	{
		if (message == null) return null;
		Matcher matcher = PET_COLLECTION_PATTERN.matcher(message.replace('\u00A0', ' ').trim());
		if (!matcher.find()) return null;
		String itemName = matcher.group(1).trim().replaceFirst("\\.$", "");
		return matchesDiscordFilter(DROP_ITEM_ALLOWLIST, itemName) ? itemName : null;
	}

	static PendingAllowlistedDrop allowlistedValuableDrop(String message)
	{
		if (message == null) return null;
		Matcher matcher = VALUABLE_DROP_PATTERN.matcher(message.replace('\u00A0', ' ').trim());
		if (!matcher.find()) return null;
		String itemName = matcher.group(2).trim();
		if (!matchesDiscordFilter(DROP_ITEM_ALLOWLIST, itemName)) return null;
		try
		{
			int quantity = matcher.group(1) == null ? 1 : Integer.parseInt(matcher.group(1));
			long totalValue = Long.parseLong(matcher.group(3).replace(",", ""));
			return new PendingAllowlistedDrop(itemName, quantity, totalValue);
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	private static String normalizeDropFilterValue(String value)
	{
		return value == null ? "" : value.replace('\u00A0', ' ').trim().toLowerCase(java.util.Locale.ROOT);
	}

	private void scheduleAllowlistedDropFallback(PendingAllowlistedDrop drop)
	{
		if ((!config.discordDropsEnabled() && !config.statsEnabled()) || isTemporaryLootWorld()) return;
		String itemKey = normalizeDropFilterValue(drop.itemName);
		PendingAllowlistedDrop existing = pendingAllowlistedDrops.get(itemKey);
		if (existing != null)
		{
			if (drop.totalValue != null)
			{
				existing.quantity = drop.quantity;
				existing.totalValue = drop.totalValue;
			}
			return;
		}
		pendingAllowlistedDrops.put(itemKey, drop);
		int collectionTick = client.getTickCount();
		scheduleAllowlistedDropFallback(itemKey, collectionTick, 3);
	}

	private void scheduleAllowlistedDropFallback(String itemKey, int collectionTick, int ticksRemaining)
	{
		clientThread.invokeLater(() ->
		{
			if (ticksRemaining > 0)
			{
				scheduleAllowlistedDropFallback(itemKey, collectionTick, ticksRemaining - 1);
				return;
			}
			PendingAllowlistedDrop drop = pendingAllowlistedDrops.remove(itemKey);
			if (drop == null || panel == null) return;
			Integer normalLootTick = recentAllowlistedLootTicks.get(itemKey);
			if (normalLootTick != null && normalLootTick >= collectionTick - 2)
			{
				log.debug("Allowlisted fallback skipped after normal loot event: {}", drop.itemName);
				return;
			}
			ItemStack resolved = resolveCollectionLogItem(drop.itemName);
			if (resolved == null)
			{
				log.debug("Unable to resolve allowlisted item: {}", drop.itemName);
				return;
			}
			ItemStack item = new ItemStack(resolved.getId(), drop.quantity);
			String source = drop.totalValue == null ? "Collection Log" : "Valuable drop";
			log.debug("Using chat fallback for allowlisted item: {}", drop.itemName);
			notifyDiscordDrop(source, java.util.Collections.singletonList(item),
				"COLLECTION_LOG", null, drop.totalValue);
		});
	}

	private ItemStack resolveCollectionLogItem(String itemName)
	{
		for (net.runelite.http.api.item.ItemPrice candidate : itemManager.search(itemName))
		{
			if (candidate.getName() != null && candidate.getName().trim().equalsIgnoreCase(itemName))
			{
				return new ItemStack(candidate.getId(), 1);
			}
		}
		return null;
	}

	private static String discordWikiLink(String label, String search)
	{
		String url = HttpUrl.parse("https://oldschool.runescape.wiki/")
			.newBuilder()
			.addPathSegments("w/Special:Search")
			.addQueryParameter("search", search)
			.build()
			.toString()
			.replace(")", "\\)");
		return "[" + label + "](" + url + ")";
	}

	private boolean isTemporaryLootWorld()
	{
		java.util.EnumSet<WorldType> worldTypes = client.getWorldType();
		return worldTypes.contains(WorldType.SEASONAL)
			|| worldTypes.contains(WorldType.DEADMAN)
			|| worldTypes.contains(WorldType.PVP)
			|| worldTypes.contains(WorldType.BOUNTY)
			|| worldTypes.contains(WorldType.PVP_ARENA)
			|| worldTypes.contains(WorldType.HIGH_RISK)
			|| worldTypes.contains(WorldType.BETA_WORLD)
			|| worldTypes.contains(WorldType.TOURNAMENT_WORLD)
			|| worldTypes.contains(WorldType.NOSAVE_MODE)
			|| worldTypes.contains(WorldType.QUEST_SPEEDRUNNING)
			|| worldTypes.contains(WorldType.LAST_MAN_STANDING)
			|| worldTypes.contains(WorldType.FRESH_START_WORLD);
	}

	private void refreshRanks()
	{
		rankSyncCompleted = true;
		clientThread.invoke(() -> refreshRanksOnClientThread(true));
	}

	private void refreshRanksAutomatically()
	{
		clientThread.invoke(() -> refreshRanksOnClientThread(false));
	}

	private void resetRanks()
	{
		lastCombatAchievementPoints = -1;
		if (!combatAchievementAccount.isEmpty())
		{
			String key = "combatAchievementPoints.v2." + accountCacheKey(combatAchievementAccount);
			configManager.unsetConfiguration("live-on-clan-messages", key);
		}
		combatAchievementAccount = "";
		lastQuestPoints = -1;
		lastMaximumQuestPoints = -1;
		lastObservedRankTotalLevel = -1;
		questAccount = "";
		rankWidgetRefreshPending = false;
		rankSyncCompleted = false;
		rankBankItems.clear();
		rankBankItemIds.clear();
		rankBankAccount = "";
		rankBankLoaded = false;
		if (panel != null) panel.resetRanks();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK)
		{
			if (client.getLocalPlayer() == null) return;
			ensureRankBankAccount(client.getLocalPlayer().getName());
			rankBankItems.clear();
			rankBankItemIds.clear();
			collectItemNames(event.getItemContainer(), rankBankItems);
			collectItemIds(event.getItemContainer(), rankBankItemIds);
			rankBankLoaded = true;
			refreshRanksAutomatically();
		}
		else if (event.getContainerId() == InventoryID.INV
			|| event.getContainerId() == InventoryID.WORN)
		{
			refreshRanksAutomatically();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.CA_POINTS)
		{
			refreshRanksAutomatically();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (client.getLocalPlayer() == null) return;
		int totalLevel = currentTotalLevel();
		if (totalLevel == lastObservedRankTotalLevel) return;
		lastObservedRankTotalLevel = totalLevel;
		refreshRanksAutomatically();
	}

	private int currentTotalLevel()
	{
		int totalLevel = 0;
		for (Skill skill : Skill.values()) totalLevel += client.getRealSkillLevel(skill);
		return totalLevel;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		int groupId = event.getGroupId();
		if (groupId == InterfaceID.ACCOUNT || groupId == InterfaceID.ACCOUNT_SUMMARY_SIDEPANEL
			|| groupId == InterfaceID.QUESTLIST)
		{
			// Quest points can change without a skill-level event. Force a fresh
			// read when the relevant game interfaces become available.
			lastQuestPoints = -1;
			lastMaximumQuestPoints = -1;
		}
		if (groupId == InterfaceID.CA_OVERVIEW || groupId == InterfaceID.CA_TASKS
			|| groupId == InterfaceID.CA_REWARDS || groupId == InterfaceID.CA_BOSSES
			|| groupId == InterfaceID.CA_BOSS
			|| groupId == InterfaceID.ACCOUNT || groupId == InterfaceID.ACCOUNT_SUMMARY_SIDEPANEL
			|| groupId == InterfaceID.QUESTLIST)
		{
			scheduleRankWidgetRefresh();
		}
	}

	/**
	 * Character Summary and Combat Achievements populate their child widgets
	 * after the WidgetLoaded event. Re-read on the following client ticks so
	 * the user does not need to press Synchronize just to obtain the values.
	 */
	private void scheduleRankWidgetRefresh()
	{
		if (rankWidgetRefreshPending) return;
		rankWidgetRefreshPending = true;
		clientThread.invokeLater(() ->
		{
			rankWidgetRefreshPending = false;
			refreshRanksOnClientThread(false);
			clientThread.invokeLater(() ->
			{
				refreshRanksOnClientThread(false);
				clientThread.invokeLater(() -> refreshRanksOnClientThread(false));
			});
		});
	}

	private void refreshRanksOnClientThread(boolean explicitSync)
	{
		if (panel == null || client.getLocalPlayer() == null) return;
		String accountName = client.getLocalPlayer().getName();
		ensureRankBankAccount(accountName);
		java.util.Set<String> items = new java.util.HashSet<>();
		java.util.Set<Integer> itemIds = new java.util.HashSet<>();
		collectItemNames(client.getItemContainer(InventoryID.INV), items);
		collectItemNames(client.getItemContainer(InventoryID.WORN), items);
		collectItemIds(client.getItemContainer(InventoryID.INV), itemIds);
		collectItemIds(client.getItemContainer(InventoryID.WORN), itemIds);
		if (rankBankLoaded)
		{
			items.addAll(rankBankItems);
			itemIds.addAll(rankBankItemIds);
		}
		log.debug("Ranks sync inventory/equipment item ids: {}", itemIds);
		int totalLevel = currentTotalLevel();
		lastObservedRankTotalLevel = totalLevel;
		java.util.List<String> checks = new ArrayList<>();
		boolean zukHelm = containsAnyItemId(itemIds, ItemID.SLAYER_HELM_ZUK, ItemID.SLAYER_HELM_I_ZUK,
			ItemID.SW_SLAYER_HELM_I_ZUK, ItemID.PVPA_SLAYER_HELM_I_ZUK);
		boolean tzTokHelm = containsAnyItemId(itemIds, ItemID.SLAYER_HELM_JAD, ItemID.SLAYER_HELM_I_JAD,
			ItemID.SW_SLAYER_HELM_I_JAD, ItemID.PVPA_SLAYER_HELM_I_JAD);
		boolean vampyricHelm = containsAnyItemId(itemIds, ItemID.SLAYER_HELM_VERZIK, ItemID.SLAYER_HELM_I_VERZIK,
			ItemID.SW_SLAYER_HELM_I_VERZIK, ItemID.PVPA_SLAYER_HELM_I_VERZIK);
		boolean infernalMaxCape = containsAnyItemId(itemIds,
			ItemID.SKILLCAPE_MAX_INFERNALCAPE,
			ItemID.SKILLCAPE_MAX_INFERNALCAPE_BROKEN,
			ItemID.SKILLCAPE_MAX_INFERNALCAPE_TROUVER,
			ItemID.SKILLCAPE_MAX_INFERNALCAPE_TROUVER_BROKEN,
			ItemID.SKILLCAPE_MAX_INFERNALCAPE_TROUVER_MANGLED);
		boolean dizanasMaxCape = containsAnyItemId(itemIds,
			ItemID.SKILLCAPE_MAX_DIZANAS,
			ItemID.SKILLCAPE_MAX_DIZANAS_BROKEN,
			ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER,
			ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER_BROKEN,
			ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER_MANGLED);
		boolean infernalCape = containsAnyItemId(itemIds, ItemID.INFERNAL_CAPE, ItemID.INFERNAL_CAPE_BROKEN)
			|| infernalMaxCape
			|| zukHelm || containsItemVariant(items, "Infernal cape");
		boolean fireCape = containsItemId(itemIds, ItemID.TZHAAR_CAPE_FIRE)
			|| containsItemVariant(items, "Fire cape") || infernalCape;
		boolean quiver = containsAnyItemId(itemIds, ItemID.DIZANAS_QUIVER_UNCHARGED, ItemID.DIZANAS_QUIVER_CHARGED,
			ItemID.DIZANAS_QUIVER_INFINITE, ItemID.DIZANAS_QUIVER_UNCHARGED_TROUVER,
			ItemID.DIZANAS_QUIVER_CHARGED_TROUVER, ItemID.DIZANAS_QUIVER_INFINITE_TROUVER)
			|| dizanasMaxCape || zukHelm || containsItemVariant(items, "Dizana's quiver");
		if (!accountName.equals(questAccount))
		{
			questAccount = accountName;
			lastQuestPoints = -1;
			lastMaximumQuestPoints = -1;
		}
		int questPoints = explicitSync || lastQuestPoints < 0 ? readQuestPoints() : lastQuestPoints;
		if (questPoints >= 0) lastQuestPoints = questPoints;
		else questPoints = lastQuestPoints;
		String accountKey = "combatAchievementPoints.v2." + accountCacheKey(accountName);
		if (!accountName.equals(combatAchievementAccount))
		{
			combatAchievementAccount = accountName;
			lastCombatAchievementPoints = storedInteger(accountKey, -1);
		}
		int combatAchievementPoints = readCombatAchievementPoints();
		if (combatAchievementPoints >= 0)
		{
			lastCombatAchievementPoints = combatAchievementPoints;
			configManager.setConfiguration("live-on-clan-messages", accountKey, combatAchievementPoints);
		}
		else combatAchievementPoints = lastCombatAchievementPoints;
		log.debug("CA threshold varbits: easy={}, medium={}, hard={}, parsedPoints={}",
			client.getVarbitValue(VarbitID.CA_THRESHOLD_EASY),
			client.getVarbitValue(VarbitID.CA_THRESHOLD_MEDIUM),
			client.getVarbitValue(VarbitID.CA_THRESHOLD_HARD), combatAchievementPoints);
		boolean easyCombatAchievements = combatAchievementPoints >= 41;
		boolean mediumCombatAchievements = combatAchievementPoints >= 161;
		boolean hardCombatAchievements = combatAchievementPoints >= 419;
		boolean eliteCombatAchievements = combatAchievementPoints >= 1075 || tzTokHelm;
		boolean masterCombatAchievements = combatAchievementPoints >= 1945 || vampyricHelm || zukHelm;
		boolean grandmasterCombatAchievements = combatAchievementPoints >= 2671 || zukHelm;
		boolean questCape = (lastMaximumQuestPoints > 0 && questPoints >= lastMaximumQuestPoints)
			|| containsAnyItemId(itemIds, ItemID.SKILLCAPE_QP, ItemID.SKILLCAPE_QP_TRIMMED)
			|| containsAnyItem(items, "Quest point cape", "Quest point cape (t)");
		boolean diaryCape = containsAnyItemId(itemIds, ItemID.SKILLCAPE_AD, ItemID.SKILLCAPE_AD_TRIMMED)
			|| containsItem(items, "Achievement diary cape");
		boolean maxCapeItem = containsAnyItemId(itemIds, ItemID.SKILLCAPE_MAX, ItemID.SKILLCAPE_MAX_WORN)
			|| infernalMaxCape || dizanasMaxCape
			|| containsAnyItem(items, "Max cape", "Max cape (t)");
		boolean maxCape = totalLevel >= 2376 || maxCapeItem;
		int effectiveTotalLevel = maxCapeItem ? Math.max(totalLevel, 2376) : totalLevel;
		int effectiveQuestPoints = questCape ? Math.max(300, questPoints) : questPoints;
		log.debug("Ranks quest detection: questPoints={}, questCape={}", questPoints, questCape);
		checks.add("Total level: " + effectiveTotalLevel + (effectiveTotalLevel >= 2376 ? " ✓ (Max)" : effectiveTotalLevel >= 2300 ? " ✓ (2300+)" : " — requer 2300"));
		checks.add("Quest points: " + (questPoints < 0 ? "— abra o Character Summary e sincronize" : questPoints));
		checks.add("Quest cape (todas as quests): " + (questCape ? "✓" : "— não obtida"));
		checks.add(itemStatus("Fire cape", fireCape));
		checks.add(itemStatus("Infernal cape", infernalCape));
		checks.add(itemStatus("Dizana's quiver", quiver));
		checks.add("Combat Achievements Easy (41 pontos): " + achievementStatus(combatAchievementPoints, 41));
		checks.add("Combat Achievements Medium (161 pontos): " + achievementStatus(combatAchievementPoints, 161));
		checks.add("Combat Achievements Hard (419 pontos): " + achievementStatus(combatAchievementPoints, 419));
		checks.add("Combat Achievements Elite (1075 pontos): " + achievementStatus(combatAchievementPoints, 1075, tzTokHelm, "TzTok slayer helmet"));
		checks.add("Combat Achievements Master (1945 pontos): " + achievementStatus(combatAchievementPoints, 1945, vampyricHelm || zukHelm, "Vampyric slayer helmet"));
		checks.add("Combat Achievements Grandmaster (2671 pontos): " + achievementStatus(combatAchievementPoints, 2671, zukHelm, "TzKal slayer helmet"));
		checks.add("Passo 1: equipe ou coloque as capas no inventário para detectar os itens.");
		checks.add("Passo 2: abra a aba Combat Achievements no jogo para carregar seus pontos.");
		checks.add("Passo 3: abra a categoria/tier desejada e clique novamente em Sincronizar.");
		checks.add("Passo 4: abra a página do Inferno no Collection Log para conferir o registro local.");
		checks.add("EHB não é considerado no cálculo dos ranks.");
		checks.add("Combat Achievements: sincronize com a aba correspondente aberta.");
		String rank = highestPossibleRank(effectiveTotalLevel, effectiveQuestPoints, questCape, fireCape, infernalCape, quiver,
			diaryCape, maxCape, easyCombatAchievements, mediumCombatAchievements, hardCombatAchievements,
			eliteCombatAchievements, masterCombatAchievements, grandmasterCombatAchievements);
		String advice = rankSyncCompleted ? rankAdvice(rank, effectiveTotalLevel, effectiveQuestPoints, questCape,
			fireCape, infernalCape, quiver, diaryCape, maxCape, easyCombatAchievements, mediumCombatAchievements,
			hardCombatAchievements, eliteCombatAchievements, masterCombatAchievements, grandmasterCombatAchievements) : "";
		String displayedRank = rankSyncCompleted ? rank : "não sincronizado";
		String clanRank = currentClanRankTitle(accountName);
		String nextRank = rankSyncCompleted ? nextRegularRankTarget(clanRank, rank) : "em análise";
		java.util.List<String> nextChecks = rankSyncCompleted
			? missingRequirements(requirementsForRank(nextRank, effectiveTotalLevel, questPoints, questCape, fireCape,
				infernalCape, quiver, diaryCape, maxCape, rankBankLoaded, combatAchievementPoints,
				easyCombatAchievements, mediumCombatAchievements, hardCombatAchievements,
				eliteCombatAchievements, masterCombatAchievements, grandmasterCombatAchievements))
			: java.util.Collections.emptyList();
		java.util.List<String> overviewChecks = rankSyncCompleted
			? rankOverviewChecks(effectiveTotalLevel, questPoints, questCape, fireCape, infernalCape, quiver,
				diaryCape, maxCape, rankBankLoaded, combatAchievementPoints, easyCombatAchievements,
				mediumCombatAchievements, hardCombatAchievements, eliteCombatAchievements,
				masterCombatAchievements, grandmasterCombatAchievements)
			: java.util.Collections.emptyList();
		panel.updateRanks(accountName, clanRank, currentClanRankIcon(accountName),
			displayedRank, clanRankIconFor(displayedRank), nextRank, clanRankIconFor(nextRank),
			nextChecks, overviewChecks, advice);
		maybeNotifyAvailableRank(accountName, clanRank, rank,
			rankBankLoaded && questPoints >= 0 && combatAchievementPoints >= 0);
		if (explicitSync) fetchRankRequestStatus();
	}

	private void maybeNotifyAvailableRank(String accountName, String clanRank,
		String eligibleRank, boolean dataComplete)
	{
		if (!config.enabled()) return;
		String accountKey = accountCacheKey(accountName);
		int currentIndex = regularRankIndex(clanRank);
		int eligibleIndex = regularRankIndex(eligibleRank);
		if (!dataComplete || currentIndex < 0 || eligibleIndex < 0)
		{
			return;
		}

		String rankKey = "rankAvailableNotification.rank.v2." + accountKey;
		String timeKey = "rankAvailableNotification.time.v2." + accountKey;
		int notifiedIndex = regularRankIndex(
			configManager.getConfiguration("live-on-clan-messages", rankKey));
		long notifiedAt = storedLong(timeKey, 0L);
		long now = System.currentTimeMillis();
		if (!shouldNotifyAvailableRank(currentIndex, eligibleIndex, notifiedIndex,
			notifiedAt, now, rankRequestStatusKnown && rankRequestPending)) return;

		configManager.setConfiguration("live-on-clan-messages", rankKey, eligibleRank);
		configManager.setConfiguration("live-on-clan-messages", timeKey, now);
		String message = rankNotificationMessage(eligibleRank);
		ChatMessageBuilder builder = new ChatMessageBuilder()
			.append(Color.GREEN, "[Live On] ")
			.append(Color.WHITE, "Promoção de rank disponível: ");
		appendClanRankWithIcon(builder, new Color(255, 184, 0), eligibleRank);
		builder.append(Color.WHITE, "! Solicite pelo plugin do clã.");
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(builder.build())
			.build());
		notifier.notify(message);
	}

	static String rankNotificationMessage(String rank)
	{
		return "[Live On] Promoção de rank disponível: " + rank + "! Solicite pelo plugin do clã.";
	}

	static boolean shouldNotifyAvailableRank(int currentIndex, int eligibleIndex, int notifiedIndex,
		long notifiedAt, long now, boolean requestPending)
	{
		if (requestPending || currentIndex < 0 || eligibleIndex <= currentIndex) return false;
		if (eligibleIndex > notifiedIndex) return true;
		return eligibleIndex == notifiedIndex
			&& (notifiedAt <= 0L || now - notifiedAt >= RANK_NOTIFICATION_COOLDOWN_MILLIS);
	}

	private Icon clanRankIconFor(String displayRank)
	{
		if (displayRank == null || displayRank.trim().isEmpty()) return null;
		String rankKey = displayRank.trim().toLowerCase(java.util.Locale.ROOT);
		// "General" is the clan's final Discord-only rank and uses the native
		// Brigadier emblem. "Major" continues to use the native General title.
		String titleKey = rankKey.startsWith("general")
			? "brigadier"
			: RANK_TITLE_ALIASES.getOrDefault(rankKey, displayRank).trim().toLowerCase(java.util.Locale.ROOT);
		net.runelite.api.clan.ClanSettings settings = client.getClanSettings();
		if (settings == null) return null;
		for (int rankValue = -1; rankValue <= 127; rankValue++)
		{
			ClanTitle title = settings.titleForRank(new ClanRank(rankValue));
			if (title == null || title.getName() == null || !title.getName().trim().toLowerCase(java.util.Locale.ROOT).equals(titleKey)) continue;
			BufferedImage image = chatIconManager.getRankImage(title);
			if (image == null) return null;
			java.awt.Image scaled = image.getScaledInstance(20, 20, java.awt.Image.SCALE_SMOOTH);
			return new ImageIcon(scaled);
		}
		return null;
	}

	private void appendClanRankWithIcon(ChatMessageBuilder builder, Color color, String rankName)
	{
		Integer iconIndex = clanRankChatIconIndex(rankName);
		if (iconIndex != null)
		{
			builder.img(iconIndex).append(" ");
		}
		else
		{
			log.debug("Native clan rank icon not available for {}", rankName);
		}
		builder.append(color, rankName);
	}

	private Integer clanRankChatIconIndex(String displayRank)
	{
		if (displayRank == null || displayRank.trim().isEmpty()) return null;
		String rankKey = displayRank.trim().toLowerCase(java.util.Locale.ROOT);
		String titleKey = rankKey.startsWith("general")
			? "brigadier"
			: RANK_TITLE_ALIASES.getOrDefault(rankKey, displayRank).trim().toLowerCase(java.util.Locale.ROOT);
		net.runelite.api.clan.ClanSettings settings = client.getClanSettings();
		if (settings == null) return null;
		for (int rankValue = -1; rankValue <= 127; rankValue++)
		{
			ClanTitle title = settings.titleForRank(new ClanRank(rankValue));
			if (title == null || title.getName() == null
				|| !title.getName().trim().toLowerCase(java.util.Locale.ROOT).equals(titleKey)) continue;
			int iconIndex = chatIconManager.getIconNumber(title);
			return iconIndex >= 0 ? iconIndex : null;
		}
		return null;
	}

	private Icon currentClanRankIcon(String playerName)
	{
		net.runelite.api.clan.ClanSettings settings = client.getClanSettings();
		if (settings == null) return null;
		net.runelite.api.clan.ClanMember member = settings.findMember(playerName);
		if (member == null || member.getRank() == null) return null;
		ClanTitle title = settings.titleForRank(member.getRank());
		if (title == null) return null;
		BufferedImage image = chatIconManager.getRankImage(title);
		if (image == null) return null;
		java.awt.Image scaled = image.getScaledInstance(20, 20, java.awt.Image.SCALE_SMOOTH);
		return new ImageIcon(scaled);
	}

	private String currentClanRankTitle(String playerName)
	{
		net.runelite.api.clan.ClanSettings settings = client.getClanSettings();
		if (settings == null) return "Carregando…";
		net.runelite.api.clan.ClanMember member = settings.findMember(playerName);
		if (member == null || member.getRank() == null) return "Não identificado";
		ClanTitle title = settings.titleForRank(member.getRank());
		if (title != null && title.getName() != null && !title.getName().trim().isEmpty())
		{
			log.debug("Clan rank detection for {}: rankValue={}, titleId={}, title={}",
				playerName, member.getRank().getRank(), title.getId(), title.getName());
			String name = title.getName().trim();
			// Some clan configurations expose the Brigadier emblem with the textual
			// title "General". Identify the final rank by its native emblem so it is
			// not confused with the clan's Major progression rank.
			if (name.equalsIgnoreCase("general") && clanTitleUsesIcon(settings, title, "brigadier"))
			{
				return "General";
			}
				switch (name.toLowerCase(java.util.Locale.ROOT))
				{
				case "helper": return "Recruta";
				case "recruit": return "Soldado";
				case "private": return "Soldado";
				case "corporal": return "Cabo";
				case "novice": return "Aluno";
				case "sergeant": return "Sargento";
				case "cadet": return "Cadete";
				case "lieutenant": return "Tenente";
				case "captain": return "Capitão";
				case "general": return "Major";
				case "colonel": return "Coronel";
				case "brigadier": return "General";
				default: return name;
			}
		}
		ClanRank rank = member.getRank();
		if (rank.equals(ClanRank.OWNER)) return "Owner";
		if (rank.equals(ClanRank.DEPUTY_OWNER)) return "Deputy Owner";
		if (rank.equals(ClanRank.ADMINISTRATOR)) return "Administrador";
		if (rank.equals(ClanRank.GUEST)) return "Recruta";
		return "Rank " + rank.getRank();
	}

	private boolean clanTitleUsesIcon(net.runelite.api.clan.ClanSettings settings,
		net.runelite.api.clan.ClanTitle currentTitle, String expectedTitleName)
	{
		BufferedImage currentImage = chatIconManager.getRankImage(currentTitle);
		if (currentImage == null) return false;
		for (int rankValue = -1; rankValue <= 127; rankValue++)
		{
			ClanTitle candidate = settings.titleForRank(new ClanRank(rankValue));
			if (candidate == null || candidate.getName() == null
				|| !candidate.getName().trim().equalsIgnoreCase(expectedTitleName))
			{
				continue;
			}
			BufferedImage candidateImage = chatIconManager.getRankImage(candidate);
			return sameImage(currentImage, candidateImage);
		}
		return false;
	}

	private static boolean sameImage(BufferedImage first, BufferedImage second)
	{
		if (first == null || second == null || first.getWidth() != second.getWidth()
			|| first.getHeight() != second.getHeight())
		{
			return false;
		}
		for (int y = 0; y < first.getHeight(); y++)
		{
			for (int x = 0; x < first.getWidth(); x++)
			{
				if (first.getRGB(x, y) != second.getRGB(x, y)) return false;
			}
		}
		return true;
	}

	private static final java.util.List<String> REGULAR_RANKS = java.util.Arrays.asList(
		"recruta", "soldado", "cabo", "aluno", "sargento", "cadete",
		"tenente", "capitão", "major", "coronel");

	private static int regularRankIndex(String rank)
	{
		String normalized = rank == null ? "" : rank.trim().toLowerCase(java.util.Locale.ROOT);
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
		for (int index = 0; index < REGULAR_RANKS.size(); index++)
		{
			if (normalized.equals(REGULAR_RANKS.get(index))) return index;
		}
		return -1;
	}

	private static String nextRegularRankTarget(String currentRank, String eligibleRank)
	{
		int current = regularRankIndex(currentRank);
		int eligible = regularRankIndex(eligibleRank);
		if (current < 0) return "Cargo especial";
		int base = Math.max(current, eligible);
		if (base >= REGULAR_RANKS.size() - 1) return "General — somente via Discord";
		String target = REGULAR_RANKS.get(base + 1);
		return target.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + target.substring(1);
	}

	private static java.util.List<String> missingRequirements(java.util.List<String> requirements)
	{
		java.util.List<String> missing = new ArrayList<>();
		for (String requirement : requirements)
		{
			if (!requirement.trim().startsWith("✓")) missing.add(requirement);
		}
		return missing;
	}

	private static java.util.List<String> rankOverviewChecks(int totalLevel, int questPoints, boolean questCape,
		boolean fireCape, boolean infernalCape, boolean quiver, boolean diaryCape, boolean maxCape,
		boolean bankLoaded, int combatAchievementPoints, boolean easy, boolean medium, boolean hard,
		boolean elite, boolean master, boolean grandmaster)
	{
		java.util.List<String> result = new ArrayList<>();
		if (!bankLoaded) result.add("! Abra o banco uma vez para verificar todos os itens");
		result.add(pointsRequirement("Quest points", questPoints, 300, "aguarde o login carregar"));
		result.add(itemRequirement("Quest cape", questCape, bankLoaded));
		result.add(itemRequirement("Fire cape", fireCape, bankLoaded));
		result.add(itemRequirement("Infernal cape", infernalCape, bankLoaded));
		result.add(itemRequirement("Dizana's quiver", quiver, bankLoaded));
		result.add(itemRequirement("Diary cape", diaryCape, bankLoaded));
		if (maxCape)
		{
			result.add("✓ Max cape / 2376 total atendido");
		}
		else
		{
			result.add("✕ Total level: " + totalLevel + "/2376 — equivalente à Max cape");
		}
		String caTier = grandmaster ? "Grandmaster" : master ? "Master" : elite ? "Elite"
			: hard ? "Hard" : medium ? "Medium" : easy ? "Easy" : "Nenhum tier";
		if (combatAchievementPoints < 0)
			result.add("! Combat Achievements ainda não carregado");
		else
			result.add((easy ? "✓ " : "✕ ") + "Combat Achievements: " + caTier
				+ " (" + combatAchievementPoints + " pontos)");
		return result;
	}

	private static java.util.List<String> requirementsForRank(String rank, int totalLevel, int questPoints,
		boolean questCape, boolean fireCape, boolean infernalCape, boolean quiver, boolean diaryCape,
		boolean maxCape, boolean bankLoaded, int combatAchievementPoints, boolean easy, boolean medium, boolean hard,
		boolean elite, boolean master, boolean grandmaster)
	{
		java.util.List<String> result = new ArrayList<>();
		if (rank == null) return result;
		switch (rank.toLowerCase(java.util.Locale.ROOT))
		{
			case "soldado":
				result.add("! Promoção automática após 30 dias no clã");
				break;
			case "cabo":
				result.add(pointsRequirement("Quest points", questPoints, 200, "abra o Character Summary"));
				result.add(itemRequirement("Fire cape", fireCape, bankLoaded));
				break;
			case "aluno":
				result.add(pointsRequirement("Quest points", questPoints, 250, "abra o Character Summary"));
				result.add(itemRequirement("Fire cape", fireCape, bankLoaded));
				result.add(caRequirement("Combat Achievements Easy", combatAchievementPoints, 41, easy));
				break;
			case "sargento":
				result.add(pointsRequirement("Quest points", questPoints, 300, "abra o Character Summary"));
				result.add(itemRequirement("Fire cape", fireCape, bankLoaded));
				result.add(caRequirement("Combat Achievements Medium", combatAchievementPoints, 161, medium));
				break;
			case "cadete":
				result.add(itemRequirement("Quest cape", questCape, bankLoaded));
				result.add(itemRequirement("Fire cape", fireCape, bankLoaded));
				result.add(caRequirement("Combat Achievements Hard", combatAchievementPoints, 419, hard));
				break;
			case "tenente":
				result.add(itemRequirement("Quest cape", questCape, bankLoaded));
				result.add(itemRequirement("Dizana's quiver ou Infernal cape", quiver || infernalCape, bankLoaded));
				result.add(caRequirement("Combat Achievements Elite", combatAchievementPoints, 1075, elite));
				break;
			case "capitão":
				result.add(itemRequirement("Diary cape", diaryCape, bankLoaded));
				result.add(itemRequirement("Dizana's quiver", quiver, bankLoaded));
				result.add(itemRequirement("Infernal cape", infernalCape, bankLoaded));
				result.add(caRequirement("Combat Achievements Master", combatAchievementPoints, 1945, master));
				break;
			case "major":
				result.add(itemRequirement("Diary cape", diaryCape, bankLoaded));
				result.add(itemRequirement("Dizana's quiver", quiver, bankLoaded));
				result.add(itemRequirement("Infernal cape", infernalCape, bankLoaded));
				result.add(pointsRequirement("Total level", totalLevel, 2300, "entre no jogo"));
				result.add(caRequirement("Combat Achievements Master", combatAchievementPoints, 1945, master));
				break;
			case "coronel":
				result.add(itemRequirement("Diary cape", diaryCape, bankLoaded));
				result.add(itemRequirement("Max cape ou 2376 total", maxCape, bankLoaded));
				result.add(caRequirement("Combat Achievements Grandmaster", combatAchievementPoints, 2671, grandmaster));
				break;
			default:
				break;
		}
		return result;
	}

	private static String pointsRequirement(String label, int value, int required, String unknownInstruction)
	{
		if (value < 0) return "! " + label + ": não verificado — " + unknownInstruction;
		return (value >= required ? "✓ " : "✕ ") + label + ": " + value + "/" + required;
	}

	private static String itemRequirement(String label, boolean detected, boolean bankLoaded)
	{
		if (detected) return "✓ " + label + " detectado";
		return bankLoaded ? "✕ " + label + " não encontrado — coloque no inventário ou equipe se possuir"
			: "! " + label + " ainda não verificado — abra o banco";
	}

	private static String caRequirement(String label, int points, int required, boolean completed)
	{
		if (completed) return "✓ " + label + (points >= 0 ? ": " + points + "/" + required : " detectado");
		if (points < 0) return "! " + label + ": abra o menu Combat Achievements";
		return "✕ " + label + ": " + points + "/" + required;
	}

	private int readCombatAchievementPoints()
	{
		// CA_POINTS is server-backed and available without opening the Combat
		// Achievements interface. Prefer it over parsing transient widgets.
		int automaticPoints = Math.max(
			client.getVarbitValue(VarbitID.CA_POINTS),
			client.getServerVarbitValue(VarbitID.CA_POINTS));
		if (automaticPoints >= 0) return automaticPoints;
		Widget caRewards = client.getWidget(InterfaceID.CA_REWARDS);
		int points = parseCombatAchievementPoints(caRewards);
		if (points >= 0) return points;
		Widget caOverview = client.getWidget(InterfaceID.CA_OVERVIEW);
		points = parseCombatAchievementPoints(caOverview);
		if (points >= 0) return points;
		Widget caTasks = client.getWidget(InterfaceID.CA_TASKS);
		points = parseCombatAchievementPoints(caTasks);
		if (points >= 0) return points;
		Widget summary = client.getWidget(InterfaceID.ACCOUNT_SUMMARY_SIDEPANEL);
		points = parseCombatAchievementPoints(summary);
		if (points >= 0) return points;
		Widget combatAchievements = client.getWidget(InterfaceID.COMBAT_INTERFACE);
		points = parseCombatAchievementPoints(combatAchievements);
		if (points >= 0) return points;
		Widget[] roots = client.getWidgetRoots();
		if (roots != null) for (Widget root : roots)
		{
			points = parseCombatAchievementPoints(root);
			if (points >= 0) return points;
		}
		return -1;
	}

	private int parseCombatAchievementPoints(Widget root)
	{
		List<String> texts = new ArrayList<>();
		collectWidgetTexts(root, texts);
		for (String text : texts)
		{
			String normalized = text.toLowerCase(java.util.Locale.ROOT);
			if (!normalized.contains("point")) continue;
			Matcher direct = Pattern.compile("(?i)(?:combat achievements?\\s+)?total\\s+points?\\s*[:\\-]\\s*([0-9][0-9,]*)").matcher(text);
			if (direct.find()) return Integer.parseInt(direct.group(1).replace(",", ""));
			Matcher combatPoints = Pattern.compile("(?i)combat achievements?\\s+points?\\s*[:\\-]\\s*([0-9][0-9,]*)").matcher(text);
			if (combatPoints.find()) return Integer.parseInt(combatPoints.group(1).replace(",", ""));
			Matcher reverse = Pattern.compile("(?i)([0-9][0-9,]*)\\s*(?:combat achievements?\\s*)?points?").matcher(text);
			if (reverse.find() && normalized.contains("combat"))
				return Integer.parseInt(reverse.group(1).replace(",", ""));
		}
		log.debug("Combat Achievement points not found in visible widgets");
		return -1;
	}

	private void collectWidgetTexts(Widget widget, List<String> texts)
	{
		if (widget == null) return;
		if (widget.getText() != null && !widget.getText().trim().isEmpty()) texts.add(widget.getText().trim());
		Widget[] children = widget.getChildren();
		if (children != null) for (Widget child : children) collectWidgetTexts(child, texts);
		Widget[] dynamicChildren = widget.getDynamicChildren();
		if (dynamicChildren != null) for (Widget child : dynamicChildren) collectWidgetTexts(child, texts);
		Widget[] staticChildren = widget.getStaticChildren();
		if (staticChildren != null) for (Widget child : staticChildren) collectWidgetTexts(child, texts);
		Widget[] nestedChildren = widget.getNestedChildren();
		if (nestedChildren != null) for (Widget child : nestedChildren) collectWidgetTexts(child, texts);
	}

	private int readQuestPoints()
	{
		// Quest ids map to rows in the game's Quest DB table. Sum each finished
		// quest's point reward so no interface needs to be opened and new quests
		// automatically contribute to both the earned and maximum totals.
		int earnedPoints = 0;
		int maximumPoints = 0;
		int readableRows = 0;
		for (Quest quest : Quest.values())
		{
			Object[] values;
			try
			{
				values = client.getDBTableField(
					quest.getId(), DBTableID.Quest.COL_QUESTPOINTS, 0);
			}
			catch (IllegalArgumentException exception)
			{
				log.debug("Quest DB row {} is not readable", quest.getId());
				continue;
			}
			int reward = firstInteger(values, -1);
			if (reward < 0) continue;
			readableRows++;
			maximumPoints += reward;
			boolean finished = false;
			if (reward > 0)
			{
				try
				{
					finished = quest.getState(client) == QuestState.FINISHED;
				}
				catch (RuntimeException exception)
				{
					log.debug("Unable to read quest state for {}", quest.getName());
				}
			}
			if (finished)
			{
				earnedPoints += reward;
			}
		}
		if (readableRows > 0)
		{
			lastMaximumQuestPoints = maximumPoints;
			log.debug("Ranks automatic quest points: {}/{} from {} quest rows",
				earnedPoints, maximumPoints, readableRows);
			return earnedPoints;
		}
		Widget questListPoints = client.getWidget(InterfaceID.Questlist.QUESTPOINTS);
		int points = parseQuestPoints(questListPoints, true);
		if (points >= 0) return points;
		Widget account = client.getWidget(InterfaceID.ACCOUNT);
		points = parseQuestPoints(account, false);
		if (points >= 0) return points;
		Widget summary = client.getWidget(InterfaceID.ACCOUNT_SUMMARY_SIDEPANEL);
		return parseQuestPoints(summary, false);
	}

	private static int firstInteger(Object[] values, int fallback)
	{
		if (values == null) return fallback;
		for (Object value : values)
		{
			if (value instanceof Number) return ((Number) value).intValue();
		}
		return fallback;
	}

	private int parseQuestPoints(Widget root, boolean allowNumberOnly)
	{
		List<String> texts = new ArrayList<>();
		collectWidgetTexts(root, texts);
		for (int i = 0; i < texts.size(); i++)
		{
			String text = texts.get(i);
			String normalized = text.toLowerCase(java.util.Locale.ROOT);
			if (normalized.contains("quests completed") || normalized.startsWith("completed")) continue;
			Matcher numberOnlySameLine = Pattern.compile("^\\s*([0-9][0-9,]*)\\s*$").matcher(text);
			if (allowNumberOnly && numberOnlySameLine.find())
				return Integer.parseInt(numberOnlySameLine.group(1).replace(",", ""));
			Matcher fractionSameLine = Pattern.compile("^\\s*([0-9][0-9,]*)\\s*/\\s*([0-9][0-9,]*)\\s*$").matcher(text);
			if (allowNumberOnly && fractionSameLine.find())
				return Integer.parseInt(fractionSameLine.group(1).replace(",", ""));
			Matcher sameLine = Pattern.compile("(?i)(?:quest points?|qp)\\s*[:\\-]?\\s*([0-9][0-9,]*)").matcher(text);
			if (sameLine.find()) return Integer.parseInt(sameLine.group(1).replace(",", ""));
			Matcher reverseLine = Pattern.compile("(?i)([0-9][0-9,]*)\\s*(?:quest points?|qp)\\b").matcher(text);
			if (reverseLine.find()) return Integer.parseInt(reverseLine.group(1).replace(",", ""));
			if (text.contains("/")) continue;
			if (!normalized.contains("quest point") && !normalized.equals("qp")) continue;
			Matcher sameLineNumber = Pattern.compile("([0-9][0-9,]*)").matcher(text);
			if (sameLineNumber.find() && normalized.contains("quest point"))
				return Integer.parseInt(sameLineNumber.group(1).replace(",", ""));
			for (int j = i + 1; j < Math.min(i + 4, texts.size()); j++)
			{
				String candidate = texts.get(j);
				String candidateNormalized = candidate.toLowerCase(java.util.Locale.ROOT);
				if (candidateNormalized.contains("quests completed") || candidateNormalized.contains("combat achievements"))
					break;
				Matcher fraction = Pattern.compile("^\\s*([0-9][0-9,]*)\\s*/\\s*([0-9][0-9,]*)\\s*$").matcher(candidate);
				if (allowNumberOnly && fraction.find())
					return Integer.parseInt(fraction.group(1).replace(",", ""));
				if (candidate.contains("/")) break;
				Matcher number = Pattern.compile("^\\s*([0-9][0-9,]*)\\s*$").matcher(candidate);
				if (number.find()) return Integer.parseInt(number.group(1).replace(",", ""));
			}
		}
		return -1;
	}

	private static String achievementStatus(int points, int required)
	{
		if (points < 0) return "— abra a aba Combat Achievements e sincronize";
		return points >= required ? "✓" : "— " + points + "/" + required + " pontos";
	}

	private static String achievementStatus(int points, int required, boolean helmet, String helmetName)
	{
		if (points >= required || helmet) return "✓" + (helmet && points < required ? " (" + helmetName + ")" : "");
		if (points < 0) return "— abra a aba Combat Achievements ou equipe o " + helmetName;
		return "— " + points + "/" + required + " pontos ou equipe o " + helmetName;
	}

	private void ensureRankBankAccount(String accountName)
	{
		String safeAccount = accountName == null ? "" : accountName;
		if (safeAccount.equals(rankBankAccount)) return;
		rankBankAccount = safeAccount;
		rankBankItems.clear();
		rankBankItemIds.clear();
		rankBankLoaded = false;
	}

	private void collectItemNames(ItemContainer container, java.util.Set<String> names)
	{
		if (container == null) return;
		for (Item item : container.getItems())
		{
			if (item.getId() > 0) names.add(itemManager.getItemComposition(item.getId()).getName().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private static void collectItemIds(ItemContainer container, java.util.Set<Integer> ids)
	{
		if (container == null) return;
		for (Item item : container.getItems()) if (item.getId() > 0) ids.add(item.getId());
	}

	private static boolean containsItem(java.util.Set<String> items, String name)
	{
		return items.contains(name.toLowerCase(java.util.Locale.ROOT));
	}

	private static boolean containsAnyItem(java.util.Set<String> items, String... names)
	{
		for (String name : names) if (containsItem(items, name)) return true;
		return false;
	}

	private static boolean containsItemVariant(java.util.Set<String> items, String baseName)
	{
		String normalizedBase = baseName.toLowerCase(java.util.Locale.ROOT);
		for (String item : items)
		{
			String normalizedItem = item.toLowerCase(java.util.Locale.ROOT);
			if (normalizedItem.equals(normalizedBase) || normalizedItem.startsWith(normalizedBase + " ("))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsItemId(java.util.Set<Integer> items, int id)
	{
		return items.contains(id);
	}

	private static boolean containsAnyItemId(java.util.Set<Integer> items, int... ids)
	{
		for (int id : ids) if (containsItemId(items, id)) return true;
		return false;
	}

	private static String itemStatus(java.util.Set<String> items, String name)
	{
		return name + (containsItem(items, name) ? " ✓" : " — equipe ou coloque na bolsa");
	}

	private static String itemStatus(String name, boolean obtained)
	{
		return name + (obtained ? " ✓" : " — equipe ou coloque na bolsa");
	}

	private static String rankAdvice(String rank, int totalLevel, int questPoints, boolean questCape, boolean fireCape,
		boolean infernalCape, boolean quiver, boolean diaryCape, boolean maxCape, boolean easy, boolean medium,
		boolean hard, boolean elite, boolean master, boolean grandmaster)
	{
		if ("Coronel".equals(rank)) return "Rank máximo atingido, parabéns!";
		if (!questCape && fireCape && (quiver || infernalCape) && elite)
		{
			return "Você pode solicitar seu novo rank via Discord no canal #ranks."
				+ "<br>Próximo rank: Tenente<br>Requisitos faltantes: Quest cape";
		}
		String next;
		List<String> missing = new ArrayList<>();
		if ("Major".equals(rank))
		{
			next = "Coronel";
			if (!diaryCape) missing.add("Diary cape");
			if (!maxCape) missing.add("2376 total level");
			if (!grandmaster) missing.add("Grandmaster CAs");
		}
		else if ("Capitão".equals(rank))
		{
			next = "Major";
			if (totalLevel < 2300) missing.add("2300 total level");
		}
		else if ("Tenente".equals(rank))
		{
			next = "Capitão";
			if (!quiver) missing.add("Dizana's quiver");
			if (!infernalCape) missing.add("Infernal cape");
			if (!diaryCape) missing.add("Diary cape");
			if (!master) missing.add("Master CAs");
		}
		else if ("Cadete".equals(rank))
		{
			next = "Tenente";
			if (!elite) missing.add("Elite CAs");
			if (!quiver && !infernalCape) missing.add("Dizana's quiver ou Infernal cape");
		}
		else if ("Sargento".equals(rank))
		{
			next = "Cadete";
			if (!questCape) missing.add("Quest cape");
			if (!hard) missing.add("Hard CAs");
		}
		else if ("Aluno".equals(rank))
		{
			next = "Sargento";
			if (questPoints < 300) missing.add("300 Quest points");
			if (!medium) missing.add("Medium CAs");
		}
		else if ("Cabo".equals(rank))
		{
			next = "Aluno";
			if (questPoints < 250) missing.add("250 Quest points");
			if (!fireCape) missing.add("Fire cape");
			if (!easy) missing.add("Easy CAs");
		}
		else
		{
			next = "Cabo";
			if (questPoints < 200) missing.add("200 Quest points");
			if (!fireCape) missing.add("Fire cape");
		}
		if (missing.isEmpty()) return "Você pode solicitar seu novo rank via Discord no canal #ranks.";
		boolean eligible = rank.equals("Cabo") || rank.equals("Aluno") || rank.equals("Sargento")
			|| rank.equals("Cadete") || rank.equals("Tenente") || rank.equals("Capitão") || rank.equals("Major");
		String nextMessage = "Próximo rank: " + next + "<br>Requisitos faltantes: " + String.join(", ", missing);
		return eligible ? "Você pode solicitar seu novo rank via Discord no canal #ranks.<br>" + nextMessage : nextMessage;
	}

	private static String highestPossibleRank(int totalLevel, int questPoints, boolean questCape, boolean fireCape,
		boolean infernalCape, boolean quiver, boolean diaryCape, boolean maxCape, boolean easyCombatAchievements,
		boolean mediumCombatAchievements, boolean hardCombatAchievements, boolean eliteCombatAchievements,
		boolean masterCombatAchievements, boolean grandmasterCombatAchievements)
	{
		if (diaryCape && maxCape && grandmasterCombatAchievements) return "Coronel";
		if (diaryCape && quiver && infernalCape && totalLevel >= 2300 && masterCombatAchievements) return "Major";
		if (diaryCape && quiver && infernalCape && masterCombatAchievements) return "Capitão";
		if (questCape && (quiver || infernalCape) && eliteCombatAchievements) return "Tenente";
		if (questCape && fireCape && hardCombatAchievements) return "Cadete";
		if (questPoints >= 300 && fireCape && mediumCombatAchievements) return "Sargento";
		if (questPoints >= 250 && fireCape && easyCombatAchievements) return "Aluno";
		if (questPoints >= 200 && fireCape) return "Cabo";
		if (!fireCape) return "Fire cape pendente";
		if (questPoints < 0) return "Quest points não sincronizados";
		return "Quest points pendentes";
	}

	private void submitDropStats(Collection<ItemStack> items, String source, Long singleItemValueOverride)
	{
		if (client.getLocalPlayer() == null) return;
		List<Map<String, Object>> validDrops = new ArrayList<>();
		for (ItemStack item : items)
		{
			long value = effectiveDropValue(item, items.size(), singleItemValueOverride);
			if (value < 1_000_000L) continue;
			Map<String, Object> validDrop = new LinkedHashMap<>();
			validDrop.put("item", item.getQuantity() + "x " + itemManager.getItemComposition(item.getId()).getName());
			validDrop.put("value", value);
			validDrops.add(validDrop);
		}
		if (validDrops.isEmpty()) return;
		// Prepare payload; include playerName so server can verify via WOM
		java.util.Map<String, Object> dropPayload = new java.util.LinkedHashMap<>();
		dropPayload.put("playerName", client.getLocalPlayer().getName());
		dropPayload.put("drops", validDrops);
		dropPayload.put("source", source);
		postJson("stats/drops", gson.toJson(dropPayload), new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to submit drop statistics", exception);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (response.isSuccessful())
					{
						fetchMvpDrops();
					}
					else
					{
						log.debug("Drop statistics submission failed: {}", response.code());
					}
				}
			}
		});
	}

	private void sendDiscordDrop(String playerName, String description, int thumbnailItemId, String source,
		String category, Integer npcId, List<Map<String, Object>> items, long totalValue,
		Integer killCount, Double rarestProbability, java.awt.Image screenshot)
	{
		if (screenshot == null)
		{
			log.debug("Discord drop notification skipped because the screenshot was unavailable");
			return;
		}
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("title", "Loot Drop");
		embed.put("description", description);
		embed.put("color", dropEmbedColor(totalValue));
		embed.put("author", author(playerName));
		embed.put("timestamp", Instant.now().toString());
		embed.put("footer", footer());
		List<Map<String, Object>> fields = new ArrayList<>();
		if (killCount != null)
		{
			fields.add(embedField(dropCountLabel(category), discordCodeBlock(String.format(
				java.util.Locale.ROOT, "%,d", killCount)), true));
		}
		fields.add(embedField("Total Value", discordCodeBlock(formatGp(totalValue)), true));
		if (rarestProbability != null)
		{
			fields.add(embedField("Item Rarity", discordCodeBlock(formatProbability(rarestProbability)), true));
		}
		embed.put("fields", fields);
		Map<String, Object> thumbnail = new LinkedHashMap<>();
		thumbnail.put("url", "https://static.runelite.net/cache/item/icon/" + thumbnailItemId + ".png");
		embed.put("thumbnail", thumbnail);
		Map<String, Object> payload = new LinkedHashMap<>();
		// Keep content empty because the existing rich embed already contains the
		// human-readable message; consumers should use extra for processing.
		payload.put("content", "");
		payload.put("tts", false);
		payload.put("embeds", java.util.Collections.singletonList(embed));
		// Dink-compatible metadata. Custom webhook consumers should parse these
		// structured fields instead of trying to recover item data from the embed.
		payload.put("type", "LOOT");
		payload.put("playerName", playerName);
		payload.put("accountType", String.valueOf(client.getAccountType()));
		payload.put("seasonalWorld", client.getWorldType().contains(WorldType.SEASONAL));
		payload.put("dinkAccountHash", liveOnAccountHash(playerName));
		payload.put("world", client.getWorld());
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("items", items);
		extra.put("source", source);
		// Dink only populates party for supported raids. An empty list is safer
		// than claiming that every ordinary NPC/event drop came from a solo party.
		extra.put("party", java.util.Collections.emptyList());
		extra.put("category", category);
		extra.put("killCount", killCount);
		extra.put("rarestProbability", rarestProbability);
		extra.put("npcId", npcId);
		payload.put("extra", extra);
		try
		{
			byte[] screenshotBytes = null;
			if (screenshot != null)
			{
				Map<String, Object> image = new LinkedHashMap<>();
				image.put("url", "attachment://loot.png");
				embed.put("image", image);
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ImageIO.write((BufferedImage) screenshot, "png", output);
				screenshotBytes = output.toByteArray();
			}
			// Serialize only after attachment://loot.png has been added to the
			// embed, otherwise Discord renders the PNG as a separate attachment.
			MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM)
				.addFormDataPart("payload_json", gson.toJson(payload));
			if (screenshotBytes != null)
			{
				multipart.addFormDataPart("file", "loot.png",
					RequestBody.create(MediaType.parse("image/png"), screenshotBytes));
			}
			Request request = discordNotificationRequest(multipart.build());
			if (request == null)
			{
				return;
			}
			okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
			{
				@Override public void onFailure(okhttp3.Call call, IOException exception) { log.debug("Unable to send Discord drop notification", exception); }
				@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
				{
					try (Response ignored = response) { if (!response.isSuccessful()) log.debug("Discord notification relay returned {}", response.code()); }
				}
			});
		}
		catch (IOException exception)
		{
			log.debug("Unable to prepare Discord drop screenshot", exception);
		}
	}

	private static Map<String, Object> embedField(String name, String value, boolean inline)
	{
		Map<String, Object> field = new LinkedHashMap<>();
		field.put("name", name);
		field.put("value", value == null || value.trim().isEmpty() ? "-" : value);
		field.put("inline", inline);
		return field;
	}

	private static String discordCodeBlock(String value)
	{
		String safe = value == null ? "-" : value.replace("```", "'''");
		return "```\n" + safe + "\n```";
	}

	private static String formatGp(long value)
	{
		if (value >= 1_000_000_000L)
		{
			return String.format(java.util.Locale.ROOT, "%.1fB GP", value / 1_000_000_000.0);
		}
		if (value >= 1_000_000L)
		{
			return String.format(java.util.Locale.ROOT, "%.1fM GP", value / 1_000_000.0);
		}
		if (value >= 1_000L)
		{
			return String.format(java.util.Locale.ROOT, "%.1fK GP", value / 1_000.0);
		}
		return value + " GP";
	}

	private static int dropEmbedColor(long totalValue)
	{
		if (totalValue >= 500_000_000L)
		{
			return 0xFF2E94;
		}
		if (totalValue >= 100_000_000L)
		{
			return 0xFF7F00;
		}
		if (totalValue >= 50_000_000L)
		{
			return 0x99FF99;
		}
		return 0x66B2FF;
	}

	private static String formatDropValue(long value)
	{
		if (value >= 1_000_000_000L)
		{
			return String.format(java.util.Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
		}
		if (value >= 1_000_000L)
		{
			return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
		}
		if (value >= 1_000L)
		{
			return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000.0);
		}
		return String.valueOf(value);
	}

	private Integer readDropKillCount(String category, String source)
	{
		if (source == null || source.trim().isEmpty()) return null;
		Integer chatCount = configManager.getRSProfileConfiguration("killcount", cleanBossName(source), int.class);
		if (chatCount != null && chatCount > 0) return chatCount;
		try
		{
			String stored = configManager.getConfiguration(LootTrackerConfig.GROUP,
				configManager.getRSProfileKey(), "drops_" + category + "_" + source);
			if (stored == null) return null;
			com.google.gson.JsonObject record = gson.fromJson(stored, com.google.gson.JsonObject.class);
			if (record == null || !record.has("kills")) return null;
			int kills = record.get("kills").getAsInt();
			return kills >= 0 ? kills + 1 : null;
		}
		catch (RuntimeException exception)
		{
			log.debug("Unable to read loot tracker KC for {}", source, exception);
			return null;
		}
	}

	private static String cleanBossName(String source)
	{
		String clean = source.toLowerCase(java.util.Locale.ROOT).replace(":", "");
		if ("the leviathan".equals(clean)) return "leviathan";
		if ("the whisperer".equals(clean)) return "whisperer";
		if ("the hueycoatl".equals(clean)) return "hueycoatl";
		if (clean.startsWith("barrows")) return "barrows chests";
		return clean;
	}

	private static String dropCountLabel(String category)
	{
		if ("PICKPOCKET".equals(category)) return "Pickpocket Count";
		if ("EVENT".equals(category)) return "Completion Count";
		return "Kill Count";
	}

	private static String formatProbability(double probability)
	{
		if (!(probability > 0.0) || !Double.isFinite(probability)) return "Indisponível";
		double denominator = 1.0 / probability;
		return String.format(java.util.Locale.ROOT, "1 in %,.1f (%.3g%%)", denominator, probability * 100.0);
	}

	private static String petSourceFromMilestone(String milestone)
	{
		if (milestone == null) return null;
		Matcher matcher = Pattern.compile("(?i)\\bfrom\\s+(.+)$").matcher(milestone.trim());
		return matcher.find() ? matcher.group(1).replaceFirst("\\.$", "").trim() : null;
	}

	/**
	 * Dink consumers expect a stable, opaque per-account identifier. Keep a
	 * locally generated value instead of deriving it from the RSN, so renames do
	 * not expose or silently change the identifier.
	 */
	private String liveOnAccountHash(String playerName)
	{
		String key = "notificationAccountHash." + accountCacheKey(playerName);
		String stored = configManager.getConfiguration("live-on-clan-messages", key);
		if (stored != null && !stored.trim().isEmpty())
		{
			return stored.trim();
		}
		byte[] randomBytes = new byte[32];
		new java.security.SecureRandom().nextBytes(randomBytes);
		StringBuilder generated = new StringBuilder(64);
		for (byte value : randomBytes)
		{
			generated.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
		}
		String result = generated.toString();
		configManager.setConfiguration("live-on-clan-messages", key, result);
		return result;
	}

	private void sendPetNotification(String playerName, String petName, String milestone, String gameMessage,
		boolean duplicate, boolean backpack, Boolean previouslyOwned, java.awt.Image screenshot)
	{
		if (screenshot == null)
		{
			log.debug("Discord pet notification skipped because the screenshot was unavailable");
			return;
		}
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("title", duplicate ? "Dupe pet obtained!" : "New pet obtained!");
		String description;
		if (backpack)
		{
			description = playerName + " feels something weird sneaking into their backpack";
		}
		else if (duplicate)
		{
			description = playerName + " has a funny feeling like they would have been followed...";
		}
		else
		{
			description = playerName + " has a funny feeling like they're being followed";
		}
		embed.put("description", description);
		embed.put("timestamp", Instant.now().toString());
		embed.put("color", duplicate ? 0xED4245 : 0x57F287);
		embed.put("footer", footer());
		embed.put("author", author(playerName));
		List<Map<String, Object>> fields = new ArrayList<>();
		if (petName != null && !petName.trim().isEmpty())
		{
			fields.add(embedField("Name", discordCodeBlock(petName.trim()), true));
		}
		String status = duplicate ? "Already owned" : Boolean.FALSE.equals(previouslyOwned) ? "New!" : "Previously owned";
		fields.add(embedField("Status", discordCodeBlock(status), true));
		String petSource = petSourceFromMilestone(milestone);
		Double petRarity = null;
		if (milestone != null && !milestone.trim().isEmpty())
		{
			fields.add(embedField("KC", discordCodeBlock(milestone.trim()), true));
		}
		if (petName != null && petSource != null)
		{
			java.util.OptionalDouble rarity = dropRarityService.getRarityByItemName(petSource, petName.trim());
			if (rarity.isPresent())
			{
				petRarity = rarity.getAsDouble();
				fields.add(embedField("Rarity", discordCodeBlock(formatProbability(petRarity)), true));
			}
			java.util.OptionalInt petItemId = dropRarityService.findItemId(petSource, petName.trim());
			if (petItemId.isPresent())
			{
				Map<String, Object> thumbnail = new LinkedHashMap<>();
				thumbnail.put("url", "https://static.runelite.net/cache/item/icon/" + petItemId.getAsInt() + ".png");
				embed.put("thumbnail", thumbnail);
			}
		}
		embed.put("fields", fields);
		if (screenshot != null)
		{
			Map<String, Object> image = new LinkedHashMap<>();
			image.put("url", "attachment://loot.png");
			embed.put("image", image);
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("content", "");
		payload.put("tts", false);
		payload.put("embeds", java.util.Collections.singletonList(embed));
		payload.put("type", "PET");
		payload.put("playerName", playerName);
		payload.put("accountType", String.valueOf(client.getAccountType()));
		payload.put("seasonalWorld", client.getWorldType().contains(WorldType.SEASONAL));
		payload.put("dinkAccountHash", liveOnAccountHash(playerName));
		payload.put("world", client.getWorld());
		Map<String, Object> extra = new LinkedHashMap<>();
		if (petName != null && !petName.trim().isEmpty())
		{
			extra.put("petName", petName.trim());
		}
		if (milestone != null && !milestone.trim().isEmpty())
		{
			extra.put("milestone", milestone.trim());
		}
		extra.put("duplicate", duplicate);
		extra.put("previouslyOwned", previouslyOwned);
		if (petRarity != null) extra.put("rarity", petRarity);
		extra.put("gameMessage", gameMessage);
		payload.put("extra", extra);
		try
		{
			MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("payload_json", gson.toJson(payload));
			if (screenshot != null)
			{
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ImageIO.write((BufferedImage) screenshot, "png", output);
				multipart.addFormDataPart("file", "pet.png", RequestBody.create(MediaType.parse("image/png"), output.toByteArray()));
			}
			Request request = discordNotificationRequest(multipart.build());
			if (request == null)
			{
				return;
			}
			okHttpClient.newCall(request).enqueue(new SilentCallback());
		}
		catch (IOException exception) { log.debug("Unable to prepare pet notification payload", exception); }
	}

	private Request discordNotificationRequest(RequestBody body)
	{
		HttpUrl base = serverBaseUrl();
		if (base == null || authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			log.debug("Discord notification skipped because the clan server is not authenticated");
			return null;
		}
		HttpUrl url = base.newBuilder().addPathSegments("notifications/discord").build();
		return requestBuilder(url).post(body).build();
	}

	private static Map<String, Object> footer()
	{
		Map<String, Object> footer = new LinkedHashMap<>();
		footer.put("text", "Enviado pelo Live ON Clan Plugin");
		footer.put("icon_url", "https://raw.githubusercontent.com/MilicoOSRS/live-on-clan/master/src/main/resources/live-on-logo.png");
		return footer;
	}

	private Map<String, Object> author(String playerName)
	{
		Map<String, Object> author = new LinkedHashMap<>();
		author.put("name", playerName);
		author.put("url", "https://wiseoldman.net/players/" + playerName.replace(" ", "%20"));
		String badgeUrl = accountBadgeUrl(String.valueOf(client.getAccountType()),
			client.getWorldType().contains(WorldType.SEASONAL));
		if (badgeUrl != null)
		{
			author.put("icon_url", badgeUrl);
		}
		return author;
	}

	private static String accountBadgeUrl(String accountType, boolean seasonal)
	{
		final String wikiImages = "https://oldschool.runescape.wiki/images/";
		if (seasonal)
		{
			return wikiImages + "Leagues_chat_badge.png";
		}
		switch (accountType)
		{
			case "IRONMAN": return wikiImages + "Ironman_chat_badge.png";
			case "ULTIMATE_IRONMAN": return wikiImages + "Ultimate_ironman_chat_badge.png";
			case "HARDCORE_IRONMAN": return wikiImages + "Hardcore_ironman_chat_badge.png";
			case "GROUP_IRONMAN": return wikiImages + "Group_ironman_chat_badge.png";
			case "HARDCORE_GROUP_IRONMAN": return wikiImages + "Hardcore_group_ironman_chat_badge.png";
			case "UNRANKED_GROUP_IRONMAN": return wikiImages + "Unranked_group_ironman_chat_badge.png";
			default: return null;
		}
	}

	private static final class SilentCallback implements okhttp3.Callback
	{
		@Override public void onFailure(okhttp3.Call call, IOException e) { log.debug("Unable to submit statistics", e); }
		@Override public void onResponse(okhttp3.Call call, Response response) throws IOException { response.close(); }
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (pendingPet && (pendingPetMilestone != null || ++pendingPetTicks > PET_DETAILS_WAIT_TICKS))
		{
			String playerName = client.getLocalPlayer() == null ? "Jogador" : client.getLocalPlayer().getName();
			String petName = pendingPetName;
			String milestone = pendingPetMilestone;
			String gameMessage = pendingPetGameMessage;
			boolean duplicate = pendingPetDuplicate;
			boolean backpack = pendingPetBackpack;
			Boolean previouslyOwned = pendingPetPreviouslyOwned == null ? Boolean.TRUE : pendingPetPreviouslyOwned;
			resetPendingPet();
			drawManager.requestNextFrameListener(image -> sendPetNotification(playerName, petName, milestone,
				gameMessage, duplicate, backpack, previouslyOwned, image));
		}
		if (clanLiveBadgeDecorator != null)
		{
			clanLiveBadgeDecorator.refresh();
		}
		if (config.enabled() && client.getLocalPlayer() != null)
		{
			String currentAccount = WomMembership.normalizePlayerName(client.getLocalPlayer().getName());
			if (!currentAccount.equalsIgnoreCase(verifiedAccount))
			{
				verifiedAccount = currentAccount;
				verifyToken();
			}
		}
	}

	private void resetPendingPet()
	{
		pendingPet = false;
		pendingPetName = null;
		pendingPetMilestone = null;
		pendingPetGameMessage = null;
		pendingPetDuplicate = false;
		pendingPetBackpack = false;
		pendingPetPreviouslyOwned = null;
		pendingPetTicks = 0;
	}

	private synchronized void configurePolling()
	{
		if (pollingTask != null)
		{
			pollingTask.cancel(false);
			pollingTask = null;
		}
		if (rankRequestsPollingTask != null)
		{
			rankRequestsPollingTask.cancel(false);
			rankRequestsPollingTask = null;
		}
		if (mvpDropsPollingTask != null)
		{
			mvpDropsPollingTask.cancel(false);
			mvpDropsPollingTask = null;
		}
		if (panel == null || !config.enabled() || serverBaseUrl() == null
			|| authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			return;
		}
		long interval = Math.max(5, config.pollIntervalSeconds());
		pollingTask = executor.scheduleAtFixedRate(this::fetchMessages, 0, interval, TimeUnit.SECONDS);
		mvpDropsPollingTask = executor.scheduleAtFixedRate(this::fetchMvpRankings, 2, 60, TimeUnit.SECONDS);
		if (isStaff && hasStaffAccessKey())
		{
			rankRequestsPollingTask = executor.scheduleAtFixedRate(this::fetchRankRequests, 10, interval, TimeUnit.SECONDS);
		}
	}

	private void fetchMessages()
	{
		if (!messageFetchInFlight.compareAndSet(false, true))
		{
			return;
		}
		HttpUrl base = serverBaseUrl();
		if (base == null)
		{
			panel.setStatus("URL inválida");
			messageFetchInFlight.set(false);
			return;
		}
		HttpUrl url = base.newBuilder().addPathSegment("messages").addQueryParameter("after", lastMessageId).build();
		okHttpClient.newCall(requestBuilder(url).get().build()).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch clan messages", exception);
				if (panel != null) panel.setStatus("Sem conexão");
				messageFetchInFlight.set(false);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					String clearMarker = response.header("X-Live-On-Cleared-At", "");
					if (!clearMarker.isEmpty() && !clearMarker.equals(lastClearMarker))
					{
						lastClearMarker = clearMarker;
						if (panel != null) panel.clearMessages();
					}
					if (!response.isSuccessful() || response.body() == null)
					{
						if (panel != null) panel.setStatus("Erro " + response.code());
						return;
					}
					ClanMessage[] received = gson.fromJson(response.body().string(), ClanMessage[].class);
					log.debug("Message fetch for {} returned {} message(s) after id {}",
						authenticatedPlayerName,
						received == null ? 0 : received.length,
						lastMessageId);
					if (received != null)
					{
						for (ClanMessage message : received)
						{
							if (message.getId() != null)
							{
								lastMessageId = maxMessageId(lastMessageId, message.getId());
								if (!messageCursorAccount.isEmpty())
								{
									messageCursorByAccount.put(messageCursorAccount, lastMessageId);
									configManager.setConfiguration(
										"live-on-clan-messages",
										messageCursorConfigKey(messageCursorAccount),
										lastMessageId);
								}
							}
							if (message.getId() != null && isPinnedValue(message.getPinned()))
							{
								String deliveryKey = messageCursorAccount + '\u0000' + message.getId();
								if (!deliveredPinnedMessageIds.add(deliveryKey))
								{
									continue;
								}
							}
							if (message.getId() != null && locallyDisplayedMessageIds.remove(message.getId()))
							{
								continue;
							}
							String pendingRankKey = rankRequestKey(message.getMessage());
							if ("STAFF".equalsIgnoreCase(message.getMode())
								&& pendingRankKey != null
								&& !displayedPendingRankRequests.add(pendingRankKey))
							{
								continue;
							}
							if (panel != null) panel.addMessage(message);
							queueBroadcast(message.getMessage(), "CLAN".equalsIgnoreCase(message.getMode()));
						}
					}
					if (panel != null) panel.setAuthenticatedPlayer(authenticatedPlayerName);
				}
				finally
				{
					messageFetchInFlight.set(false);
				}
			}
		});
	}

	private void fetchMvpDrops()
	{
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			return;
		}
		getJson("stats/mvp-drops", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch MVP drops", exception);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						log.debug("MVP drops fetch failed: {}", response.code());
						return;
					}
					MvpDropEntry[] ranking = gson.fromJson(response.body().string(), MvpDropEntry[].class);
					if (panel != null)
					{
						panel.setMvpDrops(ranking == null
							? java.util.Collections.emptyList()
							: java.util.Arrays.asList(ranking));
					}
				}
			}
		});
	}

	private void fetchMvpRankings()
	{
		fetchMvpDrops();
		fetchMvpEfficiency();
		fetchLives();
		fetchMvpMembers();
		fetchClanTags();
	}

	private void fetchMvpEfficiency()
	{
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			return;
		}
		getJson("stats/mvp-efficiency", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch MVP efficiency rankings", exception);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						log.debug("MVP efficiency fetch failed: {}", response.code());
						return;
					}
					MvpEfficiencyResponse rankings = gson.fromJson(
						response.body().string(), MvpEfficiencyResponse.class);
					if (panel != null && rankings != null)
					{
						panel.setMvpEfficiency(
							rankings.ehb == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(rankings.ehb),
							rankings.ehp == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(rankings.ehp));
					}
				}
			}
		});
	}

	private void fetchLives()
	{
		if (!config.liveStatusEnabled() || authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			onlineLiveChannels.clear();
			if (panel != null) panel.updateOnlineLives(java.util.Collections.emptyList());
			return;
		}
		getJson("lives", liveChannelsCallback(false));
		if (isStaff && hasStaffAccessKey())
		{
			getJson("admin/live-channels", liveChannelsCallback(true));
		}
	}

	private okhttp3.Callback liveChannelsCallback(boolean managed)
	{
		return new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch Twitch channels", exception);
				if (managed && panel != null) panel.setLivesStatus("Falha ao atualizar");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						if (managed && panel != null) panel.setLivesStatus("Erro " + response.code());
						return;
					}
					LiveChannel[] parsed = gson.fromJson(response.body().string(), LiveChannel[].class);
					java.util.List<LiveChannel> channels = parsed == null
						? java.util.Collections.emptyList()
						: java.util.Arrays.asList(parsed);
					if (managed)
					{
						if (panel != null) panel.updateManagedLives(channels);
					}
					else
					{
						onlineLiveChannels.clear();
						for (LiveChannel channel : channels)
						{
							if (channel.online && channel.playerName != null)
							{
								onlineLiveChannels.put(normalizeChatPlayerName(channel.playerName), channel);
							}
						}
						if (panel != null) panel.updateOnlineLives(channels);
						clientThread.invokeLater(() -> client.runScript(ScriptID.BUILD_CHATBOX));
					}
				}
			}
		};
	}

	private void saveLiveChannel(String rsn, String twitchLogin)
	{
		if (!isStaff || rsn.isEmpty() || twitchLogin.isEmpty())
		{
			if (panel != null) panel.setLivesStatus("Informe RSN e canal da Twitch");
			return;
		}
		java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("playerName", authenticatedPlayerName);
		payload.put("rsn", rsn);
		payload.put("twitchLogin", twitchLogin);
		postJson("admin/live-channels", gson.toJson(payload), new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to save Twitch channel", exception);
				if (panel != null) panel.setLivesStatus("Falha ao associar canal");
			}
			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (panel != null) panel.setLivesStatus(response.isSuccessful() ? "Canal associado" : "Erro " + response.code());
					if (response.isSuccessful())
					{
						if (panel != null) panel.clearLiveFields();
						fetchLives();
					}
				}
			}
		});
	}

	private void deleteLiveChannel(LiveChannel channel)
	{
		if (!isStaff || channel == null)
		{
			return;
		}
		HttpUrl base = serverBaseUrl();
		if (base == null) return;
		HttpUrl url = base.newBuilder().addPathSegment("admin").addPathSegment("live-channels")
			.addPathSegment(Integer.toString(channel.id)).build();
		okHttpClient.newCall(requestBuilder(url).delete().build()).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to remove Twitch channel", exception);
				if (panel != null) panel.setLivesStatus("Falha ao remover canal");
			}
			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (panel != null) panel.setLivesStatus(response.isSuccessful() ? "Canal removido" : "Erro " + response.code());
					if (response.isSuccessful()) fetchLives();
				}
			}
		});
	}

	private void fetchMvpMembers()
	{
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			mvpMembers.clear();
			return;
		}
		getJson("mvp-members", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch MVP members", exception);
				if (isDeputyOwner && panel != null) panel.setMvpMembersStatus("Falha ao atualizar");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						if (isDeputyOwner && panel != null) panel.setMvpMembersStatus("Erro " + response.code());
						return;
					}
					MvpMember[] parsed = gson.fromJson(response.body().string(), MvpMember[].class);
					java.util.List<MvpMember> members = parsed == null
						? java.util.Collections.emptyList()
						: java.util.Arrays.asList(parsed);
					mvpMembers.clear();
					for (MvpMember member : members)
					{
						if (member.playerName != null)
						{
							mvpMembers.add(normalizeChatPlayerName(member.playerName));
						}
					}
					if (isDeputyOwner && panel != null) panel.updateMvpMembers(members);
					clientThread.invokeLater(() -> client.runScript(ScriptID.BUILD_CHATBOX));
				}
			}
		});
	}

	private void saveMvpMember(String rsn)
	{
		if (!isDeputyOwner || rsn == null || rsn.trim().isEmpty())
		{
			if (panel != null) panel.setMvpMembersStatus("Informe o nome do membro");
			return;
		}
		java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("playerName", authenticatedPlayerName);
		payload.put("rsn", rsn.trim());
		postJson("admin/mvp-members", gson.toJson(payload), new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to save MVP member", exception);
				if (panel != null) panel.setMvpMembersStatus("Falha ao adicionar MVP");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (panel != null) panel.setMvpMembersStatus(response.isSuccessful() ? "MVP adicionado" : "Erro " + response.code());
					if (response.isSuccessful())
					{
						if (panel != null) panel.clearMvpMemberField();
						fetchMvpMembers();
					}
				}
			}
		});
	}

	private void deleteMvpMember(MvpMember member)
	{
		if (!isDeputyOwner || member == null)
		{
			return;
		}
		HttpUrl base = serverBaseUrl();
		if (base == null) return;
		HttpUrl url = base.newBuilder().addPathSegment("admin").addPathSegment("mvp-members")
			.addPathSegment(Integer.toString(member.id)).build();
		okHttpClient.newCall(requestBuilder(url).delete().build()).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to remove MVP member", exception);
				if (panel != null) panel.setMvpMembersStatus("Falha ao remover MVP");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (panel != null) panel.setMvpMembersStatus(response.isSuccessful() ? "MVP removido" : "Erro " + response.code());
					if (response.isSuccessful()) fetchMvpMembers();
				}
			}
		});
	}

	private void fetchClanTags()
	{
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			clanTagsByPlayer.clear();
			return;
		}
		getJson("clan-tags", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch clan tags", exception);
				if (isDeputyOwner && panel != null) panel.setClanTagsStatus("Falha ao atualizar");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						if (isDeputyOwner && panel != null) panel.setClanTagsStatus("Erro " + response.code());
						return;
					}
					ClanTagsResponse parsed = gson.fromJson(response.body().string(), ClanTagsResponse.class);
					clanTagsByPlayer.clear();
					if (parsed != null && parsed.tags != null)
					{
						for (ClanTag clanTag : parsed.tags)
						{
							String markup = clanTagMarkup(clanTag);
							if (!markup.isEmpty()) knownClanTagMarkup.add(markup);
							if (clanTag.members == null) continue;
							for (ClanTagMember member : clanTag.members)
							{
								if (member.playerName == null) continue;
								clanTagsByPlayer.computeIfAbsent(normalizeChatPlayerName(member.playerName), key -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(clanTag);
							}
						}
					}
					if (isDeputyOwner && panel != null) panel.updateClanTags(parsed);
					clientThread.invokeLater(() -> client.runScript(ScriptID.BUILD_CHATBOX));
				}
			}
		});
	}

	private void createClanTag(String code, String color)
	{
		String normalizedCode = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
		if (!isDeputyOwner || !normalizedCode.matches("[A-Z0-9]{1,5}"))
		{
			if (panel != null) panel.setClanTagsStatus(isDeputyOwner ? "Use de 1 a 5 letras ou números" : "Apenas Deputy Owner pode alterar");
			return;
		}
		java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("playerName", authenticatedPlayerName);
		payload.put("code", normalizedCode);
		payload.put("color", color);
		postJson("admin/clan-tags", gson.toJson(payload), clanTagWriteCallback("Etiqueta criada", panel::clearClanTagCode));
	}

	private void addClanTagMember(ClanTag clanTag, String rsn)
	{
		if (!isDeputyOwner || clanTag == null || rsn == null || rsn.trim().isEmpty())
		{
			if (panel != null) panel.setClanTagsStatus(isDeputyOwner ? "Informe o nome do membro" : "Apenas Deputy Owner pode alterar");
			return;
		}
		java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("playerName", authenticatedPlayerName);
		payload.put("rsn", rsn.trim());
		postJson("admin/clan-tags/" + clanTag.id + "/members", gson.toJson(payload),
			clanTagWriteCallback("Membro adicionado", panel::clearClanTagMember));
	}

	private void deleteClanTag(ClanTag clanTag)
	{
		if (!isDeputyOwner || clanTag == null) return;
		deleteClanTagPath("admin/clan-tags/" + clanTag.id, "Etiqueta removida");
	}

	private void removeClanTagMember(ClanTag clanTag, ClanTagMember member)
	{
		if (!isDeputyOwner || clanTag == null || member == null) return;
		deleteClanTagPath("admin/clan-tags/" + clanTag.id + "/members/" + member.id, "Membro removido");
	}

	private okhttp3.Callback clanTagWriteCallback(String success, Runnable clearAction)
	{
		return new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to update clan tags", exception);
				if (panel != null) panel.setClanTagsStatus("Falha ao salvar");
			}
			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (panel != null) panel.setClanTagsStatus(response.isSuccessful() ? success : "Erro " + response.code());
					if (response.isSuccessful())
					{
						if (clearAction != null) clearAction.run();
						fetchClanTags();
					}
				}
			}
		};
	}

	private void deleteClanTagPath(String path, String success)
	{
		HttpUrl base = serverBaseUrl();
		if (base == null) return;
		HttpUrl.Builder builder = base.newBuilder();
		for (String segment : path.split("/")) builder.addPathSegment(segment);
		okHttpClient.newCall(requestBuilder(builder.build()).delete().build()).enqueue(clanTagWriteCallback(success, null));
	}

	boolean isLiveStatusVisible()
	{
		return (config.liveStatusEnabled() && !onlineLiveChannels.isEmpty()) || !mvpMembers.isEmpty()
			|| !clanTagsByPlayer.isEmpty();
	}

	boolean isPlayerLive(String playerName)
	{
		return config.liveStatusEnabled()
			&& onlineLiveChannels.containsKey(normalizeChatPlayerName(playerName));
	}

	boolean isPlayerMvp(String playerName)
	{
		return mvpMembers.contains(normalizeChatPlayerName(playerName));
	}

	String clanTagBadges(String playerName)
	{
		java.util.List<ClanTag> tags = clanTagsByPlayer.get(normalizeChatPlayerName(playerName));
		if (tags == null || tags.isEmpty()) return "";
		StringBuilder badges = new StringBuilder();
		for (ClanTag clanTag : tags) badges.append(clanTagMarkup(clanTag));
		return badges.toString();
	}

	String removeKnownClanTagMarkup(String text)
	{
		String cleaned = text == null ? "" : text;
		for (String markup : knownClanTagMarkup) cleaned = cleaned.replace(markup, "");
		return cleaned;
	}

	private static String clanTagMarkup(ClanTag clanTag)
	{
		if (clanTag == null || clanTag.code == null || !clanTag.code.matches("[A-Z0-9]{1,5}")) return "";
		String color;
		switch (clanTag.color == null ? "" : clanTag.color.toLowerCase(java.util.Locale.ROOT))
		{
			case "red": color = "ff6464"; break;
			case "blue": color = "66b2ff"; break;
			case "green": color = "67d96d"; break;
			case "purple": color = "c68cff"; break;
			case "white": color = "ffffff"; break;
			default: color = "ffc628";
		}
		return " <col=" + color + ">" + clanTag.code + "</col>";
	}

	String decoratedPlayerNameIn(String displayedText)
	{
		String normalized = normalizeChatPlayerName(displayedText);
		if (mvpMembers.contains(normalized)) return displayedText;
		if (clanTagsByPlayer.containsKey(normalized)) return displayedText;
		for (Map.Entry<String, LiveChannel> entry : onlineLiveChannels.entrySet())
		{
			String key = entry.getKey();
			if (normalized.equals(key) || normalized.startsWith(key + " "))
			{
				LiveChannel channel = entry.getValue();
				return channel.playerName == null ? displayedText : channel.playerName;
			}
		}
		return null;
	}

	java.util.Set<String> livePlayerNamesForDiagnostics()
	{
		return new java.util.LinkedHashSet<>(onlineLiveChannels.keySet());
	}

	static String normalizeChatPlayerName(String playerName)
	{
		return WomMembership.normalizePlayerName(playerName).toLowerCase(java.util.Locale.ROOT);
	}

	private static boolean isDeputyOwnerRole(String roleName)
	{
		return roleName != null
			&& "DEPUTYOWNER".equals(roleName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT));
	}

	private static String maxMessageId(String current, String candidate)
	{
		long currentId = parseMessageId(current);
		long candidateId = parseMessageId(candidate);
		if (candidateId > currentId)
		{
			return Long.toString(candidateId);
		}
		return current == null ? "" : current;
	}

	private static long parseMessageId(String value)
	{
		if (value == null || value.isEmpty())
		{
			return -1L;
		}
		try
		{
			return Long.parseLong(value);
		}
		catch (NumberFormatException exception)
		{
			return -1L;
		}
	}

	private static boolean isPinnedValue(Object pinned)
	{
		return Boolean.TRUE.equals(pinned)
			|| (pinned instanceof Number && ((Number) pinned).intValue() != 0);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			deliveredPinnedMessageIds.clear();
			pendingAllowlistedDrops.clear();
			recentAllowlistedLootTicks.clear();
			rankBankItems.clear();
			rankBankItemIds.clear();
			rankBankAccount = "";
			rankBankLoaded = false;
			lastObservedRankTotalLevel = -1;
			rankRequestStatusKnown = false;
			rankRequestPending = false;
			questAccount = "";
			lastQuestPoints = -1;
			lastMaximumQuestPoints = -1;
		}
	}

	private void queueBroadcast(String message, boolean clanChannel)
	{
		clientThread.invoke(() ->
		{
			ChatMessageBuilder builder = new ChatMessageBuilder();
			Color messageColor = clanChannel ? Color.WHITE : Color.YELLOW;
			builder.append(clanChannel ? Color.GREEN : Color.YELLOW, "[Live ON] ");
			if (!appendStructuredRankMessage(builder, message, messageColor))
			{
				appendMessageWithLinks(builder, message, messageColor);
			}
			chatMessageManager.queue(QueuedMessage.builder()
				.type(clanChannel ? ChatMessageType.CLAN_MESSAGE : ChatMessageType.BROADCAST)
				.runeLiteFormattedMessage(builder.build())
				.build());
		});
	}

	private static void appendMessageWithLinks(ChatMessageBuilder builder, String message, Color color)
	{
		if (message == null || message.isEmpty())
		{
			return;
		}
		Matcher urls = URL_PATTERN.matcher(message);
		int previousEnd = 0;
		while (urls.find())
		{
			if (urls.start() > previousEnd)
			{
				builder.append(color, message.substring(previousEnd, urls.start()));
			}
			String original = urls.group();
			String validUrl = validChatUrl(original);
			if (validUrl == null)
			{
				builder.append(color, original);
			}
			else
			{
				builder.append(Color.CYAN, validUrl);
				if (validUrl.length() < original.length())
				{
					builder.append(color, original.substring(validUrl.length()));
				}
			}
			previousEnd = urls.end();
		}
		if (previousEnd < message.length())
		{
			builder.append(color, message.substring(previousEnd));
		}
	}

	private boolean appendStructuredRankMessage(ChatMessageBuilder builder, String message, Color color)
{
if (message == null || message.isEmpty())
{
return false;
}

Matcher rankRequest = RANK_REQUEST_MESSAGE_PATTERN.matcher(message);
if (rankRequest.matches())
{
appendChatText(builder, color, rankRequest.group("player") + " solicitou um rank: ");
String rankName = rankRequest.group("rank");
appendClanRankWithIcon(builder, color, rankName);
return true;
}

Matcher promotion = PROMOTION_MESSAGE_PATTERN.matcher(message);
if (promotion.matches())
{
appendChatText(builder, color, promotion.group("player") + " foi promovido para ");
String rankName = promotion.group("rank");
appendClanRankWithIcon(builder, color, rankName);
appendChatText(builder, color, "!");
return true;
}

return false;
}

private static void appendChatText(ChatMessageBuilder builder, Color color, String text)
	{
		if (color == null)
		{
			builder.append(text);
			return;
		}
		builder.append(color, text);
	}

	private void publishDraft(String mode)
	{
		String message = panel.getDraft();
		if (message.isEmpty()) return;
		panel.setPublishing(true);
		HttpUrl base = serverBaseUrl();
		if (base == null)
		{
			panel.setStatus("URL inválida");
			panel.setPublishing(false);
			return;
		}
		String rsnName = authenticatedPlayerName;
		if (rsnName == null || rsnName.isEmpty())
		{
			panel.setStatus("Valide sua conta pelo WOM");
			panel.setPublishing(false);
			return;
		}
		java.util.Map<String, Object> bodyMap = new java.util.LinkedHashMap<>();
		bodyMap.put("message", message);
		bodyMap.put("mode", mode);
		bodyMap.put("playerName", rsnName);
		// Include pinned flag when publishing a BROADCAST if the panel checkbox is selected (server enforces staff requirement)
		final boolean pinned = panel != null && panel.isPinSelected();
		bodyMap.put("pinned", pinned);
		String jsonBody = gson.toJson(bodyMap);
				log.debug("Publish payload: {}", jsonBody);
				RequestBody body = RequestBody.create(JSON, jsonBody);
				Request request = requestBuilder(base.newBuilder().addPathSegment("messages").build()).post(body).build();
		okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to publish clan message", exception);
				if (panel != null) panel.setStatus("Falha ao publicar");
				if (panel != null) panel.setPublishing(false);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					String body = response.body() == null ? "" : response.body().string();
					log.debug("Publish response: code={} body={}", response.code(), body);
					if (panel != null)
					{
						if (response.isSuccessful())
						{
							panel.clearDraft();
							panel.setStatus("Publicado");
							displayPublishedMessage(body, "Staff", message, mode, pinned);
						}
						else if (response.code() == 403 && body.contains("staff_required"))
						{
							panel.setStatus("Acesso staff necessário para publicar");
						}
						else if (response.code() == 403 && body.contains("broadcast_role_required"))
						{
							panel.setStatus("Broadcast indisponível para este cargo");
						}
						else if (response.code() == 401 && body.contains("unauthorized"))
						{
							panel.setStatus("Falha de autenticação WOM. Clique em Verificar agora e tente de novo");
						}
						else
						{
							panel.setStatus("Erro " + response.code());
						}
					}
					if (!response.isSuccessful()) log.debug("Publish failed: code={} body={}", response.code(), body);
					if (panel != null) panel.setPublishing(false);
					if (response.isSuccessful())
					{
						scheduleMessageRefresh();
						if (isStaff) fetchSentMessages();
					}
				}
			}
		});
	}

	private void displayPublishedMessage(String responseBody, String author, String message, String mode, boolean pinned)
	{
		PublishResponse published = null;
		try
		{
			published = gson.fromJson(responseBody, PublishResponse.class);
		}
		catch (RuntimeException exception)
		{
			log.debug("Unable to parse published message response", exception);
		}
		String id = published == null ? null : published.id;
		if (id != null && !id.isEmpty())
		{
			locallyDisplayedMessageIds.add(id);
		}
		ClanMessage localMessage = new ClanMessage(id, author, message, mode, pinned);
		if (panel != null)
		{
			panel.addMessage(localMessage);
		}
		queueBroadcast(message, "CLAN".equalsIgnoreCase(mode));
	}

	private void scheduleMessageRefresh()
	{
		if (executor != null && !executor.isShutdown())
		{
			executor.schedule(this::fetchMessages, 250, TimeUnit.MILLISECONDS);
		}
	}

	private void fetchSentMessages()
	{
		if (!isStaff)
		{
			return;
		}
		if (!hasStaffAccessKey())
		{
			if (panel != null) panel.setSentMessagesStatus("Configure a chave da staff");
			return;
		}
		getJson("admin/sent-messages", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch sent messages", exception);
				if (panel != null) panel.setSentMessagesStatus("Falha ao atualizar");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						if (panel != null) panel.setSentMessagesStatus(response.code() == 403
							? "Verifique a chave da staff"
							: "Erro " + response.code());
						return;
					}
					ClanMessagesPanel.StaffSentMessage[] sent = gson.fromJson(
						response.body().string(), ClanMessagesPanel.StaffSentMessage[].class);
					if (panel != null)
					{
						panel.updateSentMessages(sent == null
							? java.util.Collections.emptyList()
							: java.util.Arrays.asList(sent));
					}
				}
			}
		});
	}

	private void deleteSentMessage(ClanMessagesPanel.StaffSentMessage sentMessage)
	{
		if (!isStaff || sentMessage == null || sentMessage.id == null)
		{
			return;
		}
		HttpUrl base = serverBaseUrl();
		if (base == null) return;
		HttpUrl url = base.newBuilder().addPathSegment("admin").addPathSegment("messages")
			.addPathSegment(sentMessage.id).build();
		okHttpClient.newCall(requestBuilder(url).delete().build()).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to delete sent message", exception);
				if (panel != null) panel.setSentMessagesStatus("Falha ao remover");
			}
			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (panel != null) panel.setSentMessagesStatus(response.isSuccessful() ? "Mensagem removida" : "Erro " + response.code());
					if (response.isSuccessful()) fetchSentMessages();
				}
			}
		});
	}

	private void resendSentMessage(ClanMessagesPanel.StaffSentMessage sentMessage)
	{
		if (sentMessage == null || panel == null)
		{
			return;
		}
		panel.setDraft(sentMessage.message, sentMessage.isPinned());
		SwingUtilities.invokeLater(() -> publishDraft(
			"CLAN".equalsIgnoreCase(sentMessage.mode) ? "CLAN" : "BROADCAST"));
	}

	private void togglePinnedMessage(ClanMessagesPanel.StaffSentMessage sentMessage)
	{
		if (!isStaff || sentMessage == null || sentMessage.id == null)
		{
			return;
		}
		if (!"BROADCAST".equalsIgnoreCase(sentMessage.mode))
		{
			if (panel != null) panel.setSentMessagesStatus("Somente broadcasts podem ser fixados");
			return;
		}
		HttpUrl base = serverBaseUrl();
		if (base == null) return;
		boolean newPinnedValue = !sentMessage.isPinned();
		java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("pinned", newPinnedValue);
		payload.put("playerName", authenticatedPlayerName);
		RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
		HttpUrl url = base.newBuilder().addPathSegment("admin").addPathSegment("messages")
			.addPathSegment(sentMessage.id).addPathSegment("pin").build();
		okHttpClient.newCall(requestBuilder(url).post(body).build()).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to change pinned message", exception);
				if (panel != null) panel.setSentMessagesStatus("Falha ao alterar mensagem fixada");
			}
			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (panel != null)
					{
						panel.setSentMessagesStatus(response.isSuccessful()
							? (newPinnedValue ? "Mensagem fixada" : "Mensagem desfixada")
							: "Erro " + response.code());
					}
					if (response.isSuccessful()) fetchSentMessages();
				}
			}
		});
	}

	private void clearMessages()
	{
		if (!isStaff)
		{
			panel.setStatus("Acesso staff necessário");
			return;
		}
		postJson("admin/messages/clear", "{}", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception) { log.debug("Unable to clear clan messages", exception); if (panel != null) panel.setStatus("Falha ao limpar mensagens"); }
			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response) { if (panel != null) panel.setStatus(response.isSuccessful() ? "Mensagens limpas" : "Erro " + response.code()); }
			}
		});
	}

	private void requestRank()
	{
		String playerName = authenticatedPlayerName;
		if (playerName == null || playerName.isEmpty())
		{
			if (panel != null) panel.setStatus("Valide sua conta pelo WOM");
			return;
		}
		String currentRank = panel.getCurrentRank();
		if (currentRank.isEmpty() || currentRank.contains("não sincronizado") || currentRank.contains("em análise"))
		{
			if (panel != null) panel.setStatus("Sincronize o rank antes de solicitar");
			return;
		}
		String message = playerName + " solicitou um rank: " + currentRank;
		HttpUrl base = serverBaseUrl();
		if (base == null)
		{
			if (panel != null) panel.setStatus("URL inválida");
			return;
		}
		java.util.Map<String, Object> requestPayload = new java.util.LinkedHashMap<>();
		requestPayload.put("message", message);
		requestPayload.put("mode", "STAFF");
		requestPayload.put("playerName", playerName);
		RequestBody body = RequestBody.create(JSON, gson.toJson(requestPayload));
		Request request = requestBuilder(base.newBuilder().addPathSegment("messages").build()).post(body).build();
		okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to send rank request", exception);
				if (panel != null) panel.setStatus("Falha ao solicitar rank");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					String responseBody = response.body() == null ? "" : response.body().string();
					log.debug("Rank request response: code={} body={}", response.code(), responseBody);
					if (panel != null)
					{
						if (response.isSuccessful())
						{
							rankRequestStatusKnown = true;
							rankRequestPending = true;
							panel.setRankRequestState(true, 0);
							panel.setStatusSuccess("Solicita\u00E7\u00E3o enviada para a staff.");
							// Rank requests are staff-only. Do not simulate the STAFF
							// message for the requesting member.
							scheduleMessageRefresh();
							if (isStaff) fetchRankRequests();
						}
						else if (response.code() == 409)
						{
							rankRequestStatusKnown = true;
							rankRequestPending = true;
							panel.setRankRequestState(true, 0);
							panel.setStatus("Você já possui uma solicitação pendente");
						}
						else if (response.code() == 429)
						{
							int retryAfter = rankRetryAfter(responseBody, response.header("Retry-After"));
							panel.setRankRequestState(false, retryAfter);
							panel.setStatus("Aguarde " + Math.max(1, (retryAfter + 59) / 60) + " min para solicitar novamente");
						}
						else panel.setStatus("Erro " + response.code());
					}
				}
			}
		});
	}

	private void fetchRankRequestStatus()
	{
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty() || panel == null) return;
		HttpUrl base = serverBaseUrl();
		if (base == null) return;
		Request request = requestBuilder(base.newBuilder().addPathSegments("rank-request/status").build()).get().build();
		okHttpClient.newCall(request).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch rank request status", exception);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null) return;
					com.google.gson.JsonObject state = gson.fromJson(response.body().string(), com.google.gson.JsonObject.class);
					boolean pending = state != null && state.has("pending") && state.get("pending").getAsBoolean();
					int cooldown = state != null && state.has("cooldownRemaining")
						? Math.max(0, state.get("cooldownRemaining").getAsInt()) : 0;
					rankRequestStatusKnown = true;
					rankRequestPending = pending;
					panel.setRankRequestState(pending, cooldown);
				}
			}
		});
	}

	private int rankRetryAfter(String responseBody, String retryHeader)
	{
		try
		{
			com.google.gson.JsonObject payload = gson.fromJson(responseBody, com.google.gson.JsonObject.class);
			if (payload != null && payload.has("retryAfter"))
				return Math.max(1, payload.get("retryAfter").getAsInt());
		}
		catch (RuntimeException ignored) { }
		try { return Math.max(1, Integer.parseInt(retryHeader)); }
		catch (RuntimeException ignored) { return 60; }
	}

	private void verifyToken()
	{
		verifyToken(false);
	}

	private void verifyToken(boolean manual)
	{
		if (!config.enabled())
		{
			if (panel != null)
			{
				panel.showConnectionRequired();
			}
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (client.getLocalPlayer() == null)
			{
				// During startup the plugin can run a few ticks before LocalPlayer is
				// created. Keep that expected transition silent; only show feedback
				// when the user explicitly pressed Verify.
				if (manual && panel != null) panel.setStatus("Jogador não disponível");
				if (panel != null) panel.setAuthenticated(false, false);
				authenticatedPlayerName = "";
				isStaff = false;
				return;
			}
			final String rsn = WomMembership.normalizePlayerName(client.getLocalPlayer().getName());
			verifiedAccount = rsn;
			final String key = rsn.toLowerCase(java.util.Locale.ROOT);
			final long now = System.currentTimeMillis();
			// Check cache first
			// A user-triggered verification must consult WOM again. Reusing a cached
			// membership here can keep an old clan role and hide staff access for up
			// to an hour after reconnecting.
			CacheEntry cached = manual ? null : womCache.get(key);
			if (cached != null && cached.expiresAtMillis > now)
			{
				boolean member = cached.member;
				String roleName = cached.role;
				if (member)
				{
					boolean staff = WomMembership.isStaffRole(roleName);
					if (roleName != null)
					{
							String norm = roleName.replaceAll("[^A-Za-z0-9]", "" ).toUpperCase(java.util.Locale.ROOT);
							java.util.Set<String> allowed = new java.util.HashSet<>();
							allowed.add("OWNER"); allowed.add("DEPUTYOWNER"); allowed.add("MODERATOR"); allowed.add("ADMINISTRATOR");
							if (allowed.contains(norm)) staff = true;					}
					isStaff = staff;
					isDeputyOwner = isDeputyOwnerRole(roleName);
					canPublishBroadcast = WomMembership.canPublishBroadcast(roleName);
					switchMessageCursorAccount(rsn);
					authenticatedPlayerName = rsn;
					configurePolling();
					rankRequestStatusKnown = false;
					rankRequestPending = false;
					fetchRankRequestStatus();
					if (panel != null) { panel.setAccessMessage(""); panel.clearRanksStatus(); panel.setAuthenticated(true, staff); panel.setDeputyOwner(isDeputyOwner); panel.setBroadcastAllowed(canPublishBroadcast); panel.setAuthenticatedPlayer(rsn); }
					if (staff && hasStaffAccessKey())
					{
						fetchRankRequests();
						fetchSentMessages();
					}
					if (panel != null) panel.startVerifyCooldown(VERIFY_COOLDOWN_SECONDS);
					return;
				}
				else
				{
					authenticatedPlayerName = "";
					isStaff = false;
					isDeputyOwner = false;
					canPublishBroadcast = false;
					if (panel != null) { panel.setStatus("Não é membro do clã (WOM)"); panel.setAccessMessage("Membro não identificado. Este plugin é exclusivo para membros do Live On."); panel.startVerifyCooldown(VERIFY_COOLDOWN_SECONDS); }
					return;
				}
			}
			// Not cached: perform network check. Disable verify button and show status.
			if (panel != null) { panel.setVerifyEnabled(false); panel.setAccessMessage("Verificando..."); }
			okhttp3.Call previousCall = currentWomCall;
			if (previousCall != null)
			{
				previousCall.cancel();
			}
			HttpUrl womUrl = new HttpUrl.Builder()
				.scheme("https")
				.host("api.wiseoldman.net")
				.addPathSegment("v2")
				.addPathSegment("players")
				.addPathSegment(rsn)
				.addPathSegment("groups")
				.addQueryParameter("limit", "50")
				.build();
			okhttp3.Call call = okHttpClient.newCall(new Request.Builder()
				.url(womUrl)
				.header("Accept", "application/json")
				.header("User-Agent", WOM_USER_AGENT)
				.get()
				.build());
			currentWomCall = call;
			call.enqueue(new okhttp3.Callback()
			{
				@Override public void onFailure(okhttp3.Call call, IOException exception)
				{
					if (call.isCanceled())
					{
						return;
					}
					if (currentWomCall != call)
					{
						return;
					}
					log.debug("WOM membership check failed", exception);
					if (panel != null) panel.setAuthenticated(false, false);
					if (panel != null) { panel.setStatus("Falha ao verificar grupo (WOM)"); panel.setAccessMessage("Falha ao verificar grupo (WOM)"); panel.startVerifyCooldown(VERIFY_COOLDOWN_SECONDS); }
					authenticatedPlayerName = "";
					isStaff = false;
					if (currentWomCall == call) currentWomCall = null;
				}

				@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
				{
					try (Response resp = response)
					{
							// Ignore a response superseded by another verification.
							if (currentWomCall != call)
							{
								return;
							}
							if (!resp.isSuccessful() || resp.body() == null)
							{
								log.debug("WOM membership check returned HTTP {}", resp.code());
								if (panel != null) panel.setAuthenticated(false, false);
								if (panel != null) { panel.setStatus("Falha ao consultar o WOM (erro " + resp.code() + ")"); panel.setAccessMessage("Não foi possível validar sua conta no Wise Old Man."); panel.startVerifyCooldown(VERIFY_COOLDOWN_SECONDS); }
								authenticatedPlayerName = "";
								isStaff = false;
								if (currentWomCall == call) currentWomCall = null;
								return;
							}
							WomMembership.Result womMembership = WomMembership.parse(gson, resp.body().string());
							boolean member = womMembership.member;
							String roleName = womMembership.role;
							// store result in cache
							long ttlSeconds = member ? WOM_CACHE_TTL_SECONDS : WOM_NEGATIVE_CACHE_TTL_SECONDS;
							long expires = System.currentTimeMillis() + (ttlSeconds * 1000L);
							womCache.put(key, new CacheEntry(member, roleName, expires));
							if (member)
							{
								boolean staff = WomMembership.isStaffRole(roleName);
								if (roleName != null)
								{
									String norm = roleName.replaceAll("[^A-Za-z0-9]","" ).toUpperCase(java.util.Locale.ROOT);
									java.util.Set<String> allowed = new java.util.HashSet<>();
									allowed.add("OWNER"); allowed.add("DEPUTYOWNER"); allowed.add("MODERATOR"); allowed.add("ADMINISTRATOR");
									if (allowed.contains(norm)) staff = true;
								}
								isStaff = staff;
								isDeputyOwner = isDeputyOwnerRole(roleName);
								canPublishBroadcast = WomMembership.canPublishBroadcast(roleName);
								switchMessageCursorAccount(rsn);
								authenticatedPlayerName = rsn;
								configurePolling();
								rankRequestStatusKnown = false;
								rankRequestPending = false;
								fetchRankRequestStatus();
								if (panel != null) { panel.setAccessMessage(""); panel.clearRanksStatus(); panel.setAuthenticated(true, staff); panel.setDeputyOwner(isDeputyOwner); panel.setBroadcastAllowed(canPublishBroadcast); panel.setAuthenticatedPlayer(rsn); }
								if (staff && hasStaffAccessKey())
								{
									fetchRankRequests();
									fetchSentMessages();
								}
							}
							else
							{
								authenticatedPlayerName = "";
								isStaff = false;
								isDeputyOwner = false;
								canPublishBroadcast = false;
								if (panel != null) panel.setAuthenticated(false, false);
								if (panel != null) { panel.setStatus("Não é membro do clã (WOM)"); panel.setAccessMessage("Membro não identificado. Este plugin é exclusivo para membros do Live On."); }
							}
							// start cooldown so user cannot spam immediately
							if (panel != null) panel.startVerifyCooldown(VERIFY_COOLDOWN_SECONDS);
							if (currentWomCall == call) currentWomCall = null;
					}
				}
			});
		});
	}

	private void postJson(String path, String json, okhttp3.Callback callback)
	{
		HttpUrl base = serverBaseUrl();
		if (base == null)
		{
			panel.setStatus("URL inválida");
			return;
		}
		RequestBody body = RequestBody.create(JSON, json);
		Request request = requestBuilder(base.newBuilder().addPathSegments(path).build())
			.post(body)
			.build();
		okHttpClient.newCall(request).enqueue(callback);	}

	private void getJson(String path, okhttp3.Callback callback)
	{
		HttpUrl base = serverBaseUrl();
		if (base == null)
		{
			panel.setStatus("URL inválida");
			return;
		}
		okHttpClient.newCall(requestBuilder(base.newBuilder().addPathSegments(path).build()).get().build()).enqueue(callback);
	}

	private Request.Builder requestBuilder(HttpUrl url)
	{
		Request.Builder builder = new Request.Builder().url(url);
		String playerName = authenticatedPlayerName;
		if (playerName != null && !playerName.isEmpty())
		{
			builder.header("X-Live-On-Player", playerName);
			String staffAccessKey = config.staffAccessKey() == null
				? ""
				: config.staffAccessKey().trim();
			if (isStaff && !staffAccessKey.isEmpty())
			{
				builder.header("Authorization", "Bearer " + staffAccessKey);
			}
			else
			{
				builder.header("Authorization", "LiveOnPlayer " + playerName);
			}
		}
		return builder;
	}

	private boolean hasStaffAccessKey()
	{
		return config.staffAccessKey() != null && !config.staffAccessKey().trim().isEmpty();
	}

	private void saveStaffAccessKey(String staffAccessKey)
	{
		configManager.setConfiguration(
			"live-on-clan-messages",
			"staffAccessKey",
			staffAccessKey == null ? "" : staffAccessKey.trim());
		if (isStaff && staffAccessKey != null && !staffAccessKey.trim().isEmpty())
		{
			configurePolling();
			fetchLives();
			fetchRankRequests();
			fetchSentMessages();
			fetchMvpMembers();
			fetchClanTags();
		}
	}

	private synchronized void switchMessageCursorAccount(String playerName)
	{
		String accountKey = WomMembership.normalizePlayerName(playerName)
			.toLowerCase(java.util.Locale.ROOT);
		if (accountKey.equals(messageCursorAccount))
		{
			return;
		}
		if (!messageCursorAccount.isEmpty())
		{
			messageCursorByAccount.put(messageCursorAccount, lastMessageId);
			configManager.setConfiguration(
				"live-on-clan-messages",
				messageCursorConfigKey(messageCursorAccount),
				lastMessageId);
		}
		messageCursorAccount = accountKey;
		String storedCursor = configManager.getConfiguration(
			"live-on-clan-messages",
			messageCursorConfigKey(accountKey));
		lastMessageId = messageCursorByAccount.getOrDefault(
			accountKey,
			storedCursor == null ? "" : storedCursor);
		lastClearMarker = "";
		displayedPendingRankRequests.clear();
		if (panel != null)
		{
			panel.clearMessages();
		}
	}

	private HttpUrl serverBaseUrl()
	{
		String configuredUrl = config.serverUrl() == null ? "" : config.serverUrl().trim();
		if (configuredUrl.isEmpty() || configuredUrl.contains("example.invalid"))
		{
			configuredUrl = "http://127.0.0.1:8080";
		}
		return HttpUrl.parse(configuredUrl);
	}

	private void rebuildNavigationButton()
	{
		if (panel == null) return;
		if (navigationButton != null) clientToolbar.removeNavigation(navigationButton);
		navigationButton = NavigationButton.builder()
			.tooltip("Live on clan")
			.icon(createIcon())
			.panel(panel)
			.priority(config.sidebarIconPriority())
			.build();
		clientToolbar.addNavigation(navigationButton);
	}

	private BufferedImage createIcon()
	{
		BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		graphics.setColor(new Color(25, 90, 25));
		graphics.fillOval(3, 3, 26, 26);
		graphics.setColor(new Color(80, 220, 80));
		graphics.fillOval(5, 5, 22, 22);
		graphics.dispose();
		return icon;
	}


	private int storedInteger(String key, int fallback)
	{
		String value = configManager.getConfiguration("live-on-clan-messages", key);
		if (value == null) return fallback;
		try { return Integer.parseInt(value); }
		catch (NumberFormatException exception) { return fallback; }
	}

	private long storedLong(String key, long fallback)
	{
		String value = configManager.getConfiguration("live-on-clan-messages", key);
		if (value == null) return fallback;
		try { return Long.parseLong(value); }
		catch (NumberFormatException exception) { return fallback; }
	}

	private static String accountCacheKey(String accountName)
	{
		return accountName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}

	private static String messageCursorConfigKey(String accountName)
	{
		return "messageCursor.v1." + accountCacheKey(accountName);
	}

	private void fetchRankRequests()
	{
		if (!isStaff)
		{
			log.debug("Not staff, skipping rank requests fetch");
			return;
		}
		if (!hasStaffAccessKey())
		{
			if (panel != null) panel.setRankRequestsStatus("Configure a chave da staff");
			return;
		}
		getJson("admin/rank-requests", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch rank requests", exception);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						log.debug("Failed to fetch rank requests: " + response.code());
						if (response.code() == 403 && panel != null)
						{
							panel.setRankRequestsStatus("Verifique a chave da staff");
						}
						return;
					}
					String jsonBody = response.body().string();
					log.debug("Rank requests response: " + jsonBody);
					RankRequestsPanel.RankRequest[] requests = gson.fromJson(jsonBody, RankRequestsPanel.RankRequest[].class);
					java.util.List<RankRequestsPanel.RankRequest> requestList = requests == null
						? java.util.Collections.emptyList()
						: java.util.Arrays.asList(requests);
					log.debug("Deserialized " + requestList.size() + " rank requests");
					if (panel != null) panel.updateRankRequests(requestList);
					java.util.Set<String> currentPendingKeys = new java.util.HashSet<>();
					for (RankRequestsPanel.RankRequest rankRequest : requestList)
					{
						String key = rankRequestKey(rankRequest.playerName, rankRequest.rankName);
						currentPendingKeys.add(key);
						if (displayedPendingRankRequests.add(key))
						{
							String notification = rankRequest.playerName + " solicitou um rank: " + rankRequest.rankName;
							if (panel != null)
							{
								panel.addMessage(new ClanMessage(null, rankRequest.playerName, notification, "STAFF", false));
							}
							queueBroadcast(notification, false);
						}
					}
					displayedPendingRankRequests.retainAll(currentPendingKeys);
				}
			}
		});
		fetchRankRequestActivity();
	}

	private void fetchRankRequestActivity()
	{
		if (!isStaff || !hasStaffAccessKey())
		{
			return;
		}
		getJson("admin/rank-request-activity", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch rank request activity", exception);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						log.debug("Failed to fetch rank request activity: {}", response.code());
						if (response.code() == 403 && panel != null)
						{
							panel.setRankRequestsStatus("Verifique a chave da staff");
						}
						return;
					}
					RankRequestsPanel.RankRequestActivity[] activities = gson.fromJson(
						response.body().string(), RankRequestsPanel.RankRequestActivity[].class);
					if (panel != null)
					{
						panel.updateRankRequestActivity(activities == null
							? java.util.Collections.emptyList()
							: java.util.Arrays.asList(activities));
					}
				}
			}
		});
	}

	private void declineRankRequest(RankRequestsPanel.RankRequest request)
	{
		resolveRankRequest(request, "DECLINED");
	}

	private static String rankRequestKey(String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher matcher = RANK_REQUEST_MESSAGE_PATTERN.matcher(message);
		return matcher.matches() ? rankRequestKey(matcher.group("player"), matcher.group("rank")) : null;
	}

	private static String rankRequestKey(String playerName, String rankName)
	{
		return (playerName == null ? "" : playerName.trim().toLowerCase(java.util.Locale.ROOT))
			+ '\u0000'
			+ (rankName == null ? "" : rankName.trim().toLowerCase(java.util.Locale.ROOT));
	}

	private void resolveRankRequest(RankRequestsPanel.RankRequest request, String decision)
	{
		if (request == null || !isStaff)
		{
			if (panel != null) panel.setRankRequestsStatus("Acesso staff ausente");
			return;
		}
		HttpUrl base = serverBaseUrl();
		if (base == null)
		{
			if (panel != null) panel.setRankRequestsStatus("URL inválida");
			return;
		}
		java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("decision", decision);
		payload.put("playerName", authenticatedPlayerName);
		RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
		HttpUrl url = base.newBuilder()
			.addPathSegment("admin")
			.addPathSegment("rank-requests")
			.addPathSegment(Integer.toString(request.id))
			.addPathSegment("decision")
			.build();
		okHttpClient.newCall(requestBuilder(url).post(body).build()).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to resolve rank request", exception);
				if (panel != null) panel.setRankRequestsStatus("Falha ao atualizar solicitação");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					String responseBody = response.body() == null ? "" : response.body().string();
					if (!response.isSuccessful())
					{
						log.debug("Rank request decision failed: code={} body={}", response.code(), responseBody);
						if (panel != null) panel.setRankRequestsStatus("Erro " + response.code());
						return;
					}
					if (panel != null)
					{
						panel.setRankRequestsStatus("DECLINED".equals(decision)
							? "Solicitação recusada"
							: "Solicitação aceita");
					}
					fetchRankRequests();
				}
			}
		});
	}

	private void deleteRankRequest(int id)
	{
		if (!isStaff)
		{
			log.debug("Not staff, skipping rank request delete");
			return;
		}
		HttpUrl base = serverBaseUrl();
		if (base == null) return;
		okHttpClient.newCall(requestBuilder(base.newBuilder().addPathSegments("admin/rank-requests/" + id).build()).delete().build()).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to delete rank request", exception);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (response.isSuccessful())
					{
						fetchRankRequests();
					}
					else log.debug("Failed to delete rank request: " + response.code());
				}
			}
		});
	}

	private void confirmRankRequest(RankRequestsPanel.RankRequest request)
	{
		if (request == null || request.playerName == null || request.rankName == null)
		{
			if (panel != null) panel.setRankRequestsStatus("Solicita\u00E7\u00E3o inv\u00E1lida");
			return;
		}
		if (!isStaff)
		{
			if (panel != null) panel.setRankRequestsStatus("Acesso staff ausente");
			return;
		}
		HttpUrl base = serverBaseUrl();
		if (base == null)
		{
			if (panel != null) panel.setRankRequestsStatus("URL inválida");
			return;
		}
		String message = request.playerName + " foi promovido para " + request.rankName + "!";
		String rsnName = authenticatedPlayerName;
		if (rsnName == null || rsnName.isEmpty())
		{
			if (panel != null) panel.setRankRequestsStatus("Valide sua conta pelo WOM");
			return;
		}
		java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
		map.put("message", message);
		map.put("mode", "CLAN");
		map.put("playerName", rsnName);
		RequestBody body = RequestBody.create(JSON, gson.toJson(map));
		Request publishRequest = requestBuilder(base.newBuilder().addPathSegment("messages").build()).post(body).build();
		okHttpClient.newCall(publishRequest).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to publish promotion message", exception);
				if (panel != null) panel.setRankRequestsStatus("Falha ao publicar promoção");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					String responseBody = response.body() == null ? "" : response.body().string();
					if (!response.isSuccessful())
					{
						if (panel != null) panel.setRankRequestsStatus("Erro " + response.code());
						log.debug("Unable to publish promotion message: code={} body={}", response.code(), responseBody);
						return;
					}
					if (panel != null) panel.setRankRequestsStatus("Promo\u00E7\u00E3o publicada no clan channel");
					displayPublishedMessage(responseBody, "Staff", message, "CLAN", false);
					resolveRankRequest(request, "ACCEPTED");
					scheduleMessageRefresh();
					fetchSentMessages();
				}
			}
		});
	}


	private static final class RoleResponse
	{
		private String role;
	}

	private static final class PublishResponse
	{
		private String id;
	}

	@Provides
	ClanMessagesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanMessagesConfig.class);
	}
}
