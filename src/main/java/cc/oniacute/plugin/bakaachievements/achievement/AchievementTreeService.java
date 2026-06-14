package cc.oniacute.plugin.bakaachievements.achievement;

import cc.oniacute.plugin.bakaachievements.hook.PermissionResolver;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.util.PathUtil;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Shared read-only operations over the achievement tree.
 */
public final class AchievementTreeService {

    private final AchievementRegistry registry;
    private final DisabledRegistry disabledRegistry;

    public AchievementTreeService(AchievementRegistry registry, DisabledRegistry disabledRegistry) {
        this.registry = registry;
        this.disabledRegistry = disabledRegistry;
    }

    public Optional<AchievementNode> node(String path) {
        return registry.getNode(path);
    }

    public List<AchievementNode> visibleChildren(List<AchievementNode> children,
                                                 Player viewer,
                                                 boolean checkPermission) {
        if (children == null || children.isEmpty()) return List.of();

        List<AchievementNode> result = new ArrayList<>();
        for (AchievementNode node : children) {
            if (disabledRegistry.isDisabledOrAncestor(node.nodePath())) continue;
            if (checkPermission && !canAccess(viewer, node)) continue;
            result.add(node);
        }

        result.sort(Comparator.comparingInt(this::typeRank));
        return result;
    }

    public boolean canAccess(Player player, AchievementNode node) {
        if (!node.permission()) return true;
        return PermissionResolver.has(player, "bakaachievements.category." + node.nodePath());
    }

    public @Nullable String parentOf(String targetPath) {
        Category root = registry.getRoot();
        if (root == null) return null;
        return findInCategory(root, targetPath);
    }

    public int[] progressOf(List<AchievementNode> nodes, PlayerAchievementData data) {
        int done = 0;
        int total = 0;
        for (AchievementNode node : nodes) {
            if (node.nodeType() == AchievementNode.NodeType.ACHIEVEMENT) {
                total++;
                if (data != null && data.isUnlocked(node.nodePath())) done++;
            } else if (node instanceof MixedNode mixed) {
                total++;
                if (data != null && data.isUnlocked(mixed.nodePath())) done++;
                total += countAchievements(mixed.children());
                done += countUnlocked(mixed.children(), data);
            } else if (node instanceof Category cat) {
                total += cat.countAchievements();
                done += countUnlocked(cat.children(), data);
            }
        }
        return new int[]{done, total};
    }

    public int countUnlocked(List<AchievementNode> children, PlayerAchievementData data) {
        if (data == null || children == null) return 0;
        int count = 0;
        for (AchievementNode child : children) {
            if (child.nodeType() == AchievementNode.NodeType.ACHIEVEMENT) {
                if (data.isUnlocked(child.nodePath())) count++;
            } else if (child instanceof MixedNode mixed) {
                if (data.isUnlocked(mixed.nodePath())) count++;
                count += countUnlocked(mixed.children(), data);
            } else if (child instanceof Category cat) {
                count += countUnlocked(cat.children(), data);
            }
        }
        return count;
    }

    private int countAchievements(List<AchievementNode> children) {
        int count = 0;
        if (children == null) return 0;
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

    private @Nullable String findInCategory(Category category, String targetPath) {
        for (AchievementNode child : category.children()) {
            if (child.nodePath().equals(targetPath)
                    && (child.nodeType() == AchievementNode.NodeType.ACHIEVEMENT
                    || child.nodeType() == AchievementNode.NodeType.MIXED)) {
                return category.nodePath();
            }
            if (child instanceof Category subCat) {
                String found = findInCategory(subCat, targetPath);
                if (found != null) return found;
            }
            if (child instanceof MixedNode mixed) {
                String found = findInMixed(mixed, targetPath);
                if (found != null) return found;
            }
        }
        return null;
    }

    private @Nullable String findInMixed(MixedNode mixed, String targetPath) {
        if (mixed.nodePath().equals(targetPath)) return mixed.nodePath();
        for (AchievementNode child : mixed.children()) {
            if (child.nodePath().equals(targetPath)) return mixed.nodePath();
            if (child instanceof Category subCat) {
                String found = findInCategory(subCat, targetPath);
                if (found != null) return found;
            }
            if (child instanceof MixedNode subMixed) {
                String found = findInMixed(subMixed, targetPath);
                if (found != null) return found;
            }
        }
        return null;
    }

    private int typeRank(AchievementNode node) {
        return switch (node.nodeType()) {
            case CATEGORY -> 0;
            case MIXED -> 1;
            case ACHIEVEMENT -> 2;
        };
    }
}
