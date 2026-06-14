package cc.oniacute.plugin.bakaachievements.achievement.condition;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.Category;
import cc.oniacute.plugin.bakaachievements.achievement.MixedNode;
import cc.oniacute.plugin.bakaachievements.api.condition.ConditionType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * 条件评估器——对单个 {@link Condition} 执行判断。
 * <p>
 * 支持数字比较、字符串比较、权限检查、成就/分类进度检查、物品检查，以及取反逻辑。
 * PAPI 解析由调用方在主线程完成后传入已解析值，本类仅做纯逻辑判断。
 * 自定义条件通过 {@link cc.oniacute.plugin.bakaachievements.api.BakaAchievementsApi} 注册。
 * </p>
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    public static boolean evaluateResolved(Player player, Condition condition,
                                           String targetResolved, String currentResolved) {
        try {
            boolean result = evaluateRaw(player, condition.op(), targetResolved, currentResolved, condition);
            return condition.negate() != result;
        } catch (Exception e) {
            BakaAchievements.getInstance().getLogger()
                    .log(Level.WARNING, "条件評価異常: " + condition, e);
            return false;
        }
    }

    // ── 内部评估逻辑（纯函数，线程安全） ──────────────────

    private static boolean evaluateRaw(Player player, String op, String target, String current, Condition fullCondition) {
        return switch (op) {
            case "="   -> evaluateEqual(target, current, false, false);
            case ">="  -> evaluateNumeric(target, current, ">=", false);
            case "<="  -> evaluateNumeric(target, current, "<=", false);
            case ">"   -> evaluateNumeric(target, current, ">",  false);
            case "<"   -> evaluateNumeric(target, current, "<",  false);
            case "|="  -> evaluateEqual(target, current, true, true);
            case "|>=" -> evaluateNumeric(target, current, ">=", true);
            case "|<=" -> evaluateNumeric(target, current, "<=", true);
            case "|>"  -> evaluateNumeric(target, current, ">",  true);
            case "|<"  -> evaluateNumeric(target, current, "<",  true);
            case "hasPermission"  -> player.hasPermission(target);
            case "hasEffect"       -> evaluateHasEffect(player, target, current);
            case "gotAchievement" -> evaluateGotAchievement(player, target, current);
            case "gotCategory"    -> evaluateGotCategory(player, target, current);
            case "hasItem"        -> evaluateHasItem(player, target, fullCondition.itemOptions());
            default -> evaluateCustom(player, op, target, current);
        };
    }

    // ── gotAchievement / gotCategory ──────────────────────

    /** 检查玩家在目标成就上的进度是否 >= current%（current 为空时仅判断是否已解锁） */
    private static boolean evaluateGotAchievement(Player player, String target, String current) {
        BakaAchievements plugin = BakaAchievements.getInstance();
        if (plugin == null) return false;

        var nodeOpt = plugin.getAchievementRegistry().getNode(target);
        if (nodeOpt.isEmpty()) return false;
        var node = nodeOpt.get();
        var nt = node.nodeType();
        if (nt != AchievementNode.NodeType.ACHIEVEMENT && nt != AchievementNode.NodeType.MIXED) return false;

        // 当前已解锁 → 100%
        if (plugin.getApi().isUnlocked(player.getUniqueId(), target)) return true;

        // 未解锁且有数值 current → 计算条件平均进度
        Double requiredProgress = parseDouble(current);
        if (requiredProgress == null || requiredProgress <= 0) return false;

        if (node.conditionGroup().isEmpty()) return false;

        double totalProgress = 0.0;
        for (Condition cond : node.conditionGroup().conditions()) {
            String t = resolvePapi(player, cond.target());
            String c = resolvePapi(player, cond.current());
            boolean passed = evaluateResolved(player, cond, t, c);
            totalProgress += progress(cond, t, c, passed);
        }
        double avgProgress = totalProgress / node.conditionGroup().conditions().size();
        return avgProgress * 100.0 >= requiredProgress;
    }

    /** 检查玩家在目标分类上的完成百分比是否 >= current% */
    private static boolean evaluateGotCategory(Player player, String target, String current) {
        BakaAchievements plugin = BakaAchievements.getInstance();
        if (plugin == null) return false;

        var nodeOpt = plugin.getAchievementRegistry().getNode(target);
        if (nodeOpt.isEmpty()) return false;
        var nt = nodeOpt.get().nodeType();
        if (nt != AchievementNode.NodeType.CATEGORY && nt != AchievementNode.NodeType.MIXED) return false;

        var node = nodeOpt.get();
        int total;
        if (node instanceof Category cat) {
            total = cat.countAchievements();
        } else if (node instanceof MixedNode mixed) {
            total = mixed.countAchievements();
        } else {
            return false;
        }
        if (total == 0) return false;

        int done = countUnlockedInNode(node, player);
        double pct = 100.0 * done / total;

        Double required = parseDouble(current);
        if (required == null) return false;
        return pct >= required;
    }

    private static int countUnlockedInNode(AchievementNode node, Player player) {
        BakaAchievements plugin = BakaAchievements.getInstance();
        if (plugin == null) return 0;
        int count = 0;
        // MIXED 节点自身算一个成就
        if (node.nodeType() == AchievementNode.NodeType.MIXED
                && plugin.getApi().isUnlocked(player.getUniqueId(), node.nodePath())) {
            count++;
        }
        for (AchievementNode child : node.children()) {
            if (child.nodeType() == AchievementNode.NodeType.ACHIEVEMENT) {
                if (plugin.getApi().isUnlocked(player.getUniqueId(), child.nodePath())) count++;
            } else if (child.nodeType() == AchievementNode.NodeType.MIXED) {
                if (plugin.getApi().isUnlocked(player.getUniqueId(), child.nodePath())) count++;
                count += countUnlockedInNode(child, player);
            } else if (child instanceof Category cat) {
                count += countUnlockedInCategory(cat, player);
            }
        }
        return count;
    }

    private static int countUnlockedInCategory(Category cat, Player player) {
        BakaAchievements plugin = BakaAchievements.getInstance();
        if (plugin == null) return 0;
        int count = 0;
        for (AchievementNode child : cat.children()) {
            if (child.nodeType() == AchievementNode.NodeType.ACHIEVEMENT) {
                if (plugin.getApi().isUnlocked(player.getUniqueId(), child.nodePath())) count++;
            } else if (child.nodeType() == AchievementNode.NodeType.MIXED) {
                if (plugin.getApi().isUnlocked(player.getUniqueId(), child.nodePath())) count++;
                count += countUnlockedInNode(child, player);
            } else if (child instanceof Category subCat) {
                count += countUnlockedInCategory(subCat, player);
            }
        }
        return count;
    }

    // ── hasItem ───────────────────────────────────────────

    /** 检查玩家背包/副手/盔甲栏中是否存在符合要求的物品 */
    private static boolean evaluateHasItem(Player player, String materialName, Condition.ItemConditionOptions opts) {
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) return false;

        Condition.ItemConditionOptions options = (opts != null) ? opts : Condition.ItemConditionOptions.NONE;

        int totalFound = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (matchesItem(item, mat, options)) {
                totalFound += item.getAmount();
            }
        }
        // 检查副手
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() != Material.AIR && matchesItem(offHand, mat, options)) {
            totalFound += offHand.getAmount();
        }

        if (options.amount() > 0) {
            return totalFound >= options.amount();
        }
        return totalFound > 0;
    }

    private static boolean matchesItem(ItemStack item, Material mat, Condition.ItemConditionOptions opts) {
        if (item.getType() != mat) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return opts.isEmpty();

        // 名称检查
        if (opts.name() != null) {
            String displayName = "";
            if (meta.hasDisplayName()) {
                displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(meta.displayName());
            }
            if (!displayName.contains(opts.name())) return false;
        }

        // Lore 检查
        if (opts.loreIncluded() != null) {
            List<net.kyori.adventure.text.Component> lore = meta.lore();
            if (lore == null || lore.isEmpty()) return false;
            boolean found = false;
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer plainSer =
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            for (var line : lore) {
                if (plainSer.serialize(line).contains(opts.loreIncluded())) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        // 耐久度检查
        if (opts.durability() >= 0 && meta instanceof Damageable dmg) {
            if (dmg.getDamage() != opts.durability()) return false;
        }

        // CustomModelData 检查
        if (opts.modelData() != null) {
            if (meta.hasCustomModelData()) {
                if (meta.getCustomModelData() != opts.modelData()) return false;
            } else {
                return false;
            }
        }

        return true;
    }

    // ── hasEffect ──────────────────────────────────────────

    /**
     * 检查玩家是否拥有 target 指定的药水效果，且剩余时间 >= current 秒。
     * <p>
     * target 为药水效果名（如 SPEED、JUMP_BOOST），current 为最短剩余秒数。
     * current 为空或 ≤0 时，仅检查是否拥有该效果。
     * </p>
     */
    private static boolean evaluateHasEffect(Player player, String target, String current) {
        org.bukkit.potion.PotionEffectType type = org.bukkit.Registry.POTION_EFFECT_TYPE.get(
                org.bukkit.NamespacedKey.minecraft(target.toLowerCase()));
        if (type == null) return false;

        org.bukkit.potion.PotionEffect effect = player.getPotionEffect(type);
        if (effect == null) return false;

        // current 为空时仅检查是否存在
        if (current == null || current.isBlank()) return true;

        Double minSeconds = parseDouble(current);
        if (minSeconds == null) return true; // 非数字 → 仅检查存在

        // 剩余时间（秒）
        double remainingSec = effect.getDuration() / 20.0;
        return remainingSec >= minSeconds;
    }

    // ── 自定义条件 ────────────────────────────────────────

    private static boolean evaluateCustom(Player player, String op, String target, String current) {
        BakaAchievements plugin = BakaAchievements.getInstance();
        if (plugin == null || plugin.getApi() == null) return false;
        Optional<ConditionType> customType = plugin.getApi().getCustomCondition(op);
        if (customType.isEmpty()) {
            plugin.getLogger().warning("未知条件操作符: '" + op + "'，跳过评估");
            return false;
        }
        try {
            return customType.get().evaluate(new ConditionContext(player, target, current));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "自定义条件 '" + op + "' 评估异常", e);
            return false;
        }
    }

    // ── 数字/字符串比较 ──────────────────────────────────

    private static boolean evaluateEqual(String target, String current, boolean ignoreCase, boolean round) {
        Double tNum = parseDouble(target);
        Double cNum = parseDouble(current);
        if (tNum != null && cNum != null) {
            double c = round ? Math.round(cNum) : cNum;
            return Double.compare(tNum, c) == 0;
        }
        return ignoreCase ? target.equalsIgnoreCase(current) : target.equals(current);
    }

    private static boolean evaluateNumeric(String target, String current, String op, boolean round) {
        Double tNum = parseDouble(target);
        Double cNum = parseDouble(current);
        if (tNum == null || cNum == null) return false;
        double c = round ? Math.round(cNum) : cNum;

        return switch (op) {
            case ">=" -> c >= tNum;
            case "<=" -> c <= tNum;
            case ">"  -> c > tNum;
            case "<"  -> c < tNum;
            default   -> false;
        };
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── PAPI ──────────────────────────────────────────────

    /** 主线程安全解析 PAPI */
    private static String resolvePapi(Player player, String text) {
        if (text == null || text.isEmpty() || !text.contains("%")) return text != null ? text : "";
        try {
            if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            }
        } catch (Exception ignored) {}
        return text;
    }

    // ── 条件进度计算（供 GUI lore 使用） ─────────────────

    public static double progress(Condition condition, String targetResolved,
                                  String currentResolved, boolean passed) {
        if (passed) return 1.0;
        // 特殊操作符：只有 0 或 1
        if (isBinaryOp(condition.op())) return 0.0;
        // 尝试数值进度
        Double tNum = parseDouble(targetResolved);
        Double cNum = parseDouble(currentResolved);
        if (tNum != null && cNum != null && tNum > 0) {
            return Math.min(1.0, Math.max(0.0, cNum / tNum));
        }
        return 0.0;
    }

    private static boolean isBinaryOp(String op) {
        return switch (op) {
            case "hasPermission", "hasEffect", "gotAchievement", "gotCategory", "hasItem" -> true;
            default -> false;
        };
    }
}
