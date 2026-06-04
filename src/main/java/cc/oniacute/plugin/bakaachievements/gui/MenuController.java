package cc.oniacute.plugin.bakaachievements.gui;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.*;
import cc.oniacute.plugin.bakaachievements.gui.view.CategoryMenu;
import cc.oniacute.plugin.bakaachievements.gui.view.AchievementDetailMenu;
import cc.oniacute.plugin.bakaachievements.gui.view.SearchResultMenu;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * GUI 控制器——监听所有箱子菜单事件并分发到对应 Holder。
 */
public final class MenuController implements Listener {

    private final BakaAchievements plugin;
    private final AchievementRegistry registry;
    private final PlayerDataStorage storage;
    private final DisabledRegistry disabledRegistry;
    private final ItemFactory itemFactory;

    public MenuController(BakaAchievements plugin, AchievementRegistry registry,
                          PlayerDataStorage storage, DisabledRegistry disabledRegistry,
                          ItemFactory itemFactory) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
        this.disabledRegistry = disabledRegistry;
        this.itemFactory = itemFactory;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BakaMenuHolder holder)) return;
        event.setCancelled(true); // 所有 GUI 点击均取消默认行为

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getCurrentItem() == null) return;

        holder.handleClick(event.getSlot(), player, event.isShiftClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BakaMenuHolder) {
            event.setCancelled(true);
        }
    }

    // ── 方便子菜单打开 ────────────────────────────────────

    /** 为玩家打开根分类菜单 */
    public void openRoot(Player player) {
        storage.load(player.getUniqueId()).thenAcceptAsync(data -> {
            Category root = registry.getRoot();
            if (root == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                CategoryMenu menu = new CategoryMenu(plugin, root, player, data,
                        this, registry, disabledRegistry, itemFactory, storage);
                player.openInventory(menu.getInventory());
            });
        });
    }

    /** 打开指定分类菜单 */
    public void openCategory(Player player, String categoryPath) {
        registry.getNode(categoryPath).ifPresentOrElse(
                node -> {
                    if (node.nodeType() != AchievementNode.NodeType.CATEGORY) return;
                    storage.load(player.getUniqueId()).thenAcceptAsync(data -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            CategoryMenu menu = new CategoryMenu(plugin, (Category) node, player, data,
                                    this, registry, disabledRegistry, itemFactory, storage);
                            player.openInventory(menu.getInventory());
                        });
                    });
                },
                () -> player.sendMessage(Component.text("分类不存在: " + categoryPath))
        );
    }

    /** 打开成就详情菜单 */
    public void openDetail(Player player, Achievement achievement) {
        storage.load(player.getUniqueId()).thenAcceptAsync(data -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                AchievementDetailMenu menu = new AchievementDetailMenu(plugin, achievement, player,
                        data, this, itemFactory, storage);
                player.openInventory(menu.getInventory());
            });
        });
    }

    /** 打开搜索结果菜单 */
    public void openSearch(Player player, String keyword) {
        storage.load(player.getUniqueId()).thenAcceptAsync(data -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                SearchResultMenu menu = new SearchResultMenu(plugin, keyword, player, data,
                        this, registry, disabledRegistry, itemFactory, storage);
                player.openInventory(menu.getInventory());
            });
        });
    }
}
