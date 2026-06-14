package cc.oniacute.plugin.bakaachievements;

import cc.oniacute.plugin.bakaachievements.achievement.AchievementRegistry;
import cc.oniacute.plugin.bakaachievements.achievement.ProgressService;
import cc.oniacute.plugin.bakaachievements.api.BakaAchievementsApi;
import cc.oniacute.plugin.bakaachievements.bootstrap.PluginBootstrap;
import cc.oniacute.plugin.bakaachievements.bootstrap.ServiceContainer;
import cc.oniacute.plugin.bakaachievements.config.ConfigManager;
import cc.oniacute.plugin.bakaachievements.config.Messages;
import cc.oniacute.plugin.bakaachievements.gui.MenuStack;
import cc.oniacute.plugin.bakaachievements.hook.HdbHook;
import cc.oniacute.plugin.bakaachievements.hook.PapiHook;
import cc.oniacute.plugin.bakaachievements.storage.DisabledRegistry;
import cc.oniacute.plugin.bakaachievements.storage.YamlPlayerDataStorage;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit entrypoint for BakaAchievements.
 *
 * <p>Runtime construction and teardown live in {@link PluginBootstrap}; this
 * class intentionally keeps the public compatibility surface and delegates
 * lifecycle, command, and listener callbacks to the service layer.</p>
 */
public final class BakaAchievements extends JavaPlugin implements Listener {

    private static BakaAchievements instance;

    private PluginBootstrap bootstrap;
    private ServiceContainer services;

    @Override
    public void onEnable() {
        instance = this;
        bootstrap = new PluginBootstrap(this);
        services = bootstrap.enable();
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.disable();
        }
        services = null;
        bootstrap = null;
        instance = null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (bootstrap != null) {
            bootstrap.handlePlayerJoin(event.getPlayer());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        return services.bakaCommand().onCommand(sender, command, label, args);
    }

    @Override
    public java.util.@org.jetbrains.annotations.Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String @NotNull [] args) {
        return services.bakaCommand().onTabComplete(sender, command, alias, args);
    }

    public int reloadAchievements() {
        return services.reloadService().reloadAchievements();
    }

    public void installServices(ServiceContainer services) {
        this.services = services;
    }

    public static BakaAchievements getInstance() { return instance; }
    public MiniMessage getMiniMessage() { return services.miniMessage(); }
    public ConfigManager getConfigManager() { return services.configManager(); }
    public Messages getMessages() { return services.messages(); }
    public PapiHook getPapiHook() { return services.papiHook(); }
    public BakaAchievementsApi getApi() { return services.api(); }
    public AchievementRegistry getAchievementRegistry() { return services.achievementRegistry(); }
    public ProgressService getProgressService() { return services.progressService(); }
    public DisabledRegistry getDisabledRegistry() { return services.disabledRegistry(); }
    public HdbHook getHdbHook() { return services.hdbHook(); }
    public MenuStack getMenuStack() { return services.menuStack(); }
    public YamlPlayerDataStorage getPlayerDataStorage() { return services.playerDataStorage(); }
    public ServiceContainer getServices() { return services; }
}
