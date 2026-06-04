package cc.oniacute.plugin.bakaachievements.gui.view;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.*;
import cc.oniacute.plugin.bakaachievements.gui.BakaMenuHolder;
import cc.oniacute.plugin.bakaachievements.gui.ItemFactory;
import cc.oniacute.plugin.bakaachievements.gui.MenuController;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搜索结果菜单——复用分类菜单结构，显示搜索结果。
 */
public final class SearchResultMenu implements BakaMenuHolder {

    private static final int SLOTS_PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;

    private final Inventory inventory;
    private final BakaAchievements plugin;
    private final String keyword;
    private final Player viewer;
    private final PlayerAchievementData playerData;
    private final MenuController controller;
    private final AchievementRegistry registry;
    private final DisabledRegistry disabledRegistry;
    private final ItemFactory itemFactory;
    private final PlayerDataStorage storage;

    private final int currentPage;
    private final int totalPages;
    private final List<AchievementNode> results;

    public SearchResultMenu(BakaAchievements plugin, String keyword, Player viewer,
                            PlayerAchievementData playerData, MenuController controller,
                            AchievementRegistry registry, DisabledRegistry disabledRegistry,
                            ItemFactory itemFactory, PlayerDataStorage storage) {
        this(plugin, keyword, viewer, playerData, controller, registry,
                disabledRegistry, itemFactory, storage, 0);
    }

    private SearchResultMenu(BakaAchievements plugin, String keyword, Player viewer,
                             PlayerAchievementData playerData, MenuController controller,
                             AchievementRegistry registry, DisabledRegistry disabledRegistry,
                             ItemFactory itemFactory, PlayerDataStorage storage, int page) {
        this.plugin = plugin;
        this.keyword = keyword;
        this.viewer = viewer;
        this.playerData = playerData;
        this.controller = controller;
        this.registry = registry;
        this.disabledRegistry = disabledRegistry;
        this.itemFactory = itemFactory;
        this.storage = storage;
        this.currentPage = page;

        // 搜索
        this.results = search(keyword.toLowerCase());

        this.totalPages = Math.max(1, (results.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);

        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("搜索: " + keyword));
        render();
    }

    private List<AchievementNode> search(String kw) {
        List<AchievementNode> matched = new ArrayList<>();
        for (Map.Entry<String, AchievementNode> entry : registry.getAllNodes().entrySet()) {
            AchievementNode node = entry.getValue();

            // 跳过 disable 的
            if (disabledRegistry.isDisabledOrAncestor(node.nodePath())) continue;

            // 模糊匹配：nodePath, display, name
            String nodePathLower = node.nodePath().toLowerCase();
            String displayLower = PlainTextComponentSerializer.plainText()
                    .serialize(Component.text(node.display())).toLowerCase();
            String nameLower = node.name().toLowerCase();

            if (nodePathLower.contains(kw) || displayLower.contains(kw) || nameLower.contains(kw)) {
                matched.add(node);
            }
        }
        return matched;
    }

    private void render() {
        inventory.clear();

        // 搜索头
        inventory.setItem(4, itemFactory.searchHeader(keyword));

        // 分页物品
        int start = currentPage * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, results.size());
        for (int i = start; i < end; i++) {
            AchievementNode node = results.get(i);
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
        return MenuType.SEARCH;
    }

    @Override
    public void handleClick(int slot, Player player, boolean isShiftClick) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREV && currentPage > 0) {
            SearchResultMenu prev = new SearchResultMenu(plugin, keyword, viewer, playerData,
                    controller, registry, disabledRegistry, itemFactory, storage, currentPage - 1);
            player.openInventory(prev.getInventory());
            return;
        }
        if (slot == SLOT_NEXT && currentPage < totalPages - 1) {
            SearchResultMenu next = new SearchResultMenu(plugin, keyword, viewer, playerData,
                    controller, registry, disabledRegistry, itemFactory, storage, currentPage + 1);
            player.openInventory(next.getInventory());
            return;
        }

        int itemIndex = currentPage * SLOTS_PER_PAGE + slot;
        if (itemIndex < 0 || itemIndex >= results.size()) return;

        AchievementNode node = results.get(itemIndex);
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
