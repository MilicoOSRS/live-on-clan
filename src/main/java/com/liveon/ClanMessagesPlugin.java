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
import net.runelite.api.Skill;
import net.runelite.api.ScriptID;
import net.runelite.api.WorldType;
import net.runelite.api.ItemContainer;
import net.runelite.api.Item;
import net.runelite.api.InventoryID;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.gameval.InterfaceID;
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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.events.ConfigChanged;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.ImageUtil;
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
	// Internal script called by rebuildchatbox after the vanilla clan rank is resolved.
	private static final int ADD_CHATBOX_MESSAGE_SCRIPT = 4483;
	private static final String WOM_USER_AGENT = "Live-On-RuneLite-Plugin";
	private static final int QUEST_CAPE_POINTS_REQUIRED = 339;

	// Mapping Portuguese rank names (lowercase) to clan title names used in-game
	private static final Map<String, String> RANK_TITLE_ALIASES = new LinkedHashMap<>();
	static {
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
	@Inject private DrawManager drawManager;
	@Inject private ChatMessageBuilder chatMessageBuilder;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private OkHttpClient okHttpClient;
	@Inject private Gson gson;
	@Inject private ClanMessagesConfig config;
	@Inject private ConfigManager configManager;

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
	private String verifiedAccount = "";
	private volatile String authenticatedPlayerName = "";
	private int lastCombatAchievementPoints = -1;
	private String combatAchievementAccount = "";
	private int lastQuestPoints = -1;
	private String questAccount = "";
	private boolean rankSyncCompleted;
	private boolean rankWidgetRefreshPending;
	private ScheduledFuture<?> rankRequestsPollingTask;
	private ScheduledFuture<?> mvpDropsPollingTask;
	// If true, the user manually disconnected and auto-verification should be paused until they click Verify
	private final java.util.Map<String, LiveChannel> onlineLiveChannels = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<String> mvpMembers = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private int liveChatIconId = -1;
	private int mvpChatIconId = -1;

	@Override
	protected void startUp()
	{
		executor = Executors.newSingleThreadScheduledExecutor();
		RankVisuals.registerChatIcons(chatIconManager);
		liveChatIconId = registerGreenLiveIcon();
		mvpChatIconId = chatIconManager.registerChatIcon(
			ImageUtil.loadImageResource(getClass(), "/chat/mvp.png"));
		panel = new ClanMessagesPanel(() -> publishDraft("BROADCAST"), () -> publishDraft("CLAN"), () -> verifyToken(true), this::clearMessages, this::refreshRanks, this::resetRanks, this::requestRank, this::fetchRankRequests, this::deleteRankRequest, this::confirmRankRequest, this::declineRankRequest, this::fetchSentMessages, this::deleteSentMessage, this::resendSentMessage, this::togglePinnedMessage, this::fetchLives, this::saveLiveChannel, this::deleteLiveChannel, this::fetchMvpMembers, this::saveMvpMember, this::deleteMvpMember, config.staffAccessKey(), this::saveStaffAccessKey);
		panel.clearRankDetails();
		if (config.enabled())
		{
			verifyToken();
		}
		navigationButton = NavigationButton.builder()
			.tooltip("Live on clan")
			.icon(createIcon())
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		configurePolling();
	}

	@Override
	protected void shutDown()
	{
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
		okhttp3.Call womCall = currentWomCall;
		if (womCall != null)
		{
			womCall.cancel();
			currentWomCall = null;
		}
		verifiedAccount = "";
		authenticatedPlayerName = "";
		isStaff = false;
		onlineLiveChannels.clear();
		mvpMembers.clear();
		liveChatIconId = -1;
		mvpChatIconId = -1;
		panel = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("live-on-clan-messages".equals(event.getGroup()))
		{
			if ("enabled".equals(event.getKey()))
			{
				if (config.enabled())
				{
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
					onlineLiveChannels.clear();
					mvpMembers.clear();
					if (panel != null)
					{
						panel.setAuthenticated(false, false);
						panel.setStatus("Conexao com o clan desativada");
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
		if (groupId != WidgetInfo.CHATBOX_MESSAGE_LINES.getGroupId())
		{
			return;
		}

		Widget menuWidget = client.getWidget(groupId, childId);
		Widget messageLines = menuWidget == null ? null : menuWidget.getParent();
		if (messageLines == null || messageLines.getId() != WidgetInfo.CHATBOX_MESSAGE_LINES.getPackedId())
		{
			return;
		}

		int messageChildIndex = (childId - WidgetInfo.CHATBOX_FIRST_MESSAGE.getChildId()) * 4 + 1;
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
			client.createMenuEntry(menuPosition++)
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
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			return;
		}
		String message = event.getMessage().replaceAll("<[^>]*>", "");
		if (message.contains("funny feeling") || message.contains("being followed") || message.contains("sneaking into your backpack"))
		{
			if (config.discordDropsEnabled() && client.getLocalPlayer() != null)
			{
				String player = client.getLocalPlayer().getName();
				if (config.discordDropScreenshot()) drawManager.requestNextFrameListener(image -> sendPetNotification(player, image));
				else sendPetNotification(player, null);
			}
			return;
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
		if (!isMvp && !isLive)
		{
			return;
		}
		StringBuilder badges = new StringBuilder();
		if (isMvp && mvpChatIconId >= 0)
		{
			badges.append("<img=").append(chatIconManager.chatIconIndex(mvpChatIconId)).append(">");
		}
		if (isLive && liveChatIconId >= 0)
		{
			badges.append("<img=").append(chatIconManager.chatIconIndex(liveChatIconId)).append(">");
		}
		if (badges.length() > 0 && !sender.startsWith(badges.toString()))
		{
			objectStack[1] = badges + sender;
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		notifyDiscordDrop(event.getNpc().getName(), event.getItems());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() != LootRecordType.NPC)
		{
			notifyDiscordDrop(event.getName(), event.getItems());
		}
	}

	private void notifyDiscordDrop(String source, Collection<ItemStack> items)
	{
		if (items.isEmpty() || isTemporaryLootWorld()) return;
		long totalValue = 0;
		for (ItemStack item : items)
		{
			long value = (long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
			totalValue += value;
		}
		if (config.statsEnabled() && totalValue >= 1_000_000L)
		{
			submitDropStats(items, source, totalValue);
		}
		long minimumValue = Math.max(0, config.discordDropMinimumValue());
		if (!config.discordDropsEnabled())
		{
			return;
		}
		List<String> notableItems = new ArrayList<>();
		int thumbnailItemId = -1;
		for (ItemStack item : items)
		{
			long value = (long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
			if (value < minimumValue)
			{
				continue;
			}
			if (thumbnailItemId < 0)
			{
				thumbnailItemId = item.getId();
			}
			String itemName = itemManager.getItemComposition(item.getId()).getName();
			String displayName = item.getQuantity() > 1 ? item.getQuantity() + " x " + itemName : itemName;
			notableItems.add(discordWikiLink(displayName, itemName));
		}
		if (notableItems.isEmpty())
		{
			return;
		}
		String playerName = client.getLocalPlayer() == null ? "Jogador" : client.getLocalPlayer().getName();
		String sourceName = source == null ? "Loot" : source;
		String description = "Just got " + joinNaturalLanguage(notableItems) + " from "
			+ discordWikiLink(sourceName, sourceName);
		final int dropThumbnailItemId = thumbnailItemId;
		if (config.discordDropScreenshot())
		{
			drawManager.requestNextFrameListener(image -> sendDiscordDrop(playerName, description, dropThumbnailItemId, image));
		}
		else
		{
			sendDiscordDrop(playerName, description, dropThumbnailItemId, null);
		}
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

	private static String joinNaturalLanguage(List<String> values)
	{
		if (values.size() == 1)
		{
			return values.get(0);
		}
		if (values.size() == 2)
		{
			return values.get(0) + " and " + values.get(1);
		}
		return String.join(", ", values.subList(0, values.size() - 1))
			+ ", and " + values.get(values.size() - 1);
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
		questAccount = "";
		rankWidgetRefreshPending = false;
		rankSyncCompleted = false;
		if (panel != null) panel.resetRanks();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INVENTORY.getId()
			|| event.getContainerId() == InventoryID.EQUIPMENT.getId())
		{
			refreshRanksAutomatically();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		int groupId = event.getGroupId();
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
		java.util.Set<String> items = new java.util.HashSet<>();
		java.util.Set<Integer> itemIds = new java.util.HashSet<>();
		collectItemNames(client.getItemContainer(InventoryID.INVENTORY), items);
		collectItemNames(client.getItemContainer(InventoryID.EQUIPMENT), items);
		collectItemIds(client.getItemContainer(InventoryID.INVENTORY), itemIds);
		collectItemIds(client.getItemContainer(InventoryID.EQUIPMENT), itemIds);
		log.debug("Ranks sync inventory/equipment item ids: {}", itemIds);
		int totalLevel = 0;
		for (Skill skill : Skill.values()) totalLevel += client.getRealSkillLevel(skill);
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
			|| zukHelm || containsItem(items, "Infernal cape");
		boolean fireCape = containsItemId(itemIds, ItemID.TZHAAR_CAPE_FIRE) || infernalCape;
		boolean quiver = containsAnyItemId(itemIds, ItemID.DIZANAS_QUIVER_UNCHARGED, ItemID.DIZANAS_QUIVER_CHARGED,
			ItemID.DIZANAS_QUIVER_INFINITE, ItemID.DIZANAS_QUIVER_UNCHARGED_TROUVER,
			ItemID.DIZANAS_QUIVER_CHARGED_TROUVER, ItemID.DIZANAS_QUIVER_INFINITE_TROUVER)
			|| dizanasMaxCape || zukHelm;
		String accountName = client.getLocalPlayer().getName();
		if (!accountName.equals(questAccount))
		{
			questAccount = accountName;
			lastQuestPoints = -1;
		}
		int questPoints = readQuestPoints();
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
		boolean questCape = questPoints >= QUEST_CAPE_POINTS_REQUIRED
			|| containsAnyItemId(itemIds, ItemID.SKILLCAPE_QP, ItemID.SKILLCAPE_QP_TRIMMED)
			|| containsAnyItem(items, "Quest point cape", "Quest point cape (t)");
		boolean diaryCape = containsAnyItemId(itemIds, ItemID.SKILLCAPE_AD, ItemID.SKILLCAPE_AD_TRIMMED)
			|| containsItem(items, "Achievement diary cape");
		boolean maxCapeItem = containsAnyItemId(itemIds, ItemID.SKILLCAPE_MAX, ItemID.SKILLCAPE_MAX_WORN)
			|| infernalMaxCape || dizanasMaxCape
			|| containsAnyItem(items, "Max cape", "Max cape (t)");
		boolean maxCape = totalLevel >= 2376 || maxCapeItem;
		int effectiveTotalLevel = maxCapeItem ? Math.max(totalLevel, 2376) : totalLevel;
		int effectiveQuestPoints = questCape ? Math.max(QUEST_CAPE_POINTS_REQUIRED, questPoints) : questPoints;
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
		java.util.List<String> summaryChecks = new ArrayList<>();
		summaryChecks.add("Total level: " + (effectiveTotalLevel > 0 ? "✓ " + effectiveTotalLevel : "— leitura pendente") + (effectiveTotalLevel >= 2376 ? " (2376 total)" : ""));
		summaryChecks.add(questSummary(questPoints, questCape));
		summaryChecks.add(itemStatus("Fire cape", fireCape));
		summaryChecks.add(itemStatus("Infernal cape", infernalCape));
		summaryChecks.add(itemStatus("Dizana's quiver", quiver));
		summaryChecks.add(combatAchievementSummary(combatAchievementPoints, tzTokHelm, vampyricHelm, zukHelm));
		summaryChecks.add("Diary cape: " + (diaryCape ? "✓" : "— requisito pendente"));
		String rank = highestPossibleRank(effectiveTotalLevel, effectiveQuestPoints, questCape, fireCape, infernalCape, quiver,
			diaryCape, maxCape, easyCombatAchievements, mediumCombatAchievements, hardCombatAchievements,
			eliteCombatAchievements, masterCombatAchievements, grandmasterCombatAchievements);
		String advice = rankSyncCompleted ? rankAdvice(rank, effectiveTotalLevel, effectiveQuestPoints, questCape,
			fireCape, infernalCape, quiver, diaryCape, maxCape, easyCombatAchievements, mediumCombatAchievements,
			hardCombatAchievements, eliteCombatAchievements, masterCombatAchievements, grandmasterCombatAchievements) : "";
		String displayedRank = rankSyncCompleted ? rank : "não sincronizado";
		String nextRank = rankSyncCompleted ? nextRankFromAdvice(rank, advice) : "em análise";
		panel.updateRanks(displayedRank, nextRank, summaryChecks, advice);
	}

	private static String nextRankFromAdvice(String simulatedRank, String advice)
	{
		if ("Coronel".equals(simulatedRank)) return "Rank máximo atingido";
		Matcher matcher = Pattern.compile("Próximo rank:\\s*([^<]+)").matcher(advice == null ? "" : advice);
		return matcher.find() ? matcher.group(1).trim() : "em análise";
	}

	private int readCombatAchievementPoints()
	{
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
		Widget questListPoints = client.getWidget(InterfaceID.Questlist.QUESTPOINTS);
		int points = parseQuestPoints(questListPoints, true);
		if (points >= 0) return points;
		Widget account = client.getWidget(InterfaceID.ACCOUNT);
		points = parseQuestPoints(account, false);
		if (points >= 0) return points;
		Widget summary = client.getWidget(InterfaceID.ACCOUNT_SUMMARY_SIDEPANEL);
		return parseQuestPoints(summary, false);
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

	private static String questSummary(int questPoints, boolean questCape)
	{
		String status = questPoints >= 0 ? "✓ " + questPoints + " Quest points" : questCape ? "✓ Quest cape detectada" : "— leitura pendente";
		return "Quest points: " + status + " | Abra Quests e clique em Sync";
	}

	private static String combatAchievementSummary(int points, boolean tzTokHelm, boolean vampyricHelm, boolean zukHelm)
	{
		String tier = points < 0 ? "— leitura pendente" : "✓ " + points + " pontos";
		if (points >= 2671 || zukHelm) tier += " — Grandmaster";
		else if (points >= 1945 || vampyricHelm) tier += " — Master";
		else if (points >= 1075 || tzTokHelm) tier += " — Elite";
		else if (points >= 419) tier += " — Hard";
		else if (points >= 161) tier += " — Medium";
		else if (points >= 41) tier += " — Easy";
		return "Combat achievements: " + tier + " | Abra Combat Achievements e clique em Sync";
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

	private static String currentClanRank(ClanChannel channel, String playerName)
	{
		if (channel == null) return "Clan channel não carregado";
		ClanChannelMember member = channel.findMember(playerName);
		if (member == null || member.getRank() == null) return "Membro não encontrado no clan channel";
		ClanRank rank = member.getRank();
		if (rank.equals(ClanRank.JMOD)) return "J-Mod";
		if (rank.equals(ClanRank.OWNER)) return "Owner";
		if (rank.equals(ClanRank.DEPUTY_OWNER)) return "Deputy owner";
		if (rank.equals(ClanRank.ADMINISTRATOR)) return "Administrator";
		if (rank.equals(ClanRank.GUEST)) return "Guest";
		return "Rank " + rank.getRank();
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

	private void submitDropStats(Collection<ItemStack> items, String source, long totalValue)
	{
		if (client.getLocalPlayer() == null) return;
		List<String> names = new ArrayList<>();
		for (ItemStack item : items) names.add(item.getQuantity() + "x " + itemManager.getItemComposition(item.getId()).getName());
		// Prepare payload; include playerName so server can verify via WOM
		java.util.Map<String, Object> dropPayload = new java.util.LinkedHashMap<>();
		dropPayload.put("playerName", client.getLocalPlayer().getName());
		dropPayload.put("item", String.join(", ", names));
		dropPayload.put("value", totalValue);
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

	private void sendDiscordDrop(String playerName, String description, int thumbnailItemId, java.awt.Image screenshot)
	{
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("description", description);
		embed.put("color", 7895160);
		embed.put("author", author(playerName));
		Map<String, Object> thumbnail = new LinkedHashMap<>();
		thumbnail.put("url", "https://static.runelite.net/cache/item/icon/" + thumbnailItemId + ".png");
		embed.put("thumbnail", thumbnail);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("content", "");
		payload.put("tts", false);
		payload.put("embeds", java.util.Collections.singletonList(embed));
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
				multipart.addFormDataPart("files[0]", "loot.png",
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

	private void sendPetNotification(String playerName, java.awt.Image screenshot)
	{
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("title", "Pet Drop");
		embed.put("description", "A pet was obtained.");
		embed.put("timestamp", Instant.now().toString());
		embed.put("color", 7895160);
		embed.put("footer", footer());
		embed.put("author", author(playerName));
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
		try
		{
			MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("payload_json", gson.toJson(payload));
			if (screenshot != null)
			{
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ImageIO.write((BufferedImage) screenshot, "png", output);
				multipart.addFormDataPart("files[0]", "loot.png", RequestBody.create(MediaType.parse("image/png"), output.toByteArray()));
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
		footer.put("text", "Live on");
		footer.put("icon_url", "https://i.imgur.com/m8ogvRt.png");
		return footer;
	}

	private static Map<String, Object> author(String playerName)
	{
		Map<String, Object> author = new LinkedHashMap<>();
		author.put("name", playerName);
		author.put("url", "https://wiseoldman.net/players/" + playerName.replace(" ", "%20"));
		return author;
	}

	private static String formatGp(long value)
	{
		if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000d);
		if (value >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000d);
		return Long.toString(value);
	}

	private static final class SilentCallback implements okhttp3.Callback
	{
		@Override public void onFailure(okhttp3.Call call, IOException e) { log.debug("Unable to submit statistics", e); }
		@Override public void onResponse(okhttp3.Call call, Response response) throws IOException { response.close(); }
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
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
		if (panel == null || !config.enabled() || serverBaseUrl() == null)
		{
			if (panel != null) panel.setStatus("Configure o servidor");
			return;
		}
		long interval = Math.max(5, config.pollIntervalSeconds());
		pollingTask = executor.scheduleAtFixedRate(this::fetchMessages, 0, interval, TimeUnit.SECONDS);
		mvpDropsPollingTask = executor.scheduleAtFixedRate(this::fetchMvpRankings, 2, 60, TimeUnit.SECONDS);
		if (isStaff)
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
					if (panel != null) panel.setStatus("Conectado");
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
		if (isStaff)
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
				if (isStaff && panel != null) panel.setMvpMembersStatus("Falha ao atualizar");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						if (isStaff && panel != null) panel.setMvpMembersStatus("Erro " + response.code());
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
					if (isStaff && panel != null) panel.updateMvpMembers(members);
					clientThread.invokeLater(() -> client.runScript(ScriptID.BUILD_CHATBOX));
				}
			}
		});
	}

	private void saveMvpMember(String rsn)
	{
		if (!isStaff || rsn == null || rsn.trim().isEmpty())
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
		if (!isStaff || member == null)
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

	private int registerGreenLiveIcon()
	{
		BufferedImage image = new BufferedImage(11, 11, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(new Color(20, 90, 35));
			graphics.fillOval(0, 0, 11, 11);
			graphics.setColor(new Color(45, 220, 85));
			graphics.fillOval(2, 2, 7, 7);
		}
		finally
		{
			graphics.dispose();
		}
		return chatIconManager.registerChatIcon(image);
	}

	private static String normalizeChatPlayerName(String playerName)
	{
		return WomMembership.normalizePlayerName(playerName).toLowerCase(java.util.Locale.ROOT);
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
Integer iconId = RankVisuals.registerChatIconForRank(chatIconManager, rankName);
if (iconId != null && iconId >= 0)
{
builder.img(iconId).append(" ");
appendChatText(builder, color, rankName);
return true;
}

RankVisuals.appendRankWithIcon(chatIconManager, builder, color, rankName);
return true;
}

Matcher promotion = PROMOTION_MESSAGE_PATTERN.matcher(message);
if (promotion.matches())
{
appendChatText(builder, color, promotion.group("player") + " foi promovido para ");
String rankName = promotion.group("rank");
Integer iconId = RankVisuals.registerChatIconForRank(chatIconManager, rankName);
if (iconId != null && iconId >= 0)
{
builder.img(iconId).append(" ");
appendChatText(builder, color, rankName);
appendChatText(builder, color, "!");
return true;
}

RankVisuals.appendRankWithIcon(chatIconManager, builder, color, rankName);
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

	private void appendMessagePrefixIcon(ChatMessageBuilder builder, String message)
{
// If the message is a structured rank message, use the rank mentioned as the prefix icon when possible.
String rankFromMessage = null;
if (message != null)
{		Matcher rankRequest = RANK_REQUEST_MESSAGE_PATTERN.matcher(message);
if (rankRequest.matches()) rankFromMessage = rankRequest.group("rank");
else
{			Matcher promotion = PROMOTION_MESSAGE_PATTERN.matcher(message);			if (promotion.matches()) rankFromMessage = promotion.group("rank");		}
}

// Prefer panel rank chat icon if available
		// If a rank was mentioned in the message, prefer the panel resource icon first (ensures identical image),
		// then fall back to clan title icons if no resource is available.
		if (rankFromMessage != null && !rankFromMessage.trim().isEmpty())
		{
			// Try resource-based icon (same one shown in the Solicitações panel)
			Integer regId = RankVisuals.registerChatIconForRank(chatIconManager, rankFromMessage);
			if (regId != null && regId >= 0)
			{
				log.debug("appendMessagePrefixIcon: using registered resource icon for rank '{}' -> iconId={}", rankFromMessage, regId);
				builder.img(regId).append(" ");
				appendChatText(builder, null, rankFromMessage);
				return;
			}
			// If no resource, try clan titles (aliases allowed)
			String rankKey = rankFromMessage.trim().toLowerCase(java.util.Locale.ROOT);
			net.runelite.api.clan.ClanSettings cs = client.getClanSettings();
			if (cs != null)
			{
				for (int r = -1; r <= 127; r++)
				{
					ClanTitle t = cs.titleForRank(new ClanRank(r));
					if (t == null || t.getName() == null) continue;
					String titleName = t.getName().trim().toLowerCase(java.util.Locale.ROOT);
					if (titleName.equals(rankKey) || (RANK_TITLE_ALIASES.containsKey(rankKey) && titleName.equals(RANK_TITLE_ALIASES.get(rankKey).toLowerCase(java.util.Locale.ROOT))))
					{
						int iconNumber = chatIconManager.getIconNumber(t);
						int iconIndex = chatIconManager.chatIconIndex(iconNumber);
						log.debug("appendMessagePrefixIcon: matched clan title '{}' for rank '{}' -> iconIndex={}", t.getName(), rankFromMessage, iconIndex);
						if (iconIndex >= 0)
						{
							builder.img(iconIndex).append(" ");
							return;
						}
					}
				}
			}
		}
		
if (panel != null)
{
String currentRank = panel.getCurrentRank();
// If the message mentioned a rank, prefer using that icon for the prefix.
if (rankFromMessage != null && !rankFromMessage.trim().isEmpty())
{			Integer msgRankIconId = RankVisuals.chatIconIndexFor(chatIconManager, rankFromMessage);
log.debug("appendMessagePrefixIcon: rankFromMessage='{}' -> chatIconIdMap match={}", rankFromMessage, msgRankIconId);
			// If no registered chat icon, try to find a clan title with the same name and use its chat icon
			net.runelite.api.clan.ClanSettings settingsLocal = client.getClanSettings();
			if (settingsLocal != null)
			{
				// Titles will be checked later via members; keep settingsLocal non-null for that path.
			}
			// Fallback: try to register resource icon used by panel dynamically
			
		}
		if (currentRank != null && !currentRank.trim().isEmpty())
{
Integer rankIconId = RankVisuals.chatIconIndexFor(chatIconManager, currentRank);
log.debug("appendMessagePrefixIcon: currentRank='{}' -> chatIconIdMap match={}", currentRank, rankIconId);
log.debug("Registered rank icons: {}", RankVisuals.chatIconIds());
if (rankIconId != null && rankIconId >= 0)
{				builder.img(rankIconId).append(" ");				return;			}		}
}

// Fallback: use clan title icon (try to use the player's clan title)
// If a rank was mentioned in the message, try to register its resource icon now (ensures the same icon as the panel).
if (rankFromMessage != null && !rankFromMessage.trim().isEmpty())
{
	javax.swing.Icon resourceIcon = RankVisuals.rankIconFor(rankFromMessage);
	if (resourceIcon instanceof javax.swing.ImageIcon)
	{
		try
		{
			java.awt.Image img = ((javax.swing.ImageIcon) resourceIcon).getImage();
			int w = img.getWidth(null);
			int h = img.getHeight(null);
			if (w > 0 && h > 0)
			{
				java.awt.image.BufferedImage buffered = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
				java.awt.Graphics2D g = buffered.createGraphics();
				g.drawImage(img, 0, 0, null);
				g.dispose();
				int regId = chatIconManager.registerChatIcon(buffered);
				if (regId >= 0)
				{
					builder.img(regId).append(" ");
					return;
				}
			}
		}
		catch (RuntimeException | Error ex)
		{
			log.debug("Failed to register resource icon for rank {}", rankFromMessage, ex);
		}
	}
}
net.runelite.api.clan.ClanSettings settings = client.getClanSettings();
ClanTitle title = null;
if (settings != null && client.getLocalPlayer() != null)
{			net.runelite.api.clan.ClanMember member = settings.findMember(client.getLocalPlayer().getName());
if (member != null && member.getRank() != null)
{				title = settings.titleForRank(member.getRank());
}
}
// Last-resort fallback: a default clan title (previous behavior)
if (title == null)
{			title = settings == null ? null : settings.titleForRank(ClanRank.DEPUTY_OWNER);
}
if (title == null)
{			return;		}
int iconNumber = chatIconManager.getIconNumber(title);
		int iconIndex = chatIconManager.chatIconIndex(iconNumber);
if (iconIndex >= 0)
{			builder.img(iconIndex).append(" ");
}
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
						if (panel != null) panel.setSentMessagesStatus("Erro " + response.code());
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
							panel.setStatusSuccess("Solicita\u00E7\u00E3o de rank enviada a staff!");
							// Rank requests are staff-only. Do not simulate the STAFF
							// message for the requesting member.
							scheduleMessageRefresh();
							if (isStaff) fetchRankRequests();
						}
						else panel.setStatus("Erro " + response.code());
					}
				}
			}
		});
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
				panel.setStatus("Ative Conectar ao clan nas configuracoes");
			}
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (client.getLocalPlayer() == null)
			{
				if (panel != null) panel.setStatus("Jogador não disponível");
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
					switchMessageCursorAccount(rsn);
					authenticatedPlayerName = rsn;
					configurePolling();
					if (panel != null) { panel.setAccessMessage(""); panel.setAuthenticated(true, staff); }
					if (panel != null) panel.setStatus(staff ? "Acesso staff confirmado (WOM)" : "Acesso liberado (membro WOM)");
					if (staff)
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
								switchMessageCursorAccount(rsn);
								authenticatedPlayerName = rsn;
								configurePolling();
								if (panel != null) { panel.setAccessMessage(""); panel.setAuthenticated(true, staff); }
								if (panel != null) panel.setStatus(staff ? "Acesso staff confirmado (WOM)" : "Acesso liberado (membro WOM)");
								if (staff)
								{
									fetchRankRequests();
									fetchSentMessages();
								}
							}
							else
							{
								authenticatedPlayerName = "";
								isStaff = false;
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

	private void saveStaffAccessKey(String staffAccessKey)
	{
		configManager.setConfiguration(
			"live-on-clan-messages",
			"staffAccessKey",
			staffAccessKey == null ? "" : staffAccessKey.trim());
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
		}
		messageCursorAccount = accountKey;
		lastMessageId = messageCursorByAccount.getOrDefault(accountKey, "");
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

	private static String accountCacheKey(String accountName)
	{
		return accountName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}

	private void fetchRankRequests()
	{
		if (!isStaff)
		{
			log.debug("Not staff, skipping rank requests fetch");
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
