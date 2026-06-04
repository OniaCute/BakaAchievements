package cc.oniacute.plugin.bakaachievements;

import cc.oniacute.plugin.bakaachievements.achievement.AchievementLoader;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.achievement.Category;
import cc.oniacute.plugin.bakaachievements.achievement.ProgressService;
import cc.oniacute.plugin.bakaachievements.api.BakaAchievementsApi;
import cc.oniacute.plugin.bakaachievements.chat.ChatBroadcastService;
import cc.oniacute.plugin.bakaachievements.command.BakaCommand;
import cc.oniacute.plugin.bakaachievements.config.ConfigManager;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.gui.ItemFactory;
import cc.oniacute.plugin.bakaachievements.gui.MenuController;
import cc.oniacute.plugin.bakaachievements.hook.PapiHook;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.storage.YamlPlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.AsyncExecutor;
import cc.oniacute.plugin.bakaachievements.vanilla.VanillaSyncService;
import cc.oniacute.plugin.bakaachievements.vanilla.VanillaTreeBuilder;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * BakaAchievements —— Paper 1.21.11 自定义成就插件。
 * <p>
 * 功能包括：成就树管理、原版成就同步、箱子 GUI、
 * 自定义条件评估、PlaceholderAPI 支持、热重载。
 * </p>
 *
 * @author OniaCute (www.oniacute.cc)
 */
public final class BakaAchievements extends JavaPlugin implements Listener {

    private static BakaAchievements instance;

    // ── 核心组件 ─────────────────────────────────────────
    private MiniMessage              miniMessage;
    private ConfigManager            configManager;
    private Messages                 messages;
    private AsyncExecutor            asyncExecutor;

    // ── 业务组件 ─────────────────────────────────────────
    private AchievementRegistry      achievementRegistry;
    private YamlPlayerDataStorage    playerDataStorage;
    private DisabledRegistry         disabledRegistry;
    private ProgressService          progressService;
    private VanillaSyncService       vanillaSyncService;
    private ChatBroadcastService     chatBroadcastService;

    // ── GUI ──────────────────────────────────────────────
    private ItemFactory              itemFactory;
    private MenuController           menuController;

    // ── API & Hook ───────────────────────────────────────
    private PapiHook                 papiHook;
    private BakaAchievementsApi      api;
    private BakaCommand              bakaCommand;

    // ── 定时任务 ID ─────────────────────────────────────
    private int flushTaskId   = -1;
    private int scanTaskId    = -1;

    @Override
    public void onEnable() {
        instance = this;

        // ── 1. 基础组件 ───────────────────────────────────
        miniMessage = MiniMessage.miniMessage();
        configManager = new ConfigManager(this);
        configManager.loadAll();
        messages = new Messages(this);

        asyncExecutor = new AsyncExecutor(this);
        asyncExecutor.init(configManager.getThreadPoolSize());

        // ── 2. 数据层 ─────────────────────────────────────
        achievementRegistry = new AchievementRegistry();
        playerDataStorage = new YamlPlayerDataStorage(this, asyncExecutor);
        disabledRegistry = new DisabledRegistry();

        // ── 3. 加载成就 ───────────────────────────────────
        loadAchievements();

        // ── 4. 加载禁用列表 ──────────────────────────────
        loadDisabled();

        // ── 5. 业务服务 ───────────────────────────────────
        progressService = new ProgressService(this, achievementRegistry,
                playerDataStorage, asyncExecutor);

        chatBroadcastService = new ChatBroadcastService(this);
        progressService.setChatBroadcastService(chatBroadcastService);

        vanillaSyncService = new VanillaSyncService(this, achievementRegistry,
                playerDataStorage);
        vanillaSyncService.setChatBroadcastService(chatBroadcastService);

        // ── 6. GUI ────────────────────────────────────────
        itemFactory = new ItemFactory(miniMessage, messages);
        menuController = new MenuController(this, achievementRegistry,
                playerDataStorage, disabledRegistry, itemFactory);

        // ── 7. 命令 ───────────────────────────────────────
        bakaCommand = new BakaCommand(this, menuController,
                achievementRegistry, playerDataStorage, progressService, disabledRegistry);

        // ── 8. API ────────────────────────────────────────
        api = new BakaAchievementsApi(this, achievementRegistry,
                playerDataStorage, progressService);

        // ── 9. 事件注册 ───────────────────────────────────
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(menuController, this);
        getServer().getPluginManager().registerEvents(vanillaSyncService, this);
        getServer().getPluginManager().registerEvents(chatBroadcastService, this);

        // ── 10. PlaceholderAPI ────────────────────────────
        papiHook = new PapiHook(this);
        papiHook.tryRegister();

        // ── 11. 定时任务 ──────────────────────────────────
        startScheduledTasks();

        getLogger().info("BakaAchievements v" + getPluginMeta().getVersion()
                + " 已启动（成就总数: " + achievementRegistry.getAchievementCount() + "）");
    }

    @Override
    public void onDisable() {
        // 停止定时任务
        if (flushTaskId >= 0) getServer().getScheduler().cancelTask(flushTaskId);
        if (scanTaskId >= 0) getServer().getScheduler().cancelTask(scanTaskId);

        // 注销 PAPI
        if (papiHook != null) {
            papiHook.unregister();
        }

        // 强制刷盘（必须等待完成，否则关服时数据可能丢失）
        if (playerDataStorage != null) {
            try {
                playerDataStorage.flushAll().join();
            } catch (Exception e) {
                getLogger().severe("最终刷盘失败: " + e.getMessage());
            }
        }

        // 关闭异步线程池
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
        }

        getServer().getScheduler().cancelTasks(this);
        getLogger().info("BakaAchievements 已停止");
        instance = null;
    }

    // ─────────────────────────────────────────────────────
    //  加载
    // ─────────────────────────────────────────────────────

    private void loadAchievements() {
        try {
            // 自定义成就
            AchievementLoader loader = new AchievementLoader(this);
            Category customRoot = loader.load();

            // 原版成就镜像
            VanillaTreeBuilder vanillaBuilder = new VanillaTreeBuilder(this);
            Category vanillaRoot = vanillaBuilder.build();

            // 合并到根
            java.util.List<cc.oniacute.plugin.bakaachievements.achievement.AchievementNode> children
                    = new ArrayList<>();
            children.addAll(customRoot.children());
            children.add(vanillaRoot);

            Category root = new Category("__root__", "root", "成就列表",
                    org.bukkit.Material.CHEST, false, children);
            achievementRegistry.setRoot(root);
        } catch (Exception e) {
            getLogger().severe("成就加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadDisabled() {
        try {
            org.bukkit.configuration.file.YamlConfiguration disabledCfg =
                    configManager.getDisabledConfig();
            if (disabledCfg.contains("disabled")) {
                java.util.Set<String> set = new java.util.HashSet<>(
                        disabledCfg.getStringList("disabled"));
                disabledRegistry.setAll(set);
            }
        } catch (Exception e) {
            getLogger().warning("加载 disabled.yml 失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────
    //  定时任务
    // ─────────────────────────────────────────────────────

    private void startScheduledTasks() {
        // 脏数据刷盘（异步 IO，不含 PAPI，安全）
        long flushInterval = configManager.getFlushIntervalTicks();
        flushTaskId = getServer().getScheduler().runTaskTimerAsynchronously(
                this, () -> playerDataStorage.flushAll(),
                flushInterval, flushInterval
        ).getTaskId();

        // 成就条件定时扫描
        // 先在主线程收集在线玩家列表，再逐个提交评估（评估内部先主线程 PAPI → 异步逻辑）
        long scanInterval = configManager.getScanIntervalTicks();
        scanTaskId = getServer().getScheduler().runTaskTimer(
                this, () -> {
                    for (Player player : getServer().getOnlinePlayers()) {
                        progressService.evaluateAll(player);
                    }
                },
                scanInterval, scanInterval
        ).getTaskId();
    }

    // ─────────────────────────────────────────────────────
    //  事件
    // ─────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 预加载玩家数据（异步，但不阻塞评估）
        playerDataStorage.load(player.getUniqueId()).thenAccept(data -> {
            // 数据加载完成后，在主线程评估
            Bukkit.getScheduler().runTask(this, () -> progressService.evaluateAll(player));
        });
    }

    // ─────────────────────────────────────────────────────
    //  命令分发
    // ─────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        return bakaCommand.onCommand(sender, command, label, args);
    }

    @Override
    public java.util.@org.jetbrains.annotations.Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                           @NotNull Command command,
                                                           @NotNull String alias,
                                                           @NotNull String @NotNull [] args) {
        return bakaCommand.onTabComplete(sender, command, alias, args);
    }

    // ─────────────────────────────────────────────────────
    //  公开访问器
    // ─────────────────────────────────────────────────────

    public static BakaAchievements getInstance() { return instance; }
    public MiniMessage getMiniMessage() { return miniMessage; }
    public ConfigManager getConfigManager() { return configManager; }
    public Messages getMessages() { return messages; }
    public PapiHook getPapiHook() { return papiHook; }
    public BakaAchievementsApi getApi() { return api; }
    public AchievementRegistry getAchievementRegistry() { return achievementRegistry; }
    public ProgressService getProgressService() { return progressService; }
    public DisabledRegistry getDisabledRegistry() { return disabledRegistry; }
}
