package cc.oniacute.plugin.bakaachievements.bootstrap;

import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementTreeService;
import cc.oniacute.plugin.bakaachievements.achievement.ProgressService;
import cc.oniacute.plugin.bakaachievements.api.BakaAchievementsApi;
import cc.oniacute.plugin.bakaachievements.chat.AchievementShareService;
import cc.oniacute.plugin.bakaachievements.chat.ChatBroadcastService;
import cc.oniacute.plugin.bakaachievements.command.AchievementCommandRunner;
import cc.oniacute.plugin.bakaachievements.command.BakaCommand;
import cc.oniacute.plugin.bakaachievements.config.ConfigManager;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.gui.ItemFactory;
import cc.oniacute.plugin.bakaachievements.gui.MenuController;
import cc.oniacute.plugin.bakaachievements.gui.MenuRefreshScheduler;
import cc.oniacute.plugin.bakaachievements.gui.MenuSessionService;
import cc.oniacute.plugin.bakaachievements.gui.MenuStack;
import cc.oniacute.plugin.bakaachievements.hook.HdbHook;
import cc.oniacute.plugin.bakaachievements.hook.PapiHook;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataRepository;
import cc.oniacute.plugin.bakaachievements.storage.YamlPlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.AsyncExecutor;
import cc.oniacute.plugin.bakaachievements.util.PlaceholderResolver;
import cc.oniacute.plugin.bakaachievements.vanilla.VanillaSyncService;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Mutable service registry owned by the plugin lifecycle.
 *
 * <p>The container keeps construction order out of the Bukkit entrypoint while
 * preserving the existing public getters on {@code BakaAchievements}.</p>
 */
public final class ServiceContainer {

    private MiniMessage miniMessage;
    private ConfigManager configManager;
    private Messages messages;
    private AsyncExecutor asyncExecutor;
    private AchievementRegistry achievementRegistry;
    private AchievementTreeService achievementTreeService;
    private YamlPlayerDataStorage playerDataStorage;
    private PlayerDataRepository playerDataRepository;
    private DisabledRegistry disabledRegistry;
    private ProgressService progressService;
    private VanillaSyncService vanillaSyncService;
    private ChatBroadcastService chatBroadcastService;
    private ItemFactory itemFactory;
    private MenuController menuController;
    private MenuRefreshScheduler menuRefreshScheduler;
    private MenuSessionService menuSessionService;
    private MenuStack menuStack;
    private HdbHook hdbHook;
    private PapiHook papiHook;
    private BakaAchievementsApi api;
    private BakaCommand bakaCommand;
    private AchievementShareService shareService;
    private AchievementCommandRunner commandRunner;
    private AchievementReloadService reloadService;
    private PlaceholderResolver placeholderResolver;

    public MiniMessage miniMessage() { return miniMessage; }
    public ConfigManager configManager() { return configManager; }
    public Messages messages() { return messages; }
    public AsyncExecutor asyncExecutor() { return asyncExecutor; }
    public AchievementRegistry achievementRegistry() { return achievementRegistry; }
    public AchievementTreeService achievementTreeService() { return achievementTreeService; }
    public YamlPlayerDataStorage playerDataStorage() { return playerDataStorage; }
    public PlayerDataRepository playerDataRepository() { return playerDataRepository; }
    public DisabledRegistry disabledRegistry() { return disabledRegistry; }
    public ProgressService progressService() { return progressService; }
    public VanillaSyncService vanillaSyncService() { return vanillaSyncService; }
    public ChatBroadcastService chatBroadcastService() { return chatBroadcastService; }
    public ItemFactory itemFactory() { return itemFactory; }
    public MenuController menuController() { return menuController; }
    public MenuRefreshScheduler menuRefreshScheduler() { return menuRefreshScheduler; }
    public MenuSessionService menuSessionService() { return menuSessionService; }
    public MenuStack menuStack() { return menuStack; }
    public HdbHook hdbHook() { return hdbHook; }
    public PapiHook papiHook() { return papiHook; }
    public BakaAchievementsApi api() { return api; }
    public BakaCommand bakaCommand() { return bakaCommand; }
    public AchievementShareService shareService() { return shareService; }
    public AchievementCommandRunner commandRunner() { return commandRunner; }
    public AchievementReloadService reloadService() { return reloadService; }
    public PlaceholderResolver placeholderResolver() { return placeholderResolver; }

    public void miniMessage(MiniMessage v) { this.miniMessage = v; }
    public void configManager(ConfigManager v) { this.configManager = v; }
    public void messages(Messages v) { this.messages = v; }
    public void asyncExecutor(AsyncExecutor v) { this.asyncExecutor = v; }
    public void achievementRegistry(AchievementRegistry v) { this.achievementRegistry = v; }
    public void achievementTreeService(AchievementTreeService v) { this.achievementTreeService = v; }
    public void playerDataStorage(YamlPlayerDataStorage v) { this.playerDataStorage = v; }
    public void playerDataRepository(PlayerDataRepository v) { this.playerDataRepository = v; }
    public void disabledRegistry(DisabledRegistry v) { this.disabledRegistry = v; }
    public void progressService(ProgressService v) { this.progressService = v; }
    public void vanillaSyncService(VanillaSyncService v) { this.vanillaSyncService = v; }
    public void chatBroadcastService(ChatBroadcastService v) { this.chatBroadcastService = v; }
    public void itemFactory(ItemFactory v) { this.itemFactory = v; }
    public void menuController(MenuController v) { this.menuController = v; }
    public void menuRefreshScheduler(MenuRefreshScheduler v) { this.menuRefreshScheduler = v; }
    public void menuSessionService(MenuSessionService v) { this.menuSessionService = v; }
    public void menuStack(MenuStack v) { this.menuStack = v; }
    public void hdbHook(HdbHook v) { this.hdbHook = v; }
    public void papiHook(PapiHook v) { this.papiHook = v; }
    public void api(BakaAchievementsApi v) { this.api = v; }
    public void bakaCommand(BakaCommand v) { this.bakaCommand = v; }
    public void shareService(AchievementShareService v) { this.shareService = v; }
    public void commandRunner(AchievementCommandRunner v) { this.commandRunner = v; }
    public void reloadService(AchievementReloadService v) { this.reloadService = v; }
    public void placeholderResolver(PlaceholderResolver v) { this.placeholderResolver = v; }
}
