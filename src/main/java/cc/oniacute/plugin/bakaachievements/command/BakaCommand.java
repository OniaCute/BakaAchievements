package cc.oniacute.plugin.bakaachievements.command;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.achievement.PlayerAchievementData;
import cc.oniacute.plugin.bakaachievements.achievement.ProgressService;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.gui.MenuController;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.storage.PlayerDataStorage;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;
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
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendMsg(sender, "<red>此命令仅限玩家使用。</red>");
                return true;
            }
            if (!player.hasPermission("bakaachievements.player")) {
                sendMsg(sender, messages.noPermission());
                return true;
            }
            menuController.openRoot(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "search"  -> handleSearch(sender, args);
            case "check"   -> handleCheck(sender, args);
            case "set"     -> handleSet(sender, args);
            case "enable"  -> handleEnable(sender, args, false);
            case "disable" -> handleEnable(sender, args, true);
            case "reload"  -> handleReload(sender, args);
            case "info"    -> { sendInfo(sender); yield true; }
            default        -> {
                if (sender instanceof Player player && player.hasPermission("bakaachievements.player")) {
                    menuController.openRoot(player);
                } else {
                    sendMsg(sender, messages.invalidUsage().replace("{usage}", "/bac [search|check|set|enable|disable|reload]"));
                }
                yield true;
            }
        };
    }

    // ── search ───────────────────────────────────────────

    private boolean handleSearch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, "<red>此命令仅限玩家使用。</red>");
            return true;
        }
        if (!player.hasPermission("bakaachievements.player")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }
        if (args.length < 2) {
            sendMsg(sender, messages.invalidUsage().replace("{usage}", "/bac search <关键词>"));
            return true;
        }
        String keyword = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        menuController.openSearch(player, keyword);
        return true;
    }

    // ── check ────────────────────────────────────────────

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bakaachievements.player")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }
        if (args.length < 2) {
            sendMsg(sender, messages.invalidUsage().replace("{usage}", "/bac check <玩家名称>"));
            return true;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendMsg(sender, messages.playerNotFound());
            return true;
        }

        storage.load(target.getUniqueId()).thenAccept(data -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            Map<String, PlayerAchievementData.AchievementStatus> all = data.getAll();
            List<String> unlockedList = all.entrySet().stream()
                    .filter(e -> e.getValue().unlocked())
                    .map(e -> {
                        String time = e.getValue().achieveTime() > 0
                                ? sdf.format(new Date(e.getValue().achieveTime())) : "未知";
                        return "<green>" + e.getKey() + "</green> <gray>- " + time + "</gray>";
                    })
                    .collect(Collectors.toList());

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sendMsg(sender, "<gold>玩家 " + targetName + " 的成就信息:</gold>");
                sendMsg(sender, "<gray>已解锁: " + unlockedList.size() + " / " + registry.getAchievementCount() + "</gray>");
                for (String line : unlockedList) {
                    sendMsg(sender, "  " + line);
                }
            });
        });

        return true;
    }

    // ── set ──────────────────────────────────────────────

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bakaachievements.admin")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }
        if (args.length < 4) {
            sendMsg(sender, messages.invalidUsage().replace("{usage}", "/bac set <玩家> <节点> <true/false>"));
            return true;
        }

        String targetName = args[1];
        String nodePath = args[2];
        boolean status = Boolean.parseBoolean(args[3]);

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sendMsg(sender, messages.playerNotFound());
            return true;
        }

        registry.getNode(nodePath).ifPresentOrElse(
                node -> {
                    progressService.forceSet(target, nodePath, status);
                    sendMsg(sender, messages.setSuccess()
                            .replace("{player}", targetName)
                            .replace("{node}", nodePath)
                            .replace("{status}", String.valueOf(status)));
                },
                () -> sendMsg(sender, messages.nodeNotFound().replace("{node}", nodePath))
        );

        return true;
    }

    // ── enable / disable ─────────────────────────────────

    private boolean handleEnable(CommandSender sender, String[] args, boolean isDisable) {
        if (!sender.hasPermission("bakaachievements.admin")) {
            sendMsg(sender, messages.noPermission());
            return true;
        }
        if (args.length < 2) {
            String cmd = isDisable ? "disable" : "enable";
            sendMsg(sender, messages.invalidUsage().replace("{usage}", "/bac " + cmd + " <节点>"));
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
            default -> {
                plugin.getConfigManager().reloadConfig();
                plugin.getConfigManager().reloadMessages();
                sendMsg(sender, messages.configReloaded() + " " + messages.messagesReloaded());
            }
        }
        return true;
    }

    private void sendInfo(CommandSender sender) {
        sendMsg(sender, "<gradient:#6cd3ff:#dc67ff>BakaAchievements</gradient> <gray>v" +
                plugin.getPluginMeta().getVersion() + "</gray>");
        sendMsg(sender, "<gray>  成就总数: " + registry.getAchievementCount() + "</gray>");
        sendMsg(sender, "<gray>  PlaceholderAPI: " +
                (plugin.getPapiHook().isEnabled() ? "<green>✓</green>" : "<red>✗</red>") + "</gray>");
    }

    // ── tab 补全 ─────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return filter(List.of("search", "check", "set", "enable", "disable", "reload", "info"), args[0]);
        }

        return switch (args[0].toLowerCase()) {
            case "search"  -> args.length == 2 ? filter(registry.getAllNodes().keySet(), args[1]) : List.of();
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
                args.length == 2 ? filter(List.of("config", "message"), args[1]) : List.of();
            default -> List.of();
        };
    }

    private List<String> filter(Collection<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private void sendMsg(CommandSender sender, String miniMessageStr) {
        sender.sendMessage(miniMessage.deserialize(miniMessageStr));
    }

    private void saveDisabled() {
        org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();
        yml.set("disabled", new ArrayList<>(disabledRegistry.getAllDisabled()));
        plugin.getConfigManager().saveDisabled(yml);
    }
}
