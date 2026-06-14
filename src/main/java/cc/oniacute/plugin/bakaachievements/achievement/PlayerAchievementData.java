package cc.oniacute.plugin.bakaachievements.achievement;

import java.util.HashMap;
import java.util.Map;

/**
 * 单玩家的成就状态数据，兼含玩家偏好设置。
 * <p>
 * 以节点路径为键，记录每个成就的达成状态和达成时间。
 * 线程安全由外部调用方（ProgressService）保证；
 * dirty 标记用于驱动存储层按需写盘。
 * </p>
 */
public class PlayerAchievementData {

    // ── 成就状态 ────────────────────────────────────────────

    /** nodePath → AchievementStatus */
    private final Map<String, AchievementStatus> achievements = new HashMap<>();

    // ── 玩家偏好 ────────────────────────────────────────────

    /**
     * 是否接收"自己达成成就"的提示广播（默认开启）。
     * 对应 {@code /bac tips on/off self}。
     */
    private boolean tipsSelf = true;

    /**
     * 是否接收"他人达成成就"的提示广播（默认开启）。
     * 对应 {@code /bac tips on/off others}。
     */
    private boolean tipsOthers = true;

    // ── 脏标记 ──────────────────────────────────────────────

    /**
     * 有未写入磁盘的变更时为 {@code true}。
     * volatile 保证多线程可见性（写操作仍需外部同步）。
     */
    private volatile boolean dirty;

    // ── 内部 Record ─────────────────────────────────────────

    /**
     * 单个成就的状态记录（不可变）。
     */
    public record AchievementStatus(boolean unlocked, long achieveTime) {

        /** 未解锁的默认状态单例 */
        public static final AchievementStatus LOCKED = new AchievementStatus(false, -1L);

        /**
         * 构造已解锁状态。
         *
         * @param time 解锁时间戳（毫秒）
         */
        public static AchievementStatus unlocked(long time) {
            return new AchievementStatus(true, time);
        }
    }

    // ── 成就状态操作 ─────────────────────────────────────────

    /**
     * 获取成就状态，未达成返回 {@link AchievementStatus#LOCKED}。
     *
     * @param nodePath 成就节点路径
     */
    public AchievementStatus getStatus(String nodePath) {
        return achievements.getOrDefault(nodePath, AchievementStatus.LOCKED);
    }

    /**
     * 设置成就状态并标记脏。
     *
     * @param nodePath 成就节点路径
     * @param status   新状态
     */
    public void setStatus(String nodePath, AchievementStatus status) {
        achievements.put(nodePath, status);
        dirty = true;
    }

    /**
     * 返回所有成就状态的浅拷贝，不影响内部结构。
     */
    public Map<String, AchievementStatus> getAll() {
        return new HashMap<>(achievements);
    }

    /**
     * 检查指定成就是否已达成。
     *
     * @param nodePath 成就节点路径
     */
    public boolean isUnlocked(String nodePath) {
        return getStatus(nodePath).unlocked();
    }

    /**
     * 统计已达成成就数量。
     */
    public int countUnlocked() {
        return (int) achievements.values().stream()
                .filter(AchievementStatus::unlocked)
                .count();
    }

    // ── 玩家偏好操作 ─────────────────────────────────────────

    /**
     * 是否接收"自己达成成就"的提示广播（默认 {@code true}）。
     * 对应 {@code /bac tips on self}。
     */
    public boolean isTipsSelf() {
        return tipsSelf;
    }

    /**
     * 设置是否接收"自己达成成就"的提示广播，并标记脏。
     *
     * @param value {@code true} 表示接收，{@code false} 表示屏蔽
     */
    public void setTipsSelf(boolean value) {
        this.tipsSelf = value;
        dirty = true;
    }

    /**
     * 是否接收"他人达成成就"的提示广播（默认 {@code true}）。
     * 对应 {@code /bac tips on others}。
     */
    public boolean isTipsOthers() {
        return tipsOthers;
    }

    /**
     * 设置是否接收"他人达成成就"的提示广播，并标记脏。
     *
     * @param value {@code true} 表示接收，{@code false} 表示屏蔽
     */
    public void setTipsOthers(boolean value) {
        this.tipsOthers = value;
        dirty = true;
    }

    // ── 脏标记操作 ───────────────────────────────────────────

    /** 是否存在未持久化的变更。 */
    public boolean isDirty() {
        return dirty;
    }

    /** 由存储层在写盘成功后调用，清除脏标记。 */
    public void markClean() {
        dirty = false;
    }
}
