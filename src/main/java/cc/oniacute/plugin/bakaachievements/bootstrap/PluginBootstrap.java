package cc.oniacute.plugin.bakaachievements.bootstrap;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Builds and tears down all runtime services.
 */
public final class PluginBootstrap {

    private final BakaAchievements plugin;
    private ServiceContainer services;
    private int flushTaskId = -1;
    private int scanTaskId = -1;

    public PluginBootstrap(BakaAchievements plugin) {
        this.plugin = plugin;
    }

    public ServiceContainer enable() {
        services = new ServiceContainer();
        plugin.installServices(services);

        services.miniMessage(MiniMessage.miniMessage());

        ConfigManager configManager = new ConfigManager(plugin);
        configManager.loadAll();
        services.configManager(configManager);
        services.messages(new Messages(plugin));

        AsyncExecutor asyncExecutor = new AsyncExecutor(plugin);
        asyncExecutor.init(configManager.getThreadPoolSize());
        services.asyncExecutor(asyncExecutor);

        services.achievementRegistry(new AchievementRegistry());
        services.disabledRegistry(new DisabledRegistry());
        services.placeholderResolver(new PlaceholderResolver());

        YamlPlayerDataStorage storage = new YamlPlayerDataStorage(plugin, asyncExecutor);
        services.playerDataStorage(storage);
        services.playerDataRepository(new PlayerDataRepository(storage));

        services.achievementTreeService(new AchievementTreeService(
                services.achievementRegistry(), services.disabledRegistry()));
        services.reloadService(new AchievementReloadService(
                plugin, configManager, services.messages(),
                services.achievementRegistry(), services.disabledRegistry()));

        services.reloadService().reloadAchievements();
        services.reloadService().loadDisabled();

        services.progressService(new ProgressService(plugin,
                services.achievementRegistry(), storage, asyncExecutor));
        services.chatBroadcastService(new ChatBroadcastService(plugin));
        services.vanillaSyncService(new VanillaSyncService(plugin,
                services.achievementRegistry(), storage));

        HdbHook hdbHook = new HdbHook(plugin);
        hdbHook.init();
        services.hdbHook(hdbHook);

        services.menuStack(new MenuStack());
        services.menuSessionService(new MenuSessionService());
        services.menuRefreshScheduler(new MenuRefreshScheduler(plugin));
        services.itemFactory(new ItemFactory(services.miniMessage(), services.messages(), hdbHook));

        MenuController menuController = new MenuController(plugin,
                services.achievementRegistry(), storage,
                services.disabledRegistry(), services.itemFactory());
        menuController.setRefreshScheduler(services.menuRefreshScheduler());
        services.menuController(menuController);

        services.commandRunner(new AchievementCommandRunner(
                plugin, menuController, services.menuStack()));
        services.progressService().setCommandRunner(services.commandRunner());

        services.shareService(new AchievementShareService(plugin, storage));
        services.menuController().setShareService(services.shareService());

        services.bakaCommand(new BakaCommand(plugin, services.menuController(),
                services.achievementRegistry(), storage,
                services.progressService(), services.disabledRegistry()));

        services.api(new BakaAchievementsApi(plugin, services.achievementRegistry(),
                storage, services.progressService()));

        registerEvents();

        services.papiHook(new PapiHook(plugin));
        services.papiHook().tryRegister();

        startScheduledTasks();

        plugin.getLogger().info("BakaAchievements v" + plugin.getPluginMeta().getVersion()
                + " 已启动（成就总数: " + services.achievementRegistry().getAchievementCount() + "）");
        return services;
    }

    public void disable() {
        if (services == null) return;

        if (flushTaskId >= 0) plugin.getServer().getScheduler().cancelTask(flushTaskId);
        if (scanTaskId >= 0) plugin.getServer().getScheduler().cancelTask(scanTaskId);

        if (services.menuRefreshScheduler() != null) {
            services.menuRefreshScheduler().cancelAll();
        }
        if (services.papiHook() != null) {
            services.papiHook().unregister();
        }
        if (services.playerDataStorage() != null) {
            try {
                services.playerDataStorage().flushAll().join();
            } catch (Exception e) {
                plugin.getLogger().severe("最终刷盘失败: " + e.getMessage());
            }
        }
        if (services.asyncExecutor() != null) {
            services.asyncExecutor().shutdown();
        }

        plugin.getServer().getScheduler().cancelTasks(plugin);
        plugin.getLogger().info("BakaAchievements 已停止");
    }

    private void registerEvents() {
        plugin.getServer().getPluginManager().registerEvents(plugin, plugin);
        plugin.getServer().getPluginManager().registerEvents(services.menuController(), plugin);
        plugin.getServer().getPluginManager().registerEvents(services.vanillaSyncService(), plugin);
        plugin.getServer().getPluginManager().registerEvents(services.chatBroadcastService(), plugin);
    }

    private void startScheduledTasks() {
        long flushInterval = services.configManager().getFlushIntervalTicks();
        flushTaskId = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, () -> services.playerDataStorage().flushAll(),
                flushInterval, flushInterval).getTaskId();

        long scanInterval = services.configManager().getScanIntervalTicks();
        scanTaskId = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        services.progressService().evaluateAll(player);
                    }
                },
                scanInterval, scanInterval).getTaskId();
    }

    public void handlePlayerJoin(Player player) {
        services.playerDataStorage().load(player.getUniqueId()).thenAccept(data ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    services.vanillaSyncService().syncCompletedSilently(player, data);
                    services.progressService().evaluateAll(player);
                }));
    }
}
