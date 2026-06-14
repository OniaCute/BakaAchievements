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

    public String playerOnly() {
        return cfg().getMessage("PLAYER_ONLY", "{prefix} <red>此命令仅限玩家使用.</red>");
    }

    public String configReloaded() {
        return cfg().getMessage("CONFIG_RELOADED", "<green>配置已重载。</green>");
    }

    public String messagesReloaded() {
        return cfg().getMessage("MESSAGES_RELOADED", "<green>语言文件已重载。</green>");
    }

    public String achievementsReloaded() {
        return cfg().getMessage("ACHIEVEMENTS_RELOADED", "<green>成就已重载。（总数: {count}）</green>");
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

    public String tipsEnabled() {
        return cfg().getMessage("TIPS_ENABLED", "<green>成就分享提示已开启。</green>");
    }

    public String tipsDisabled() {
        return cfg().getMessage("TIPS_DISABLED", "<yellow>成就分享提示已关闭。</yellow>");
    }

    // ── GUI ────────────────────────────────────────────

    public String guiTitleAchievements() {
        return cfg().getMessage("GUI_TITLE_ACHIEVEMENTS", "成就列表");
    }

    public String guiRootDisplay() {
        return cfg().getMessage("GUI_ROOT_DISPLAY", "成就列表");
    }

    public String guiVanillaDisplay() {
        return cfg().getMessage("GUI_VANILLA_DISPLAY", "原版成就");
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

    public String guiEmptyCategory() {
        return cfg().getMessage("GUI_EMPTY_CATEGORY", "<gray>（空分类）</gray>");
    }

    public String guiClickToEnter() {
        return cfg().getMessage("GUI_CLICK_TO_ENTER", "<green>▶ 点击进入</green>");
    }

    public String guiRequirementsHeader() {
        return cfg().getMessage("GUI_REQUIREMENTS_HEADER", "<gold>要求:</gold>");
    }

    public String guiMixedUnlockedNav() {
        return cfg().getMessage("GUI_MIXED_UNLOCKED_NAV", "<yellow>▶ 左键尝试完成</yellow>");
    }

    public String guiMixedRightClick() {
        return cfg().getMessage("GUI_MIXED_RIGHT_CLICK", "<green>▶ 右键打开子分类</green>");
    }

    public String guiActionBackName() {
        return cfg().getMessage("GUI_ACTION_BACK_NAME", "<gray>返回上级</gray>");
    }

    public String guiActionBackLore() {
        return cfg().getMessage("GUI_ACTION_BACK_LORE", "<gray>点击返回: <white>{target}</white></gray>");
    }

    public String guiActionPrevName() {
        return cfg().getMessage("GUI_ACTION_PREV_NAME", "<yellow>上一页</yellow>");
    }

    public String guiActionPrevLore() {
        return cfg().getMessage("GUI_ACTION_PREV_LORE",
                "<gray>当前页: <white>{current}/{total}</white></gray>|<gray>点击前往: <yellow>{target}</yellow></gray>");
    }

    public String guiActionNextName() {
        return cfg().getMessage("GUI_ACTION_NEXT_NAME", "<yellow>下一页</yellow>");
    }

    public String guiActionNextLore() {
        return cfg().getMessage("GUI_ACTION_NEXT_LORE",
                "<gray>当前页: <white>{current}/{total}</white></gray>|<gray>点击前往: <yellow>{target}</yellow></gray>");
    }

    public String guiActionStatsLore() {
        return cfg().getMessage("GUI_ACTION_STATS_LORE",
                "<gray>进度: <green>{done}</green><gray>/</gray><white>{total}</white> <aqua>{percent}%</aqua></gray>|{progress_bar}|<gray>{path}</gray>|<yellow>{root_hint}</yellow>|<yellow>{share_hint}</yellow>");
    }

    public String guiActionRootHint() {
        return cfg().getMessage("GUI_ACTION_ROOT_HINT", "左键返回根目录");
    }

    public String guiActionShareHint() {
        return cfg().getMessage("GUI_ACTION_SHARE_HINT", "Shift+左键分享总进度");
    }

    public String guiSearchEmptyName() {
        return cfg().getMessage("GUI_SEARCH_EMPTY_NAME", "<yellow>没有找到结果</yellow>");
    }

    public String guiSearchEmptyLore() {
        return cfg().getMessage("GUI_SEARCH_EMPTY_LORE",
                "<gray>关键词: <white>{keyword}</white></gray>|<gray>换一个关键词再试.</gray>");
    }

    public String guiEmptyCategoryName() {
        return cfg().getMessage("GUI_EMPTY_CATEGORY_NAME", "<yellow>空分类</yellow>");
    }

    public String guiEmptyCategoryLore() {
        return cfg().getMessage("GUI_EMPTY_CATEGORY_LORE",
                "<gray>这里暂时没有可显示的成就.</gray>");
    }

    // ── 聊天广播 ───────────────────────────────────────

    public String chatAchievementUnlocked() {
        return cfg().getMessage("CHAT_ACHIEVEMENT_UNLOCKED",
                "<gold>{player_displayname}</gold> <gray>解锁了成就</gray> <green>{display}</green>");
    }

    // ── 成就分享 ───────────────────────────────────────

    public String shareUnlocked() {
        return cfg().getMessage("SHARE_ACHIEVEMENT_UNLOCKED",
                "<gold>{player_displayname}</gold> <gray>分享了成就</gray> {__HOVER__} <dark_gray>(已达成 {time})</dark_gray>");
    }

    public String shareLocked() {
        return cfg().getMessage("SHARE_ACHIEVEMENT_LOCKED",
                "<gold>{player_displayname}</gold> <gray>分享了成就</gray> {__HOVER__} <dark_gray>{progress_bar} {percent}</dark_gray>");
    }

    public String shareOnCooldown(long seconds) {
        return cfg().getMessage("SHARE_ON_COOLDOWN",
                "<yellow>分享太频繁了, 请在 {seconds}s 后重试.</yellow>")
                .replace("{seconds}", String.valueOf(Math.max(1L, seconds)));
    }

    public String shareProgress() {
        return cfg().getMessage("SHARE_PROGRESS",
                "<gradient:#6cd3ff:#dc67ff>{player_displayname}</gradient> <gray>已完成</gray> <green>{done}</green><gray>/</gray><white>{total}</white> <gray>项成就</gray> (<yellow>{percent}%</yellow>)");
    }

    public String timeUnknown() {
        return cfg().getMessage("TIME_UNKNOWN", "未知");
    }

    // ── 信息命令 ───────────────────────────────────────

    public String infoHeader() {
        return cfg().getMessage("INFO_HEADER",
                "<gradient:#6cd3ff:#dc67ff>BakaAchievements</gradient> <gray>v{version}</gray>");
    }

    public String infoTotal() {
        return cfg().getMessage("INFO_TOTAL", "<gray>成就总数: {total}</gray>");
    }

    public String infoPapi() {
        return cfg().getMessage("INFO_PAPI", "<gray>PlaceholderAPI: {status}</gray>");
    }

    // ── Hover 卡片文本 ─────────────────────────────────

    public String hoverTitle() {
        return cfg().getMessage("HOVER_TITLE", "<gold>成就详情</gold>");
    }

    public String hoverUnlocked() {
        return cfg().getMessage("HOVER_UNLOCKED", "<green>已达成！</green>");
    }

    public String hoverUnlockedTime() {
        return cfg().getMessage("HOVER_UNLOCKED_TIME", "<green>已达成 ✓ {time}</green>");
    }

    public String hoverLockedNoCondition() {
        return cfg().getMessage("HOVER_LOCKED_NO_CONDITION", "<yellow>未达成（无条件限制）</yellow>");
    }

    public String hoverLockedWithCount() {
        return cfg().getMessage("HOVER_LOCKED_WITH_COUNT", "<yellow>未达成（{count} 个条件）</yellow>");
    }

    public String hoverRequirementsHeader() {
        return cfg().getMessage("HOVER_REQUIREMENTS_HEADER", "<gold>要求:</gold>");
    }

    public String hoverSeparator() {
        return cfg().getMessage("HOVER_SEPARATOR", "<dark_gray>──────────────</dark_gray>");
    }

    // ── 按钮 fallback ──────────────────────────────────

    public String btnPrevPage() {
        return cfg().getMessage("BTN_PREV_PAGE", "上一页");
    }

    public String btnNextPage() {
        return cfg().getMessage("BTN_NEXT_PAGE", "下一页");
    }

    public String btnClose() {
        return cfg().getMessage("BTN_CLOSE", "关闭");
    }

    public String btnBack() {
        return cfg().getMessage("BTN_BACK", "返回上级");
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
