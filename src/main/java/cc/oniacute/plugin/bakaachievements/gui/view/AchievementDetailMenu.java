package cc.oniacute.plugin.bakaachievements.gui.view;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.*;
import cc.oniacute.plugin.bakaachievements.gui.BakaMenuHolder;
import cc.oniacute.plugin.bakaachievements.gui.ItemFactory;
import cc.oniacute.plugin.bakaachievements.gui.MenuController;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 成就详情菜单——27 槽位，显示单个成就的详细信息和条件进度。
 */
public final class AchievementDetailMenu implements BakaMenuHolder {

    private static final int SLOT_ACHIEVEMENT = 13;
    private static final int SLOT_BACK = 22;
    private static final int SLOTS = 27;

    private final Inventory inventory;
    private final BakaAchievements plugin;
    private final Achievement achievement;
    private final Player viewer;
    private final PlayerAchievementData playerData;
    private final MenuController controller;
    private final ItemFactory itemFactory;
    private final PlayerDataStorage storage;

    public AchievementDetailMenu(BakaAchievements plugin, Achievement achievement, Player viewer,
                                  PlayerAchievementData playerData, MenuController controller,
                                  ItemFactory itemFactory, PlayerDataStorage storage) {
        this.plugin = plugin;
        this.achievement = achievement;
        this.viewer = viewer;
        this.playerData = playerData;
        this.controller = controller;
        this.itemFactory = itemFactory;
        this.storage = storage;

        String title = PlainTextComponentSerializer.plainText().serialize(
                Component.text(achievement.name()));
        this.inventory = Bukkit.createInventory(this, SLOTS, Component.text(title));
        render();
    }

    private void render() {
        // 成就主体（带附魔光效）
        ItemStack achItem = itemFactory.build(achievement, playerData, viewer);
        inventory.setItem(SLOT_ACHIEVEMENT, achItem);

        // 条件进度
        List<ItemStack> condItems = itemFactory.buildConditionItems(achievement, viewer);
        int startSlot = 0;
        for (ItemStack cond : condItems) {
            if (startSlot >= SLOTS) break;
            // 跳过成就槽和返回槽
            if (startSlot == SLOT_ACHIEVEMENT) startSlot++;
            if (startSlot == SLOT_BACK) startSlot++;
            inventory.setItem(startSlot, cond);
            startSlot++;
        }

        // 返回按钮
        ItemStack back = itemFactory.backButton();
        inventory.setItem(SLOT_BACK, back);

        // 空白填充
        if (plugin.getConfigManager().isGuiFillEmpty()) {
            ItemStack filler = itemFactory.fillEmpty();
            for (int i = 0; i < SLOTS; i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, filler);
                }
            }
        }
    }

    @Override
    public MenuType getMenuType() {
        return MenuType.DETAIL;
    }

    @Override
    public void handleClick(int slot, Player player, boolean isShiftClick) {
        if (slot == SLOT_BACK) {
            String parentPath = PathUtil.parent(achievement.nodePath());
            if (parentPath.equals(PathUtil.ROOT)) {
                controller.openRoot(player);
            } else {
                controller.openCategory(player, parentPath);
            }
            return;
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
