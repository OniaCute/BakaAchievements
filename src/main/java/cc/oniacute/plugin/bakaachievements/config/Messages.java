package cc.oniacute.plugin.bakaachievements.config;

import cc.oniacute.plugin.bakaachievements.BakaAchievements;

/**
 * 消息门面——从 {@link ConfigManager} 中读取 {@code messages.yml} 的文本。
 * <p>
 * 提供带前缀包裹、变量替换等便捷方法。
 * 所有返回值均为 MiniMessage 格式字符串，供 GUI/Chat/Command 使用。
 * </p>
 */
public final class Messages {

    private final BakaAchievements plugin;

    public Messages(BakaAchievements plugin) {
        this.plugin = plugin;
    }

    private ConfigManager cfg() {
        return plugin.getConfigManager();
    }

    // ── 前缀 ───────────────────────────────────────────

    public String prefix() {
        return cfg().getMessage("PREFIX",
                "<gradient:#6cd3ff:#dc67ff>BakaAchievements</gradient> <dark_gray>»</dark_gray>");
    }

    // ── 通用 ───────────────────────────────────────────

    public String noPermission() {
        return cfg().getMessage("NO_PERMISSION", "<red>你没有权限执行此操作！</red>");
    }

    public String playerNotFound() {
        return cfg().getMessage("PLAYER_NOT_FOUND", "<red>玩家未找到或不在线。</red>");
    }

    public String configReloaded() {
        return cfg().getMessage("CONFIG_RELOADED", "<green>配置已重载。</green>");
    }

    public String messagesReloaded() {
        return cfg().getMessage("MESSAGES_RELOADED", "<green>语言文件已重载。</green>");
    }

    // ── 命令 ───────────────────────────────────────────

    public String setSuccess() {
        return cfg().getMessage("SET_SUCCESS", "<green>已设置 {player} 的成就 {node} 为 {status}。</green>");
    }

    public String enableSuccess() {
        return cfg().getMessage("ENABLE_SUCCESS", "<green>成就 {node} 已启用。</green>");
    }

    public String disableSuccess() {
        return cfg().getMessage("DISABLE_SUCCESS", "<green>成就 {node} 已禁用。</green>");
    }

    public String nodeNotFound() {
        return cfg().getMessage("NODE_NOT_FOUND", "<red>成就节点不存在: {node}</red>");
    }

    public String invalidUsage() {
        return cfg().getMessage("INVALID_USAGE", "<red>用法: {usage}</red>");
    }

    // ── GUI ────────────────────────────────────────────

    public String guiTitleAchievements() {
        return cfg().getMessage("GUI_TITLE_ACHIEVEMENTS", "成就列表");
    }

    public String guiTitleSearch() {
        return cfg().getMessage("GUI_TITLE_SEARCH", "搜索: {keyword}");
    }

    public String guiTitleDetail() {
        return cfg().getMessage("GUI_TITLE_DETAIL", "成就详情");
    }

    public String guiPrevPage() {
        return cfg().getMessage("GUI_PREV_PAGE", "<yellow>上一页</yellow>");
    }

    public String guiNextPage() {
        return cfg().getMessage("GUI_NEXT_PAGE", "<yellow>下一页</yellow>");
    }

    public String guiClose() {
        return cfg().getMessage("GUI_CLOSE", "<red>关闭</red>");
    }

    public String guiBack() {
        return cfg().getMessage("GUI_BACK", "<gray>返回上级</gray>");
    }

    public String guiPageInfo() {
        return cfg().getMessage("GUI_PAGE_INFO", "第 {current}/{total} 页");
    }

    public String guiProgress() {
        return cfg().getMessage("GUI_PROGRESS", "进度: {done}/{total}");
    }

    public String guiUnlockedTime() {
        return cfg().getMessage("GUI_UNLOCKED_TIME", "达成时间: {time}");
    }

    public String guiPermissionRequired() {
        return cfg().getMessage("GUI_PERMISSION_REQUIRED", "<red>需要权限节点: {node}</red>");
    }

    public String guiNoAccess() {
        return cfg().getMessage("GUI_NO_ACCESS", "<red>你没有权限访问此成就。</red>");
    }

    public String guiNoConditions() {
        return cfg().getMessage("GUI_NO_CONDITIONS", "<gray>此成就无条件限制</gray>");
    }

    // ── 聊天广播 ───────────────────────────────────────

    public String chatAchievementUnlocked() {
        return cfg().getMessage("CHAT_ACHIEVEMENT_UNLOCKED",
                "<gold>{player}</gold> <gray>解锁了成就</gray> <green>{display}</green>");
    }

    // ── 工具方法 ───────────────────────────────────────

    /** 获取原始消息文本（无前缀） */
    public String getMessage(String key, String def) {
        return cfg().getMessage(key, def);
    }

    /** 给消息添加前缀 */
    public String prefixed(String messageKey) {
        return prefix() + " " + cfg().getMessage(messageKey, messageKey);
    }
}
