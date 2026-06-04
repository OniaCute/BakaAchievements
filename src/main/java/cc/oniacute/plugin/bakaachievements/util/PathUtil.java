package cc.oniacute.plugin.bakaachievements.util;

/**
 * 节点路径工具类。
 * <p>
 * 处理成就节点路径的解析、校验和面包屑生成。
 * 路径格式：{@code 分类.子分类...成就名称}，最多 5 层。
 * </p>
 */
public final class PathUtil {

    private PathUtil() {}

    /** 根分类路径（虚拟） */
    public static final String ROOT = "__root__";

    /** 原版成就根分类路径 */
    public static final String VANILLA_ROOT = "vanilla";

    /** 最大嵌套深度 */
    public static final int MAX_DEPTH = 5;

    /**
     * 校验节点路径是否合法。
     *
     * @param path 节点路径
     * @return {@code true} 表示合法
     */
    public static boolean isValid(String path) {
        if (path == null || path.isEmpty()) return false;
        String[] parts = path.split("\\.");
        if (parts.length < 1 || parts.length > MAX_DEPTH) return false;
        for (String part : parts) {
            if (part.isEmpty() || !part.matches("[a-zA-Z0-9_]+")) return false;
        }
        return true;
    }

    /**
     * 获取路径的深度（以 {@code .} 分隔的段数）。
     */
    public static int depth(String path) {
        return path.split("\\.").length;
    }

    /**
     * 获取父路径（去掉最后一段）。
     *
     * @param path 节点路径
     * @return 父路径；若已是根则返回 {@link #ROOT}
     */
    public static String parent(String path) {
        int idx = path.lastIndexOf('.');
        return idx > 0 ? path.substring(0, idx) : ROOT;
    }

    /**
     * 获取路径的末段（最后一段）。
     */
    public static String lastName(String path) {
        int idx = path.lastIndexOf('.');
        return idx > 0 ? path.substring(idx + 1) : path;
    }

    /**
     * 以数组形式获取面包屑路径——从根到当前节点每段逐步拼接。
     * <p>
     * 例如 {@code "test.cus_1"} → {@code ["test", "test.cus_1"]}
     * </p>
     */
    public static String[] breadcrumbs(String path) {
        String[] parts = path.split("\\.");
        String[] crumbs = new String[parts.length];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
            crumbs[i] = sb.toString();
        }
        return crumbs;
    }
}
