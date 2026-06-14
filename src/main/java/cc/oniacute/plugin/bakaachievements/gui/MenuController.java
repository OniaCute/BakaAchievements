package cc.oniacute.plugin.bakaachievements.gui;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.Achievement;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementTreeService;
import cc.oniacute.plugin.bakaachievements.achievement.Category;
import cc.oniacute.plugin.bakaachievements.achievement.MixedNode;
import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;
import cc.oniacute.plugin.bakaachievements.achievement.ProgressService;
import cc.oniacute.plugin.bakaachievements.achievement.condition.Condition;
import cc.oniacute.plugin.bakaachievements.achievement.condition.ConditionEvaluator;
import cc.oniacute.plugin.bakaachievements.chat.AchievementShareService;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.hook.PermissionResolver;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.ComponentUtil;
import cc.oniacute.plugin.bakaachievements.util.MiniMessageUtil;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 菜单控制器——管理所有 GUI 菜单的打开、渲染、事件处理和刷新。
 * <p>
 * 实现 {@link Listener} 以拦截箱子菜单交互事件；
 * 所有渲染在主线程完成，满足 Bukkit API 线程安全要求。
 * </p>
 *
 * <h3>菜单布局（6行 × 9列 = 54槽）</h3>
 * <pre>
 *   行1 ( 0- 8): 全部周圈玻璃板
 *   行2 ( 9-17): 玻璃[9] | 内容[10-16] | 玻璃[17]
 *   行3 (18-26): 玻璃[18] | 内容[19-25] | 玻璃[26]
 *   行4 (27-35): 玻璃[27] | 内容[28-34] | 玻璃[35]
 *   行5 (36-44): 全部玻璃板（倒数第2行）
 *   行6 (45-53): Action Bar
 * </pre>
 *
 * <h3>Action Bar（槽45-53）</h3>
 * <pre>
 *   45: 返回上级（首页有上级时）/ 上一页（第二页起）/ 填充物（根首页）
 *   46-48: 填充物
 *   49: Stats（当前分类名 + 进度）—— 左键返回根目录，Shift+左键分享总进度
 *   50: Special（默认填充物）
 *   51-52: 填充物
 *   53: 下一页（仅当有后续页时）/ 填充物
 * </pre>
 *
 * <h3>线程安全</h3>
 * <p>
 * 状态映射使用 {@link ConcurrentHashMap}；
 * 所有 {@code setItem()} 和 {@code openInventory()} 调用均在主线程执行。
 * </p>
 */
public final class MenuController implements Listener {

    // ── 常量 ─────────────────────────────────────────────

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 21;   // 3行 × 7列

    /** content slot → inventory slot (0-indexed) */
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    /** 周圈玻璃板槽位（行1全满 + 左右两侧 + 倒数第2行全满） */
    private static final int[] BORDER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17, 18, 26, 27, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    // Action Bar 槽位
    private static final int SLOT_PREV  = 45;
    private static final int SLOT_FILL1 = 46;
    private static final int SLOT_FILL2 = 47;
    private static final int SLOT_FILL3 = 48;
    private static final int SLOT_STATS = 49;
    private static final int SLOT_SPECIAL = 50;
    private static final int SLOT_FILL4 = 51;
    private static final int SLOT_PAGE_PREV = 52;
    private static final int SLOT_NEXT  = 53;

    // PDC 标记键
    private static final String PDC_KEY = "baka_gui";
    private static final String SEARCH_STACK_PREFIX = "__search__:";

    // ── 字段 ─────────────────────────────────────────────

    private final BakaAchievements plugin;
    private final AchievementRegistry registry;
    private final PlayerDataStorage storage;
    private final DisabledRegistry disabledRegistry;
    private final ItemFactory itemFactory;
    private final Messages messages;
    private final MiniMessage miniMessage;
    private final ProgressService progressService;
    private final AchievementTreeService treeService;
    private final MenuSessionService sessionService;
    private final ActionBarRenderer actionBarRenderer;

    private MenuRefreshScheduler refreshScheduler;
    private AchievementShareService shareService;

    /** 玩家当前打开的菜单路径 */
    private final Map<UUID, String> openMenuPath = new ConcurrentHashMap<>();
    /** 玩家当前菜单类型: "category" / "search" / "check" */
    private final Map<UUID, String> openMenuType = new ConcurrentHashMap<>();
    /** 玩家当前页码 */
    private final Map<UUID, Integer> openMenuPage = new ConcurrentHashMap<>();
    /** 检查模式下目标玩家 UUID */
    private final Map<UUID, UUID> openMenuCheckTarget = new ConcurrentHashMap<>();
    /** 检查模式下目标玩家名称 */
    private final Map<UUID, String> openMenuCheckTargetName = new ConcurrentHashMap<>();
    /** 检查模式下目标玩家数据 */
    private final Map<UUID, PlayerAchievementData> openMenuCheckData = new ConcurrentHashMap<>();

    /** PDC NamespacedKey（惰性初始化） */
    private NamespacedKey pdcKey;

    // ── 构造器 ───────────────────────────────────────────

    /**
     * 构造菜单控制器。
     *
     * @param plugin          插件主实例
     * @param registry        成就注册表
     * @param storage         玩家数据存储
     * @param disabledRegistry 禁用注册表
     * @param itemFactory     物品工厂
     */
    public MenuController(BakaAchievements plugin, AchievementRegistry registry,
                          PlayerDataStorage storage, DisabledRegistry disabledRegistry,
                          ItemFactory itemFactory) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
        this.disabledRegistry = disabledRegistry;
        this.itemFactory = itemFactory;
        this.messages = plugin.getMessages();
        this.miniMessage = plugin.getMiniMessage();
        this.progressService = plugin.getProgressService();
        this.treeService = plugin.getServices().achievementTreeService();
        this.sessionService = plugin.getServices().menuSessionService();
        this.actionBarRenderer = new ActionBarRenderer(plugin, registry, itemFactory);
    }

    // ── Setter ───────────────────────────────────────────

    /** 设置刷新调度器（由 {@link BakaAchievements#onEnable()} 注入） */
    public void setRefreshScheduler(MenuRefreshScheduler s) {
        this.refreshScheduler = s;
    }

    /** 设置分享服务（由 {@link BakaAchievements#onEnable()} 注入） */
    public void setShareService(AchievementShareService s) {
        this.shareService = s;
    }

    // ── 公共方法 ─────────────────────────────────────────

    /**
     * 为玩家打开根分类菜单。
     *
     * @param player 玩家
     */
    public void openRoot(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> openRoot(player));
            return;
        }
        plugin.getMenuStack().clear(player);
        openMenuForPath(player, PathUtil.ROOT, 0);
    }

    /**
     * 为玩家打开指定路径的分类菜单。
     *
     * @param player       玩家
     * @param categoryPath 分类节点路径
     */
    public void openCategory(Player player, String categoryPath) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, categoryPath));
            return;
        }
        openMenuForPath(player, categoryPath, 0);
    }

    /**
     * 为玩家打开搜索结果菜单。
     *
     * @param player  玩家
     * @param keyword 搜索关键词
     */
    public void openSearch(Player player, String keyword) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> openSearch(player, keyword));
            return;
        }
        if (keyword == null || keyword.isBlank()) {
            openRoot(player);
            return;
        }
        renderSearch(player, keyword, 0);
    }

    /**
     * 以只读模式查看目标玩家的成就状态。
     *
     * @param viewer     查看者
     * @param targetName 目标玩家名称
     * @param targetUUID 目标玩家 UUID
     * @param data       目标玩家成就数据
     */
    public void openPlayerCheck(Player viewer, String targetName, UUID targetUUID,
                                 PlayerAchievementData data) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> openPlayerCheck(viewer, targetName, targetUUID, data));
            return;
        }
        UUID viewerUUID = viewer.getUniqueId();

        // 保存检查上下文
        openMenuPath.put(viewerUUID, PathUtil.ROOT);
        openMenuType.put(viewerUUID, "check");
        openMenuPage.put(viewerUUID, 0);
        openMenuCheckTarget.put(viewerUUID, targetUUID);
        openMenuCheckTargetName.put(viewerUUID, targetName);
        openMenuCheckData.put(viewerUUID, data);
        saveSession(viewerUUID, new MenuSession(MenuSession.MenuType.CHECK,
                PathUtil.ROOT, 0, null, targetUUID, targetName, data));

        // 清理菜单历史
        plugin.getMenuStack().clear(viewer);

        renderCheckMenu(viewer, targetName, targetUUID, data, PathUtil.ROOT, 0);
    }

    private void renderCheckPage(Player viewer, int page) {
        UUID viewerUUID = viewer.getUniqueId();
        String targetName = openMenuCheckTargetName.get(viewerUUID);
        UUID targetUUID = openMenuCheckTarget.get(viewerUUID);
        PlayerAchievementData data = openMenuCheckData.get(viewerUUID);
        String path = openMenuPath.getOrDefault(viewerUUID, PathUtil.ROOT);
        if (targetName == null || targetUUID == null || data == null) {
            viewer.closeInventory();
            return;
        }
        renderCheckMenu(viewer, targetName, targetUUID, data, path, page);
    }

    private void openCheckCategory(Player viewer, String categoryPath, int page) {
        UUID viewerUUID = viewer.getUniqueId();
        String targetName = openMenuCheckTargetName.get(viewerUUID);
        UUID targetUUID = openMenuCheckTarget.get(viewerUUID);
        PlayerAchievementData data = openMenuCheckData.get(viewerUUID);
        if (targetName == null || targetUUID == null || data == null) {
            viewer.closeInventory();
            return;
        }
        renderCheckMenu(viewer, targetName, targetUUID, data, categoryPath, page);
    }

    private void renderCheckMenu(Player viewer, String targetName, UUID targetUUID,
                                 PlayerAchievementData data, String path, int page) {
        UUID viewerUUID = viewer.getUniqueId();
        openMenuPath.put(viewerUUID, path);
        openMenuType.put(viewerUUID, "check");
        openMenuPage.put(viewerUUID, page);
        openMenuCheckTarget.put(viewerUUID, targetUUID);
        openMenuCheckTargetName.put(viewerUUID, targetName);
        openMenuCheckData.put(viewerUUID, data);
        saveSession(viewerUUID, new MenuSession(MenuSession.MenuType.CHECK,
                path, page, null, targetUUID, targetName, data));

        String title = messages.getMessage("GUI_CHECK_TITLE",
                "<gradient:#6cd3ff:#dc67ff>成就 - {player}</gradient>")
                .replace("{player_displayname}", targetName)
                .replace("{player}", targetName);
        Inventory inv = createInventory(path, "check", title);

        AchievementNode node = registry.getNode(path).orElse(null);
        List<AchievementNode> children;
        if (node instanceof Category cat) {
            children = cat.children();
        } else if (node instanceof MixedNode mixed) {
            children = mixed.children();
        } else {
            Category rootCat = registry.getRoot();
            if (rootCat == null) {
                viewer.openInventory(inv);
                return;
            }
            children = rootCat.children();
            path = PathUtil.ROOT;
            openMenuPath.put(viewerUUID, path);
        }

        List<AchievementNode> items = filterAccessibleNodes(children, viewer, data, false);
        fillMenu(inv, viewer, items, page, path, data, null);

        viewer.openInventory(inv);
        scheduleRefresh(viewer, () -> renderCheckPage(viewer,
                openMenuPage.getOrDefault(viewer.getUniqueId(), 0)));
    }

    /**
     * 刷新玩家当前打开的菜单（由 {@code [refresh]} 命令触发）。
     *
     * @param player 玩家
     */
    public void refreshCurrentMenu(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshCurrentMenu(player));
            return;
        }

        UUID uuid = player.getUniqueId();
        String path = openMenuPath.get(uuid);
        String type = openMenuType.get(uuid);
        int page = openMenuPage.getOrDefault(uuid, 0);

        if (path == null) return;

        // 先取消旧刷新任务
        if (refreshScheduler != null) {
            refreshScheduler.triggerNow(player);
        }

        try {
            switch (type) {
                case "search" -> renderSearch(player, path, page);
                case "check"  -> {
                    UUID targetUUID = openMenuCheckTarget.get(uuid);
                    String targetName = openMenuCheckTargetName.get(uuid);
                    PlayerAchievementData data = openMenuCheckData.get(uuid);
                    if (targetUUID != null && targetName != null && data != null) {
                        renderCheckMenu(player, targetName, targetUUID, data, path, page);
                    }
                }
                default -> openMenuForPath(player, path, page);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "刷新菜单失败，玩家=" + player.getName() + " path=" + path, e);
        }
    }

    // ── 事件处理 ─────────────────────────────────────────

    /**
     * 处理箱子菜单点击事件。
     * <p>
     * 非本插件菜单直接返回；所有点击均取消（包括漏斗交互）。
     * 仅左键分类/成就、Shift+左键分享等操作有实际效果。
     * </p>
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof BakaMenuHolder holder)) return;

        // 全部取消，防止玩家拿走任何物品
        event.setCancelled(true);

        // 仅处理实际点击了物品的情况
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // 区分顶部箱子与玩家背包
        if (slot >= inv.getSize()) return; // 玩家背包点击，忽略

        // Action Bar 处理
        if (slot >= 45 && slot <= 53) {
            handleActionBarClick(event, player, holder, slot);
            return;
        }

        // 周圈玻璃板忽略
        if (isBorderSlot(slot)) return;

        // 内容区处理
        if (isContentSlot(slot)) {
            if (handleContentClick(event, player, holder, slot)) {
                playClickSound(player);
            }
        }
    }

    /**
     * 取消所有拖拽（防止玩家拖拽物品进菜单）。
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BakaMenuHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * 防止漏斗移走菜单物品。
     * <p>
     * 高优先级确保在任何漏斗操作前拦截。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory dest = event.getDestination();

        if ((source.getHolder() instanceof BakaMenuHolder) ||
                (dest.getHolder() instanceof BakaMenuHolder)) {
            event.setCancelled(true);
            return;
        }

        // 额外 PDC 检查（兜底保障：若 Holder 因某种原因丢失）
        if (hasGuiPdcTag(source) || hasGuiPdcTag(dest)) {
            event.setCancelled(true);
        }
    }

    /**
     * 菜单关闭时清理状态。
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BakaMenuHolder)) return;

        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 不主动在此清理 openMenuPath——仅在玩家主动关闭根菜单时清理
        // 保留状态以允许 [previous] 命令回退
        // 但取消刷新任务
        if (refreshScheduler != null) {
            refreshScheduler.cancel(player);
        }
    }

    // ── 私有：Action Bar 点击处理 ────────────────────────

    private void handleActionBarClick(InventoryClickEvent event, Player player,
                                       BakaMenuHolder holder, int slot) {
        boolean acted = false;
        switch (slot) {
            case SLOT_PREV -> {
                if (hasParent(player)) {
                    handleBack(player);
                    acted = true;
                }
            }
            case SLOT_PAGE_PREV -> {
                int currentPage = openMenuPage.getOrDefault(player.getUniqueId(), 0);
                if (currentPage > 0) {
                    navigateToPage(player, holder, currentPage - 1);
                    acted = true;
                }
            }
            case SLOT_NEXT -> {
                int currentPage = openMenuPage.getOrDefault(player.getUniqueId(), 0);
                List<AchievementNode> items = getCurrentItems(player, holder);
                int totalPages = items.isEmpty() ? 1 : (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
                if (currentPage + 1 < totalPages) {
                    navigateToPage(player, holder, currentPage + 1);
                    acted = true;
                }
            }
            case SLOT_STATS -> {
                if ("check".equals(holder.getMenuType()) && event.isLeftClick()) {
                    renderCheckRoot(player);
                    acted = true;
                } else if (event.isShiftClick() && event.isLeftClick()) {
                    shareOwnProgress(player);
                    acted = true;
                } else if (event.isLeftClick() && !event.isShiftClick()) {
                    openRoot(player);
                    acted = true;
                }
            }
            // 46-48, 50, 51: 无操作
            default -> { /* 忽略 */ }
        }
        if (acted) playClickSound(player);
    }

    /** 翻页 */
    private void navigateToPage(Player player, BakaMenuHolder holder, int page) {
        switch (holder.getMenuType()) {
            case "search" -> renderSearch(player, holder.getMenuPath(), page);
            case "check" -> renderCheckPage(player, page);
            default -> openMenuForPath(player, holder.getMenuPath(), page);
        }
    }

    /** 处理"返回上级" */
    private void handleBack(Player player) {
        var menuStack = plugin.getMenuStack();
        var pathOpt = menuStack.pop(player);
        if (pathOpt.isEmpty()) {
            player.closeInventory();
            return;
        }
        String path = pathOpt.get();
        if (path.startsWith(SEARCH_STACK_PREFIX)) {
            renderSearch(player, path.substring(SEARCH_STACK_PREFIX.length()), 0, false);
        } else if ("check".equals(openMenuType.get(player.getUniqueId()))) {
            if (MenuStack.ROOT_PATH.equals(path) || PathUtil.ROOT.equals(path)) {
                renderCheckRoot(player);
            } else {
                openCheckCategory(player, path, 0);
            }
        } else if (MenuStack.ROOT_PATH.equals(path)) {
            openRoot(player);
        } else {
            openMenuForPath(player, path, 0);
        }
    }

    private void renderCheckRoot(Player viewer) {
        UUID viewerUUID = viewer.getUniqueId();
        String targetName = openMenuCheckTargetName.get(viewerUUID);
        UUID targetUUID = openMenuCheckTarget.get(viewerUUID);
        PlayerAchievementData data = openMenuCheckData.get(viewerUUID);
        if (targetName == null || targetUUID == null || data == null) {
            viewer.closeInventory();
            return;
        }
        renderCheckMenu(viewer, targetName, targetUUID, data, PathUtil.ROOT, 0);
    }

    /**
     * 分享自己的成就总进度（Shift+左键 Stats 按钮触发）。
     * <p>
     * 向所有在线玩家广播进度消息，格式如：
     * {@code oniac 已完成 12/45 项成就 (26.67%)}
     * 尊重接收者的 {@code tipsOthers} 偏好。
     * </p>
     */
    private void shareOwnProgress(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerAchievementData data = storage.getCached(uuid);
        if (data == null) return;

        int total = registry.getAchievementCount();
        int done = data.countUnlocked();
        double pct = total > 0 ? 100.0 * done / total : 0.0;

        String template = messages.getMessage("SHARE_PROGRESS",
                "<gradient:#6cd3ff:#dc67ff>{player}</gradient> <gray>已完成</gray> <green>{done}</green><gray>/</gray><white>{total}</white> <gray>项成就</gray> (<yellow>{percent}%</yellow>)");
        String msg = template
                .replace("{prefix}", messages.getMessage("PREFIX", ""))
                .replace("{player_displayname}", PlainTextComponentSerializer.plainText()
                        .serialize(player.displayName()))
                .replace("{player}", player.getName())
                .replace("{done}", String.valueOf(done))
                .replace("{total}", String.valueOf(total))
                .replace("{percent}", String.format("%.2f", pct));

        Component component;
        try {
            component = MiniMessageUtil.parse(msg, miniMessage);
        } catch (Exception e) {
            component = Component.text(player.getName() + " 已完成 " + done + "/" + total
                    + " 项成就 (" + String.format("%.2f", pct) + "%)");
        }

        for (Player receiver : Bukkit.getOnlinePlayers()) {
            boolean isSelf = receiver.getUniqueId().equals(uuid);
            if (isSelf) {
                receiver.sendMessage(component);
                continue;
            }
            PlayerAchievementData recvData = storage.getCached(receiver.getUniqueId());
            if (recvData == null || recvData.isTipsOthers()) {
                receiver.sendMessage(component);
            }
        }
    }

    // ── 私有：内容区点击处理 ──────────────────────────────

    private boolean handleContentClick(InventoryClickEvent event, Player player,
                                     BakaMenuHolder holder, int slot) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return false;

        // 尝试通过槽位反查节点
        int contentIndex = slotToContentIndex(slot);
        if (contentIndex < 0) return false;

        int page = openMenuPage.getOrDefault(player.getUniqueId(), 0);
        List<AchievementNode> items = getCurrentItems(player, holder);

        int itemIndex = page * ITEMS_PER_PAGE + contentIndex;
        if (itemIndex >= items.size()) return false;

        AchievementNode node = items.get(itemIndex);
        if (node == null) return false;

        if ("check".equals(holder.getMenuType())) {
            if (event.isRightClick() && hasChildren(node)) {
                plugin.getMenuStack().push(player, stackEntryForHolder(holder));
                openCheckCategory(player, node.nodePath(), 0);
                return true;
            }
            return false;
        }

        // Shift+左键 → 分享（仅成就和 MIXED 节点）
        if (event.isShiftClick() && event.isLeftClick() &&
                (node.nodeType() == AchievementNode.NodeType.ACHIEVEMENT
                 || node.nodeType() == AchievementNode.NodeType.MIXED) &&
                shareService != null) {
            try {
                shareService.tryShare(player, node);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "分享成就失败", e);
            }
            return true;
        }

        // 右键 → 打开分支。优先处理右键, 避免与左键完成逻辑互相干扰。
        if (event.isRightClick() && hasChildren(node)) {
            plugin.getMenuStack().push(player, stackEntryForHolder(holder));
            if ("check".equals(holder.getMenuType())) {
                openCheckCategory(player, node.nodePath(), 0);
            } else {
                openMenuForPath(player, node.nodePath(), 0);
            }
            return true;
        }

        // 左键 → 分类导航 / 成就尝试完成；MIXED 始终只尝试完成自身成就。
        if (event.isLeftClick() && !event.isShiftClick()) {
            switch (node.nodeType()) {
                case CATEGORY -> {
                    Category cat = (Category) node;
                    // 压栈当前路径
                    plugin.getMenuStack().push(player, stackEntryForHolder(holder));
                    openMenuForPath(player, cat.nodePath(), 0);
                    return true;
                }
                case ACHIEVEMENT -> {
                    Achievement ach = (Achievement) node;
                    // 仅非自动成就且可访问时尝试完成
                    if (!ach.auto() && isNodeAccessible(player, ach)) {
                        try {
                            progressService.tryComplete(player, ach);
                            // 完成尝试后刷新当前菜单
                            refreshCurrentMenu(player);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING,
                                    "尝试完成成就失败: " + ach.nodePath(), e);
                        }
                        return true;
                    }
                }
                case MIXED -> {
                    MixedNode mixed = (MixedNode) node;
                    if (!mixed.auto() && isNodeAccessible(player, mixed)) {
                        try {
                            progressService.tryComplete(player, mixed);
                            refreshCurrentMenu(player);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING,
                                    "尝试完成混合成就失败: " + mixed.nodePath(), e);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasChildren(AchievementNode node) {
        return node.children() != null && !node.children().isEmpty();
    }

    // ── 私有：菜单渲染 ────────────────────────────────────

    /**
     * 为指定分类路径渲染菜单。
     *
     * @param player   玩家
     * @param menuPath 菜单路径
     * @param page     页码（从0开始）
     */
    private void openMenuForPath(Player player, String menuPath, int page) {
        UUID uuid = player.getUniqueId();

        // 获取节点（Category 或 MixedNode 均可）
        AchievementNode node = registry.getNode(menuPath).orElse(null);
        if (node == null) {
            openRoot(player);
            return;
        }

        List<AchievementNode> children;
        String displayTitle;
        boolean needsPermission;

        if (node instanceof Category cat) {
            children = cat.children();
            displayTitle = cat.display();
            needsPermission = cat.permission();
        } else if (node instanceof MixedNode mixed) {
            children = mixed.children();
            displayTitle = mixed.display();
            needsPermission = mixed.permission();
        } else {
            // 纯 Achievement 节点：找到父分类并打开
            String parent = findCategoryParent(menuPath);
            if (parent != null) {
                openMenuForPath(player, parent, page);
            } else {
                openRoot(player);
            }
            return;
        }

        // 保存状态
        openMenuPath.put(uuid, menuPath);
        openMenuType.put(uuid, "category");
        openMenuPage.put(uuid, page);
        cleanupCheckContext(uuid);
        saveSession(uuid, new MenuSession(MenuSession.MenuType.CATEGORY,
                menuPath, page, null, null, null, null));

        // 加载玩家数据
        PlayerAchievementData data = storage.getCached(uuid);
        if (data == null) {
            PlayerAchievementData finalData = data;
            String finalMenuPath = menuPath;
            storage.load(uuid).thenAccept(d ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openMenuPath.put(uuid, finalMenuPath);
                        openMenuType.put(uuid, "category");
                        openMenuPage.put(uuid, page);
                        saveSession(uuid, new MenuSession(MenuSession.MenuType.CATEGORY,
                                finalMenuPath, page, null, null, null, null));
                        renderAsMenu(player, finalMenuPath, displayTitle, needsPermission,
                                children, page, d);
                    })
            );
            return;
        }

        renderAsMenu(player, menuPath, displayTitle, needsPermission, children, page, data);
    }

    /** 渲染通用的子节点列表菜单（Category 和 MixedNode 共用） */
    private void renderAsMenu(Player player, String nodePath, String displayTitle,
                               boolean needsPermission, List<AchievementNode> children,
                               int page, PlayerAchievementData data) {
        String title = nodePath.equals(PathUtil.ROOT)
                ? messages.guiTitleAchievements()
                : toPlainText(displayTitle);
        Inventory inv = createInventory(nodePath, "category", title);

        List<AchievementNode> items = filterAccessibleNodes(children, player, data, true);

        // 检查玩家是否有权限访问此节点
        if (!nodePath.equals(PathUtil.ROOT) && needsPermission) {
            String permNode = "bakaachievements.category." + nodePath;
            if (!PermissionResolver.has(player, permNode)) {
                // 无权限——菜单已打开，通过 filterAccessibleNodes 过滤
            }
        }

        fillMenu(inv, player, items, page, nodePath, data, null);
        player.openInventory(inv);

        scheduleRefresh(player, () -> openMenuForPath(player, nodePath,
                openMenuPage.getOrDefault(player.getUniqueId(), 0)));
    }

    /**
     * 树遍历查找成就节点的实际父分类路径。
     * @return 父分类路径，未找到返回 null
     */
    @org.jetbrains.annotations.Nullable
    private String findCategoryParent(String achievementPath) {
        return treeService.parentOf(achievementPath);
    }

    @org.jetbrains.annotations.Nullable
    private String findInCategory(Category cat, String target) {
        for (var child : cat.children()) {
            if (child.nodePath().equals(target) &&
                    (child.nodeType() == AchievementNode.NodeType.ACHIEVEMENT
                     || child.nodeType() == AchievementNode.NodeType.MIXED)) {
                return cat.nodePath();
            }
            if (child instanceof Category subCat) {
                String found = findInCategory(subCat, target);
                if (found != null) return found;
            }
            if (child instanceof MixedNode mixed) {
                String found = findInMixed(mixed, target);
                if (found != null) return found;
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private String findInMixed(MixedNode mixed, String target) {
        if (mixed.nodePath().equals(target)) return mixed.nodePath();
        for (var child : mixed.children()) {
            if (child.nodePath().equals(target)) {
                return mixed.nodePath();
            }
            if (child instanceof Category subCat) {
                String found = findInCategory(subCat, target);
                if (found != null) return found;
            }
            if (child instanceof MixedNode subMixed) {
                String found = findInMixed(subMixed, target);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 渲染搜索结果菜单 */
    private void renderSearch(Player player, String keyword, int page) {
        renderSearch(player, keyword, page, true);
    }

    /** 渲染搜索结果菜单 */
    private void renderSearch(Player player, String keyword, int page, boolean pushSource) {
        UUID uuid = player.getUniqueId();
        String sourcePath = openMenuPath.getOrDefault(uuid, PathUtil.ROOT);

        // 压栈：进入搜索前保存根路径，以便返回
        if (!pushSource || openMenuType.getOrDefault(uuid, "").equals("search")) {
            // 重复搜索，不重复压栈
        } else {
            plugin.getMenuStack().push(player, sourcePath);
        }

        openMenuPath.put(uuid, keyword);
        openMenuType.put(uuid, "search");
        openMenuPage.put(uuid, page);
        cleanupCheckContext(uuid);
        saveSession(uuid, new MenuSession(MenuSession.MenuType.SEARCH,
                keyword, page, sourcePath, null, null, null));

        PlayerAchievementData data = storage.getCached(uuid);
        if (data == null) {
            storage.load(uuid).thenAccept(d ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openMenuPath.put(uuid, keyword);
                        openMenuType.put(uuid, "search");
                        openMenuPage.put(uuid, page);
                        saveSession(uuid, new MenuSession(MenuSession.MenuType.SEARCH,
                                keyword, page, sourcePath, null, null, null));
                        renderSearchInternal(player, keyword, page, d);
                    })
            );
            return;
        }

        renderSearchInternal(player, keyword, page, data);
    }

    private void renderSearchInternal(Player player, String keyword, int page,
                                       PlayerAchievementData data) {
        String title = messages.guiTitleSearch().replace("{keyword}",
                keyword.length() > 20 ? keyword.substring(0, 20) + "..." : keyword);
        Inventory inv = createInventory(keyword, "search", title);

        List<AchievementNode> items = searchNodes(keyword, player, data);

        fillMenu(inv, player, items, page, keyword, data, keyword);
        player.openInventory(inv);

        scheduleRefresh(player, () -> renderSearch(player, keyword,
                openMenuPage.getOrDefault(player.getUniqueId(), 0)));
    }

    // ── 私有：填充菜单 ────────────────────────────────────

    /**
     * 填充菜单内容区、周圈、Action Bar。
     *
     * @param inv         目标容器
     * @param player      玩家
     * @param items       当前分类/搜索结果的节点列表（已过滤、有序）
     * @param page        页码（0-indexed）
     * @param currentPath 当前菜单路径
     * @param data        玩家数据
     */
    private void fillMenu(Inventory inv, Player player, List<AchievementNode> items,
                           int page, String currentPath, PlayerAchievementData data,
                           @Nullable String emptySearchKeyword) {
        // ── 周圈玻璃板 ──
        Material borderMat = readBorderMaterial();
        ItemStack borderPane = itemFactory.borderPane(borderMat);
        for (int borderSlot : BORDER_SLOTS) {
            inv.setItem(borderSlot, borderPane);
        }

        // ── 内容区 ──
        // 计算该分类下已达成数量
        int[] doneTotal = computeProgress(items, data);

        if (items.isEmpty()) {
            for (int slot : CONTENT_SLOTS) {
                inv.setItem(slot, null);
            }
            inv.setItem(22, buildEmptyStateItem(emptySearchKeyword));
        } else {
            int startIdx = page * ITEMS_PER_PAGE;
            for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                int slot = CONTENT_SLOTS[i];
                int itemIdx = startIdx + i;

                if (itemIdx >= items.size()) {
                    // 超出范围：留空（空气）
                    inv.setItem(slot, null);
                } else {
                    AchievementNode node = items.get(itemIdx);
                    boolean accessible = isNodeAccessible(player, node);
                    ItemStack item = buildNodeItem(node, data, accessible, player, isReadOnlyMenu(inv));
                    inv.setItem(slot, item);
                }
            }
        }

        // ── Action Bar ──
        int totalPages = items.isEmpty() ? 1 : (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
        fillActionBar(inv, player, currentPath, page, totalPages, doneTotal[0], doneTotal[1]);
    }

    /**
     * 计算节点列表中的已完成和总数。
     *
     * @return int[2] = [done, total]
     */
    private int[] computeProgress(List<AchievementNode> items, PlayerAchievementData data) {
        return treeService.progressOf(items, data);
    }

    /** 递归统计子节点中的成就总数 */
    private int countAchievementsInChildren(List<AchievementNode> children) {
        int count = 0;
        for (AchievementNode child : children) {
            if (child instanceof Achievement) {
                count++;
            } else if (child instanceof MixedNode mixed) {
                count += mixed.countAchievements();
            } else if (child instanceof Category cat) {
                count += cat.countAchievements();
            }
        }
        return count;
    }

    /** 递归统计分类下已解锁的成就数 */
    private int countUnlockedInCategory(Category cat, PlayerAchievementData data) {
        if (data == null) return 0;
        return treeService.countUnlocked(cat.children(), data);
    }

    /** 递归统计子节点中已解锁的成就数 */
    private int countUnlockedInChildren(List<AchievementNode> children, PlayerAchievementData data) {
        return treeService.countUnlocked(children, data);
    }

    /** 填充 Action Bar */
    private void fillActionBar(Inventory inv, Player player, String currentPath,
                                int page, int totalPages, int done, int total) {
        actionBarRenderer.render(inv, player, currentPath, page, totalPages, done, total, SEARCH_STACK_PREFIX);
    }

    /**
     * 判断当前菜单是否有可返回的上级。
     * <p>
     * 以 MenuStack 的实际推栈记录为准（而非 PathUtil.parent 字符串计算），
     * 确保原版扁平路径（如 {@code vanilla.minecraft.story_root}）也能正确显示返回按钮。
     * </p>
     */
    private boolean hasParent(Player player) {
        return plugin.getMenuStack().peek(player).isPresent();
    }

    private String stackEntryForHolder(BakaMenuHolder holder) {
        if ("search".equals(holder.getMenuType())) {
            return SEARCH_STACK_PREFIX + holder.getMenuPath();
        }
        return holder.getMenuPath();
    }

    // ── 私有：物品构建 ────────────────────────────────────

    /**
     * 根据节点类型构建对应的展示物品。
     */
    private ItemStack buildNodeItem(AchievementNode node, PlayerAchievementData data,
                                     boolean accessible, Player viewer, boolean readOnly) {
        switch (node.nodeType()) {
            case CATEGORY -> {
                Category cat = (Category) node;
                int total = cat.countAchievements();
                int done = data != null ? countUnlockedInCategory(cat, data) : 0;
                return itemFactory.forCategory(cat, done, total, accessible, viewer);
            }
            case ACHIEVEMENT -> {
                Achievement ach = (Achievement) node;
                List<ItemFactory.ResolvedCondition> resolved = resolveConditions(viewer, ach);
                return itemFactory.forAchievement(ach, data, resolved, accessible, viewer, readOnly);
            }
            case MIXED -> {
                MixedNode mixed = (MixedNode) node;
                int total = mixed.countAchievements();
                int done = data != null
                        ? countUnlockedInChildren(mixed.children(), data)
                          + (data.isUnlocked(mixed.nodePath()) ? 1 : 0)
                        : 0;
                List<ItemFactory.ResolvedCondition> resolved = resolveConditions(viewer, mixed);
                return itemFactory.forMixedNode(mixed, data, resolved, accessible, viewer, done, total, readOnly);
            }
            default -> {
                return new ItemStack(Material.BARRIER);
            }
        }
    }

    private boolean isReadOnlyMenu(Inventory inv) {
        return inv.getHolder() instanceof BakaMenuHolder holder
                && "check".equals(holder.getMenuType());
    }

    private ItemStack buildEmptyStateItem(@Nullable String keyword) {
        if (keyword != null) {
            Material mat = readMaterial("gui.empty-search.material", Material.PAPER);
            String lore = messages.guiSearchEmptyLore().replace("{keyword}", keyword);
            return itemFactory.emptyStateItem(mat, messages.guiSearchEmptyName(), lore);
        }
        Material mat = readMaterial("gui.empty-category.material", Material.BARRIER);
        return itemFactory.emptyStateItem(mat, messages.guiEmptyCategoryName(), messages.guiEmptyCategoryLore());
    }

    /**
     * 解析成就的条件——在主线程解析 PAPI 并评估。
     *
     * @param player 玩家
     * @param node   成就/混合节点
     * @return 已解析的条件列表；无条件时返回空列表
     */
    private List<ItemFactory.ResolvedCondition> resolveConditions(Player player, AchievementNode node) {
        if (node.conditionGroup().isEmpty()) {
            return List.of();
        }

        List<ItemFactory.ResolvedCondition> result = new ArrayList<>();
        for (Condition cond : node.conditionGroup().conditions()) {
            try {
                // 在主线程解析 PAPI（调用方保证在主线程）
                String targetResolved = resolvePapi(player, cond.target());
                String currentResolved = resolvePapi(player, cond.current());

                // 评估
                boolean passed = ConditionEvaluator.evaluateResolved(
                        player, cond, targetResolved, currentResolved);

                // 计算进度
                double progress = ConditionEvaluator.progress(
                        cond, targetResolved, currentResolved, passed);

                result.add(new ItemFactory.ResolvedCondition(
                        cond, targetResolved, currentResolved, passed, progress));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "解析条件失败: node=" + node.nodePath()
                                + " cond=" + cond.display(), e);
                // 继续处理其余条件，当前条件标记为未通过
                result.add(new ItemFactory.ResolvedCondition(
                        cond, cond.target(), cond.current(), false, 0.0));
            }
        }
        return result;
    }

    // ── 私有：节点过滤 ────────────────────────────────────

    /**
     * 过滤可访问的节点列表——排除被禁用的节点和祖先被禁用的节点。
     *
     * @param children   子节点列表
     * @param player     玩家
     * @param data       玩家数据
     * @param checkPerm  是否检查权限
     * @return 过滤后的节点列表
     */
    private List<AchievementNode> filterAccessibleNodes(List<AchievementNode> children,
                                                         Player player,
                                                         PlayerAchievementData data,
                                                         boolean checkPerm) {
        return treeService.visibleChildren(children, player, checkPerm);
    }

    /** 检查玩家是否有权限访问指定节点 */
    private boolean isNodeAccessible(Player player, AchievementNode node) {
        return treeService.canAccess(player, node);
    }

    // ── 私有：搜索 ────────────────────────────────────────

    /**
     * 在所有成就节点中搜索匹配关键词的节点（不区分大小写）。
     * <p>
     * 匹配范围：
     * </p>
     * <ul>
     *   <li>{@code name()} 包含关键词</li>
     *   <li>MiniMessage 解析后的 {@code display()} 纯文本包含关键词</li>
     *   <li>{@code descriptions()} 任意行包含关键词</li>
     *   <li>{@code Condition#display()} 包含关键词（仅 Achievement 节点）</li>
     * </ul>
     *
     * @param keyword 搜索关键词
     * @param player  玩家（用于权限过滤）
     * @param data    玩家数据
     * @return 匹配的节点列表
     */
    private List<AchievementNode> searchNodes(String keyword, Player player,
                                               PlayerAchievementData data) {
        if (keyword == null || keyword.isBlank()) return List.of();

        String lowerKeyword = keyword.toLowerCase();
        List<AchievementNode> results = new ArrayList<>();
        PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

        for (Map.Entry<String, AchievementNode> entry : registry.getAllNodes().entrySet()) {
            AchievementNode node = entry.getValue();

            // 跳过禁用节点
            if (disabledRegistry.isDisabledOrAncestor(node.nodePath())) continue;

            // 跳过根节点
            if (node.nodePath().equals(PathUtil.ROOT)) continue;

            // 权限过滤
            if (node.permission()) {
                String permNode = "bakaachievements.category." + node.nodePath();
                if (!PermissionResolver.has(player, permNode)) continue;
            }

            // 匹配 name
            if (node.name() != null && node.name().toLowerCase().contains(lowerKeyword)) {
                results.add(node);
                continue;
            }

            // 匹配 display（MiniMessage → 纯文本）
            if (node.display() != null && !node.display().isBlank()) {
                try {
                    String plainDisplay = plainText.serialize(
                            MiniMessageUtil.parse(node.display(), miniMessage));
                    if (plainDisplay.toLowerCase().contains(lowerKeyword)) {
                        results.add(node);
                        continue;
                    }
                } catch (Exception ignored) {
                    // MiniMessage 解析失败，跳过 display 匹配
                }
            }

            // 匹配 descriptions
            boolean descMatch = false;
            if (node instanceof Achievement ach && ach.descriptions() != null) {
                for (String desc : ach.descriptions()) {
                    if (desc != null && !desc.isBlank()) {
                        try {
                            String plainDesc = plainText.serialize(
                                    MiniMessageUtil.parse(desc, miniMessage));
                            if (plainDesc.toLowerCase().contains(lowerKeyword)) {
                                descMatch = true;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } else if (node instanceof Category cat && cat.descriptions() != null) {
                for (String desc : cat.descriptions()) {
                    if (desc != null && desc.toLowerCase().contains(lowerKeyword)) {
                        descMatch = true;
                        break;
                    }
                }
            }
            if (descMatch) {
                results.add(node);
                continue;
            }

            // 匹配条件 display（仅 Achievement）
            if (node instanceof Achievement ach && !ach.conditionGroup().isEmpty()) {
                for (Condition cond : ach.conditionGroup().conditions()) {
                    if (cond.display() != null &&
                            cond.display().toLowerCase().contains(lowerKeyword)) {
                        results.add(node);
                        break;
                    }
                }
            }
        }

        return results;
    }

    // ── 私有：辅助方法 ────────────────────────────────────

    /** 获取当前菜单对应的节点列表 */
    private List<AchievementNode> getCurrentItems(Player player, BakaMenuHolder holder) {
        UUID uuid = player.getUniqueId();
        PlayerAchievementData data = storage.getCached(uuid);

        switch (holder.getMenuType()) {
            case "search" -> {
                if (data == null) data = getCheckData(uuid);
                return searchNodes(holder.getMenuPath(), player,
                        data != null ? data : new PlayerAchievementData());
            }
            case "check" -> {
                PlayerAchievementData checkData = openMenuCheckData.get(uuid);
                if (checkData == null) return List.of();
                AchievementNode node = registry.getNode(holder.getMenuPath()).orElse(null);
                if (node instanceof Category cat) {
                    return filterAccessibleNodes(cat.children(), player, checkData, false);
                }
                if (node instanceof MixedNode mixed) {
                    return filterAccessibleNodes(mixed.children(), player, checkData, false);
                }
                Category rootCat = registry.getRoot();
                return rootCat == null
                        ? List.of()
                        : filterAccessibleNodes(rootCat.children(), player, checkData, false);
            }
            default -> {
                AchievementNode node = registry.getNode(holder.getMenuPath()).orElse(null);
                if (data == null) data = getCheckData(uuid);
                PlayerAchievementData currentData = data != null ? data : new PlayerAchievementData();
                if (node instanceof Category cat) {
                    return filterAccessibleNodes(cat.children(), player, currentData, true);
                }
                if (node instanceof MixedNode mixed) {
                    return filterAccessibleNodes(mixed.children(), player, currentData, true);
                }
                return List.of();
            }
        }
    }

    /** 创建带 BakaMenuHolder 的 Inventory */
    private Inventory createInventory(String path, String type, String title) {
        Component titleComponent = null;
        try {
            titleComponent = MiniMessageUtil.parse(title, miniMessage);
        } catch (Exception e) {
            titleComponent = Component.text(title);
        }
        return Bukkit.createInventory(
                new BakaMenuHolder(path, type),
                INVENTORY_SIZE,
                titleComponent
        );
    }

    /** 安排刷新任务 */
    private void scheduleRefresh(Player player, Runnable runnable) {
        if (refreshScheduler != null) {
            refreshScheduler.schedule(player, runnable);
        }
    }

    /** 解析 PAPI 占位符（必须在主线程调用） */
    private String resolvePapi(Player player, String text) {
        if (text == null || text.isEmpty() || !text.contains("%")) {
            return text != null ? text : "";
        }
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            }
        } catch (Exception ignored) {}
        return text;
    }

    /** MiniMessage → 纯文本 */
    private String toPlainText(String miniMessageStr) {
        try {
            return PlainTextComponentSerializer.plainText()
                    .serialize(MiniMessageUtil.parse(miniMessageStr, miniMessage));
        } catch (Exception e) {
            return miniMessageStr;
        }
    }

    /** 清理检查模式上下文 */
    private void cleanupCheckContext(UUID uuid) {
        openMenuCheckTarget.remove(uuid);
        openMenuCheckTargetName.remove(uuid);
        openMenuCheckData.remove(uuid);
    }

    private void saveSession(UUID viewer, MenuSession session) {
        sessionService.put(viewer, session);
    }

    /** 获取检查模式下的数据 */
    @Nullable
    private PlayerAchievementData getCheckData(UUID uuid) {
        return openMenuCheckData.get(uuid);
    }

    /** 判断是否为周圈玻璃板槽位 */
    private boolean isBorderSlot(int slot) {
        for (int bs : BORDER_SLOTS) {
            if (bs == slot) return true;
        }
        return false;
    }

    /** 判断是否为内容槽位 */
    private boolean isContentSlot(int slot) {
        for (int cs : CONTENT_SLOTS) {
            if (cs == slot) return true;
        }
        return false;
    }

    /** 内容槽位 → 内容索引 */
    private int slotToContentIndex(int slot) {
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    /** 从 config.yml 读取周圈玻璃板材质 */
    private Material readBorderMaterial() {
        try {
            String matStr = plugin.getConfigManager().getConfig()
                    .getString("gui.border-material", "GRAY_STAINED_GLASS_PANE");
            Material mat = Material.matchMaterial(matStr);
            if (mat != null && mat.isItem()) return mat;
        } catch (Exception ignored) {}
        return Material.GRAY_STAINED_GLASS_PANE;
    }

    private Material readMaterial(String path, Material defaultMat) {
        try {
            String matStr = plugin.getConfigManager().getConfig().getString(path);
            if (matStr != null && !matStr.isBlank()) {
                Material mat = Material.matchMaterial(matStr);
                if (mat != null && mat.isItem()) return mat;
            }
        } catch (Exception ignored) {}
        return defaultMat;
    }

    /**
     * 检查 Inventory 中是否有本插件的 PDC 标记物品（兜底保障）。
     */
    private boolean hasGuiPdcTag(Inventory inv) {
        if (inv == null) return false;
        if (pdcKey == null) {
            pdcKey = new NamespacedKey(plugin, PDC_KEY);
        }
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer()
                        .has(pdcKey, PersistentDataType.STRING)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── 内部类：BakaMenuHolder ───────────────────────────

    /**
     * 向玩家播放 GUI 点击音效。
     * <p>
     * 音效由 config.yml 的 {@code gui.click-sound} 控制，
     * 设为 {@code NONE} 时不播放。
     * </p>
     */
    private void playClickSound(Player player) {
        org.bukkit.Sound sound = plugin.getConfigManager().getClickSound();
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    /**
     * 本插件 GUI 菜单的 {@link InventoryHolder} 实现。
     * <p>
     * 用于在事件处理中识别本插件创建的容器界面，
     * 避免误操作其他插件或原版的容器。
     * </p>
     */
    private static class BakaMenuHolder implements InventoryHolder {

        private final String menuPath;
        private final String menuType; // "category" / "search" / "check"

        BakaMenuHolder(String menuPath, String menuType) {
            this.menuPath = menuPath;
            this.menuType = menuType;
        }

        String getMenuPath() { return menuPath; }
        String getMenuType() { return menuType; }

        @Override
        public @NotNull Inventory getInventory() {
            // 此方法在此上下文中无实际意义，返回 null
            return null;
        }
    }
}
