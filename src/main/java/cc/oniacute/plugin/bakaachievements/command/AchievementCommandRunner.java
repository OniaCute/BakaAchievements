package cc.oniacute.plugin.bakaachievements.command;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.gui.MenuController;
import cc.oniacute.plugin.bakaachievements.gui.MenuStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * 在玩家达成成就时，按顺序执行成就的 commands 列表。
 *
 * <h3>特殊命令</h3>
 * <ul>
 *   <li>{@code [close]}    — 关闭玩家当前打开的容器界面</li>
 *   <li>{@code [previous]} — 通过 MenuStack 回到上一级菜单</li>
 *   <li>{@code [refresh]}  — 重新打开玩家当前正在查看的菜单</li>
 *   <li>其他命令          — 以控制台身份执行（占位符已替换）</li>
 * </ul>
 *
 * <p><strong>注意：</strong>{@link #run(Player, Achievement)} 必须在主线程调用。</p>
 */
public final class AchievementCommandRunner {

    private final BakaAchievements plugin;
    private final MenuController menuController;
    private final MenuStack menuStack;
    private final MiniMessage miniMessage;
    private final Logger logger;

    public AchievementCommandRunner(BakaAchievements plugin,
                                    MenuController menuController,
                                    MenuStack menuStack) {
        this.plugin = plugin;
        this.menuController = menuController;
        this.menuStack = menuStack;
        this.miniMessage = plugin.getMiniMessage();
        this.logger = plugin.getLogger();
    }

    /**
     * 在主线程按顺序执行成就的所有命令。
     *
     * @param player      获得成就的玩家
     * @param achievement 被达成的成就
     * @throws IllegalStateException 若在非主线程调用（仅以日志警告形式提示）
     */
    public void run(Player player, AchievementNode node) {
        List<String> commands = node.commands();
        if (commands == null || commands.isEmpty()) return;

        if (!Bukkit.isPrimaryThread()) {
            logger.warning("[AchievementCommandRunner] run() 被在非主线程调用！"
                    + " 成就: " + node.nodePath()
                    + " 玩家: " + player.getName());
            // 切回主线程再执行
            Bukkit.getScheduler().runTask(plugin, () -> run(player, node));
            return;
        }

        for (String rawCmd : commands) {
            if (rawCmd == null || rawCmd.isBlank()) continue;
            executeOne(player, node, rawCmd.trim());
        }
    }

    // ── 单条命令执行 ─────────────────────────────────────

    private void executeOne(Player player, AchievementNode node, String rawCmd) {
        String lower = rawCmd.toLowerCase();

        if ("[close]".equals(lower)) {
            player.closeInventory();
            return;
        }

        if ("[previous]".equals(lower)) {
            handlePrevious(player);
            return;
        }

        if ("[refresh]".equals(lower)) {
            menuController.refreshCurrentMenu(player);
            return;
        }

        // 普通命令：替换占位符后以控制台身份执行
        String resolved = resolvePlaceholders(rawCmd, player, node);
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        } catch (Exception e) {
            logger.warning("[AchievementCommandRunner] 命令执行失败: "
                    + resolved + " — " + e.getMessage());
        }
    }

    // ── [previous] 处理 ──────────────────────────────────

    /**
     * 弹出 MenuStack 中的上一层菜单并打开。
     * <ul>
     *   <li>若路径为 {@link MenuStack#ROOT_PATH}，调用 {@link MenuController#openRoot(Player)}</li>
     *   <li>否则调用 {@link MenuController#openCategory(Player, String)}</li>
     *   <li>栈为空时关闭界面</li>
     * </ul>
     */
    private void handlePrevious(Player player) {
        Optional<String> pathOpt = menuStack.pop(player);
        if (pathOpt.isEmpty()) {
            // 没有历史记录，直接关闭界面
            player.closeInventory();
            return;
        }
        String path = pathOpt.get();
        if (MenuStack.ROOT_PATH.equals(path)) {
            menuController.openRoot(player);
        } else {
            menuController.openCategory(player, path);
        }
    }

    // ── 占位符替换 ───────────────────────────────────────

    /**
     * 替换命令中的内置占位符。
     *
     * <table>
     *   <caption>占位符列表</caption>
     *   <tr><th>占位符</th><th>替换内容</th></tr>
     *   <tr><td>{@code {player}}</td><td>玩家名（{@link Player#getName()}）</td></tr>
     *   <tr><td>{@code {player_displayname}}</td><td>玩家显示名（纯文本）</td></tr>
     *   <tr><td>{@code {achievement}}</td><td>成就内部名称</td></tr>
     *   <tr><td>{@code {achievement_display}}</td><td>成就显示名（纯文本，MiniMessage 解析后序列化）</td></tr>
     * </table>
     */
    private String resolvePlaceholders(String cmd, Player player, AchievementNode node) {
        String displayPlain = PlainTextComponentSerializer.plainText()
                .serialize(miniMessage.deserialize(node.display()));

        String displayNamePlain = PlainTextComponentSerializer.plainText()
                .serialize(player.displayName());

        return cmd
                .replace("{player}", player.getName())
                .replace("{player_displayname}", displayNamePlain)
                .replace("{achievement}", node.name())
                .replace("{achievement_display}", displayPlain);
    }
}
