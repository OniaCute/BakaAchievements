package cc.oniacute.plugin.bakaachievements.achievement;

import java.util.HashMap;
import java.util.Map;

/**
 * 单玩家的成就状态数据。
 * <p>
 * 以节点路径为键，记录每个成就的达成状态和达成时间。
 * 线程安全由外部调用方（ProgressService）保证。
 * </p>
 */
public class PlayerAchievementData {

    private final Map<String, AchievementStatus> achievements = new HashMap<>();
    private volatile boolean dirty;

    /**
     * 单个成就的状态记录。
     */
    public record AchievementStatus(boolean unlocked, long achieveTime) {
        public static final AchievementStatus LOCKED = new AchievementStatus(false, -1L);

        public static AchievementStatus unlocked(long time) {
            return new AchievementStatus(true, time);
        }
    }

    /**
     * 获取成就状态，未达成返回 LOCKED。
     */
    public AchievementStatus getStatus(String nodePath) {
        return achievements.getOrDefault(nodePath, AchievementStatus.LOCKED);
    }

    /**
     * 设置成就状态。
     */
    public void setStatus(String nodePath, AchievementStatus status) {
        achievements.put(nodePath, status);
        dirty = true;
    }

    /**
     * 获取所有状态（浅拷贝）。
     */
    public Map<String, AchievementStatus> getAll() {
        return new HashMap<>(achievements);
    }

    /**
     * 检查是否已达成。
     */
    public boolean isUnlocked(String nodePath) {
        return getStatus(nodePath).unlocked();
    }

    /**
     * 统计已达成数量。
     */
    public int countUnlocked() {
        return (int) achievements.values().stream().filter(AchievementStatus::unlocked).count();
    }

    /** 脏标记：有未写入磁盘的变更 */
    public boolean isDirty() { return dirty; }
    public void markClean() { dirty = false; }
}
