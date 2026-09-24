package com.liveon;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
import net.runelite.api.gameval.NpcID;
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
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.events.ConfigChanged;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.plugins.loottracker.LootTrackerConfig;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
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
@PluginDependency(LootTrackerPlugin.class)
public class ClanMessagesPlugin extends Plugin
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final Pattern RANK_REQUEST_MESSAGE_PATTERN = Pattern.compile("(?<player>.+) solicitou um rank: (?<rank>.+)");
	private static final Pattern PROMOTION_MESSAGE_PATTERN = Pattern.compile("(?:Promo\u00E7\u00E3o: )?(?<player>.+?) foi promovido para (?<rank>.+)!");
	private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b(?:https?://|twitch\\.tv/)[^\\s<>]+");
	private static final Pattern PET_TRIGGER_PATTERN = Pattern.compile(
		"You (?:have a funny feeling like you|feel something weird sneaking).*", Pattern.CASE_INSENSITIVE);
	private static final Pattern PET_CLAN_PATTERN = Pattern.compile(
		"\\b(?<user>[\\w\\s]+) (?:has a funny feeling like .+ followed|feels something weird sneaking into .+ backpack|feels like .+ acquired something special): (?:(?<pet>.+) at (?<milestone>.+)|(?<petOnly>.+))",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PET_UNTRADEABLE_PATTERN = Pattern.compile("Untradeable drop: (.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern PET_COLLECTION_PATTERN = Pattern.compile(
		"(?:New item added to your collection log|Collection log):\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLUE_COMPLETION_PATTERN = Pattern.compile(
		"You have completed (?<count>[0-9,]+) (?<tier>\\w+) Treasure Trails?\\.",
		Pattern.CASE_INSENSITIVE);
	private static final String PB_TEAM_SIZE = "(?<teamsize>\\d+(?:\\+|-\\d+)? players?|Solo)";
	private static final Pattern PB_KILLCOUNT_PATTERN = Pattern.compile(
		"Your (?<pre>completion count for |subdued |completed )?(?<boss>.+?) (?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?(?<kc>[0-9,]+)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PB_RAID_TOTAL_PATTERN = Pattern.compile(
		"^(?<boss>Tombs of Amascut(?::? (?:Expert Mode|Entry Mode))?) total completion time: (?<pb>[0-9:]+(?:\\.[0-9]+)?)\\s*\\(new personal best\\)", Pattern.CASE_INSENSITIVE);
	// Theatre of Blood's total-completion line never repeats "(Entry Mode)"/"(Hard Mode)" -
	// only the preceding wave-complete line does - so its mode has to come from whatever the
	// room-time message alongside it just stored in pendingPbMode.
	private static final Pattern PB_TOB_TOTAL_PATTERN = Pattern.compile(
		"^(?<boss>Theatre of Blood) total completion time: (?<pb>[0-9:]+(?:\\.[0-9]+)?)\\s*\\(new personal best\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern PB_NEW_TIME_PATTERN = Pattern.compile(
		"(?i)(?:(?:Fight |Lap |Challenge |Corrupted challenge )?duration:|Subdued in|(?<!total )completion time:).*?(?<pb>[0-9:]+(?:\\.[0-9]+)?)\\s*\\(new personal best\\)");
	// Theatre of Blood's wave-complete line is the only message that names the difficulty
	// (e.g. "Wave 'The Final Challenge' (Entry Mode) complete!"); the total/challenge time
	// lines that follow in the same chat message never repeat it, so a boss-less pending PB
	// would default to Normal mode unless this is captured alongside the time.
	private static final Pattern PB_MODE_PATTERN = Pattern.compile(
		"\\((?<mode>Entry Mode|Hard Mode|Expert Mode|Challenge Mode)\\)", Pattern.CASE_INSENSITIVE);
	// Same difficulty tag, but matched on every wave-complete line regardless of whether that
	// particular wave set a new personal best - unlike PB_MODE_PATTERN's callers, which only run
	// once a "(new personal best)" has already been confirmed.
	private static final Pattern WAVE_COMPLETE_PATTERN = Pattern.compile(
		"^Wave '.*?' \\((?<mode>Entry Mode|Hard Mode|Expert Mode|Challenge Mode)\\) complete!",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PB_RAID_PATTERN = Pattern.compile(
		"Team size:.*?" + PB_TEAM_SIZE + ".*?Duration:.*?(?<pb>[0-9:]+(?:\\.[0-9]+)?)\\s*\\(new personal best\\)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern CLAN_PB_ANNOUNCEMENT_PATTERN = Pattern.compile(
		"^(?<player>.+?) has achieved a new (?<activity>.+?) personal best: (?<pb>[0-9:]+(?:\\.[0-9]+)?)\\.?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern CLAN_PB_TEAM_SIZE_PATTERN = Pattern.compile(
		"\\s*\\(Team Size:\\s*(?<size>Solo|\\d+(?:\\+|-\\d+)?(?:\\s*players?)?)\\)",
		Pattern.CASE_INSENSITIVE);
	private static final long RAID_LOOT_CONTEXT_WAIT_MILLIS = 1500L;
	private static final long CLUE_WIDGET_FALLBACK_WAIT_MILLIS = 1500L;
	private static final long MVP_DROP_MINIMUM_VALUE = 1_000_000L;
	private static final String LOW_VALUE_CLUE_TEST_PROPERTY = "liveon.lowValueClueTest";
	private static final String LOW_VALUE_DROP_TEST_PROPERTY = "liveon.lowValueDropTest";
	private static final Pattern ADVENTURE_LOG_TITLE_PATTERN = Pattern.compile("The Exploits of (.+)");
	private static final Pattern ADVENTURE_LOG_PB_PATTERN = Pattern.compile(
		"^Fastest (?<kind>kill|run|Room time|Overall time)"
			+ "(?:\\s*-\\s*\\(Team size:\\s*(?<details>[^)]+)\\))?\\s*:\\s*"
			+ "(?<time>[0-9:]+(?:\\.[0-9]+)?)?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern ADVENTURE_LOG_TIME_ONLY_PATTERN = Pattern.compile(
		"^(?<time>[0-9:]+(?:\\.[0-9]+)?)$");
	private static final int PET_DETAILS_WAIT_TICKS = 5;
	static final String DISCORD_LOOT_ATTACHMENT = "loot.png";
	static final String DISCORD_PET_ATTACHMENT = "pet.png";
	private static final int DISCORD_EMBED_DESCRIPTION_LIMIT = 4096;
	// Internal script called by rebuildchatbox after the vanilla clan rank is resolved.
	private static final int ADD_CHATBOX_MESSAGE_SCRIPT = 4483;
	private static final String WOM_USER_AGENT = "Live-On-RuneLite-Plugin";
	// Drop exceptions modelled after Dink's loot filters. A trailing '*' matches
	// item variants. Official RuneLite loot events remain the source of truth.
	private static final List<String> DROP_ITEM_ALLOWLIST = java.util.Arrays.asList(
		"enhanced crystal weapon seed",
		"crystal armour seed",
		"mokhaiotl cloth",
		"sanguine dust",
		"metamorphic dust",
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
	private static final Set<Integer> SERVER_LOOT_NPC_IDS = Set.of(
		NpcID.YAMA,
		NpcID.HESPORI,
		NpcID.SAILING_BULL_SHARK_DEAD,
		NpcID.SAILING_HAMMERHEAD_SHARK_DEAD,
		NpcID.SAILING_TIGER_SHARK_DEAD,
		NpcID.SAILING_GREAT_WHITE_SHARK_DEAD,
		NpcID.SAILING_NARWHAL_DEAD,
		NpcID.SAILING_ORCA_DEAD,
		NpcID.SAILING_PYGMY_KRAKEN_DEAD,
		NpcID.SAILING_SPINED_KRAKEN_DEAD,
		NpcID.SAILING_ARMOURED_KRAKEN_DEAD,
		NpcID.SAILING_VAMPYRE_KRAKEN_DEAD,
		NpcID.SAILING_EAGLE_RAY_DEAD,
		NpcID.SAILING_BUTTERFLY_RAY_DEAD,
		NpcID.SAILING_STINGRAY_DEAD,
		NpcID.SAILING_MANTA_RAY_DEAD,
		NpcID.SAILING_OSPREY_DEAD,
		NpcID.SAILING_ALBATROSS_DEAD,
		NpcID.SAILING_FRIGATEBIRD_DEAD,
		NpcID.SAILING_TERN_DEAD,
		NpcID.SAILING_SEA_MOGRE_DEAD,
		NpcID.SAILING_DOLPHIN_DEAD,
		NpcID.SAILING_VEILED_KRAKEN_DEAD,
		NpcID.MAGGOT_KING,
		NpcID.MAGGOT_KING_CORPSE,
		16305, 16306, 16307, 16308, 16309, 16310, 16311, 16312, 16313, 16314, 16315
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
	@Inject private OverlayManager overlayManager;

	private ScheduledExecutorService executor;
	private volatile DropDeliveryClient dropDeliveryClient;
	private DropDiagnosticJournal dropDiagnosticJournal;
	private DropFrameCapture dropFrameCapture;
	private final DropScreenshotEncoder dropScreenshotEncoder = new DropScreenshotEncoder();
	private final NpcLootEventGate npcLootEventGate = new NpcLootEventGate();
	private ClanMessagesPanel panel;
	private NavigationButton navigationButton;
	// WOM membership cache: rsn (lowercase) -> CacheEntry
	private final java.util.Map<String, CacheEntry> womCache = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long WOM_CACHE_TTL_SECONDS = 3600; // 1 hour
	private static final long WOM_NEGATIVE_CACHE_TTL_SECONDS = 60;
	private volatile okhttp3.Call currentWomCall = null;
	private static final int VERIFY_COOLDOWN_SECONDS = 30;
	private static final long RANK_BANK_SETTLE_MILLIS = 1000L;

	private static final class CacheEntry { final boolean member; final String role; final long expiresAtMillis; CacheEntry(boolean m, String r, long e) { member=m; role=r; expiresAtMillis=e; } }
	private ScheduledFuture<?> pollingTask;
	private String lastMessageId = "";
	private volatile boolean messageSessionInitialized = false;
	private final AtomicLong messageSessionGeneration = new AtomicLong();
	private final AtomicLong connectionSessionGeneration = new AtomicLong();
	private String messageCursorAccount = "";
	private final java.util.Map<String, String> messageCursorByAccount = new java.util.concurrent.ConcurrentHashMap<>();
	private String lastClearMarker = "";
	private final AtomicBoolean messageFetchInFlight = new AtomicBoolean(false);
	private final AtomicBoolean eventOverlayFetchInFlight = new AtomicBoolean(false);
	private volatile EventOverlayState eventOverlayState = new EventOverlayState();
	private ClanEventOverlay eventOverlay;
	private ManualBingoClient manualBingo;
	private final AtomicBoolean pbCategoriesFetchInFlight = new AtomicBoolean(false);
	private final AtomicLong pbRankingRequestGeneration = new AtomicLong();
	private final AtomicBoolean rankRequestsFetchInFlight = new AtomicBoolean(false);
	private volatile boolean rankRequestsSessionInitialized = false;
	private final java.util.Set<String> locallyDisplayedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.Set<String> deliveredPinnedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.Set<String> displayedPendingRankRequests = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.Set<String> sessionRankNotifications = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private volatile boolean isStaff = false;
	private boolean canPublishBroadcast = false;
	private String verifiedAccount = "";
	private final MembershipRecovery membershipRecovery = new MembershipRecovery();
	private final RecoveryNotice recoveryNotice = new RecoveryNotice();
	private String recoveryNoticeAccount = "";
	private final java.util.Set<String> queuedPbSignatures = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
	private ScheduledFuture<?> rankBankRefreshTask;
	private final AtomicLong rankBankRefreshGeneration = new AtomicLong();
	private int lastCombatAchievementRankRefreshTick = -1000;
	private final java.util.Set<String> rankBankItems = new java.util.HashSet<>();
	private final java.util.Set<Integer> rankBankItemIds = new java.util.HashSet<>();
	private String rankBankAccount = "";
	private boolean rankBankLoaded;
	private int lastObservedRankTotalLevel = -1;
	private volatile boolean rankRequestStatusKnown;
	private volatile boolean rankRequestPending;
	private boolean adventureLogMenuLoaded;
	private boolean adventureLogCountersLoaded;
	private String adventureLogOwner;
	private String lastAdventureLogContentSignature = "";
	private int adventureLogRetryTick;
	private String pendingPbBoss;
	private double pendingPbSeconds = -1;
	private int pendingPbTeamSize;
	private String pendingPbMode;
	// Theatre of Blood's wave-complete line carries the difficulty even when that specific wave
	// didn't set a new room-time PB (no "(new personal best)" tag, so parseChatNewPb never sees
	// it). Tracked separately with its own tick so the total-completion message can still fall
	// back to it when only the Overall PB improved - but only within a tight, same-event window,
	// never across unrelated later raids.
	private String lastWaveMode;
	private int lastWaveModeTick = -1;
	private static final int LAST_WAVE_MODE_MAX_TICKS = 5;
	private int pendingPbTick = -1;
	// Chambers of Xeric and Theatre of Blood never send a correlating kill-count chat message,
	// so a raid completion broadcast ("Duration: ... (new personal best)", or "Team size: ...
	// Duration: ...") can sit unresolved as pendingPbSeconds with no boss name. Give the actual
	// reward chest - which unambiguously names the raid - a wide window to rescue it: measured
	// gaps between the chat message and the chest were ~8 ticks (CoX) and ~32 ticks (ToB), so
	// 30 was already too tight and lost a real ToB PB. This must stay generous, not tight,
	// since the cost of a wider window is holding stale state a bit longer, while the cost of
	// too narrow a window is silently losing a PB with no trace.
	private static final int PENDING_RAID_PB_MAX_TICKS = 80;
	private final java.util.Set<String> submittedPbSignatures = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private int combatAchievementPbScanTicks;
	private String visibleCombatAchievementPage = "";
	private int bossStatisticsBoardScanTicks;
	private final java.util.LinkedHashSet<Integer> bossStatisticsBoardGroupIds = new java.util.LinkedHashSet<>();
	private static final int DROP_DEDUP_TICKS = 8;
	private final java.util.Map<String, Integer> recentBingoDrops = boundedDropMap();
	private final RaidLootContext raidLootContext = new RaidLootContext();
	private String lastLootCountSource = "";
	private int lastLootCount = -1;
	private int lastLootCountTick = -1000;
	private String pendingClueTier = "";
	private int pendingClueCount = -1;
	private int pendingClueTicks;
	private boolean pendingClueReward;
	private boolean lowValueTestNoticeShown;
	private final ClueRewardGate clueRewardGate = new ClueRewardGate();

	private static <T> java.util.Map<String, T> boundedDropMap()
	{
		return new java.util.LinkedHashMap<String, T>()
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, T> eldest)
			{
				return size() > 128;
			}
		};
	}

	static final class BingoDrop
	{
		final String itemName;
		final int quantity;
		final Long totalValue;
		final Integer itemId;
		final String source;
		final String category;

		private BingoDrop(String itemName, int quantity, Long totalValue, Integer itemId,
			String source, String category)
		{
			this.itemName = itemName;
			this.quantity = Math.max(1, quantity);
			this.totalValue = totalValue;
			this.itemId = itemId;
			this.source = source;
			this.category = category;
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
		if (Boolean.getBoolean(LOW_VALUE_DROP_TEST_PROPERTY))
		{
			log.info("Live On local low-value drop test mode enabled");
		}
		executor = Executors.newSingleThreadScheduledExecutor();
		dropDeliveryClient = new DropDeliveryClient(okHttpClient, clientThread, executor);
		dropDiagnosticJournal = new DropDiagnosticJournal(
			net.runelite.client.RuneLite.RUNELITE_DIR.toPath().resolve("live-on-clan/drop-diagnostics.log"), executor);
		dropDeliveryClient.setDiagnosticJournal(dropDiagnosticJournal);
		dropDeliveryClient.setDeliveredCallback(destination -> {
			if ("MVP drop".equals(destination)) fetchMvpDrops();
			if ("PB".equals(destination)) fetchPbCategories();
		});
		dropDeliveryClient.setProgressListener(this::onDeliveryProgress);
		dropDeliveryClient.setOutbox(new DropOutbox(
			net.runelite.client.RuneLite.RUNELITE_DIR.toPath().resolve("live-on-clan/pending-drops"), gson));
		dropFrameCapture = new DropFrameCapture(executor,
			action -> clientThread.invokeLater(action), drawManager::requestNextFrameListener);
		RankVisuals.registerChatIcons(chatIconManager);
		clanLiveBadgeDecorator = new ClanLiveBadgeDecorator(client, this);
		eventOverlay = new ClanEventOverlay(this);
		overlayManager.add(eventOverlay);
		net.runelite.client.util.AsyncBufferedImage mvpDropIcon = itemManager.getImage(ItemID.COINS_10000);
		panel = new ClanMessagesPanel(() -> publishDraft("BROADCAST"), () -> publishDraft("CLAN"), () -> verifyToken(true), this::clearMessages, this::refreshRanks, this::resetRanks, this::requestRank, this::fetchRankRequests, this::deleteRankRequest, this::confirmRankRequest, this::declineRankRequest, this::fetchSentMessages, this::deleteSentMessage, this::resendSentMessage, this::togglePinnedMessage, this::publishPanelNotice, this::removePanelNotice, this::saveEventOverlay, this::fetchLives, this::saveLiveChannel, this::deleteLiveChannel, this::fetchMvpMembers, this::saveMvpMember, this::deleteMvpMember, this::fetchClanTags, this::createClanTag, this::addClanTagMember, this::deleteClanTag, this::removeClanTagMember, this::fetchPbCategories, this::fetchPbRanking, config.staffAccessKey(), this::saveStaffAccessKey, mvpDropIcon);
		manualBingo = new ManualBingoClient(gson, panel,
			() -> config.enabled() ? authenticatedPlayerName : "", () -> isStaff && hasStaffAccessKey(),
			() -> isDeputyOwner, (path, body) -> {
				HttpUrl base = serverBaseUrl();
				if (base == null) return null;
				Request.Builder request = requestBuilder(base.newBuilder().addPathSegments(path).build());
				if (body == null) request.get(); else request.post(RequestBody.create(JSON, body));
				return okHttpClient.newCall(request.build());
			});
		panel.setPbParticipationEnabled(config.pbRankingEnabled());

		panel.setMvpParticipationEnabled(config.statsEnabled());
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
		cancelRankBankRefresh();
		if (dropDeliveryClient != null)
		{
			dropDeliveryClient.close();
			dropDeliveryClient = null;
		}
		if (dropFrameCapture != null)
		{
			dropFrameCapture.close();
			dropFrameCapture = null;
		}
		dropScreenshotEncoder.clear();
		connectionSessionGeneration.incrementAndGet();
		if (manualBingo != null) { manualBingo.close(); manualBingo = null; }
		resetPendingPet();
		recentBingoDrops.clear();
		clearPendingCaptures();
		lastLootCountSource = "";
		lastLootCount = -1;
		lastLootCountTick = -1000;
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
		if (eventOverlay != null)
		{
			overlayManager.remove(eventOverlay);
			eventOverlay = null;
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
		recoveryNotice.reset();
		recoveryNoticeAccount = "";
		queuedPbSignatures.clear();
		membershipRecovery.accountChanged();
		submittedPbSignatures.clear();
		visibleCombatAchievementPage = "";
		isStaff = false;
		isDeputyOwner = false;
		canPublishBroadcast = false;
		onlineLiveChannels.clear();
		mvpMembers.clear();
		clanTagsByPlayer.clear();
		knownClanTagMarkup.clear();
		eventOverlayState = new EventOverlayState();
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
			if ("pbRankingEnabled".equals(event.getKey()) && panel != null)
			{
				panel.setPbParticipationEnabled(config.pbRankingEnabled());
			}
			if ("statsEnabled".equals(event.getKey()) && panel != null)
			{
				panel.setMvpParticipationEnabled(config.statsEnabled());
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
			if ("statsEnabled".equals(event.getKey()) || "discordDropsEnabled".equals(event.getKey())
				|| "pbRankingEnabled".equals(event.getKey()))
				clientThread.invokeLater(this::recoverPendingDrops);
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
		String navigable = url.regionMatches(true, 0, "twitch.tv/", 0, 10) ? "https://" + url : url;
		HttpUrl parsed = HttpUrl.parse(navigable);
		return parsed != null && ("http".equals(parsed.scheme()) || "https".equals(parsed.scheme())) ? navigable : null;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// SPAM is the type OSRS/RuneLite gives many kill-count/completion-count messages
		// (e.g. "Your completed Theatre of Blood: Entry Mode count is: 69.") - the same
		// category some players have chat filters hiding. It carries real PB correlation
		// data our tight regexes below already gate on, so it's safe to fold in here.
		if (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION
			&& event.getType() != ChatMessageType.CLAN_MESSAGE
			&& event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage()).replace('\u00A0', ' ').trim();
		if (isPbParticipationEnabled() && client.getLocalPlayer() != null)
		{
			safeCapturePb(() -> {
				Map<String, Object> announcedPb = parseClanPbAnnouncement(message, client.getLocalPlayer().getName());
				if (announcedPb != null)
				{
					String boss = (String) announcedPb.get("boss");
					// The clan-wide announcement always reports the room/challenge-time PB (the
					// same stat ChatCommandsPlugin tracks as "the" personal best) - ToA/ToB's
					// separate "Overall" total is never broadcast to the clan this way.
					submitPb(boss, (String) announcedPb.get("mode"),
						(Integer) announcedPb.get("teamSize"), (Double) announcedPb.get("seconds"),
						roomTimeType(boss));
				}
			});
		}
		if (event.getType() == ChatMessageType.GAMEMESSAGE
			|| event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION
			|| event.getType() == ChatMessageType.SPAM)
		{
			rememberLootCount(message);
			rememberClueCompletion(message);
			if (isPbParticipationEnabled())
			{
				safeCapturePb(() -> {
					rememberWaveMode(message);
					capturePersonalBest(message);
				});
			}
		}
		if (event.getType() == ChatMessageType.GAMEMESSAGE
			&& !"runelite".equalsIgnoreCase(event.getName()))
		{
			Map.Entry<String, Integer> exceptionalLoot = exceptionalGameMessageLoot(message);
			if (exceptionalLoot != null)
			{
				detectLoot(exceptionalLoot.getKey(),
					java.util.Collections.singletonList(new ItemStack(exceptionalLoot.getValue(), 1)),
					LootRecordType.EVENT.name(), null, null);
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
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.CLAN_SIDEPANEL_DRAW
			&& clanLiveBadgeDecorator != null)
		{
			clanLiveBadgeDecorator.refreshAfterRedraw();
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
		if (objectStack == null || objectStackSize <= 0)
		{
			return;
		}
		for (int stackIndex = Math.min(objectStackSize, objectStack.length) - 1; stackIndex >= 0; stackIndex--)
		{
			if (!(objectStack[stackIndex] instanceof String)) continue;
			String message = (String) objectStack[stackIndex];
			String plainMessage = Text.removeTags(message).replace('\u00A0', ' ').trim();
			String lowerMessage = plainMessage.toLowerCase(java.util.Locale.ROOT);
			if (!isDecoratableClanAchievement(lowerMessage)) continue;

			java.util.Set<String> decoratedPlayers = new java.util.HashSet<>(mvpMembers);
			decoratedPlayers.addAll(clanTagsByPlayer.keySet());
			if (config.liveStatusEnabled())
			{
				decoratedPlayers.addAll(onlineLiveChannels.keySet());
			}
			for (String playerKey : decoratedPlayers)
			{
				if (!lowerMessage.startsWith(playerKey + " ")) continue;
				boolean isMvp = mvpMembers.contains(playerKey);
				boolean isLive = config.liveStatusEnabled() && onlineLiveChannels.containsKey(playerKey);
				StringBuilder badges = new StringBuilder();
				if (isMvp) badges.append(" <col=ffc628>MVP</col>");
				if (isLive) badges.append(" <col=96ffaa>LIVE</col>");
				badges.append(clanTagBadges(playerKey));
				int insertionIndex = originalIndexAfterVisiblePrefix(message, playerKey);
				if (badges.length() > 0 && insertionIndex >= 0 && !message.contains(badges.toString()))
				{
					objectStack[stackIndex] = message.substring(0, insertionIndex) + badges
						+ message.substring(insertionIndex);
				}
				return;
			}
		}
	}

	static boolean isDecoratableClanAchievement(String lowerMessage)
	{
		if (lowerMessage == null || lowerMessage.isEmpty()) return false;
		return lowerMessage.contains(" received a new collection log item")
			|| lowerMessage.contains(" has received a new collection log item")
			|| lowerMessage.contains(" has a funny feeling like")
			|| lowerMessage.contains(" feels something weird sneaking into")
			|| lowerMessage.contains(" received a drop:")
			|| lowerMessage.contains(" received a valuable drop:")
			|| lowerMessage.contains(" received a clue item:")
			|| lowerMessage.contains(" received special loot from a raid:")
			|| (lowerMessage.contains(" has achieved a new ")
				&& lowerMessage.contains(" personal best:"));
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

	@Subscribe(priority = 1)
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		String source = Text.removeTags(event.getComposition().getName());
		log.debug("Server NPC loot event: source={}, items={}", source, event.getItems().size());
		if (!usesServerNpcLoot(event.getComposition().getId(), source)) return;
		npcLootEventGate.recordPrimary(localRecordAccount(), source, event.getItems(), client.getTickCount());
		detectLoot(source, event.getItems(), "NPC", event.getComposition().getId(), null);
	}

	static Map.Entry<String, Integer> exceptionalGameMessageLoot(String message)
	{
		if ("You have found a Pharaoh's sceptre! It fell on the floor.".equals(message))
			return new java.util.AbstractMap.SimpleImmutableEntry<>("Pyramid Plunder", ItemID.PHARAOHS_SCEPTRE);
		if ("You catch a giant blue krill!".equals(message))
			return new java.util.AbstractMap.SimpleImmutableEntry<>("Deep sea trawling", ItemID.POH_TROPHYDROP_GIANT_KRILL);
		if ("You catch a golden haddock!".equals(message))
			return new java.util.AbstractMap.SimpleImmutableEntry<>("Deep sea trawling", ItemID.POH_TROPHYDROP_HADDOCK);
		if ("You catch a orangefin!".equals(message))
			return new java.util.AbstractMap.SimpleImmutableEntry<>("Deep sea trawling", ItemID.POH_TROPHYDROP_YELLOWFIN);
		if ("You catch a huge halibut!".equals(message))
			return new java.util.AbstractMap.SimpleImmutableEntry<>("Deep sea trawling", ItemID.POH_TROPHYDROP_HALIBUT);
		if ("You catch a purplefin!".equals(message))
			return new java.util.AbstractMap.SimpleImmutableEntry<>("Deep sea trawling", ItemID.POH_TROPHYDROP_BLUEFIN);
		if ("You catch a swift marlin!".equals(message))
			return new java.util.AbstractMap.SimpleImmutableEntry<>("Deep sea trawling", ItemID.POH_TROPHYDROP_MARLIN);
		return null;
	}

	@Subscribe(priority = 1)
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (event.getNpc() == null)
		{
			log.debug("NPC loot event ignored: NPC unavailable");
			return;
		}
		log.debug("NPC loot event: source={}, items={}", event.getNpc().getName(), event.getItems().size());
		if (!usesNpcLootReceived(event.getNpc().getId(), event.getNpc().getName())) return;
		npcLootEventGate.recordPrimary(localRecordAccount(), event.getNpc().getName(),
			event.getItems(), client.getTickCount());
		detectLoot(event.getNpc().getName(), event.getItems(), LootRecordType.NPC.name(),
			event.getNpc().getId(), null);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		log.debug("Loot tracker event: type={}, source={}, items={}",
			event.getType(), event.getName(), event.getItems().size());
		if (usesNpcTrackerFallback(event.getType(), event.getName()))
		{
			String source = event.getName();
			List<ItemStack> items = groupDropStacks(event.getItems());
			if (items.isEmpty()) return;
			String account = localRecordAccount();
			long generation = connectionSessionGeneration.get();
			int tick = client.getTickCount();
			String dropEventId = UUID.randomUUID().toString();
			clientThread.invokeLater(() -> {
				DropSessionGate.State state = DropSessionGate.evaluate(generation,
					connectionSessionGeneration.get(), account, localRecordAccount(),
					config.enabled() && (config.statsEnabled() || config.discordDropsEnabled()), client.getGameState());
				if (state == DropSessionGate.State.WAIT) return false;
				if (state == DropSessionGate.State.CANCEL)
				{
					// Unlike detectLoot()'s own gate, this path used to cancel silently with no
					// trace at all. Record it so a genuine auth/session hiccup is provable later.
					dropDiagnosticJournal.record(dropEventId, "SESSION", "state=CANCEL source=" + source);
					return true;
				}
				if (npcLootEventGate.consumePrimary(account, source, items, tick)) return true;
				log.debug("NPC tracker-only loot accepted: source={}, items={}", source, items.size());
				detectLoot(source, items, LootRecordType.NPC.name(), null, null, dropEventId);
				return true;
			});
			return;
		}
		if (usesLootReceived(event.getType(), event.getName()))
		{
			if (event.getType() == LootRecordType.EVENT && event.getName() != null
				&& event.getName().toLowerCase(java.util.Locale.ROOT).startsWith("clue scroll"))
			{
				log.debug("Official clue loot event: source={}, items={}, tick={}",
					event.getName(), event.getItems().size(), client.getTickCount());
				clueRewardGate.rememberOfficial(event.getItems(), client.getTickCount());
			}
			String category = event.getType() == LootRecordType.NPC
				&& ("Crystalline Hunllef".equals(event.getName()) || "Corrupted Hunllef".equals(event.getName()))
				? LootRecordType.EVENT.name() : event.getType().name();
			detectLoot(event.getName(), event.getItems(), category, null, null);
		}
	}

	private void detectLoot(String source, Collection<ItemStack> items, String category,
		Integer npcId, Long singleItemValueOverride)
	{
		detectLoot(source, items, category, npcId, singleItemValueOverride,
			UUID.randomUUID().toString());
	}

	private void detectLoot(String source, Collection<ItemStack> items, String category,
		Integer npcId, Long singleItemValueOverride, String dropEventId)
	{
		try
		{
			processDetectedLoot(source, items, category, npcId, singleItemValueOverride, dropEventId);
		}
		catch (RuntimeException exception)
		{
			recordDropFailure(dropEventId, "DETECT", exception);
		}
	}

	private void processDetectedLoot(String source, Collection<ItemStack> items, String category,
		Integer npcId, Long singleItemValueOverride, String dropEventId)
	{
		if (!canCaptureOwnRecord(config.discordDropsEnabled() || config.statsEnabled()))
		{
			long generation = connectionSessionGeneration.get();
			String account = client.getLocalPlayer() == null ? verifiedAccount
				: WomMembership.normalizePlayerName(client.getLocalPlayer().getName());
			DropSessionGate.State state = dropSessionState(generation, account);
			dropDiagnosticJournal.record(dropEventId, "SESSION", "state=" + state);
			log.debug("Drop event not ready: source={}, state={}, gameState={}",
				source, state, client.getGameState());
			if (state == DropSessionGate.State.WAIT)
			{
				List<ItemStack> pendingItems = groupDropStacks(items);
				runWhenDropSessionReady(generation, account,
					() -> detectLoot(source, pendingItems, category, npcId, singleItemValueOverride,
						dropEventId));
			}
			return;
		}
		List<ItemStack> grouped = groupDropStacks(items);
		List<ItemStack> immediate = new ArrayList<>(grouped);
		if (grouped.isEmpty()) return;
		String eventSource = standardizeKnownLootSource(normalizeDropSource(source));
		RaidLootContext.Completion completion = raidLootContext.take(eventSource, category, client.getTickCount());
		String normalizedSource = completion == null ? eventSource : completion.source;
		// Kill count is cosmetic metadata for the notification, never a precondition for
		// sending it (matching Dink's approach: an unresolved kc shows as "unknown" rather
		// than blocking delivery). Any failure resolving it must not cost the drop itself -
		// duplicating a delivery is preferable to silently losing one.
		Integer killCount = safeKillCount(completion, category, normalizedSource);
		if (RaidLootContext.isRaid(eventSource))
		{
			rescuePendingRaidPb(eventSource);
			log.debug("Raid loot received: eventSource={}, resolvedSource={}, kc={}, items={}",
					eventSource, normalizedSource, killCount,
					grouped.stream().map(item -> item.getId() + "x" + item.getQuantity())
						.collect(java.util.stream.Collectors.joining(",")));
		}
		boolean sendImmediate = !immediate.isEmpty();
		if (!sendImmediate && !bingoDropParticipationEnabled()) return;
		// The accepted loot event is enough to record MVP drops. A missing or delayed
		// screenshot must not prevent the account's drop from reaching the server.
		dropDiagnosticJournal.record(dropEventId, "DETECTED", "items=" + grouped.size());
		log.debug("Drop {} detected: source={}, category={}, items={}", dropEventId,
			normalizedSource, category, grouped.size());
		recordMvpDrops(immediate, normalizedSource, singleItemValueOverride, dropEventId);
		final String dropPlayer = WomMembership.normalizePlayerName(client.getLocalPlayer().getName());
		final long dropGeneration = connectionSessionGeneration.get();
		final Map<String, Object> identity = new LinkedHashMap<>();
		identity.put("accountType", String.valueOf(client.getAccountType()));
		identity.put("seasonalWorld", client.getWorldType().contains(WorldType.SEASONAL));
		identity.put("world", client.getWorld());
		identity.put("dinkAccountHash", liveOnAccountHash(dropPlayer));
		captureDetectedDrop(image -> {
			dropDiagnosticJournal.record(dropEventId, "CAPTURE", image == null ? "missing" : "available");
			log.debug("Drop {} capture complete: hasFrame={}", dropEventId, image != null);
			if (RaidLootContext.isRaid(eventSource))
				log.debug("Raid drop screenshot selected: source={}, hasFrame={}", eventSource, image != null);
			java.util.function.Consumer<java.awt.Image> deliver = frame -> {
				RaidLootContext.Completion ready = completion != null ? completion
					: dropGeneration == connectionSessionGeneration.get() && dropPlayer.equals(authenticatedPlayerName)
						? raidLootContext.take(eventSource, category, client.getTickCount()) : null;
				String deliverySource = ready == null ? normalizedSource : ready.source;
				// Same principle as safeKillCount(): never let this be a precondition for delivery.
				Integer deliveryCount = ready == null ? killCount : safeCount(ready.count);
				if (RaidLootContext.isRaid(eventSource))
				{
					log.debug("Raid loot delivery: eventSource={}, deliverySource={}, kc={}",
						eventSource, deliverySource, deliveryCount);
				}
				if (sendImmediate)
					notifyDiscordDrop(deliverySource, immediate, category, npcId, singleItemValueOverride,
						frame, deliveryCount, dropPlayer, identity, dropEventId);
				if (dropGeneration == connectionSessionGeneration.get() && dropPlayer.equals(authenticatedPlayerName))
					notifyBingoDrops(deliverySource, grouped, category, frame);
			};
			Runnable delivery = () -> deliverWithFallback(dropEventId, image, deliver);
			// RuneLite can publish the generic raid loot event before its mode-specific
			// completion message. Give that message a short, non-blocking window to arrive.
			if (completion == null && RaidLootContext.isGenericRaid(eventSource)
				&& executor != null && !executor.isShutdown())
			{
				executor.schedule(() -> clientThread.invokeLater(() -> {
					if (config.enabled()) delivery.run();
				}),
					RAID_LOOT_CONTEXT_WAIT_MILLIS, TimeUnit.MILLISECONDS);
			}
			else
			{
				delivery.run();
			}
		});
	}

	// The screenshot is the only optional input to a drop notification. If preparing the request
	// fails, retry once without it: a possible duplicate is preferable to a lost drop.
	// Mirrors detectLoot()'s guard: a PB parsed from chat must not be lost to an unexpected
	// exception, and the failure must be traceable without needing the player's full client log.
	private void safeCapturePb(Runnable capture)
	{
		try
		{
			capture.run();
		}
		catch (RuntimeException exception)
		{
			recordDropFailure(UUID.randomUUID().toString(), "PB_CHAT", exception);
		}
	}

	private void deliverWithFallback(String dropEventId, java.awt.Image image,
		java.util.function.Consumer<java.awt.Image> deliver)
	{
		try
		{
			deliver.accept(image);
		}
		catch (RuntimeException exception)
		{
			recordDropFailure(dropEventId, "DELIVERY", exception);
			if (image == null) return;
			try
			{
				deliver.accept(null);
			}
			catch (RuntimeException retryException)
			{
				recordDropFailure(dropEventId, "DELIVERY_NO_IMAGE", retryException);
			}
		}
	}

	// RuneLite's EventBus only logs subscriber exceptions to client.log; keep a trace in the
	// plugin's own journal so a lost drop is diagnosable without the player's full client log.
	private void recordDropFailure(String dropEventId, String stage, RuntimeException exception)
	{
		log.warn("Drop {} failed at {}", dropEventId, stage, exception);
		String location = "unknown";
		for (StackTraceElement frame : exception.getStackTrace())
		{
			if (frame.getClassName().startsWith("com.liveon"))
			{
				location = frame.getMethodName() + ":" + frame.getLineNumber();
				break;
			}
		}
		dropDiagnosticJournal.record(dropEventId, "ERROR",
			stage + " " + exception.getClass().getSimpleName() + " at " + location);
	}

	private void rememberLootCount(String message)
	{
		Map.Entry<String, Integer> parsed = parseLootCountMessage(message);
		if (parsed == null) return;
		lastLootCountSource = parsed.getKey();
		lastLootCount = parsed.getValue();
		lastLootCountTick = client.getTickCount();
		raidLootContext.remember(lastLootCountSource, lastLootCount, lastLootCountTick);
	}

	private void rememberWaveMode(String message)
	{
		Matcher wave = WAVE_COMPLETE_PATTERN.matcher(message);
		if (!wave.find()) return;
		lastWaveMode = wave.group("mode");
		lastWaveModeTick = client.getTickCount();
	}

	// Prefer the mode already tied to this specific pending PB (pendingPbMode, set when the room-
	// time message itself was a new PB); otherwise fall back to the most recent wave-complete
	// line, but only if it's from the same event (a few ticks old), never a stale one left over
	// from some earlier, unrelated raid.
	private String resolvePendingMode()
	{
		if (pendingPbMode != null) return pendingPbMode;
		if (lastWaveModeTick >= 0 && client.getTickCount() - lastWaveModeTick <= LAST_WAVE_MODE_MAX_TICKS)
			return lastWaveMode;
		return null;
	}

	// Extracted for testing: this is the message a raid chest's kill-count correlation
	// depends on, e.g. "Your completed Chambers of Xeric count is: 1,360." - it does not
	// always appear (a solo Chambers of Xeric run can complete without it), which is why
	// killCount must stay optional metadata rather than a precondition for delivery.
	static Map.Entry<String, Integer> parseLootCountMessage(String message)
	{
		Matcher matcher = PB_KILLCOUNT_PATTERN.matcher(message);
		if (!matcher.find()) return null;
		try
		{
			String source = normalizeDropSource(matcher.group("boss"));
			int count = Integer.parseInt(matcher.group("kc").replace(",", ""));
			return new java.util.AbstractMap.SimpleImmutableEntry<>(source, count);
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	static String standardizeKnownLootSource(String source)
	{
		if ("Crystalline Hunllef".equals(source)) return "The Gauntlet";
		if ("Corrupted Hunllef".equals(source)) return "Corrupted Gauntlet";
		return source;
	}

	static boolean raidSourceMatches(String eventSource, String announcedSource)
	{
		if (eventSource == null || announcedSource == null) return false;
		String event = eventSource.toLowerCase(java.util.Locale.ROOT);
		String announced = announcedSource.toLowerCase(java.util.Locale.ROOT);
		return (event.equals("chambers of xeric") || event.equals("theatre of blood")
			|| event.equals("tombs of amascut")) && announced.startsWith(event);
	}

	private static boolean isSpecialLootNpc(String name)
	{
		return "The Whisperer".equals(name) || "Araxxor".equals(name)
			|| "Branda the Fire Queen".equals(name) || "Eldric the Ice King".equals(name)
			|| "Crystalline Hunllef".equals(name) || "Corrupted Hunllef".equals(name);
	}

	static boolean usesServerNpcLoot(int npcId, String name)
	{
		return SERVER_LOOT_NPC_IDS.contains(npcId)
			|| (name != null && name.startsWith("Hallowed Sepulchre"));
	}

	static boolean usesNpcLootReceived(int npcId, String name)
	{
		return !usesServerNpcLoot(npcId, name) && !isSpecialLootNpc(name);
	}

	static boolean usesLootReceived(LootRecordType type, String name)
	{
		return type == LootRecordType.EVENT || type == LootRecordType.PICKPOCKET
			|| (type == LootRecordType.NPC && isSpecialLootNpc(name));
	}

	static boolean usesNpcTrackerFallback(LootRecordType type, String name)
	{
		return type == LootRecordType.NPC && !isSpecialLootNpc(name);
	}

	private void captureDetectedDrop(java.util.function.Consumer<java.awt.Image> delivery)
	{
		DropFrameCapture capture = dropFrameCapture;
		if (capture != null)
			capture.capture(() -> config.enabled() && (config.discordDropsEnabled() || config.statsEnabled())
				? DropSessionGate.State.READY : DropSessionGate.State.CANCEL, delivery);
	}

	private List<ItemStack> groupDropStacks(Collection<ItemStack> items)
	{
		Map<Integer, Integer> quantities = new LinkedHashMap<>();
		if (items != null)
		{
			for (ItemStack item : items)
			{
				if (item != null && item.getQuantity() > 0)
					quantities.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		List<ItemStack> grouped = new ArrayList<>();
		quantities.forEach((id, quantity) -> grouped.add(new ItemStack(id, quantity)));
		return grouped;
	}

	static boolean claimDropFingerprint(Map<String, Integer> cache, String account,
		List<String> parts, boolean fallback, int tick)
	{
		if (parts.isEmpty()) return false;
		List<String> sorted = new ArrayList<>(parts);
		java.util.Collections.sort(sorted);
		String key = account + "|" + String.join("|", sorted);
		Integer previous = cache.get(key);
		if (previous != null && tick >= previous && tick - previous <= DROP_DEDUP_TICKS) return false;
		if (fallback)
		{
			Integer normalTick = cache.get(account + "|normal-item|" + sorted.get(0));
			if (normalTick != null && tick >= normalTick && tick - normalTick <= DROP_DEDUP_TICKS) return false;
		}
		cache.put(key, tick);
		if (!fallback)
		{
			for (String item : sorted) cache.put(account + "|normal-item|" + item, tick);
		}
		return true;
	}

	private static String normalizeDropSource(String source)
	{
		if (source == null) return "Loot";
		String normalized = Text.removeTags(source).replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
		return normalized.isEmpty() ? "Loot" : normalized;
	}

	private void notifyBingoDrops(String source, Collection<ItemStack> items, String category,
		java.awt.Image screenshot)
	{
		if (!bingoDropParticipationEnabled() || items == null || items.isEmpty()
			|| !validBingoSource(source)) return;
		String account = authenticatedPlayerName;
		for (ItemStack item : items)
		{
			net.runelite.api.ItemComposition composition = itemManager.getItemComposition(item.getId());
			String itemName = composition == null ? null : composition.getName();
			if (itemName == null || manualBingo == null || !manualBingo.acceptsDrop(account, itemName)) continue;
			if (!claimBingoDrop(itemName, item.getQuantity())) continue;
			long value = DropItemPricing.unitPrice(itemManager, item.getId()) * item.getQuantity();
			sendDetectedDiscordDrop(new BingoDrop(itemName, item.getQuantity(), value, item.getId(), source, category),
				screenshot, UUID.randomUUID().toString());
		}
	}

	private boolean claimBingoDrop(String itemName, int quantity)
	{
		return claimDropFingerprint(recentBingoDrops, normalizeDropFilterValue(authenticatedPlayerName),
			java.util.Collections.singletonList(normalizeDropFilterValue(itemName) + "x" + quantity),
			false, client.getTickCount());
	}

	private boolean bingoDropParticipationEnabled()
	{
		if (!config.enabled() || !config.discordDropsEnabled() || isTemporaryLootWorld()
			|| authenticatedPlayerName == null || authenticatedPlayerName.isEmpty()
			|| client.getLocalPlayer() == null) return false;
		return WomMembership.normalizePlayerName(authenticatedPlayerName)
			.equals(WomMembership.normalizePlayerName(client.getLocalPlayer().getName()));
	}

	private boolean dropParticipationEnabled()
	{
		return config.enabled() && (config.discordDropsEnabled() || config.statsEnabled())
			&& !isTemporaryLootWorld() && authenticatedPlayerName != null
			&& !authenticatedPlayerName.isEmpty() && client.getLocalPlayer() != null
			&& WomMembership.normalizePlayerName(authenticatedPlayerName)
				.equals(WomMembership.normalizePlayerName(client.getLocalPlayer().getName()));
	}

	private String localRecordAccount()
	{
		return client.getLocalPlayer() == null ? ""
			: WomMembership.normalizePlayerName(client.getLocalPlayer().getName());
	}

	private boolean canCaptureOwnRecord(boolean featureEnabled)
	{
		String account = localRecordAccount();
		return config.enabled() && featureEnabled && !account.isEmpty() && !isTemporaryLootWorld()
			&& !(account.equalsIgnoreCase(verifiedAccount) && membershipRecovery.rejected());
	}

	private DropSessionGate.State recordDeliveryState(String account, boolean featureEnabled)
	{
		return RecordDeliveryGate.evaluate(account, localRecordAccount(), authenticatedPlayerName,
			config.enabled() && featureEnabled, isTemporaryLootWorld(),
			account != null && account.equalsIgnoreCase(verifiedAccount) && membershipRecovery.rejected(),
			client.getGameState());
	}

	private void onDeliveryProgress(Request request, DropDeliveryClient.Progress progress)
	{
		String account = localRecordAccount();
		if (account.isEmpty() && (client.getGameState() == GameState.LOADING
			|| client.getGameState() == GameState.CONNECTION_LOST)) account = verifiedAccount;
		if (!config.enabled() || account.isEmpty()
			|| !account.equalsIgnoreCase(request.header("X-Live-On-Player"))) return;
		if (!account.equalsIgnoreCase(recoveryNoticeAccount))
		{
			recoveryNotice.reset();
			recoveryNoticeAccount = account;
		}
		String key = request.tag(String.class);
		if (key == null) key = request.url() + "\n" + request.header("X-Live-On-Event");
		switch (progress)
		{
			case QUEUED: recoveryNotice.queued(key); break;
			case FAILED: recoveryNotice.failed(key); break;
			case ACCEPTED: recoveryNotice.accepted(key); break;
			case CANCELLED: recoveryNotice.cancelled(key); break;
		}
	}

	private void pollRecoveryNotice()
	{
		String account = localRecordAccount();
		if (account.isEmpty() && config.enabled() && (client.getGameState() == GameState.LOADING
			|| client.getGameState() == GameState.CONNECTION_LOST)) return;
		if (!config.enabled() || account.isEmpty() || !account.equalsIgnoreCase(recoveryNoticeAccount))
		{
			recoveryNotice.reset();
			recoveryNoticeAccount = account;
			return;
		}
		RecoveryNotice.Message notice = recoveryNotice.poll(System.currentTimeMillis(),
			account.equalsIgnoreCase(verifiedAccount) && membershipRecovery.failed(),
			account.equalsIgnoreCase(authenticatedPlayerName));
		if (notice == RecoveryNotice.Message.NONE) return;
		String text = notice == RecoveryNotice.Message.WAITING
			? "Falha temporária confirmada. Há registros aguardando envio; tentaremos novamente automaticamente."
			: "Envios pendentes recuperados e aceitos pelo servidor.";
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder().append(Color.YELLOW, "[Live On] " + text).build())
			.build());
	}

	private void recordMvpDrops(Collection<ItemStack> items, String source, Long singleItemValueOverride,
		String dropEventId)
	{
		if (!config.statsEnabled() || items.isEmpty() || isTemporaryLootWorld())
		{
			dropDiagnosticJournal.record(dropEventId, "MVP_SKIPPED", "participation_or_items");
			return;
		}
		long totalValue = 0;
		for (ItemStack item : items)
		{
			totalValue += effectiveDropValue(item, items.size(), singleItemValueOverride);
		}
		boolean lowValueDropTest = Boolean.getBoolean(LOW_VALUE_DROP_TEST_PROPERTY)
			|| (Boolean.getBoolean(LOW_VALUE_CLUE_TEST_PROPERTY)
				&& source != null && source.toLowerCase(java.util.Locale.ROOT).startsWith("clue scroll ("));
		if (totalValue >= (lowValueDropTest ? 1L : MVP_DROP_MINIMUM_VALUE))
		{
			submitDropStats(items, source, singleItemValueOverride, lowValueDropTest, dropEventId);
		}
		else dropDiagnosticJournal.record(dropEventId, "MVP_FILTERED", "below_minimum");
	}

	private boolean validBingoSource(String source)
	{
		return source != null && !source.trim().isEmpty()
			&& !matchesDiscordFilter(DISCORD_SOURCE_DENYLIST, source);
	}

	private void notifyDiscordDrop(String source, Collection<ItemStack> items, String category,
		Integer npcId, Long singleItemValueOverride, java.awt.Image screenshot, Integer dropKillCount,
		String playerName, Map<String, Object> identity, String dropEventId)
	{
		if (items.isEmpty()) return;
		long totalValue = 0;
		for (ItemStack item : items)
		{
			long value = effectiveDropValue(item, items.size(), singleItemValueOverride);
			totalValue += value;
		}
		boolean lowValueDropTest = Boolean.getBoolean(LOW_VALUE_DROP_TEST_PROPERTY)
			|| (Boolean.getBoolean(LOW_VALUE_CLUE_TEST_PROPERTY)
				&& source != null && source.toLowerCase(java.util.Locale.ROOT).startsWith("clue scroll ("));
		if (matchesDiscordFilter(DISCORD_SOURCE_DENYLIST, source))
		{
			log.debug("Discord drop skipped for denied source: {}", source);
			return;
		}
		long minimumValue = lowValueDropTest ? 1L : Math.max(0, config.discordDropMinimumValue());
		if (!config.discordDropsEnabled())
		{
			log.debug("Discord drop not requested: sending option disabled; source={}", source);
			return;
		}
		boolean clueTotalEligible = source != null && source.startsWith("Clue Scroll (")
			&& totalValue >= minimumValue;
		List<String> notableItems = new ArrayList<>();
		List<Map<String, Object>> dinkItems = new ArrayList<>();
		long notifiedValue = 0L;
		Double rarestProbability = null;
		int thumbnailItemId = -1;
		long thumbnailItemValue = Long.MIN_VALUE;
		for (ItemStack item : items)
		{
			long value = effectiveDropValue(item, items.size(), singleItemValueOverride);
			String itemName = itemManager.getItemComposition(item.getId()).getName();
			boolean denied = matchesDiscordFilter(DISCORD_ITEM_DENYLIST, itemName);
			boolean allowed = matchesDiscordFilter(DROP_ITEM_ALLOWLIST, itemName);
			// Explicitly allowlisted untradeables can legitimately have no GE price.
			if (!shouldNotifyDiscordItem(denied, allowed, value, minimumValue, clueTotalEligible))
			{
				continue;
			}
			if (thumbnailItemId < 0 || value > thumbnailItemValue)
			{
				thumbnailItemId = item.getId();
				thumbnailItemValue = value;
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
				: DropItemPricing.unitPrice(itemManager, item.getId()));
			dinkItem.put("name", itemName);
			List<String> criteria = new ArrayList<>();
			if (value >= minimumValue || clueTotalEligible) criteria.add("VALUE");
			if (allowed) criteria.add("ALLOWLIST");
			dinkItem.put("criteria", criteria);
			dinkItem.put("rarity", rarity);
			dinkItems.add(dinkItem);
		}
		if (notableItems.isEmpty())
		{
			dropDiagnosticJournal.record(dropEventId, "DISCORD_FILTERED", "before_request");
			log.debug("Drop {} Discord filtered before request: source={}, totalValue={}, minimumValue={}, itemCount={}",
				dropEventId, source, totalValue, minimumValue, items.size());
			return;
		}
		String sourceName = source == null ? "Loot" : source;
		String description = String.join("\n", notableItems) + "\n" + discordWikiLink(sourceName, sourceName);
		final int dropThumbnailItemId = thumbnailItemId;
		final long dropTotalValue = notifiedValue;
		final Double dropRarestProbability = rarestProbability;
		sendDiscordDrop(playerName, description, dropThumbnailItemId, sourceName, category, npcId,
			dinkItems, dropTotalValue, dropKillCount, dropRarestProbability, screenshot, identity, dropEventId);
	}

	static boolean shouldNotifyDiscordItem(boolean denied, boolean allowed, long value, long minimumValue)
	{
		return shouldNotifyDiscordItem(denied, allowed, value, minimumValue, false);
	}

	static boolean shouldNotifyDiscordItem(boolean denied, boolean allowed, long value, long minimumValue,
		boolean clueTotalEligible)
	{
		return !denied && (allowed || clueTotalEligible || (value > 0 && value >= Math.max(0, minimumValue)));
	}

	private long effectiveDropValue(ItemStack item, int itemCount, Long singleItemValueOverride)
	{
		if (singleItemValueOverride != null && itemCount == 1)
		{
			return Math.max(0L, singleItemValueOverride);
		}
		return DropItemPricing.unitPrice(itemManager, item.getId()) * item.getQuantity();
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

	static java.util.Map.Entry<String, Integer> parseClueCompletion(String message)
	{
		if (message == null) return null;
		Matcher matcher = CLUE_COMPLETION_PATTERN.matcher(message.replace('\u00A0', ' ').trim());
		if (!matcher.find()) return null;
		try
		{
			return new java.util.AbstractMap.SimpleImmutableEntry<>(matcher.group("tier"),
				Integer.parseInt(matcher.group("count").replace(",", "")));
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	private void rememberClueCompletion(String message)
	{
		java.util.Map.Entry<String, Integer> clue = parseClueCompletion(message);
		if (clue == null) return;
		pendingClueTier = clue.getKey();
		pendingClueCount = clue.getValue();
		pendingClueTicks = 0;
	}

	private void captureClueReward()
	{
		if (!pendingClueReward) return;
		Widget itemsWidget = client.getWidget(InterfaceID.TrailRewardscreen.ITEMS);
		Widget[] children = itemsWidget == null ? null : itemsWidget.getChildren();
		if (children == null) return;
		List<ItemStack> items = new ArrayList<>();
		for (Widget child : children)
		{
			if (child != null && child.getItemId() >= 0 && child.getItemQuantity() > 0)
			{
				items.add(new ItemStack(child.getItemId(), child.getItemQuantity()));
			}
		}
		if (items.isEmpty()) return;
		String tier = pendingClueTier.isEmpty() ? "Unknown"
			: pendingClueTier.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
				+ pendingClueTier.substring(1).toLowerCase(java.util.Locale.ROOT);
		String source = "Clue Scroll (" + tier + ")";
		if (pendingClueCount > 0)
		{
			lastLootCountSource = source;
			lastLootCount = pendingClueCount;
			lastLootCountTick = client.getTickCount();
		}
		resetPendingClue();
		int rewardTick = client.getTickCount();
		log.debug("Clue reward widget: source={}, items={}, tick={}", source, items.size(), rewardTick);
		if (!clueRewardGate.shouldSendWidget(items, rewardTick))
		{
			log.debug("Clue widget matches official loot event; using official event");
			return;
		}
		ScheduledExecutorService currentExecutor = executor;
		if (currentExecutor == null || currentExecutor.isShutdown())
		{
			detectLoot(source, items, LootRecordType.EVENT.name(), null, null);
			return;
		}
		try
		{
			currentExecutor.schedule(() -> clientThread.invokeLater(() -> {
				if (clueRewardGate.shouldSendWidget(items, rewardTick))
				{
					log.debug("No matching official clue loot event; using reward widget");
					detectLoot(source, items, LootRecordType.EVENT.name(), null, null);
				}
				else
				{
					log.debug("Clue widget matches official loot event; using official event");
				}
			}), CLUE_WIDGET_FALLBACK_WAIT_MILLIS, TimeUnit.MILLISECONDS);
		}
		catch (java.util.concurrent.RejectedExecutionException ignored)
		{
			detectLoot(source, items, LootRecordType.EVENT.name(), null, null);
		}
	}

	private void resetPendingClue()
	{
		pendingClueTier = "";
		pendingClueCount = -1;
		pendingClueTicks = 0;
		pendingClueReward = false;
	}

	private static String normalizeDropFilterValue(String value)
	{
		return value == null ? "" : value.replace('\u00A0', ' ').trim().toLowerCase(java.util.Locale.ROOT);
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
		rankSyncCompleted = true;
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
		cancelRankBankRefresh();
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
			rankSyncCompleted = true;
			scheduleRankBankRefresh();
		}
		else if (event.getContainerId() == InventoryID.INV
			|| event.getContainerId() == InventoryID.WORN)
		{
			refreshRanksAutomatically();
		}
	}

	/**
	 * Bank contents can arrive in several consecutive container updates while
	 * the bank opens. Recalculate once after those updates settle.
	 */
	private synchronized void scheduleRankBankRefresh()
	{
		if (executor == null || executor.isShutdown()) return;
		cancelRankBankRefresh();
		long refreshGeneration = rankBankRefreshGeneration.get();
		long generation = connectionSessionGeneration.get();
		String account = authenticatedPlayerName;
		rankBankRefreshTask = executor.schedule(() ->
		{
			synchronized (ClanMessagesPlugin.this)
			{
				if (refreshGeneration != rankBankRefreshGeneration.get()) return;
				rankBankRefreshTask = null;
			}
			clientThread.invokeLater(() ->
			{
				if (refreshGeneration == rankBankRefreshGeneration.get()
					&& isCurrentConnectionSession(generation, account))
				{
					refreshRanksOnClientThread(true);
				}
			});
		}, RANK_BANK_SETTLE_MILLIS, TimeUnit.MILLISECONDS);
	}

	private synchronized void cancelRankBankRefresh()
	{
		rankBankRefreshGeneration.incrementAndGet();
		if (rankBankRefreshTask != null)
		{
			rankBankRefreshTask.cancel(false);
			rankBankRefreshTask = null;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.CA_POINTS)
		{
			refreshRanksAutomatically();
		}
		else if (event.getVarbitId() == VarbitID.TOA_SCOREBOARD_TAB
			|| event.getVarbitId() == VarbitID.TOB_SCOREBOARD_TAB)
		{
			// Switching raid modes reuses the same scoreboard widgets.
			bossStatisticsBoardScanTicks = 4;
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
		if (groupId == InterfaceID.TRAIL_REWARDSCREEN)
		{
			pendingClueReward = true;
			pendingClueTicks = 0;
			captureClueReward();
		}
		// Physical scoreboards do not share one stable interface group. Restrict
		// the short inspection window to the group that actually loaded instead
		// of collecting text from every visible game interface.
		bossStatisticsBoardGroupIds.add(groupId);
		while (bossStatisticsBoardGroupIds.size() > 8)
		{
			java.util.Iterator<Integer> oldest = bossStatisticsBoardGroupIds.iterator();
			oldest.next();
			oldest.remove();
		}
		bossStatisticsBoardScanTicks = 4;
		if (groupId == InterfaceID.MENU || groupId == InterfaceID.MENU_NEW)
		{
			adventureLogMenuLoaded = true;
			lastAdventureLogContentSignature = "";
		}
		else if (groupId == InterfaceID.JOURNALSCROLL)
		{
			adventureLogCountersLoaded = true;
			lastAdventureLogContentSignature = "";
		}
		if (groupId == InterfaceID.ACCOUNT || groupId == InterfaceID.ACCOUNT_SUMMARY_SIDEPANEL
			|| groupId == InterfaceID.QUESTLIST)
		{
			// Quest points can change without a skill-level event. Force a fresh
			// read when the relevant game interfaces become available.
			lastQuestPoints = -1;
			lastMaximumQuestPoints = -1;
		}
		boolean combatAchievementsGroup = groupId == InterfaceID.CA_OVERVIEW
			|| groupId == InterfaceID.CA_TASKS || groupId == InterfaceID.CA_REWARDS
			|| groupId == InterfaceID.CA_BOSSES || groupId == InterfaceID.CA_BOSS;
		if (groupId == InterfaceID.ACCOUNT || groupId == InterfaceID.ACCOUNT_SUMMARY_SIDEPANEL
			|| groupId == InterfaceID.QUESTLIST)
		{
			scheduleRankWidgetRefresh();
		}
		else if (combatAchievementsGroup)
		{
			scheduleSingleRankRefresh();
		}
		if (combatAchievementsGroup)
		{
			// The boss name and statistics are populated asynchronously. Read only
			// their official widgets for a short period after the CA interface loads.
			combatAchievementPbScanTicks = 6;
		}
	}

	/** CA points come from a game varbit, so one deferred refresh is enough. */
	private void scheduleSingleRankRefresh()
	{
		int tick = client.getTickCount();
		if (rankWidgetRefreshPending || tick - lastCombatAchievementRankRefreshTick < 3) return;
		lastCombatAchievementRankRefreshTick = tick;
		rankWidgetRefreshPending = true;
		clientThread.invokeLater(() ->
		{
			rankWidgetRefreshPending = false;
			refreshRanksOnClientThread(false);
		});
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

		String sessionKey = accountKey + "|" + eligibleRank.toLowerCase(java.util.Locale.ROOT);
		if (!shouldNotifyAvailableRank(currentIndex, eligibleIndex,
			sessionRankNotifications.contains(sessionKey), rankRequestStatusKnown && rankRequestPending)) return;
		sessionRankNotifications.add(sessionKey);
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

	static boolean shouldNotifyAvailableRank(int currentIndex, int eligibleIndex,
		boolean notifiedThisSession, boolean requestPending)
	{
		if (requestPending || currentIndex < 0 || eligibleIndex <= currentIndex) return false;
		return !notifiedThisSession;
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
		if (!bankLoaded) result.add("! Abra o banco uma vez<br>para verificar seus itens");
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
		// getChildren() already exposes the widget's complete child collection.
		// Walking each specialized child array as well repeats the same subtrees.
		Widget[] children = widget.getChildren();
		if (children != null) for (Widget child : children) collectWidgetTexts(child, texts);
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
		cancelRankBankRefresh();
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

	private void submitDropStats(Collection<ItemStack> items, String source, Long singleItemValueOverride,
		boolean lowValueDropTest, String dropEventId)
	{
		if (client.getLocalPlayer() == null) return;
		Map<Integer, Long> quantitiesByItem = new LinkedHashMap<>();
		for (ItemStack item : items)
		{
			if (item == null || item.getQuantity() <= 0) continue;
			quantitiesByItem.merge(item.getId(), (long) item.getQuantity(), Long::sum);
		}
		List<Map<String, Object>> validDrops = new ArrayList<>();
		for (Map.Entry<Integer, Long> item : quantitiesByItem.entrySet())
		{
			long quantity = item.getValue();
			long value = singleItemValueOverride != null && quantitiesByItem.size() == 1
				? Math.max(0L, singleItemValueOverride)
				: DropItemPricing.unitPrice(itemManager, item.getKey()) * quantity;
			if (value < (lowValueDropTest ? 1L : MVP_DROP_MINIMUM_VALUE)) continue;
			Map<String, Object> validDrop = new LinkedHashMap<>();
			validDrop.put("item", quantity + "x " + itemManager.getItemComposition(item.getKey()).getName());
			validDrop.put("value", value);
			validDrops.add(validDrop);
		}
		if (validDrops.isEmpty()) return;
		if (lowValueDropTest)
		{
			for (Map<String, Object> drop : validDrops)
			{
				long value = (Long) drop.get("value");
				if (value >= MVP_DROP_MINIMUM_VALUE) continue;
				String message = "[Live On teste] MVP detectou " + drop.get("item")
					+ " (" + formatGp(value) + ") de " + source;
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(new ChatMessageBuilder().append(Color.YELLOW, message).build())
					.build());
			}
			validDrops.removeIf(drop -> (Long) drop.get("value") < MVP_DROP_MINIMUM_VALUE);
			if (validDrops.isEmpty()) return;
		}
		// Prepare payload; include playerName so server can verify via WOM
		java.util.Map<String, Object> dropPayload = new java.util.LinkedHashMap<>();
		dropPayload.put("playerName", client.getLocalPlayer().getName());
		dropPayload.put("eventId", dropEventId);
		log.debug("Drop {} MVP request prepared: items={}", dropEventId, validDrops.size());
		dropDiagnosticJournal.record(dropEventId, "MVP_PREPARED", "items=" + validDrops.size());
		dropPayload.put("drops", validDrops);
		dropPayload.put("source", source);
		submitDropPayload(gson.toJson(dropPayload), 0);
	}

	private void recoverPendingDrops()
	{
		String account = authenticatedPlayerName;
		if (!config.enabled() || account == null || account.isEmpty() || dropDeliveryClient == null) return;
		dropDeliveryClient.recover(serverBaseUrl(), account,
			path -> recordDeliveryState(account, path.endsWith("/stats/pbs") ? config.pbRankingEnabled()
				: path.endsWith("/stats/drops") ? config.statsEnabled() : config.discordDropsEnabled()));
	}

	private void submitDropPayload(String payload, int attempt)
	{
		HttpUrl base = serverBaseUrl();
		DropDeliveryClient delivery = dropDeliveryClient;
		if (base == null || delivery == null) return;
		String player = gson.fromJson(payload, com.google.gson.JsonObject.class).get("playerName").getAsString();
		Request request = new Request.Builder().url(base.newBuilder().addPathSegments("stats/drops").build())
			.header("X-Live-On-Player", player).header("Authorization", "LiveOnPlayer " + player)
			.header("X-Live-On-Event", gson.fromJson(payload, com.google.gson.JsonObject.class).get("eventId").getAsString())
			.post(RequestBody.create(JSON, payload)).build();
		delivery.send(request, request, "MVP drop", () -> recordDeliveryState(player, config.statsEnabled()));
	}

	private void sendDetectedDiscordDrop(BingoDrop drop, java.awt.Image screenshot, String idempotencyKey)
	{
		String playerName = authenticatedPlayerName;
		long totalValue = drop.totalValue == null ? 0L : Math.max(0L, drop.totalValue);
		String valueText = drop.totalValue == null ? "valor desconhecido" : formatDropValue(totalValue);
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("title", "Bingo Drop");
		embed.put("description", discordWikiLink(drop.quantity + "x " + drop.itemName, drop.itemName)
			+ " (" + valueText + ")\n" + discordWikiLink(drop.source, drop.source));
		embed.put("color", dropEmbedColor(totalValue));
		embed.put("author", author(playerName));
		embed.put("timestamp", Instant.now().toString());
		embed.put("footer", footer());
		embed.put("fields", java.util.Collections.singletonList(
			embedField("Total Value", discordCodeBlock(drop.totalValue == null ? "Desconhecido" : formatGp(totalValue)), true)));
		if (drop.itemId != null && drop.itemId >= 0)
		{
			Map<String, Object> thumbnail = new LinkedHashMap<>();
			thumbnail.put("url", "https://static.runelite.net/cache/item/icon/" + drop.itemId + ".png");
			embed.put("thumbnail", thumbnail);
		}

		Map<String, Object> item = new LinkedHashMap<>();
		if (drop.itemId != null) item.put("id", drop.itemId);
		item.put("name", drop.itemName);
		item.put("quantity", drop.quantity);
		item.put("priceEach", drop.totalValue == null ? null : totalValue / Math.max(1, drop.quantity));
		item.put("criteria", java.util.Collections.singletonList("BINGO_ALLOWLIST"));
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("items", java.util.Collections.singletonList(item));
		extra.put("source", drop.source);
		extra.put("category", drop.category);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("content", "");
		payload.put("tts", false);
		payload.put("embeds", java.util.Collections.singletonList(embed));
		payload.put("type", "LOOT");
		payload.put("playerName", playerName);
		payload.put("idempotencyKey", idempotencyKey);
		payload.put("accountType", String.valueOf(client.getAccountType()));
		payload.put("seasonalWorld", client.getWorldType().contains(WorldType.SEASONAL));
		payload.put("world", client.getWorld());
		payload.put("extra", extra);

		Request requestTemplate = discordNotificationRequest(RequestBody.create(JSON, ""), true);
		deliverDiscordNotification(requestTemplate, payload, embed, screenshot,
			DISCORD_LOOT_ATTACHMENT, "Bingo drop notification");
	}

	private void sendDiscordDrop(String playerName, String description, int thumbnailItemId, String source,
		String category, Integer npcId, List<Map<String, Object>> items, long totalValue,
		Integer killCount, Double rarestProbability, java.awt.Image screenshot, Map<String, Object> identity,
		String dropEventId)
	{
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("title", "Loot Drop");
		embed.put("description", limitDiscordDescription(description));
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
		String notificationId = dropEventId;
		payload.put("idempotencyKey", notificationId);
		payload.putAll(identity);
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
		if (RaidLootContext.isRaid(source))
			log.debug("Raid Discord request prepared: source={}, id={}, items={}, hasFrame={}",
				source, notificationId, items.size(), screenshot != null);
		HttpUrl base = serverBaseUrl();
		Request requestTemplate = base == null ? null : new Request.Builder()
			.url(base.newBuilder().addPathSegments("notifications/discord").build())
			.header("X-Live-On-Player", playerName).header("Authorization", "LiveOnPlayer " + playerName)
			.header("X-Live-On-Event", dropEventId)
			.post(RequestBody.create(JSON, "")).build();
		deliverDiscordNotification(requestTemplate, payload, embed, screenshot,
			DISCORD_LOOT_ATTACHMENT, "Discord drop notification");
	}

	private void deliverDiscordNotification(Request requestTemplate, Map<String, Object> payload,
		Map<String, Object> embed, java.awt.Image screenshot, String attachmentName, String destination)
	{
		if (requestTemplate == null || executor == null || executor.isShutdown())
		{
			if (requestTemplate != null && dropDiagnosticJournal != null)
				dropDiagnosticJournal.record(requestTemplate.header("X-Live-On-Event"), "DISCORD_SKIPPED",
					"executor_unavailable");
			log.debug("{} could not start: request or delivery executor unavailable", destination);
			return;
		}
		executor.execute(() -> {
			byte[] screenshotBytes = null;
			if (screenshot instanceof BufferedImage)
			{
				try
				{
					screenshotBytes = dropScreenshotEncoder.encode((BufferedImage) screenshot);
				}
				catch (IOException | RuntimeException exception)
				{
					log.debug("Unable to prepare {} screenshot; sending without it", destination, exception);
				}
			}
			DropMultipartPayload bodies = DropMultipartPayload.create(
				gson, payload, embed, screenshotBytes, attachmentName);
			String eventId = requestTemplate.header("X-Live-On-Event");
			if (eventId != null) dropDiagnosticJournal.record(eventId, "DISCORD_PREPARED",
				screenshotBytes == null ? "image=missing" : "image=available bytes=" + screenshotBytes.length);
			DropDeliveryClient delivery = dropDeliveryClient;
			if (delivery != null)
			{
				log.debug("{} prepared for server request", destination);
				delivery.send(
					requestTemplate.newBuilder().header("X-Live-On-Image",
						screenshotBytes == null ? "absent" : "present").post(bodies.initialBody).build(),
					requestTemplate.newBuilder().header("X-Live-On-Image", "absent")
						.post(bodies.retryBody).build(),
					destination,
					() -> recordDeliveryState(requestTemplate.header("X-Live-On-Player"), config.discordDropsEnabled()));
			}
			else
			{
				log.debug("{} could not start: delivery client unavailable", destination);
			}
		});
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

	static String discordAttachmentUrl(String fileName)
	{
		return "attachment://" + fileName;
	}

	static String limitDiscordDescription(String description)
	{
		if (description == null || description.length() <= DISCORD_EMBED_DESCRIPTION_LIMIT)
		{
			return description;
		}
		return description.substring(0, DISCORD_EMBED_DESCRIPTION_LIMIT - 3) + "...";
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

	// Kill count is cosmetic metadata (shown in the notification, never required to send it -
	// same principle as Dink's LootNotifier/KillCountService). Any exception here must degrade
	// to "unknown" rather than abort the drop notification that's already in flight; a duplicated
	// delivery is an acceptable cost, losing one to a metadata lookup failure is not.
	private Integer safeKillCount(RaidLootContext.Completion completion, String category, String source)
	{
		try
		{
			return completion == null ? readDropKillCount(category, source) : safeCount(completion.count);
		}
		catch (RuntimeException exception)
		{
			log.debug("Unable to resolve kill count for {}", source, exception);
			return null;
		}
	}

	private static Integer safeCount(int value)
	{
		return value;
	}

	private Integer readDropKillCount(String category, String source)
	{
		if (source == null || source.trim().isEmpty()) return null;
		// Generic raid names cannot establish the mode of this chest.
		if (RaidLootContext.isRaid(source)) return null;
		int currentTick = client.getTickCount();
		if (currentTick >= lastLootCountTick && currentTick - lastLootCountTick <= DROP_DEDUP_TICKS
			&& source.equalsIgnoreCase(lastLootCountSource) && lastLootCount > 0)
		{
			return lastLootCount;
		}
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
		if ("the gauntlet".equals(clean) || "crystalline hunllef".equals(clean)) return "gauntlet";
		if ("corrupted hunllef".equals(clean)) return "corrupted gauntlet";
		if ("the leviathan".equals(clean)) return "leviathan";
		if ("the whisperer".equals(clean)) return "whisperer";
		if ("the hueycoatl".equals(clean)) return "hueycoatl";
		if (clean.startsWith("barrows")) return "barrows chests";
		if (clean.endsWith("hallowed sepulchre)")) return "hallowed sepulchre";
		if (clean.endsWith("tempoross)")) return "tempoross";
		if (clean.endsWith("wintertodt)")) return "wintertodt";
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
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("content", "");
		payload.put("tts", false);
		payload.put("embeds", java.util.Collections.singletonList(embed));
		payload.put("type", "PET");
		payload.put("playerName", playerName);
		payload.put("idempotencyKey", UUID.randomUUID().toString());
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
		Request requestTemplate = discordNotificationRequest(RequestBody.create(JSON, ""));
		deliverDiscordNotification(requestTemplate, payload, embed, screenshot,
			DISCORD_PET_ATTACHMENT, "Discord pet notification");
	}

	private Request discordNotificationRequest(RequestBody body)
	{
		return discordNotificationRequest(body, false);
	}

	private Request discordNotificationRequest(RequestBody body, boolean bingo)
	{
		HttpUrl base = serverBaseUrl();
		if (base == null || authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			log.debug("Discord notification skipped because the clan server is not authenticated");
			return null;
		}
		HttpUrl url = base.newBuilder().addPathSegments(bingo ? "notifications/bingo" : "notifications/discord").build();
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
		if (pendingClueReward || !pendingClueTier.isEmpty())
		{
			captureClueReward();
			if ((pendingClueReward || !pendingClueTier.isEmpty()) && ++pendingClueTicks > 1)
			{
				resetPendingClue();
			}
		}
		if (isPbParticipationEnabled())
		{
			processAdventureLog();
			armCombatAchievementPbScanForVisiblePage();
			processCombatAchievementBossPb();
			processBossStatisticsBoardPb();
		}
		// A boss-first correlation (kill-count chat message already named the boss) is tight:
		// both messages are chat lines from the same event and should arrive within a few ticks.
		// A duration-first correlation (pendingPbBoss still null) gets a longer window, because
		// detectLoot()'s raid-chest rescue below is its only path to a boss name and the chest
		// can legitimately arrive several real seconds later.
		int pendingPbMaxTicks = pendingPbBoss != null ? 5 : PENDING_RAID_PB_MAX_TICKS;
		if (pendingPbTick >= 0 && client.getTickCount() - pendingPbTick > pendingPbMaxTicks)
		{
			if (pendingPbBoss == null && pendingPbSeconds > 0)
			{
				// A boss-less raid PB expired unresolved: rescuePendingRaidPb() never got a
				// matching raid-chest event in time. This is the exact failure mode that lost
				// a real Theatre of Blood PB before PENDING_RAID_PB_MAX_TICKS was widened -
				// keep a trace so a future recurrence is diagnosable instead of silent.
				dropDiagnosticJournal.record(UUID.randomUUID().toString(), "PB_EXPIRED",
					"seconds=" + pendingPbSeconds + " teamSize=" + pendingPbTeamSize);
			}
			pendingPbBoss = null;
			pendingPbSeconds = -1;
			pendingPbTeamSize = 0;
			pendingPbMode = null;
			pendingPbTick = -1;
		}
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
			if (config.discordDropsEnabled())
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
			if (!currentAccount.equalsIgnoreCase(verifiedAccount)
				|| (currentWomCall == null && membershipRecovery.due(System.currentTimeMillis())))
			{
				if (!currentAccount.equalsIgnoreCase(verifiedAccount)) membershipRecovery.accountChanged();
				// Clear the retry deadline synchronously: verifyToken()'s own begin() call only
				// runs once its clientThread.invokeLater task executes, at least one tick later.
				// Without this, due() keeps returning true across that gap and this same block
				// re-triggers verifyToken() on every intervening tick, cancelling and restarting
				// the WOM request each time instead of letting one attempt resolve.
				membershipRecovery.begin();
				verifiedAccount = currentAccount;
				verifyToken();
			}
		}
		pollRecoveryNotice();
	}

	private void processBossStatisticsBoardPb()
	{
		if (bossStatisticsBoardScanTicks <= 0 || (client.getTickCount() & 1) != 0) return;
		if (combatAchievementPbScanTicks > 0) return;
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty()) return;
		bossStatisticsBoardScanTicks--;
		List<String> texts = new ArrayList<>();
		List<Map<String, Object>> parsedRecords = parseOfficialBossScoreboards();
		Widget[] roots = client.getWidgetRoots();
		if (parsedRecords.isEmpty() && roots != null && !bossStatisticsBoardGroupIds.isEmpty())
		{
			java.util.Set<Widget> visited = java.util.Collections.newSetFromMap(
				new java.util.IdentityHashMap<Widget, Boolean>());
			for (Widget root : roots)
			{
				collectVisibleWidgetTextsForGroups(root, bossStatisticsBoardGroupIds, texts, visited);
			}
		}
		if (parsedRecords.isEmpty()) parsedRecords = parseBossStatisticsBoardPbs(texts);
		if (parsedRecords.isEmpty())
		{
			if (bossStatisticsBoardScanTicks <= 0)
			{
				bossStatisticsBoardGroupIds.clear();
			}
			return;
		}
		bossStatisticsBoardScanTicks = 0;
		bossStatisticsBoardGroupIds.clear();
		List<Map<String, Object>> submissions = new ArrayList<>();
		List<String> signatures = new ArrayList<>();
		for (Map<String, Object> parsed : parsedRecords)
		{
			Map<String, Object> payload = scoreboardPbPayload(parsed);
			double seconds = (Double) payload.get("seconds");
			int teamSize = (Integer) payload.get("teamSize");
			String timeType = (String) payload.get("timeType");
			String signature = authenticatedPlayerName + "\n" + payload.get("boss") + "\n"
				+ payload.get("mode") + "\n" + teamSize + "\n" + timeType + "\n" + seconds;
			if (!submittedPbSignatures.add(signature)) continue;
			submissions.add(payload);
			signatures.add(signature);
		}
		if (!submissions.isEmpty()) submitPbBatch(submissions, signatures);
	}

	static Map<String, Object> scoreboardPbPayload(Map<String, Object> parsed)
	{
		String boss = (String) parsed.get("boss");
		String detectedMode = (String) parsed.get("mode");
		Number parsedTeamSize = (Number) parsed.get("teamSize");
		int teamSize = parsedTeamSize == null ? 0 : parsedTeamSize.intValue();
		double seconds = ((Number) parsed.get("seconds")).doubleValue();
		Map<String, Object> payload = pbPayload(
			boss + (detectedMode == null || detectedMode.isEmpty() ? "" : " " + detectedMode), teamSize, seconds);
		Object timeType = parsed.get("timeType");
		payload.put("timeType", timeType instanceof String ? timeType : "");
		return payload;
	}

	static Map<String, Object> parseBossStatisticsBoardPb(List<String> widgetTexts)
	{
		List<Map<String, Object>> records = parseBossStatisticsBoardPbs(widgetTexts);
		return records.isEmpty() ? null : records.get(0);
	}

	static List<Map<String, Object>> parseBossStatisticsBoardPbs(List<String> widgetTexts)
	{
		List<Map<String, Object>> result = new ArrayList<>();
		if (widgetTexts == null) return result;
		String boss = null;
		List<String> normalizedTexts = new ArrayList<>();
		for (String raw : widgetTexts)
		{
			String withBreaks = (raw == null ? "" : raw).replaceAll("(?i)<br\\s*/?>", " ");
			String text = Text.removeTags(withBreaks).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
			normalizedTexts.add(text);
			Matcher title = Pattern.compile("(?i)^(.+?)\\s+Statistics$").matcher(text);
			if (title.find()) boss = title.group(1).trim();
		}
		if (boss == null || boss.isEmpty()) return result;
		for (String text : normalizedTexts)
		{
			Matcher personalBest = Pattern.compile(
				"(?i)((?:Awakened\\s+)?Personal Best(?:\\s+Awakened)? Time"
					+ "(?:\\s*\\(\\s*(?:Awakened|Normal)\\s*\\)|\\s*-\\s*(?:Awakened|Normal))?)"
					+ "\\s*:?\\s*([0-9]+(?::[0-9]+){0,2}(?:\\.[0-9]+)?)").matcher(text);
			while (personalBest.find())
			{
				double seconds;
				try { seconds = parsePbTime(personalBest.group(2)); }
				catch (NumberFormatException ignored) { continue; }
				if (seconds <= 0) continue;
				String label = personalBest.group(1);
				String mode = label.toLowerCase(java.util.Locale.ROOT).contains("awakened")
					? "Awakened" : "";
				Map<String, Object> record = new LinkedHashMap<>();
				record.put("boss", boss);
				record.put("mode", mode);
				record.put("seconds", seconds);
				result.add(record);
			}
		}
		return result;
	}

	private void processCombatAchievementBossPb()
	{
		if (combatAchievementPbScanTicks <= 0 || (client.getTickCount() & 1) != 0) return;
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty()) return;
		combatAchievementPbScanTicks--;
		Widget bossName = client.getWidget(InterfaceID.CaBoss.BOSS_NAME);
		Widget bossStats = client.getWidget(InterfaceID.CaBoss.CA_BOSS_STATS);
		if (!isVisibleWidget(bossName) || !isVisibleWidget(bossStats)) return;
		List<String> bossTexts = new ArrayList<>();
		collectVisibleWidgetTexts(bossName, bossTexts);
		List<String> statsTexts = new ArrayList<>();
		collectVisibleWidgetTexts(bossStats, statsTexts);
		Map<String, Object> parsed = parseCombatAchievementBossWidgets(firstWidgetText(bossTexts), statsTexts);
		if (parsed == null) return;
		combatAchievementPbScanTicks = 0;
		String boss = (String) parsed.get("boss");
		double seconds = (Double) parsed.get("seconds");
		Map<String, Object> payload = pbPayload(boss, 0, seconds);
		if (requiresExplicitPbTeamSize((String) payload.get("boss"))) return;
		String signature = authenticatedPlayerName + "\n" + payload.get("boss") + "\n"
			+ payload.get("mode") + "\n" + seconds;
		if (!submittedPbSignatures.add(signature)) return;
		submitPb((String) payload.get("boss"), (String) payload.get("mode"), 0, seconds);
	}

	static boolean requiresExplicitPbTeamSize(String boss)
	{
		return "Chambers of Xeric".equals(boss) || "Theatre of Blood".equals(boss)
			|| "Tombs of Amascut".equals(boss) || "The Nightmare".equals(boss);
	}

	private void armCombatAchievementPbScanForVisiblePage()
	{
		if ((client.getTickCount() & 1) != 0) return;
		String page = visibleCombatAchievementPageTitle();
		if (page.isEmpty())
		{
			// A newly loaded CA page can expose its group before the named widget is
			// populated. Preserve the short retry window until its text is available.
			if (combatAchievementPbScanTicks <= 0) visibleCombatAchievementPage = "";
			return;
		}
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty()) return;
		if (!page.equalsIgnoreCase(visibleCombatAchievementPage))
		{
			visibleCombatAchievementPage = page;
			combatAchievementPbScanTicks = 6;
		}
	}

	private String visibleCombatAchievementPageTitle()
	{
		Widget bossName = client.getWidget(InterfaceID.CaBoss.BOSS_NAME);
		if (!isVisibleWidget(bossName)) return "";
		List<String> texts = new ArrayList<>();
		collectVisibleWidgetTexts(bossName, texts);
		return firstWidgetText(texts);
	}

	private static boolean isVisibleWidget(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	private static String firstWidgetText(List<String> texts)
	{
		if (texts == null) return "";
		for (String raw : texts)
		{
			String text = Text.removeTags(raw == null ? "" : raw).replace('\u00a0', ' ').trim();
			if (!text.isEmpty()) return text;
		}
		return "";
	}

	private void collectVisibleWidgetTexts(Widget widget, List<String> texts)
	{
		collectVisibleWidgetTexts(widget, texts,
			java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Widget, Boolean>()));
	}

	private void collectVisibleWidgetTexts(Widget widget, List<String> texts, java.util.Set<Widget> visited)
	{
		if (widget == null || widget.isHidden() || !visited.add(widget)) return;
		if (widget.getText() != null && !widget.getText().trim().isEmpty()) texts.add(widget.getText().trim());
		collectVisibleWidgetTexts(widget.getChildren(), texts, visited);
		collectVisibleWidgetTexts(widget.getDynamicChildren(), texts, visited);
		collectVisibleWidgetTexts(widget.getStaticChildren(), texts, visited);
		collectVisibleWidgetTexts(widget.getNestedChildren(), texts, visited);
	}

	private void collectVisibleWidgetTexts(Widget[] widgets, List<String> texts, java.util.Set<Widget> visited)
	{
		if (widgets == null) return;
		for (Widget child : widgets) collectVisibleWidgetTexts(child, texts, visited);
	}

	private void collectVisibleWidgetTextsForGroups(Widget widget, java.util.Set<Integer> groupIds, List<String> texts,
		java.util.Set<Widget> visited)
	{
		if (widget == null || widget.isHidden() || !visited.add(widget)) return;
		if (groupIds.contains(widget.getId() >>> 16))
		{
			collectVisibleWidgetTexts(widget, texts);
			return;
		}
		collectVisibleWidgetTextsForGroups(widget.getChildren(), groupIds, texts, visited);
		collectVisibleWidgetTextsForGroups(widget.getDynamicChildren(), groupIds, texts, visited);
		collectVisibleWidgetTextsForGroups(widget.getStaticChildren(), groupIds, texts, visited);
		collectVisibleWidgetTextsForGroups(widget.getNestedChildren(), groupIds, texts, visited);
	}

	private void collectVisibleWidgetTextsForGroups(Widget[] widgets, java.util.Set<Integer> groupIds, List<String> texts,
		java.util.Set<Widget> visited)
	{
		if (widgets == null) return;
		for (Widget child : widgets) collectVisibleWidgetTextsForGroups(child, groupIds, texts, visited);
	}

	private List<Map<String, Object>> parseOfficialBossScoreboards()
	{
		List<Map<String, Object>> records = new ArrayList<>();
		records.addAll(parseToaScoreboard());
		records.addAll(parseTobScoreboard());
		appendOfficialScoreboard(records, InterfaceID.LeviathanScoreboard.TITLE_TEXT,
			InterfaceID.LeviathanScoreboard.PBT, InterfaceID.LeviathanScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.WhispererScoreboard.TITLE_TEXT,
			InterfaceID.WhispererScoreboard.PBT, InterfaceID.WhispererScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.VardorvisScoreboard.TITLE_TEXT,
			InterfaceID.VardorvisScoreboard.PBT, InterfaceID.VardorvisScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.DukeSucellusScoreboard.TITLE_TEXT,
			InterfaceID.DukeSucellusScoreboard.PBT, InterfaceID.DukeSucellusScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.MaggotKingScoreboard.TITLE_TEXT,
			InterfaceID.MaggotKingScoreboard.PBT, InterfaceID.MaggotKingScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.AmoxliatlScoreboard.TITLE_TEXT,
			InterfaceID.AmoxliatlScoreboard.PBT, InterfaceID.AmoxliatlScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.AraxxorScoreboard.TITLE_TEXT,
			InterfaceID.AraxxorScoreboard.PBT, InterfaceID.AraxxorScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.HueyScoreboard.TITLE_TEXT,
			InterfaceID.HueyScoreboard.PBT, InterfaceID.HueyScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.MuspahScoreboard.TITLE_TEXT,
			InterfaceID.MuspahScoreboard.PBT, InterfaceID.MuspahScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.NexScoreboard.TITLE_TEXT,
			InterfaceID.NexScoreboard.PBT, InterfaceID.NexScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.RoyalTitansScoreboard.TITLE_TEXT,
			InterfaceID.RoyalTitansScoreboard.PBT, InterfaceID.RoyalTitansScoreboard.PBT_CONTENT);
		appendOfficialScoreboard(records, InterfaceID.YamaScoreboard.TITLE_TEXT,
			InterfaceID.YamaScoreboard.PBT, InterfaceID.YamaScoreboard.PBT_CONTENT);
		return records;
	}

	private List<Map<String, Object>> parseToaScoreboard()
	{
		Widget scoreboard = client.getWidget(InterfaceID.ToaScoreboard.UNIVERSE);
		if (!isVisibleWidget(scoreboard)) return new ArrayList<>();
		int selectedTab = client.getVarbitValue(VarbitID.TOA_SCOREBOARD_TAB);
		String mode = toaScoreboardMode(selectedTab);
		int[] personalOverallWidgets = {
			InterfaceID.ToaScoreboard.DATA_SOLO_P_O,
			InterfaceID.ToaScoreboard.DATA_2MAN_P_O,
			InterfaceID.ToaScoreboard.DATA_3MAN_P_O,
			InterfaceID.ToaScoreboard.DATA_4MAN_P_O,
			InterfaceID.ToaScoreboard.DATA_5MAN_P_O,
			InterfaceID.ToaScoreboard.DATA_6MAN_P_O,
			InterfaceID.ToaScoreboard.DATA_7MAN_P_O,
			InterfaceID.ToaScoreboard.DATA_8MAN_P_O
		};
		List<String> values = new ArrayList<>();
		for (int widgetId : personalOverallWidgets)
		{
			Widget value = client.getWidget(widgetId);
			values.add(isVisibleWidget(value) ? Text.removeTags(value.getText()).trim() : "");
		}
		return parseToaScoreboardPbs(mode, values);
	}

	static String toaScoreboardMode(int selectedTab)
	{
		// Like ToB, the varbit stores Normal before Entry despite the visual tab order.
		return selectedTab == 1 ? "Entry Mode" : selectedTab == 2 ? "Expert Mode" : "Normal";
	}

	private List<Map<String, Object>> parseTobScoreboard()
	{
		Widget scoreboard = client.getWidget(InterfaceID.TobScoreboard.UNIVERSE);
		if (!isVisibleWidget(scoreboard)) return new ArrayList<>();
		int selectedTab = client.getVarbitValue(VarbitID.TOB_SCOREBOARD_TAB);
		String mode = tobScoreboardMode(selectedTab);
		int[] personalRoomWidgets = {
			InterfaceID.TobScoreboard.DATA_SOLO_P_R,
			InterfaceID.TobScoreboard.DATA_2MAN_P_R,
			InterfaceID.TobScoreboard.DATA_3MAN_P_R,
			InterfaceID.TobScoreboard.DATA_4MAN_P_R,
			InterfaceID.TobScoreboard.DATA_5MAN_P_R
		};
		int[] personalOverallWidgets = {
			InterfaceID.TobScoreboard.DATA_SOLO_P_O,
			InterfaceID.TobScoreboard.DATA_2MAN_P_O,
			InterfaceID.TobScoreboard.DATA_3MAN_P_O,
			InterfaceID.TobScoreboard.DATA_4MAN_P_O,
			InterfaceID.TobScoreboard.DATA_5MAN_P_O
		};
		List<String> roomTimes = new ArrayList<>();
		List<String> overallTimes = new ArrayList<>();
		for (int index = 0; index < personalRoomWidgets.length; index++)
		{
			roomTimes.add(visibleWidgetText(personalRoomWidgets[index]));
			overallTimes.add(visibleWidgetText(personalOverallWidgets[index]));
		}
		return parseTobScoreboardPbs(mode, roomTimes, overallTimes);
	}

	static String tobScoreboardMode(int selectedTab)
	{
		// The varbit order is Normal, Entry, Hard even though the interface tabs
		// are displayed as Entry, Normal, Hard.
		return selectedTab == 1 ? "Entry Mode" : selectedTab == 2 ? "Hard Mode" : "Normal";
	}

	private String visibleWidgetText(int widgetId)
	{
		Widget widget = client.getWidget(widgetId);
		return isVisibleWidget(widget) ? Text.removeTags(widget.getText()).trim() : "";
	}

	static List<Map<String, Object>> parseToaScoreboardPbs(String mode, List<String> personalOverallTimes)
	{
		List<Map<String, Object>> records = new ArrayList<>();
		if (personalOverallTimes == null) return records;
		String recordedBoss = "Tombs of Amascut " + (mode == null ? "" : mode.trim());
		for (int index = 0; index < Math.min(8, personalOverallTimes.size()); index++)
		{
			String value = personalOverallTimes.get(index) == null ? "" : personalOverallTimes.get(index).trim();
			if (!value.matches("[0-9]+(?::[0-9]+){1,2}(?:\\.[0-9]+)?")) continue;
			double seconds;
			try { seconds = parsePbTime(value); }
			catch (NumberFormatException ignored) { continue; }
			if (seconds <= 0) continue;
			Map<String, Object> record = pbPayload(recordedBoss, index + 1, seconds);
			record.put("timeType", "OVERALL");
			records.add(record);
		}
		return records;
	}

	static List<Map<String, Object>> parseTobScoreboardPbs(String mode, List<String> personalRoomTimes,
		List<String> personalOverallTimes)
	{
		List<Map<String, Object>> records = new ArrayList<>();
		String recordedBoss = "Theatre of Blood " + (mode == null ? "" : mode.trim());
		for (int teamIndex = 0; teamIndex < 5; teamIndex++)
		{
			appendTobScoreboardRecord(records, recordedBoss, teamIndex + 1, "ROOM",
				valueAt(personalRoomTimes, teamIndex));
			appendTobScoreboardRecord(records, recordedBoss, teamIndex + 1, "OVERALL",
				valueAt(personalOverallTimes, teamIndex));
		}
		return records;
	}

	private static String valueAt(List<String> values, int index)
	{
		return values != null && index >= 0 && index < values.size() && values.get(index) != null
			? values.get(index).trim() : "";
	}

	private static void appendTobScoreboardRecord(List<Map<String, Object>> records, String recordedBoss,
		int teamSize, String timeType, String value)
	{
		if (!value.matches("[0-9]+(?::[0-9]+){1,2}(?:\\.[0-9]+)?")) return;
		double seconds;
		try { seconds = parsePbTime(value); }
		catch (NumberFormatException ignored) { return; }
		if (seconds <= 0) return;
		Map<String, Object> record = pbPayload(recordedBoss, teamSize, seconds);
		record.put("timeType", timeType);
		records.add(record);
	}

	private void appendOfficialScoreboard(List<Map<String, Object>> records, int titleId, int labelId, int valueId)
	{
		Widget title = client.getWidget(titleId);
		Widget label = client.getWidget(labelId);
		Widget value = client.getWidget(valueId);
		if (!isVisibleWidget(title) || !isVisibleWidget(label) || !isVisibleWidget(value)) return;
		List<String> titleTexts = new ArrayList<>();
		List<String> pbTexts = new ArrayList<>();
		collectVisibleWidgetTexts(title, titleTexts);
		collectVisibleWidgetTexts(label, pbTexts);
		collectVisibleWidgetTexts(value, pbTexts);
		String titleText = String.join(" ", titleTexts);
		String pbText = String.join(" ", pbTexts);
		if (titleText.trim().isEmpty() || pbText.trim().isEmpty()) return;
		records.addAll(parseBossStatisticsBoardPbs(java.util.Arrays.asList(titleText, pbText)));
	}

	static Map<String, Object> parseCombatAchievementBossPb(List<String> widgetTexts)
	{
		if (widgetTexts == null) return null;
		String boss = null;
		Double seconds = null;
		for (String raw : widgetTexts)
		{
			String text = Text.removeTags(raw == null ? "" : raw).replace('\u00a0', ' ').trim();
			Matcher title = Pattern.compile("(?i)^Combat Achievements?\\s*[-–—]\\s*(.+)$").matcher(text);
			if (title.find()) boss = title.group(1).trim();
			Matcher personalBest = Pattern.compile("(?i)Personal Best\\s*:\\s*([0-9]+(?::[0-9]+){0,2}(?:\\.[0-9]+)?)").matcher(text);
			if (personalBest.find())
			{
				try { seconds = parsePbTime(personalBest.group(1)); }
				catch (NumberFormatException ignored) { return null; }
			}
		}
		if (boss == null || boss.isEmpty() || seconds == null || seconds <= 0) return null;
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("boss", boss);
		result.put("seconds", seconds);
		return result;
	}

	static Map<String, Object> parseCombatAchievementBossWidgets(String bossWidgetText, List<String> statsTexts)
	{
		String boss = Text.removeTags(bossWidgetText == null ? "" : bossWidgetText)
			.replace('\u00a0', ' ').trim();
		Matcher title = Pattern.compile("(?i)^Combat Achievements?\\s*[-–—]\\s*(.+)$").matcher(boss);
		if (title.find()) boss = title.group(1).trim();
		if (boss.isEmpty()) return null;
		List<String> combined = new ArrayList<>();
		combined.add("Combat Achievements - " + boss);
		if (statsTexts != null) combined.addAll(statsTexts);
		return parseCombatAchievementBossPb(combined);
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

	private void clearPendingCaptures()
	{
		npcLootEventGate.clear();
		resetPendingClue();
		adventureLogOwner = null;
		adventureLogMenuLoaded = false;
		adventureLogCountersLoaded = false;
		lastAdventureLogContentSignature = "";
		adventureLogRetryTick = 0;
		raidLootContext.clear();
		pendingPbBoss = null;
		pendingPbSeconds = -1;
		pendingPbTeamSize = 0;
		pendingPbMode = null;
		pendingPbTick = -1;
	}

	private void capturePersonalBest(String plainMessage)
	{
		Matcher kill = PB_KILLCOUNT_PATTERN.matcher(plainMessage);
		if (kill.find())
		{
			String boss = kill.group("boss").trim().replace(":", "");
			if (pendingPbSeconds > 0 && pendingPbTick >= 0 && client.getTickCount() - pendingPbTick <= 5)
			{
				submitCategorizedPb(boss, pendingPbTeamSize, pendingPbSeconds, pendingPbMode);
				pendingPbSeconds = -1;
				pendingPbTeamSize = 0;
				pendingPbMode = null;
				pendingPbTick = -1;
			}
			else
			{
				pendingPbBoss = boss;
				pendingPbTick = client.getTickCount();
			}
			return;
		}

		Map<String, Object> newPb = parseChatNewPb(plainMessage);
		if (newPb == null) return;
		double seconds = (Double) newPb.get("seconds");
		int teamSize = (Integer) newPb.get("teamSize");
		if (newPb.containsKey("boss"))
		{
			String totalBoss = (String) newPb.get("boss");
			int totalTeamSize = totalBoss.contains("Theatre of Blood") ? tobTeamSize() : toaTeamSize();
			Map<String, Object> values = pbPayload(tagModeIfMissing(totalBoss, resolvePendingMode()), totalTeamSize, seconds);
			submitPb((String) values.get("boss"), (String) values.get("mode"),
				(Integer) values.get("teamSize"), seconds, "OVERALL");
			pendingPbBoss = null;
			// Deliberately NOT clearing pendingPbSeconds/pendingPbTeamSize/pendingPbMode/
			// pendingPbTick here: the total-completion message and the room/challenge-time
			// message (which set those fields moments earlier) are two separate PB categories
			// ("Overall" and "Rooms"), not the same value twice. Wiping the pending room time
			// here used to discard it before the raid chest ever got a chance to submit it.
			return;
		}
		if (pendingPbBoss != null && pendingPbTick >= 0 && client.getTickCount() - pendingPbTick <= 5)
		{
			submitCategorizedPb(pendingPbBoss, teamSize, seconds, (String) newPb.get("mode"));
			pendingPbBoss = null;
			pendingPbTick = -1;
		}
		else
		{
			pendingPbSeconds = seconds;
			pendingPbTeamSize = teamSize;
			pendingPbMode = (String) newPb.get("mode");
			pendingPbTick = client.getTickCount();
		}
	}

	static Map<String, Object> parseChatNewPb(String message)
	{
		if (message == null) return null;
		message = Text.removeTags(message).replace('\u00a0', ' ').trim();
		Matcher total = PB_RAID_TOTAL_PATTERN.matcher(message);
		Matcher tobTotal = PB_TOB_TOTAL_PATTERN.matcher(message);
		Matcher matchedTotal = total.find() ? total : (tobTotal.find() ? tobTotal : null);
		if (matchedTotal != null)
		{
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("boss", matchedTotal.group("boss"));
			result.put("seconds", parsePbTime(matchedTotal.group("pb")));
			result.put("teamSize", 0);
			return result;
		}
		Matcher raid = PB_RAID_PATTERN.matcher(message);
		Matcher time = PB_NEW_TIME_PATTERN.matcher(message);
		boolean raidMatch = raid.find();
		Matcher matched = raidMatch ? raid : (time.find() ? time : null);
		if (matched == null) return null;
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("seconds", parsePbTime(matched.group("pb")));
		result.put("teamSize", raidMatch ? parseTeamSize(matched.group("teamsize")) : 0);
		Matcher mode = PB_MODE_PATTERN.matcher(message);
		if (mode.find()) result.put("mode", mode.group("mode"));
		return result;
	}

	static Map<String, Object> parseClanPbAnnouncement(String message, String playerName)
	{
		if (message == null || playerName == null || playerName.trim().isEmpty()) return null;
		Matcher announcement = CLAN_PB_ANNOUNCEMENT_PATTERN.matcher(
			Text.removeTags(message).replace('\u00A0', ' ').trim());
		if (!announcement.matches()
			|| !WomMembership.normalizePlayerName(announcement.group("player"))
				.equalsIgnoreCase(WomMembership.normalizePlayerName(playerName))) return null;
		String activity = announcement.group("activity").trim();
		Matcher team = CLAN_PB_TEAM_SIZE_PATTERN.matcher(activity);
		boolean hasTeamSize = team.find();
		int teamSize = hasTeamSize ? parseTeamSize(team.group("size")) : 0;
		if (teamSize > 24) return null;
		if (hasTeamSize) activity = team.replaceFirst("").trim();
		double seconds;
		try { seconds = parsePbTime(announcement.group("pb")); }
		catch (NumberFormatException exception) { return null; }
		if (activity.isEmpty() || seconds <= 0 || seconds > 86400) return null;
		Map<String, Object> payload = pbPayload(activity, teamSize, seconds);
		if (!hasTeamSize && requiresExplicitPbTeamSize((String) payload.get("boss"))) return null;
		return payload;
	}

	private void processAdventureLog()
	{
		if (client.getLocalPlayer() == null) return;
		if (adventureLogMenuLoaded)
		{
			adventureLogOwner = null;
			Widget menu = client.getWidget(InterfaceID.Menu.LJ_LAYER2);
			if (menu != null && menu.getChild(1) != null)
			{
				Matcher title = ADVENTURE_LOG_TITLE_PATTERN.matcher(Text.removeTags(menu.getChild(1).getText()));
				if (title.find())
				{
					adventureLogOwner = title.group(1).trim();
					adventureLogMenuLoaded = false;
				}
			}
		}
		if (!adventureLogCountersLoaded) return;
		Widget parent = client.getWidget(InterfaceID.Journalscroll.TEXTLAYER);
		if (parent == null || parent.isHidden() || parent.getStaticChildren() == null)
		{
			lastAdventureLogContentSignature = "";
			return;
		}
		Widget[] children = parent.getStaticChildren();
		List<String> lines = new ArrayList<>();
		for (Widget child : children) lines.add(Text.removeTags(child.getText()).trim());
		String contentSignature = String.join("\u0000", lines);
		if (client.getTickCount() < adventureLogRetryTick || authenticatedPlayerName == null
			|| authenticatedPlayerName.isEmpty()) return;
		if (contentSignature.equals(lastAdventureLogContentSignature)) return;
		String localName = WomMembership.normalizePlayerName(client.getLocalPlayer().getName());
		String visibleOwner = adventureLogOwnerFromLines(lines);
		boolean ownLog = isOwnAdventureLog(localName, visibleOwner, adventureLogOwner);
		if (!ownLog) return;
		lastAdventureLogContentSignature = contentSignature;
		List<Map<String, Object>> records = parseAdventureLogPbs(lines);
		if (!records.isEmpty())
		{
			long generation = connectionSessionGeneration.get();
			String account = authenticatedPlayerName;
			submitPbBatch(records, java.util.Collections.emptyList(), () -> clientThread.invokeLater(() -> {
				if (isCurrentConnectionSession(generation, account)
					&& contentSignature.equals(lastAdventureLogContentSignature))
				{
					lastAdventureLogContentSignature = "";
					adventureLogRetryTick = client.getTickCount() + 10;
				}
			}));
		}
	}

	static String adventureLogOwnerFromLines(List<String> lines)
	{
		if (lines == null) return "";
		String first = "";
		for (String rawLine : lines)
		{
			String line = Text.removeTags(rawLine == null ? "" : rawLine).trim();
			if (line.isEmpty()) continue;
			if (first.isEmpty()) first = line;
			Matcher title = ADVENTURE_LOG_TITLE_PATTERN.matcher(line);
			if (title.find()) return title.group(1).trim();
		}
		return first;
	}

	static boolean isOwnAdventureLog(String localName, String visibleOwner, String menuOwner)
	{
		String normalizedLocal = WomMembership.normalizePlayerName(localName);
		String normalizedMenu = WomMembership.normalizePlayerName(menuOwner);
		if (!normalizedMenu.isEmpty()) return normalizedLocal.equals(normalizedMenu);
		return normalizedLocal.equals(WomMembership.normalizePlayerName(visibleOwner));
	}

	static List<Map<String, Object>> parseAdventureLogPbs(List<String> lines)
	{
		Map<String, Map<String, Object>> uniqueRecords = new LinkedHashMap<>();
		if (lines == null) return new ArrayList<>();
		String boss = "";
		Map<String, Object> pending = null;
		for (String rawLine : lines)
		{
			String line = rawLine == null ? "" : rawLine.trim();
			if (line.isEmpty())
			{
				pending = null;
				continue;
			}
			Matcher descriptor = ADVENTURE_LOG_PB_PATTERN.matcher(line);
			if (descriptor.matches() && !boss.isEmpty())
			{
				String details = descriptor.group("details");
				String recordedBoss = raidModeFromAdventureDetails(boss, details);
				int teamSize = parseTeamSize(details);
				pending = pbPayload(recordedBoss, teamSize, -1);
				if (details == null && requiresExplicitPbTeamSize((String) pending.get("boss")))
				{
					pending = null;
					continue;
				}
				String kind = descriptor.group("kind");
				pending.put("timeType", kind.equalsIgnoreCase("Room time") ? "ROOM"
					: kind.equalsIgnoreCase("Overall time") ? "OVERALL" : "");
				String inlineTime = descriptor.group("time");
				if (inlineTime != null && !inlineTime.isEmpty())
				{
					pending.put("seconds", parsePbTime(inlineTime));
					putAdventureRecord(uniqueRecords, pending);
					pending = null;
				}
				continue;
			}
			Matcher timeOnly = ADVENTURE_LOG_TIME_ONLY_PATTERN.matcher(line);
			if (pending != null && timeOnly.matches())
			{
				pending.put("seconds", parsePbTime(timeOnly.group("time")));
				putAdventureRecord(uniqueRecords, pending);
				pending = null;
				continue;
			}
			// Unsupported counter statistics are labels, never activity names. In
			// particular, Jad Challenge wave rows must not become PB categories.
			if (line.regionMatches(true, 0, "Fastest ", 0, "Fastest ".length()))
			{
				pending = null;
				continue;
			}
			boss = line;
			pending = null;
		}
		return new ArrayList<>(uniqueRecords.values());
	}

	private static String raidModeFromAdventureDetails(String boss, String details)
	{
		String normalizedBoss = boss == null ? "" : boss.trim();
		String normalizedDetails = details == null ? "" : details.toLowerCase(java.util.Locale.ROOT);
		String combined = normalizedBoss.toLowerCase(java.util.Locale.ROOT);
		if (combined.startsWith("theatre of blood") || combined.startsWith("theater of blood"))
		{
			if (normalizedDetails.contains("hard mode") && !combined.matches(".*\\b(hard|hard mode|hm|hmt)\\s*$"))
				return normalizedBoss + " Hard Mode";
			if (normalizedDetails.contains("entry mode") && !combined.contains("entry"))
				return normalizedBoss + " Entry Mode";
		}
		else if (combined.startsWith("tombs of amascut") && normalizedDetails.contains("expert mode")
			&& !combined.contains("expert")) return normalizedBoss + " Expert Mode";
		return normalizedBoss;
	}

	private static void putAdventureRecord(Map<String, Map<String, Object>> records, Map<String, Object> record)
	{
		double seconds = ((Number) record.get("seconds")).doubleValue();
		if (seconds <= 0) return;
		String key = record.get("boss") + "\n" + record.get("mode") + "\n"
			+ record.get("teamSize") + "\n" + record.get("timeType");
		records.put(key, record);
	}

	// Chambers of Xeric's "Team size: ... Duration: ... (new personal best)" broadcast never
	// names the raid, and CoX sends no correlating kill-count chat message either - so that PB
	// would otherwise sit as pendingPbSeconds with no boss until it expires unresolved. The
	// reward chest that follows is an unambiguous, independent source for "which raid", so use
	// it as a second chance rather than let the PB be lost to a missing chat message.
	private void rescuePendingRaidPb(String eventSource)
	{
		if (pendingPbBoss != null || pendingPbSeconds <= 0 || pendingPbTick < 0
			|| client.getTickCount() - pendingPbTick > PENDING_RAID_PB_MAX_TICKS) return;
		double seconds = pendingPbSeconds;
		int teamSize = pendingPbTeamSize;
		String mode = pendingPbMode;
		pendingPbSeconds = -1;
		pendingPbTeamSize = 0;
		pendingPbMode = null;
		pendingPbTick = -1;
		submitCategorizedPb(eventSource, teamSize, seconds, mode);
	}

	private void submitCategorizedPb(String recordedBoss, int teamSize, double seconds)
	{
		submitCategorizedPb(recordedBoss, teamSize, seconds, null);
	}

	private void submitCategorizedPb(String recordedBoss, int teamSize, double seconds, String mode)
	{
		submitCategorizedPb(recordedBoss, teamSize, seconds, mode, roomTimeType(recordedBoss));
	}

	// Theatre of Blood and Tombs of Amascut also submit a separate "Overall" PB (the total-
	// completion chat line, handled directly in capturePersonalBest); tag the room/challenge-
	// time counterpart so the two don't collide as the same category. Chambers of Xeric has no
	// such split - it only ever sends this one message - so it keeps "". Shared by both the
	// raid-chest rescue path and the clan-announcement path, since either can be the one that
	// actually reaches the server first for a given raid's room-time PB.
	private static String tagModeIfMissing(String recordedBoss, String mode)
	{
		return mode == null || recordedBoss == null
			|| recordedBoss.toLowerCase(java.util.Locale.ROOT).contains(mode.toLowerCase(java.util.Locale.ROOT))
			? recordedBoss : recordedBoss + " " + mode;
	}

	private static String roomTimeType(String recordedBoss)
	{
		return recordedBoss != null
			&& (recordedBoss.contains("Theatre of Blood") || recordedBoss.contains("Tombs of Amascut"))
			? "ROOM" : "";
	}

	// mode comes from a separate message than recordedBoss for Theatre of Blood (its total/
	// challenge time lines never repeat the "(Entry Mode)"/"(Hard Mode)" the wave-complete line
	// carries), so it can't be baked into recordedBoss the way ToA's total-completion text
	// already includes it. Appending it here lets pbPayload()'s own mode detection pick it up
	// instead of silently defaulting to Normal.
	private void submitCategorizedPb(String recordedBoss, int teamSize, double seconds, String mode, String timeType)
	{
		if (teamSize <= 0 && recordedBoss != null)
		{
			if (recordedBoss.contains("Tombs of Amascut")) teamSize = toaTeamSize();
			else if (recordedBoss.contains("Theatre of Blood")) teamSize = tobTeamSize();
		}
		Map<String, Object> values = pbPayload(tagModeIfMissing(recordedBoss, mode), teamSize, seconds);
		submitPb((String) values.get("boss"), (String) values.get("mode"),
			(Integer) values.get("teamSize"), seconds, timeType);
	}

	private int tobTeamSize()
	{
		return occupiedRaidSlots(new int[]{VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1,
			VarbitID.TOB_CLIENT_P2, VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4});
	}

	private int toaTeamSize()
	{
		return occupiedRaidSlots(new int[]{VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1,
			VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3, VarbitID.TOA_CLIENT_P4,
			VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7});
	}

	private int occupiedRaidSlots(int[] varbits)
	{
		int players = 0;
		for (int varbit : varbits) players += Math.min(client.getVarbitValue(varbit), 1);
		return players;
	}

	static Map<String, Object> pbPayload(String recordedBoss, int teamSize, double seconds)
	{
		String boss = recordedBoss == null ? "" : recordedBoss.trim();
		String mode = "";
		String normalized = boss.toLowerCase(java.util.Locale.ROOT)
			.replace('(', ' ').replace(')', ' ').replace(':', ' ').replace('-', ' ')
			.replace('\'', ' ').replace('’', ' ')
			.replaceAll("\\s+", " ").trim();
		String canonicalRaid = null;
		if (normalized.startsWith("theatre of blood") || normalized.startsWith("theater of blood"))
		{
			canonicalRaid = "Theatre of Blood";
			if (normalized.matches(".*\\b(hard|hard mode|hm|hmt)\\s*$")) mode = "Hard Mode";
			else if (normalized.matches(".*\\b(entry mode|story mode|entry)\\s*$")) mode = "Entry Mode";
			else mode = "Normal";
		}
		else if (normalized.startsWith("tombs of amascut"))
		{
			canonicalRaid = "Tombs of Amascut";
			if (normalized.matches(".*\\b(expert mode|expert)\\s*$")) mode = "Expert Mode";
			else if (normalized.matches(".*\\b(entry mode|entry)\\s*$")) mode = "Entry Mode";
			else mode = "Normal";
		}
		else if (normalized.startsWith("chambers of xeric"))
		{
			canonicalRaid = "Chambers of Xeric";
			mode = normalized.matches(".*\\b(challenge mode|challenger mode|cm)\\s*$")
				? "Challenge Mode" : "Normal";
		}
		else
		{
			String canonicalBoss = canonicalDesertTreasureBoss(normalized);
			if (canonicalBoss != null)
			{
				boss = canonicalBoss;
				mode = normalized.matches(".*\\bawakened\\s*$") ? "Awakened" : "Normal";
			}
			else if (normalized.equals("tztok jad") || normalized.equals("tzhaar fight cave"))
			{
				boss = "TzHaar Fight Cave";
			}
			else if (normalized.equals("tzkal zuk") || normalized.equals("the inferno")
				|| normalized.equals("inferno"))
			{
				boss = "Inferno";
			}
			else
			{
				boss = canonicalActivityBoss(normalized, boss);
			}
		}
		if (canonicalRaid != null) boss = canonicalRaid;
		if (teamSize == 1) teamSize = 0;
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("boss", boss);
		result.put("mode", mode);
		result.put("teamSize", Math.max(0, teamSize));
		result.put("seconds", seconds);
		return result;
	}

	private static String canonicalActivityBoss(String normalized, String original)
	{
		switch (normalized)
		{
			case "crystalline hunllef":
			case "gauntlet":
			case "the gauntlet":
				return "Gauntlet";
			case "corrupted hunllef":
			case "corrupted gauntlet":
			case "the corrupted gauntlet":
				return "Corrupted Gauntlet";
			case "sol heredit":
			case "colosseum":
			case "fortis colosseum":
				return "Fortis Colosseum";
			case "the hueycoatl":
			case "hueycoatl":
				return "Hueycoatl";
			case "the phantom muspah":
			case "phantom muspah":
				return "Phantom Muspah";
			case "nightmare":
			case "the nightmare":
				return "The Nightmare";
			case "phosani s nightmare":
			case "phosani nightmare":
			case "phosanis nightmare":
				return "Phosani's Nightmare";
			case "the royal titans":
			case "royal titans":
				return "Royal Titans";
			case "mad angel":
			case "the mad angel":
				return "The Mad Angel";
			default:
				return original;
		}
	}

	private static String canonicalDesertTreasureBoss(String normalized)
	{
		if (normalized.startsWith("the whisperer") || normalized.startsWith("whisperer")) return "The Whisperer";
		if (normalized.startsWith("the leviathan") || normalized.startsWith("leviathan")) return "The Leviathan";
		if (normalized.startsWith("vardorvis")) return "Vardorvis";
		if (normalized.startsWith("duke sucellus")) return "Duke Sucellus";
		return null;
	}

	private static int parseTeamSize(String value)
	{
		if (value == null || value.trim().isEmpty()) return 0;
		Matcher size = Pattern.compile("(?i)(?:team size:\\s*)?(solo|\\d+)(?:\\+|-\\d+)?(?:\\s*players?)?")
			.matcher(value.trim());
		if (!size.find() || "solo".equalsIgnoreCase(size.group(1))) return 0;
		int players = Integer.parseInt(size.group(1));
		return players <= 1 ? 0 : players;
	}

	private static double parsePbTime(String value)
	{
		String[] components = value.split(":");
		double seconds = 0;
		for (String component : components) seconds = seconds * 60 + Double.parseDouble(component);
		return seconds;
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
			|| client.getGameState() != GameState.LOGGED_IN
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
		if (client.getGameState() != GameState.LOGGED_IN || authenticatedPlayerName == null
			|| authenticatedPlayerName.isEmpty()) return;
		fetchEventOverlay();
		if (manualBingo != null) manualBingo.refresh();
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
		boolean initializeSession = !messageSessionInitialized;
		long requestGeneration = messageSessionGeneration.get();
		HttpUrl.Builder urlBuilder = base.newBuilder()
			.addPathSegment("messages")
			.addQueryParameter("after", lastMessageId);
		if (initializeSession)
		{
			urlBuilder.addQueryParameter("sessionStart", "1");
		}
		HttpUrl url = urlBuilder.build();
		okHttpClient.newCall(requestBuilder(url).get().build()).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				if (requestGeneration != messageSessionGeneration.get())
				{
					messageFetchInFlight.set(false);
					return;
				}
				log.debug("Unable to fetch clan messages", exception);
				if (panel != null) panel.setStatus("Sem conexão");
				messageFetchInFlight.set(false);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (requestGeneration != messageSessionGeneration.get())
					{
						return;
					}
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
					if (initializeSession)
					{
						String latestMessageId = response.header("X-Live-On-Latest-Message-Id", "");
						if (!latestMessageId.isEmpty())
						{
							lastMessageId = maxMessageId(lastMessageId, latestMessageId);
							if (!messageCursorAccount.isEmpty())
							{
								messageCursorByAccount.put(messageCursorAccount, lastMessageId);
								configManager.setConfiguration(
									"live-on-clan-messages",
									messageCursorConfigKey(messageCursorAccount),
									lastMessageId);
							}
						}
						messageSessionInitialized = true;
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
							if (isTwitchLiveAnnouncement(message) && !config.liveStatusEnabled())
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

	private static boolean isTwitchLiveAnnouncement(ClanMessage message)
	{
		return message != null
			&& "CLAN".equalsIgnoreCase(message.getMode())
			&& "Live On".equalsIgnoreCase(message.getAuthor())
			&& message.getMessage() != null
			&& message.getMessage().contains("https://www.twitch.tv/");
	}

	private void fetchMvpDrops()
	{
		long generation = connectionSessionGeneration.get();
		String account = authenticatedPlayerName;
		ClanMessagesPanel targetPanel = panel;
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			return;
		}
		getMvpJson("stats/mvp-drops", new okhttp3.Callback()
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
					com.google.gson.JsonElement payload = gson.fromJson(
						response.body().string(), com.google.gson.JsonElement.class);
					MvpDropResponse result = new MvpDropResponse();
					if (payload != null && payload.isJsonArray())
					{
						result.ranking = gson.fromJson(payload, MvpDropEntry[].class);
					}
					else if (payload != null && payload.isJsonObject())
					{
						result = gson.fromJson(payload, MvpDropResponse.class);
					}
					if (targetPanel != null && targetPanel == panel && isCurrentConnectionSession(generation, account))
					{
						MvpDropEntry[] ranking = result == null ? null : result.ranking;
						targetPanel.setMvpDrops(ranking == null
							? java.util.Collections.emptyList()
							: java.util.Arrays.asList(ranking), result == null ? null : result.own);
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
		fetchRecentActivities();
		fetchPanelNotice();
	}

	private void fetchPanelNotice()
	{
		if (client.getGameState() != GameState.LOGGED_IN || authenticatedPlayerName == null
			|| authenticatedPlayerName.isEmpty()) return;
		getJson("panel/notice", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch panel notice", exception);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null) return;
					com.google.gson.JsonObject payload = gson.fromJson(
						response.body().string(), com.google.gson.JsonObject.class);
					String message = payload != null && payload.has("message")
						? payload.get("message").getAsString() : "";
					if (panel != null) panel.updatePanelNotice(message);
				}
			}
		});
	}

	private void fetchEventOverlay()
	{
		if (!eventOverlayFetchInFlight.compareAndSet(false, true)) return;
		String account = authenticatedPlayerName;
		getJson("event-overlay", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch event overlay", exception);
				eventOverlayFetchInFlight.set(false);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null) return;
					EventOverlayState state = gson.fromJson(response.body().string(), EventOverlayState.class);
					if (panel == null || !account.equals(authenticatedPlayerName)) return;
					if (state != null) state.synchronizedNanos = System.nanoTime();
					eventOverlayState = state == null ? new EventOverlayState() : state;
					if (panel != null) panel.updateEventOverlay(eventOverlayState);
				}
				finally
				{
					eventOverlayFetchInFlight.set(false);
				}
			}
		});
	}

	private void saveEventOverlay(EventOverlayState state)
	{
		if (!isStaff || state == null) return;
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("playerName", authenticatedPlayerName);
		payload.put("enabled", state.enabled);
		payload.put("eventName", state.eventName);
		payload.put("password", "");
		payload.put("eventDate", state.eventDate);
		payload.put("staffMessage", state.staffMessage);
		postJson("admin/event-overlay", gson.toJson(payload), new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to save event overlay", exception);
				if (panel != null) panel.setEventOverlayStatus("Falha ao salvar overlay");
			}

			@Override public void onResponse(okhttp3.Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (panel != null) panel.setEventOverlayStatus(
						response.isSuccessful() ? "Overlay atualizado" : "Erro " + response.code());
					if (response.isSuccessful()) fetchEventOverlay();
				}
			}
		});
	}

	boolean isEventOverlayVisible()
	{
		return config.enabled() && config.eventOverlayEnabled() && client.getGameState() == GameState.LOGGED_IN
			&& !authenticatedPlayerName.isEmpty();
	}

	EventOverlayState getEventOverlayState()
	{
		return eventOverlayState;
	}

	static final class EventOverlayState
	{
		boolean enabled;
		String eventName = "";
		String eventDate = "";
		String staffMessage = "";
		double serverTime;
		transient long synchronizedNanos;

		String header()
		{
			return eventName == null ? "" : eventName.trim();
		}
	}

	private void publishPanelNotice(String message)
	{
		if (!isStaff || message == null || message.trim().isEmpty()) return;
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("playerName", authenticatedPlayerName);
		payload.put("message", message.trim());
		postJson("admin/panel-notice", gson.toJson(payload), new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to publish panel notice", exception);
				if (panel != null) panel.setPanelNoticeStatus("Falha ao publicar aviso");
			}

			@Override public void onResponse(okhttp3.Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (panel != null) panel.setPanelNoticeStatus(
						response.isSuccessful() ? "Aviso publicado no Painel" : "Erro " + response.code());
					if (response.isSuccessful()) fetchPanelNotice();
				}
			}
		});
	}

	private void removePanelNotice()
	{
		if (!isStaff) return;
		HttpUrl base = serverBaseUrl();
		if (base == null) return;
		HttpUrl url = base.newBuilder().addPathSegments("admin/panel-notice").build();
		okHttpClient.newCall(requestBuilder(url).delete().build()).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to remove panel notice", exception);
				if (panel != null) panel.setPanelNoticeStatus("Falha ao remover aviso");
			}

			@Override public void onResponse(okhttp3.Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (panel != null) panel.setPanelNoticeStatus(
						response.isSuccessful() ? "Aviso removido" : "Erro " + response.code());
					if (response.isSuccessful() && panel != null) panel.updatePanelNotice("");
				}
			}
		});
	}

	private void fetchRecentActivities()
	{
		if (client.getGameState() != GameState.LOGGED_IN || authenticatedPlayerName == null
			|| authenticatedPlayerName.isEmpty()) return;
		long generation = connectionSessionGeneration.get();
		String account = authenticatedPlayerName;
		getJson("stats/recent-activity", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				if (isCurrentConnectionSession(generation, account))
					log.debug("Unable to fetch recent clan activity", exception);
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!isCurrentConnectionSession(generation, account) || !response.isSuccessful()
						|| response.body() == null) return;
					RecentActivity[] values = gson.fromJson(response.body().string(), RecentActivity[].class);
					if (panel != null) panel.updateRecentActivities(values == null
						? java.util.Collections.emptyList() : java.util.Arrays.asList(values));
				}
			}
		});
	}

	private void fetchMvpEfficiency()
	{
		long generation = connectionSessionGeneration.get();
		String account = authenticatedPlayerName;
		ClanMessagesPanel targetPanel = panel;
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			return;
		}
		getMvpJson("stats/mvp-efficiency", new okhttp3.Callback()
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
					com.google.gson.JsonObject payload = gson.fromJson(
						response.body().string(), com.google.gson.JsonObject.class);
					MvpEfficiencyResponse rankings = parseMvpEfficiencyResponse(payload);
					if (targetPanel != null && targetPanel == panel && rankings != null
						&& isCurrentConnectionSession(generation, account))
					{
						MvpEfficiencyRanking ehb = rankings.ehb;
						MvpEfficiencyRanking ehp = rankings.ehp;
						targetPanel.setMvpEfficiency(
							ehb == null || ehb.ranking == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(ehb.ranking),
							ehb == null ? null : ehb.own,
							ehp == null || ehp.ranking == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(ehp.ranking),
							ehp == null ? null : ehp.own);
					}
				}
			}
		});
	}

	private MvpEfficiencyResponse parseMvpEfficiencyResponse(com.google.gson.JsonObject payload)
	{
		if (payload == null)
		{
			return null;
		}
		MvpEfficiencyResponse response = new MvpEfficiencyResponse();
		response.ehb = parseMvpEfficiencyRanking(payload.get("ehb"));
		response.ehp = parseMvpEfficiencyRanking(payload.get("ehp"));
		return response;
	}

	private MvpEfficiencyRanking parseMvpEfficiencyRanking(com.google.gson.JsonElement payload)
	{
		if (payload == null || payload.isJsonNull())
		{
			return null;
		}
		if (payload.isJsonArray())
		{
			MvpEfficiencyRanking ranking = new MvpEfficiencyRanking();
			ranking.ranking = gson.fromJson(payload, MvpEfficiencyEntry[].class);
			return ranking;
		}
		return payload.isJsonObject() ? gson.fromJson(payload, MvpEfficiencyRanking.class) : null;
	}

	private void fetchLives()
	{
		if (!config.liveStatusEnabled() || client.getGameState() != GameState.LOGGED_IN
			|| authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
		{
			onlineLiveChannels.clear();
			if (panel != null) panel.updateOnlineLives(java.util.Collections.emptyList());
			return;
		}
		long generation = connectionSessionGeneration.get();
		String account = authenticatedPlayerName;
		getJson("lives", liveChannelsCallback(false, generation, account));
		if (isStaff && hasStaffAccessKey())
		{
			getJson("admin/live-channels", liveChannelsCallback(true, generation, account));
		}
	}

	private okhttp3.Callback liveChannelsCallback(boolean managed, long generation, String account)
	{
		return new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				if (!isCurrentConnectionSession(generation, account)) return;
				log.debug("Unable to fetch Twitch channels", exception);
				if (managed && panel != null) panel.setLivesStatus("Falha ao atualizar");
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!isCurrentConnectionSession(generation, account)) return;
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

	private DropSessionGate.State dropSessionState(long generation, String account)
	{
		if (config.enabled() && (config.discordDropsEnabled() || config.statsEnabled())
			&& generation == connectionSessionGeneration.get() && account != null && !account.isEmpty()
			&& (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty())
			&& !membershipRecovery.rejected() && !isTemporaryLootWorld()
			&& account.equalsIgnoreCase(verifiedAccount)) return DropSessionGate.State.WAIT;
		DropSessionGate.State state = DropSessionGate.evaluate(generation, connectionSessionGeneration.get(),
			account, authenticatedPlayerName, config.enabled() && (config.discordDropsEnabled() || config.statsEnabled()),
			client.getGameState());
		if (state != DropSessionGate.State.READY) return state;
		if (isTemporaryLootWorld()) return DropSessionGate.State.CANCEL;
		if (client.getLocalPlayer() == null) return DropSessionGate.State.WAIT;
		return dropParticipationEnabled() ? DropSessionGate.State.READY : DropSessionGate.State.CANCEL;
	}

	private void runWhenDropSessionReady(long generation, String account, Runnable action)
	{
		clientThread.invokeLater(DropSessionGate.task(() -> dropSessionState(generation, account), action));
	}

	private boolean isCurrentConnectionSession(long generation, String account)
	{
		return generation == connectionSessionGeneration.get()
			&& client.getGameState() == GameState.LOGGED_IN
			&& account != null && account.equals(authenticatedPlayerName);
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

	private void fetchPbCategories()
	{
		if (authenticatedPlayerName == null || authenticatedPlayerName.isEmpty()) return;
		if (!pbCategoriesFetchInFlight.compareAndSet(false, true)) return;
		if (panel != null) panel.setPbRefreshEnabled(false);
		getJson("stats/pb-categories", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to fetch PB categories", exception);
				finishPbCategoriesFetch();
			}
			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						log.debug("PB categories returned HTTP {}", response.code());
						return;
					}
					PbCategory[] values = gson.fromJson(response.body().string(), PbCategory[].class);
					if (panel != null) panel.updatePbCategories(values == null
						? java.util.Collections.emptyList() : java.util.Arrays.asList(values));
				}
				finally { finishPbCategoriesFetch(); }
			}
		});
	}

	private void finishPbCategoriesFetch()
	{
		pbCategoriesFetchInFlight.set(false);
		if (panel != null) panel.setPbRefreshEnabled(true);
	}

	private void fetchPbRanking(PbCategory category)
	{
		if (category == null || authenticatedPlayerName == null || authenticatedPlayerName.isEmpty()) return;
		long requestGeneration = pbRankingRequestGeneration.incrementAndGet();
		if (panel != null) panel.beginPbRankingRequest(requestGeneration);
		HttpUrl base = serverBaseUrl();
		if (base == null) return;
		HttpUrl url = base.newBuilder().addPathSegments("stats/pb-ranking")
			.addQueryParameter("boss", category.boss)
			.addQueryParameter("mode", category.mode == null ? "" : category.mode)
			.addQueryParameter("teamSize", Integer.toString(category.team_size))
			.addQueryParameter("timeType", category.time_type == null ? "" : category.time_type).build();
		okHttpClient.newCall(requestBuilder(url).get().build()).enqueue(new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				if (requestGeneration != pbRankingRequestGeneration.get()) return;
				log.debug("Unable to fetch PB ranking", exception);
			}
			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (requestGeneration != pbRankingRequestGeneration.get()) return;
					if (!response.isSuccessful() || response.body() == null)
					{
						log.debug("PB ranking returned HTTP {}", response.code());
						return;
					}
					PbRankingResponse parsed = gson.fromJson(response.body().string(), PbRankingResponse.class);
					if (parsed != null && panel != null) panel.updatePbRanking(parsed, requestGeneration);
				}
			}
		});
	}

	private void submitPb(String boss, String mode, int teamSize, double seconds)
	{
		submitPb(boss, mode, teamSize, seconds, "");
	}

	private void submitPb(String boss, String mode, int teamSize, double seconds, String timeType)
	{
		if (!isPbParticipationEnabled() || !PbCategory.isAllowed(boss, mode) || boss.trim().isEmpty()
			|| !canCaptureOwnRecord(config.pbRankingEnabled()) || !Double.isFinite(seconds) || seconds <= 0) return;
		String account = WomMembership.normalizePlayerName(client.getLocalPlayer().getName());
		HttpUrl base = serverBaseUrl();
		if (base == null || dropDeliveryClient == null) return;
		java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("playerName", account);
		payload.put("boss", boss.trim());
		payload.put("mode", mode == null ? "" : mode.trim());
		payload.put("teamSize", Math.max(0, teamSize));
		payload.put("timeType", timeType == null ? "" : timeType.trim());
		payload.put("seconds", seconds);
		String json = gson.toJson(payload);
		if (!queuedPbSignatures.add(pbDedupSignature(account, boss, mode, teamSize, seconds))) return;
		Request request = new Request.Builder().url(base.newBuilder().addPathSegments("stats/pbs").build())
			.header("X-Live-On-Player", account).header("Authorization", "LiveOnPlayer " + account)
			.header("X-Live-On-Event", UUID.randomUUID().toString())
			.post(RequestBody.create(JSON, json)).build();
		// The server keeps the best time for each category, so replaying an acknowledged PB is harmless.
		dropDeliveryClient.send(request, request, "PB", () -> recordDeliveryState(account, config.pbRankingEnabled()));
	}

	// The in-game completion message and the clan-wide announcement of the same PB can carry
	// different timeType labels (e.g. "OVERALL" vs ""). Dedup on the identity of the record
	// (account/boss/mode/team/time), not the full payload, so both sources of the same PB never
	// produce two requests.
	static String pbDedupSignature(String account, String boss, String mode, int teamSize, double seconds)
	{
		return account + '\n' + boss.trim() + '\n' + (mode == null ? "" : mode.trim())
			+ '\n' + Math.max(0, teamSize) + '\n' + seconds;
	}

	private boolean isPbParticipationEnabled()
	{
		return config.enabled() && config.pbRankingEnabled();
	}

	private void submitPbBatch(List<Map<String, Object>> records, List<String> submittedSignatures)
	{
		submitPbBatch(records, submittedSignatures, () -> {});
	}

	private void submitPbBatch(List<Map<String, Object>> records, List<String> submittedSignatures,
		Runnable onFailure)
	{
		if (!isPbParticipationEnabled() || records == null || records.isEmpty() || authenticatedPlayerName == null
			|| authenticatedPlayerName.isEmpty())
		{
			submittedPbSignatures.removeAll(submittedSignatures);
			return;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("playerName", authenticatedPlayerName);
		List<Map<String, Object>> allowedRecords = new ArrayList<>();
		for (Map<String, Object> record : records)
			if (PbCategory.isAllowed((String) record.get("boss"), (String) record.get("mode"))) allowedRecords.add(record);
		if (allowedRecords.isEmpty())
		{
			submittedPbSignatures.removeAll(submittedSignatures);
			return;
		}
		payload.put("pbs", allowedRecords);
		payload.put("preserveBest", true);
		postJson("stats/pbs", gson.toJson(payload), new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				log.debug("Unable to import PB batch", exception);
				onFailure.run();
				submittedPbSignatures.removeAll(submittedSignatures);
			}
			@Override public void onResponse(okhttp3.Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (response.isSuccessful())
					{
						fetchPbCategories();
					}
					else
					{
						log.debug("PB batch import returned HTTP {}", response.code());
						onFailure.run();
						submittedPbSignatures.removeAll(submittedSignatures);
					}
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
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			clearPendingCaptures();
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			clueRewardGate.clear();
			dropScreenshotEncoder.clear();
			configurePolling();
			connectionSessionGeneration.incrementAndGet();
			messageSessionInitialized = false;
			messageSessionGeneration.incrementAndGet();
			rankRequestsSessionInitialized = false;
			deliveredPinnedMessageIds.clear();
			recentBingoDrops.clear();
			lastLootCountSource = "";
			lastLootCount = -1;
			lastLootCountTick = -1000;
			rankBankItems.clear();
			rankBankItemIds.clear();
			rankBankAccount = "";
			rankBankLoaded = false;
			cancelRankBankRefresh();
			lastObservedRankTotalLevel = -1;
			rankRequestStatusKnown = false;
			rankRequestPending = false;
			questAccount = "";
			lastQuestPoints = -1;
			lastMaximumQuestPoints = -1;
		}
		else if (event.getGameState() == GameState.LOGGED_IN && config.enabled())
		{
			configurePolling();
			if (Boolean.getBoolean(LOW_VALUE_DROP_TEST_PROPERTY) && !lowValueTestNoticeShown)
			{
				lowValueTestNoticeShown = true;
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(new ChatMessageBuilder()
						.append(Color.YELLOW, "[Live On teste] Limite de 1 GP ativo para drops no Discord; MVP abaixo de 1M aparece só aqui.")
						.build())
					.build());
			}
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
				String displayed = original;
				while (!displayed.isEmpty() && ".,;:!?)]}".indexOf(displayed.charAt(displayed.length() - 1)) >= 0)
					displayed = displayed.substring(0, displayed.length() - 1);
				builder.append(Color.CYAN, displayed);
				if (displayed.length() < original.length())
				{
					builder.append(color, original.substring(displayed.length()));
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
			membershipRecovery.begin();
			final long verificationGeneration = connectionSessionGeneration.get();
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
				membershipRecovery.resolved(member, cached.expiresAtMillis);
				String roleName = cached.role;
				if (member)
				{
					boolean staff = WomMembership.isStaffRole(roleName);
					if (roleName != null)
					{
							String norm = roleName.replaceAll("[^A-Za-z0-9]", "" ).toUpperCase(java.util.Locale.ROOT);
							java.util.Set<String> allowed = new java.util.HashSet<>();
							allowed.add("OWNER"); allowed.add("DEPUTYOWNER"); allowed.add("MODERATOR"); allowed.add("ADMINISTRATOR");
							if (allowed.contains(norm)) staff = true;
					}
					isStaff = staff;
					isDeputyOwner = isDeputyOwnerRole(roleName);
					canPublishBroadcast = WomMembership.canPublishBroadcast(roleName);
					switchMessageCursorAccount(rsn);
					authenticatedPlayerName = rsn;
					recoverPendingDrops();
					configurePolling();
					rankRequestStatusKnown = false;
					rankRequestPending = false;
					fetchRankRequestStatus();
					if (panel != null) { panel.setAccessMessage(""); panel.clearRanksStatus(); panel.setAuthenticated(true, staff); panel.setDeputyOwner(isDeputyOwner); panel.setBroadcastAllowed(canPublishBroadcast); panel.setAuthenticatedPlayer(rsn); }
					fetchPbCategories();
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
				@Override public void onFailure(okhttp3.Call failed, IOException exception)
				{
					complete(null, exception);
				}

				@Override public void onResponse(okhttp3.Call completed, Response response)
				{
					try (Response ignored = response)
					{
						if (!response.isSuccessful() || response.body() == null)
							throw new IOException("WOM HTTP " + response.code());
						String body = response.body().string();
						if (body.trim().isEmpty() || "null".equals(body.trim()))
							throw new IOException("Empty WOM membership response");
						complete(WomMembership.parse(gson, body), null);
					}
					catch (IOException | RuntimeException exception) { complete(null, exception); }
				}

				private void complete(WomMembership.Result result, Exception failure)
				{
					clientThread.invokeLater(() -> {
						if (currentWomCall != call || call.isCanceled()) return;
						currentWomCall = null;
						if (!config.enabled() || verificationGeneration != connectionSessionGeneration.get()
							|| client.getLocalPlayer() == null || !rsn.equalsIgnoreCase(
								WomMembership.normalizePlayerName(client.getLocalPlayer().getName())))
						{
							membershipRecovery.stale(System.currentTimeMillis());
							return;
						}
						if (failure != null)
						{
							log.debug("WOM unavailable; retaining pending drops and retrying verification", failure);
							authenticatedPlayerName = "";
							isStaff = false;
							membershipRecovery.failed(System.currentTimeMillis());
							if (panel != null) {
								panel.setAuthenticated(false, false);
								panel.setAccessMessage("Verificação temporariamente indisponível. Tentando novamente...");
								panel.startVerifyCooldown(VERIFY_COOLDOWN_SECONDS);
							}
							return;
						}
						long ttl = result.member ? WOM_CACHE_TTL_SECONDS : WOM_NEGATIVE_CACHE_TTL_SECONDS;
						womCache.put(key, new CacheEntry(result.member, result.role,
							System.currentTimeMillis() + ttl * 1000L));
						verifyToken();
					});
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
		okHttpClient.newCall(request).enqueue(callback);
	}

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

	private void getMvpJson(String path, okhttp3.Callback callback)
	{
		HttpUrl base = serverBaseUrl();
		if (base == null)
		{
			panel.setStatus("URL inválida");
			return;
		}
		HttpUrl url = base.newBuilder().addPathSegments(path).addQueryParameter("includeOwn", "1").build();
		okHttpClient.newCall(requestBuilder(url).get().build()).enqueue(callback);
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
		submittedPbSignatures.clear();
		visibleCombatAchievementPage = "";
		combatAchievementPbScanTicks = 0;
		if (!messageCursorAccount.isEmpty())
		{
			messageCursorByAccount.put(messageCursorAccount, lastMessageId);
			configManager.setConfiguration(
				"live-on-clan-messages",
				messageCursorConfigKey(messageCursorAccount),
				lastMessageId);
		}
		messageCursorAccount = accountKey;
		pbRankingRequestGeneration.incrementAndGet();
		messageSessionInitialized = false;
		messageSessionGeneration.incrementAndGet();
		rankRequestsSessionInitialized = false;
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
		if (!rankRequestsFetchInFlight.compareAndSet(false, true))
		{
			return;
		}
		long requestGeneration = messageSessionGeneration.get();
		getJson("admin/rank-requests", new okhttp3.Callback()
		{
			@Override public void onFailure(okhttp3.Call call, IOException exception)
			{
				try
				{
					if (requestGeneration == messageSessionGeneration.get())
					{
						log.debug("Unable to fetch rank requests", exception);
					}
				}
				finally
				{
					rankRequestsFetchInFlight.set(false);
				}
			}

			@Override public void onResponse(okhttp3.Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (requestGeneration != messageSessionGeneration.get())
					{
						return;
					}
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
						currentPendingKeys.add(rankRequestKey(rankRequest.playerName, rankRequest.rankName));
					}
					if (!rankRequestsSessionInitialized)
					{
						displayedPendingRankRequests.addAll(currentPendingKeys);
						rankRequestsSessionInitialized = true;
						if (!requestList.isEmpty())
						{
							int total = requestList.size();
							String notification = total == 1
								? "1 solicitação de rank pendente."
								: total + " solicitações de rank pendentes.";
							if (panel != null)
							{
								panel.addMessage(new ClanMessage(null, "Live On", notification, "STAFF", false));
							}
							queueBroadcast(notification, false);
						}
					}
					else
					{
						for (RankRequestsPanel.RankRequest rankRequest : requestList)
						{
							String key = rankRequestKey(rankRequest.playerName, rankRequest.rankName);
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
					}
					displayedPendingRankRequests.retainAll(currentPendingKeys);
				}
				finally
				{
					rankRequestsFetchInFlight.set(false);
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
