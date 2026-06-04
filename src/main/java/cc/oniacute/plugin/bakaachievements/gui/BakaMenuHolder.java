package cc.oniacute.plugin.bakaachievements.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * 标记接口——所有 BakaAchievements GUI 的 InventoryHolder 均应实现此接口。
 * <p>
 * {@link MenuController} 通过 instanceof 检查来路由点击事件。
 * </p>
 */
public interface BakaMenuHolder extends InventoryHolder {

    /** 菜单类型枚举，供 MenuController 分发 */
    enum MenuType {
        CATEGORY,
        DETAIL,
        SEARCH
    }

    /** 菜单类型 */
    MenuType getMenuType();

    /** 处理点击事件 */
    void handleClick(int slot, org.bukkit.entity.Player player, boolean isShiftClick);

    @Override
    @NotNull
    Inventory getInventory();
}
