package cc.oniacute.plugin.bakaachievements.command;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;
import cc.oniacute.plugin.bakaachievements.achievement.ProgressService;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.gui.MenuController;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 根命令处理器——分发到各子命令。
 */
public final class BakaCommand implements TabExecutor {

    private final BakaAchievements plugin;
    private final MiniMessage miniMessage;
    private final Messages messages;
    private final MenuController menuController;
    private final AchievementRegistry registry;
    private final PlayerDataStorage storage;
    private final ProgressService progressService;
    private final DisabledRegistry disabledRegistry;
    private final Map<String, CommandHandler> handlers = new HashMap<>();

    public BakaCommand(BakaAchievements plugin, MenuController menuController,
                       AchievementRegistry registry, PlayerDataStorage storage,
                       ProgressService progressService, DisabledRegistry disabledRegistry) {
        this.plugin = plugin;
        this.miniMessage = plugin.getMiniMessage();
        this.messages = plugin.getMessages();
        this.menuController = menuController;
        this.registry = registry;
        this.storage = storage;
        this.progressService = progressService;
        this.disabledRegistry = disabledRegistry;
        registerHandlers();
    }

    @FunctionalInterface
    private interface CommandHandler {
        boolean execute(CommandSender sender, String[] args);
    }

    private void registerHandlers() {
        handlers.put("search", this::handleSearch);
        handlers.put("check", this::handleCheck);
        handlers.put("set", this::handleSet);
        handlers.put("enable", (sender, args) -> handleEnable(sender, args, false));
        handlers.put("disable", (sender, args) -> handleEnable(sender, args, true));
        handlers.put("reload", this::handleReload);
        handlers.put("tips", this::handleTips);
        handlers.put("info", this::handleInfo);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendMsg(sender, messages.playerOnly());
                return true;
            }
            if (!player.hasPermission("bakaachievements.player")) {
                sendMsg(sender, messages.noPermission());
                return true;
            }
            menuController.openRoot(player);
            return true;
        }

        CommandHandler handler = handlers.get(args[0].toLowerCase());
        return handler != null
                ? handler.execute(sender, args)
                : handleNavigationFallback(sender, args);
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bakaachievements.player")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }
        sendInfo(sender);
        return true;
    }

    private boolean handleNavigationFallback(CommandSender sender, String[] args) {
        if (sender instanceof Player player && player.hasPermission("bakaachievements.player")) {
            String navPath = args[0];
            var nodeOpt = registry.getNode(navPath);
            if (nodeOpt.isPresent()) {
                var node = nodeOpt.get();
                var nt = node.nodeType();
                if (nt == cc.oniacute.plugin.bakaachievements.achievement.AchievementNode.NodeType.CATEGORY
                        || nt == cc.oniacute.plugin.bakaachievements.achievement.AchievementNode.NodeType.MIXED) {
                    plugin.getMenuStack().clear(player);
                    plugin.getMenuStack().push(player, cc.oniacute.plugin.bakaachievements.util.PathUtil.ROOT);
                    menuController.openCategory(player, navPath);
                } else {
                    String parent = findCategoryParent(navPath);
                    if (parent != null) {
                        plugin.getMenuStack().clear(player);
                        plugin.getMenuStack().push(player, cc.oniacute.plugin.bakaachievements.util.PathUtil.ROOT);
                        menuController.openCategory(player, parent);
                    } else {
                        menuController.openRoot(player);
                    }
                }
            } else {
                menuController.openRoot(player);
            }
        } else {
            sendMsg(sender, messages.invalidUsage().replace("{usage}",
                    "/bac [search|check|set|enable|disable|reload|tips]"));
        }
        return true;
    }

    // ── search ───────────────────────────────────────────

    private boolean handleSearch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, messages.playerOnly());
            return true;
        }
        if (!player.hasPermission("bakaachievements.player")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }
        if (args.length < 2) {
            sendMsg(sender, messages.invalidUsage().replace("{usage}",
                    messages.getMessage("USAGE_SEARCH", "/bac search <keyword>")));
            return true;
        }
        String keyword = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        menuController.openSearch(player, keyword);
        return true;
    }

    // ── check ────────────────────────────────────────────

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sendMsg(sender, messages.playerOnly());
            return true;
        }
        if (!viewer.hasPermission("bakaachievements.player")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }
        if (args.length < 2) {
            sendMsg(sender, messages.invalidUsage().replace("{usage}",
                    messages.getMessage("USAGE_CHECK", "/bac check <player>")));
            return true;
        }

        String targetName = args[1];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendMsg(sender, messages.playerNotFound());
            return true;
        }

        UUID targetUUID = target.getUniqueId();
        storage.load(targetUUID).thenAccept(data ->
            plugin.getServer().getScheduler().runTask(plugin, () ->
                menuController.openPlayerCheck(viewer, targetName, targetUUID, data)
            )
        );

        return true;
    }

    // ── set ──────────────────────────────────────────────

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bakaachievements.admin")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }
        if (args.length < 4) {
            sendMsg(sender, messages.invalidUsage().replace("{usage}",
                    messages.getMessage("USAGE_SET", "/bac set <player> <node> <true/false>")));
            return true;
        }

        String targetName = args[1];
        String nodePath = args[2];
        boolean status = Boolean.parseBoolean(args[3]);

        // 验证节点是否存在
        if (registry.getNode(nodePath).isEmpty()) {
            sendMsg(sender, messages.nodeNotFound().replace("{node}", nodePath));
            return true;
        }

        // 优先尝试在线玩家
        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null) {
            progressService.forceSet(onlineTarget, nodePath, status);
            sendMsg(sender, messages.setSuccess()
                    .replace("{player}", targetName)
                    .replace("{node}", nodePath)
                    .replace("{status}", String.valueOf(status)));
            return true;
        }

        // 离线玩家：异步加载数据后设置并保存
        @SuppressWarnings("deprecation")
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        if (!offlineTarget.hasPlayedBefore()) {
            sendMsg(sender, messages.playerNotFound());
            return true;
        }

        UUID targetUUID = offlineTarget.getUniqueId();
        long achieveTime = status ? System.currentTimeMillis() : -1L;

        storage.load(targetUUID).thenAccept(data -> {
            data.setStatus(nodePath,
                    new PlayerAchievementData.AchievementStatus(status, achieveTime));
            storage.save(targetUUID, data);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                sendMsg(sender, messages.setSuccess()
                        .replace("{player}", targetName)
                        .replace("{node}", nodePath)
                        .replace("{status}", String.valueOf(status)))
            );
        });

        return true;
    }

    // ── tips ─────────────────────────────────────────────

    /**
     * 处理 /bac tips 命令。
     * <p>
     * 语法：{@code /bac tips <on|off> [self|others]}
     * <ul>
     *   <li>缺省第三参数 → 同时设置 self 和 others</li>
     *   <li>{@code self}   → 控制接收"自己"的成就解锁提示</li>
     *   <li>{@code others} → 控制接收"他人"的成就解锁提示</li>
     * </ul>
     * </p>
     */
    private boolean handleTips(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, messages.playerOnly());
            return true;
        }
        if (!player.hasPermission("bakaachievements.player")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }
        if (args.length < 2) {
            sendMsg(sender, messages.invalidUsage()
                    .replace("{usage}", "/bac tips <on|off> [self|others]"));
            return true;
        }

        String switchArg = args[1].toLowerCase();
        if (!switchArg.equals("on") && !switchArg.equals("off")) {
            sendMsg(sender, messages.invalidUsage()
                    .replace("{usage}", "/bac tips <on|off> [self|others]"));
            return true;
        }

        boolean enable = switchArg.equals("on");
        // 目标：self / others / 缺省=both
        String target = args.length >= 3 ? args[2].toLowerCase() : "both";

        UUID uuid = player.getUniqueId();
        PlayerAchievementData data = storage.getCached(uuid);

        if (data == null) {
            storage.load(uuid).thenAccept(loadedData ->
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    applyTips(player, loadedData, enable, target, uuid)
                )
            );
        } else {
            applyTips(player, data, enable, target, uuid);
        }

        return true;
    }

    private void applyTips(Player player, PlayerAchievementData data,
                            boolean enable, String target, UUID uuid) {
        switch (target) {
            case "self" -> {
                data.setTipsSelf(enable);
                storage.save(uuid, data);
                sendMsg(player, enable
                        ? messages.getMessage("TIPS_ENABLED_SELF", "{prefix} <green>自己的成就提示已开启.</green>")
                        : messages.getMessage("TIPS_DISABLED_SELF", "{prefix} <yellow>自己的成就提示已关闭.</yellow>"));
            }
            case "others" -> {
                data.setTipsOthers(enable);
                storage.save(uuid, data);
                sendMsg(player, enable
                        ? messages.getMessage("TIPS_ENABLED_OTHERS", "{prefix} <green>他人的成就提示已开启.</green>")
                        : messages.getMessage("TIPS_DISABLED_OTHERS", "{prefix} <yellow>他人的成就提示已关闭.</yellow>"));
            }
            default -> {
                // both
                data.setTipsSelf(enable);
                data.setTipsOthers(enable);
                storage.save(uuid, data);
                sendMsg(player, enable
                        ? messages.getMessage("TIPS_ENABLED_ALL", "{prefix} <green>所有成就提示已开启.</green>")
                        : messages.getMessage("TIPS_DISABLED_ALL", "{prefix} <yellow>所有成就提示已关闭.</yellow>"));
            }
        }
    }

    // ── enable / disable ─────────────────────────────────

    private boolean handleEnable(CommandSender sender, String[] args, boolean isDisable) {
        if (!sender.hasPermission("bakaachievements.admin")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }
        if (args.length < 2) {
            String cmd = isDisable ? "disable" : "enable";
            sendMsg(sender, messages.invalidUsage().replace("{usage}",
                    messages.getMessage(isDisable ? "USAGE_DISABLE" : "USAGE_ENABLE",
                            "/bac " + cmd + " <node>")));
            return true;
        }

        String nodePath = args[1];
        registry.getNode(nodePath).ifPresentOrElse(
                node -> {
                    if (isDisable) {
                        disabledRegistry.disable(nodePath);
                        sendMsg(sender, messages.disableSuccess().replace("{node}", nodePath));
                    } else {
                        disabledRegistry.enable(nodePath);
                        sendMsg(sender, messages.enableSuccess().replace("{node}", nodePath));
                    }
                    // 持久化 disabled.yml
                    saveDisabled();
                },
                () -> sendMsg(sender, messages.nodeNotFound().replace("{node}", nodePath))
        );

        return true;
    }

    // ── reload ───────────────────────────────────────────

    private boolean handleReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bakaachievements.admin")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }

        String sub = args.length >= 2 ? args[1].toLowerCase() : "all";

        switch (sub) {
            case "config" -> {
                plugin.getConfigManager().reloadConfig();
                sendMsg(sender, messages.configReloaded());
            }
            case "message" -> {
                plugin.getConfigManager().reloadMessages();
                sendMsg(sender, messages.messagesReloaded());
            }
            case "achievement" -> {
                int count = plugin.reloadAchievements();
                sendMsg(sender, messages.achievementsReloaded()
                        .replace("{count}", String.valueOf(count)));
            }
            default -> {
                plugin.getConfigManager().reloadConfig();
                plugin.getConfigManager().reloadMessages();
                int count = plugin.reloadAchievements();
                sendMsg(sender, messages.configReloaded() + " " + messages.messagesReloaded()
                        + " " + messages.achievementsReloaded().replace("{count}", String.valueOf(count)));
            }
        }
        return true;
    }

    private void sendInfo(CommandSender sender) {
        sendMsg(sender, messages.infoHeader()
                .replace("{version}", plugin.getPluginMeta().getVersion()));
        sendMsg(sender, messages.infoTotal()
                .replace("{total}", String.valueOf(registry.getAchievementCount())));
        sendMsg(sender, messages.infoPapi()
                .replace("{status}", plugin.getPapiHook().isEnabled()
                        ? messages.getMessage("INFO_STATUS_ENABLED", "<green>enabled</green>")
                        : messages.getMessage("INFO_STATUS_DISABLED", "<red>disabled</red>")));
    }

    // ── tab 补全 ─────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return filter(List.of("search", "check", "set", "enable", "disable", "reload", "tips", "info"), args[0]);
        }

        return switch (args[0].toLowerCase()) {
            case "search"  -> args.length == 2 ? filter(getAchievementDisplayNames(), args[1]) : List.of();
            case "check"   -> args.length == 2 ? null : List.of(); // null = 玩家名补全
            case "set"     -> switch (args.length) {
                case 2 -> null; // 玩家名
                case 3 -> filter(registry.getAllNodes().keySet(), args[2]);
                case 4 -> filter(List.of("true", "false"), args[3]);
                default -> List.of();
            };
            case "enable", "disable" ->
                args.length == 2 ? filter(registry.getAllNodes().keySet(), args[1]) : List.of();
            case "reload" ->
                args.length == 2 ? filter(List.of("config", "message", "achievement"), args[1]) : List.of();
            case "tips" -> switch (args.length) {
                case 2 -> filter(List.of("on", "off"), args[1]);
                case 3 -> filter(List.of("self", "others"), args[2]);
                default -> List.of();
            };
            default -> List.of();
        };
    }

    private List<String> filter(Collection<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    /** 获取所有成就的显示名称（纯文本，用于 search 命令 Tab 补全） */
    private List<String> getAchievementDisplayNames() {
        PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();
        return registry.getAllNodes().values().stream()
                .filter(n -> {
                    var nt = n.nodeType();
                    return nt == cc.oniacute.plugin.bakaachievements.achievement.AchievementNode.NodeType.ACHIEVEMENT
                            || nt == cc.oniacute.plugin.bakaachievements.achievement.AchievementNode.NodeType.MIXED;
                })
                .map(n -> {
                    try {
                        return plainText.serialize(miniMessage.deserialize(n.display()));
                    } catch (Exception e) {
                        return n.display();
                    }
                })
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 树遍历查找成就节点的实际父分类路径。
     * @return 父分类路径，未找到返回 null
     */
    @org.jetbrains.annotations.Nullable
    private String findCategoryParent(String achievementPath) {
        var root = registry.getRoot();
        if (root == null) return null;
        return findInCategory(root, achievementPath);
    }

    @org.jetbrains.annotations.Nullable
    private String findInCategory(cc.oniacute.plugin.bakaachievements.achievement.Category cat, String target) {
        for (var child : cat.children()) {
            if (child.nodePath().equals(target) &&
                    (child.nodeType() == cc.oniacute.plugin.bakaachievements.achievement.AchievementNode.NodeType.ACHIEVEMENT
                     || child.nodeType() == cc.oniacute.plugin.bakaachievements.achievement.AchievementNode.NodeType.MIXED)) {
                return cat.nodePath();
            }
            if (child instanceof cc.oniacute.plugin.bakaachievements.achievement.Category subCat) {
                String found = findInCategory(subCat, target);
                if (found != null) return found;
            }
            if (child instanceof cc.oniacute.plugin.bakaachievements.achievement.MixedNode mixed) {
                if (mixed.nodePath().equals(target)) return cat.nodePath();
                String found = findInMixed(mixed, target);
                if (found != null) return found;
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private String findInMixed(cc.oniacute.plugin.bakaachievements.achievement.MixedNode mixed, String target) {
        for (var child : mixed.children()) {
            if (child.nodePath().equals(target)) {
                return mixed.nodePath();
            }
            if (child instanceof cc.oniacute.plugin.bakaachievements.achievement.Category subCat) {
                String found = findInCategory(subCat, target);
                if (found != null) return found;
            }
            if (child instanceof cc.oniacute.plugin.bakaachievements.achievement.MixedNode subMixed) {
                String found = findInMixed(subMixed, target);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void sendMsg(CommandSender sender, String miniMessageStr) {
        String prefix = messages.getMessage("PREFIX",
                "<gradient:#6cd3ff:#dc67ff>BakaAchievements</gradient> <dark_gray>»</dark_gray>");
        String resolved = miniMessageStr.replace("{prefix}", prefix);
        sender.sendMessage(miniMessage.deserialize(resolved));
    }

    private void saveDisabled() {
        org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();
        yml.set("disabled", new ArrayList<>(disabledRegistry.getAllDisabled()));
        plugin.getConfigManager().saveDisabled(yml);
    }
}
