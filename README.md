# BakaAchievements

> Paper 1.21.11 自定义成就插件 — 箱子 GUI · 原版同步 · 异步条件评估 · MiniMessage & PlaceholderAPI

## 功能

| # | 特性 | 说明 |
|---|---|---|
| 1 | 高性能低占用 | 异步条件评估 + 内存 LRU 缓存 + 脏标记批量刷盘 |
| 2 | 异步检查 | 主线程 PAPI 解析 → 异步逻辑判断 → 主线程解锁，降低 TPS 影响 |
| 3 | 箱子 GUI | 原生 InventoryHolder，支持分页/面包屑/进度条/已解锁附魔光效 |
| 4 | 原版成就同步 | 监听 `PlayerAdvancementDoneEvent`，镜像到 `vanilla.*` 虚拟分类 |
| 5 | 自定义成就 | YAML 配置，5 层嵌套分类，`material` 必填（缺则 ERROR + 跳过） |
| 6 | 聊天广播替换 | 取消原版广播，PAPI + MiniMessage 模板自定义广播 |
| 7 | 分类进度 | 每个分类显示 `已完成/总数` + 字符进度条 |
| 8 | PAPI + MiniMessage | 文本管线：PAPI → MiniMessage，全量 `messages.yml` 可自定义 |
| 9 | 外部 API | `BakaApi` 15+ 方法 + 自定义条件类型扩展 + Bukkit Events |
| 10 | LuckPerms 兼容 | 纯 Bukkit Permission，自动兼容 LP |
| 11 | 热重载 | `/bac reload [config/message]` 无需重启 |
| 12 | 权限节点 | `bakaachievements.admin` / `.player` / `.category.<path>` / `.achievement.<path>` |

## 依赖

- **Paper 1.21.11**+
- **Java 21**+
- （可选）**PlaceholderAPI 2.11.6**+

## 命令

根命令 `/bakaachievements`，别名 `bac` / `bakaachieve` / `bachieve`。

### 玩家命令 (`bakaachievements.player`)

| 命令 | 说明 |
|---|---|
| `/bac` | 打开成就箱子 GUI（根分类） |
| `/bac search <关键词>` | 模糊搜索成就（按名称/显示名匹配） |
| `/bac check <玩家名称>` | 查看某玩家已解锁成就列表 |

### 管理员命令 (`bakaachievements.admin`)

| 命令 | 说明 |
|---|---|
| `/bac set <玩家> <节点> <true/false>` | 强制设置成就状态 |
| `/bac enable <节点>` | 启用被禁用的成就/分类 |
| `/bac disable <节点>` | 禁用成就/分类（从 GUI 隐藏整子树） |
| `/bac reload [config/message]` | 重载配置/语言文件（不指定则都重载） |

## 配置文件

### `config.yml`
```yaml
debug: false
async:
  scan-interval-ticks: 600      # 30s 定时扫描
  thread-pool-size: 2
chat:
  override-vanilla: true
  format: "<gold>%player_name%</gold> <gray>解锁了成就</gray> <green>%display%</green>"
storage:
  flush-interval-ticks: 600     # 30s 批量刷盘
  cache-evict-after-offline-minutes: 5
gui:
  rows: 6
  fill-empty: true
permission:
  player-default: true
```

### `achievements.yml`
```yaml
customAchievements:
  testCategory:
    type: "CATEGORY"
    name: "test"
    display: "<rainbow>测试分类</rainbow>"
    permission: false
    son:
      test_ach_1:
        type: "ACHIEVEMENT"
        name: "test_ach_1"
        display: "<green>测试成就一</green>"
        material: "DIAMOND"       # 必填！缺失则 ERROR + 跳过
        permission: true
        auto: true
        conditions:
          a:
            type: ">="
            target: "%player_health%"
            current: "20"
```

### 条件操作符
| 操作符 | 说明 | 适用 |
|---|---|---|
| `=` / `>=` / `<=` / `>` / `<` | 数值/字符串比较 | 数字 / 字符串 |
| `|=` / `|>=` / `|<=` / `|>` / `|<` | 同上，current 四舍五入 | 数字 |
| `|=` | 字符串忽略大小写相等 | 字符串 |
| `!` 前缀 | 取反（如 `!=`） | 全部 |
| `hasPermission` | 检查权限节点（忽略 current 值但必须存在） | — |

## 占位符（需 PlaceholderAPI）

| 占位符 | 说明 |
|---|---|
| `%bakaachievements_version%` | 插件版本号 |
| `%bakaachievements_count_total%` | 成就总数 |
| `%bakaachievements_count_done%` | 已达成数量 |
| `%bakaachievements_unlocked_<node>%` | 指定成就是否达成（`true`/`false`） |

## API

```java
BakaApi api = BakaAchievements.getInstance().getApi();

// 查询
boolean       t = api.isUnlocked(uuid, "test.cus_1");
long          t = api.getAchieveTime(uuid, "test.cus_1");
int           n = api.getUnlockedCount(uuid);
Optional<Node> n = api.getNode("test.cus_1");

// 写入
api.setStatus(uuid, "test.cus_1", true);

// 扩展自定义条件操作符
api.registerConditionType("playedTime", ctx -> {
    return ctx.targetResolved().equals("100");
});

// Bukkit 事件
// AchievementUnlockEvent (可取消) / AchievementUpdateEvent
```

## 目录结构

```
src/main/java/cc/oniacute/plugin/bakaachievements/
├── BakaAchievements.java             # 插件入口
├── achievement/                       # 成就数据模型 + 加载 + 进度
│   ├── Achievement.java              # record: nodePath/name/display/material/...
│   ├── Category.java                 # 分类嵌套 + 递归计数
│   ├── AchievementNode.java          # sealed interface
│   ├── AchievementRegistry.java      # ConcurrentHashMap 索引
│   ├── AchievementLoader.java        # YAML 解析 + material 必填校验
│   ├── PlayerAchievementData.java    # 单玩家状态
│   ├── ProgressService.java          # 主线程 PAPI → 异步评估 → 主线程解锁
│   └── condition/                    # 条件解析/评估/数据类型
├── vanilla/                           # 原版成就同步
│   ├── VanillaSyncService.java       # Advancement → 镜像节点
│   └── VanillaTreeBuilder.java       # 虚拟分类树构建
├── storage/                           # 数据存储
│   ├── YamlPlayerDataStorage.java    # playerdata/<uuid>.yml + LRU 缓存
│   └── DisabledRegistry.java         # 禁用节点注册表
├── gui/                               # 箱子 GUI
│   ├── ItemFactory.java              # material + 附魔光效
│   ├── MenuController.java           # 点击事件分发
│   └── view/                         # CategoryMenu / DetailMenu / SearchMenu
├── command/
│   └── BakaCommand.java              # TabExecutor + 6 子命令
├── chat/
│   └── ChatBroadcastService.java     # 原版广播替换
├── hook/
│   ├── PapiHook.java                 # PAPI 软依赖
│   ├── BakaAchievementsExpansion.java
│   └── PermissionResolver.java
├── config/
│   ├── ConfigManager.java            # 4 份 YAML 统一管理
│   └── Messages.java                 # 全语言文本门面
├── api/
│   ├── BakaApi.java                  # 15+ 方法公共接口
│   ├── BakaAchievementsApi.java
│   ├── event/                        # Bukkit 事件
│   └── condition/                    # 自定义条件扩展点
└── util/
    ├── AsyncExecutor.java
    ├── PathUtil.java
    └── ProgressBarUtil.java
```

## 构建

```bash
# 编译
./gradlew build

# 产出
# build/libs/BakaAchievements-1.0.0.jar

# 本地测试
./gradlew runServer
```

## License

MIT
