package cc.oniacute.plugin.bakaachievements.bootstrap;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementLoader;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementNode;
import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.achievement.Category;
import cc.oniacute.plugin.bakaachievements.config.ConfigManager;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.vanilla.VanillaTreeBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rebuilds achievement trees and reloads disabled-node state.
 */
public final class AchievementReloadService {

    private final BakaAchievements plugin;
    private final ConfigManager configManager;
    private final Messages messages;
    private final AchievementRegistry registry;
    private final DisabledRegistry disabledRegistry;

    public AchievementReloadService(BakaAchievements plugin,
                                    ConfigManager configManager,
                                    Messages messages,
                                    AchievementRegistry registry,
                                    DisabledRegistry disabledRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messages = messages;
        this.registry = registry;
        this.disabledRegistry = disabledRegistry;
    }

    public int reloadAchievements() {
        try {
            configManager.reloadAchievements();
            configManager.reloadVanilla();

            Category customRoot = new AchievementLoader(plugin).load();
            Category vanillaRoot = new VanillaTreeBuilder(plugin).build();

            List<AchievementNode> children = new ArrayList<>();
            if (configManager.isVanillaCategoryFirst()) {
                children.add(vanillaRoot);
                children.addAll(customRoot.children());
            } else {
                children.addAll(customRoot.children());
                children.add(vanillaRoot);
            }

            Category root = new Category(
                    "__root__", "root", messages.guiRootDisplay(),
                    List.of(), Material.CHEST, -1,
                    Set.of(), false, children);
            registry.setRoot(root);

            int count = registry.getAchievementCount();
            if (configManager.isDebug()) {
                plugin.getLogger().info("[BakaAchievements] Tree reload complete. nodes="
                        + registry.getAllNodes().size() + " achievements=" + count);
            } else {
                plugin.getLogger().info("成就已重载（总数: " + count + "）");
            }
            return count;
        } catch (Exception e) {
            plugin.getLogger().severe("成就重载失败: " + e.getMessage());
            e.printStackTrace();
            return registry.getAchievementCount();
        }
    }

    public void loadDisabled() {
        try {
            YamlConfiguration disabledCfg = configManager.getDisabledConfig();
            if (disabledCfg.contains("disabled")) {
                disabledRegistry.setAll(new HashSet<>(disabledCfg.getStringList("disabled")));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("加载 disabled.yml 失败: " + e.getMessage());
        }
    }
}
