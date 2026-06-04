package cc.oniacute.plugin.bakaachievements.gui.view;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.*;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.gui.BakaMenuHolder;
import cc.oniacute.plugin.bakaachievements.gui.ItemFactory;
import cc.oniacute.plugin.bakaachievements.gui.MenuController;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类菜单——显示某个分类下的子节点列表（分页 + 面包屑）。
 * <p>
 * 布局（6 行 × 9 列）：
 * <pre>
 *  0-44: 45 个物品槽位（分页）
 *  45: 上一页
 *  46-51: 面包屑路径（可点击跳转）
 *  49: 关闭按钮
 *  53: 下一页
 * </pre>
 */
public final class CategoryMenu implements BakaMenuHolder {

    private static final int SLOTS_PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;
    private static final int BREADCRUMB_START = 46;

    private final Inventory inventory;
    private final BakaAchievements plugin;
    private final Category category;
    private final Player viewer;
    private final PlayerAchievementData playerData;
    private final MenuController controller;
    private final AchievementRegistry registry;
    private final DisabledRegistry disabledRegistry;
    private final ItemFactory itemFactory;
    private final PlayerDataStorage storage;
    private final Messages messages;

    private final int currentPage;
    private final int totalPages;
    private final List<AchievementNode> visibleChildren;

    public CategoryMenu(BakaAchievements plugin, Category category, Player viewer,
                        PlayerAchievementData playerData, MenuController controller,
                        AchievementRegistry registry, DisabledRegistry disabledRegistry,
                        ItemFactory itemFactory, PlayerDataStorage storage) {
        this(plugin, category, viewer, playerData, controller, registry,
                disabledRegistry, itemFactory, storage, 0);
    }

    private CategoryMenu(BakaAchievements plugin, Category category, Player viewer,
                         PlayerAchievementData playerData, MenuController controller,
                         AchievementRegistry registry, DisabledRegistry disabledRegistry,
                         ItemFactory itemFactory, PlayerDataStorage storage, int page) {
        this.plugin = plugin;
        this.category = category;
        this.viewer = viewer;
        this.playerData = playerData;
        this.controller = controller;
        this.registry = registry;
        this.disabledRegistry = disabledRegistry;
        this.itemFactory = itemFactory;
        this.storage = storage;
        this.messages = plugin.getMessages();
        this.currentPage = page;

        // 过滤可见子节点（跳过 disable 的）
        this.visibleChildren = new ArrayList<>();
        for (AchievementNode child : category.children()) {
            if (!disabledRegistry.isDisabledOrAncestor(child.nodePath())) {
                visibleChildren.add(child);
            }
        }

        this.totalPages = Math.max(1, (visibleChildren.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);

        String title = PlainTextComponentSerializer.plainText().serialize(
                MiniMessage.miniMessage().deserialize(category.display()));
        this.inventory = Bukkit.createInventory(this, 54, Component.text(title));
        render();
    }

    private void render() {
        inventory.clear();

        // 分页物品
        int start = currentPage * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, visibleChildren.size());
        for (int i = start; i < end; i++) {
            AchievementNode node = visibleChildren.get(i);
            ItemStack item = itemFactory.build(node, playerData, viewer);
            inventory.setItem(i - start, item);
        }

        // 上一页
        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV, itemFactory.prevPageButton());
        }

        // 下一页
        if (currentPage < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, itemFactory.nextPageButton());
        }

        // 面包屑
        if (!category.nodePath().equals(PathUtil.ROOT)) {
            String[] crumbs = PathUtil.breadcrumbs(category.nodePath());
            for (int i = 0; i < crumbs.length && (BREADCRUMB_START + i) <= 51; i++) {
                final int slot = BREADCRUMB_START + i;
                final String path = crumbs[i];
                registry.getNode(path).ifPresent(node -> {
                    ItemStack breadItem = itemFactory.breadcrumbItem(path, node.display());
                    inventory.setItem(slot, breadItem);
                });
            }
        }

        // 关闭按钮
        inventory.setItem(SLOT_CLOSE, itemFactory.closeButton());

        // 空白填充
        if (plugin.getConfigManager().isGuiFillEmpty()) {
            ItemStack filler = itemFactory.fillEmpty();
            for (int i = 0; i < 54; i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, filler);
                }
            }
        }
    }

    @Override
    public MenuType getMenuType() {
        return MenuType.CATEGORY;
    }

    @Override
    public void handleClick(int slot, Player player, boolean isShiftClick) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREV && currentPage > 0) {
            CategoryMenu prev = new CategoryMenu(plugin, category, viewer, playerData,
                    controller, registry, disabledRegistry, itemFactory, storage, currentPage - 1);
            player.openInventory(prev.getInventory());
            return;
        }
        if (slot == SLOT_NEXT && currentPage < totalPages - 1) {
            CategoryMenu next = new CategoryMenu(plugin, category, viewer, playerData,
                    controller, registry, disabledRegistry, itemFactory, storage, currentPage + 1);
            player.openInventory(next.getInventory());
            return;
        }

        // 面包屑
        if (slot >= BREADCRUMB_START && slot <= 51) {
            int idx = slot - BREADCRUMB_START;
            String[] crumbs = PathUtil.breadcrumbs(category.nodePath());
            if (idx < crumbs.length) {
                controller.openCategory(player, crumbs[idx]);
                return;
            }
        }

        // 物品点击
        int itemIndex = currentPage * SLOTS_PER_PAGE + slot;
        if (itemIndex < 0 || itemIndex >= visibleChildren.size()) return;

        AchievementNode node = visibleChildren.get(itemIndex);

        // 权限检查
        String permPrefix = (node instanceof Category) ? "bakaachievements.category." : "bakaachievements.achievement.";
        if (node.permission() && !player.hasPermission(permPrefix + node.nodePath())) {
            player.sendMessage(plugin.getMiniMessage().deserialize(messages.guiNoAccess()));
            player.closeInventory();
            return;
        }

        if (node instanceof Category cat) {
            controller.openCategory(player, cat.nodePath());
        } else if (node instanceof Achievement ach) {
            controller.openDetail(player, ach);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
