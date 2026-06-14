# BakaAchievements

> Paper 1.21.11 自定义成就插件 — 箱子 GUI · 原版同步 · 成就分享 · HeadDatabase 头颅 · MiniMessage & PlaceholderAPI

## 功能

| # | 特性 | 说明 |
|---|---|---|
| 1 | 高性能低占用 | 异步条件评估 + 内存 LRU 缓存 + 脏标记批量刷盘 |
| 2 | 异步检查 | 主线程 PAPI 解析 → 异步逻辑判断 → 主线程解锁，降低 TPS 影响 |
| 3 | 箱子 GUI | 原生 InventoryHolder，6行×9列，支持分页/面包屑/进度条/已解锁附魔光效/定时刷新/断线重连恢复 |
| 4 | 原版成就同步 | 监听 `PlayerAdvancementDoneEvent`，镜像到 `vanilla.*` 虚拟分类 |
| 5 | 自定义成就 | YAML 配置，5 层嵌套分类，3 种节点类型（CATEGORY / ACHIEVEMENT / MIXED），`material` 必填（缺则 ERROR + 跳过） |
| 6 | MIXED 混合节点 | 既是分类又有成就条件，达成后仍可进入子分类浏览 |
| 7 | 聊天广播替换 | 取消原版广播，MiniMessage 模板自定义广播 |
| 8 | 成就分享 | GUI 中 Shift+左键分享成就到聊天，带悬停卡片（hover）和冷却系统 |
| 9 | 成就命令执行 | 达成时可执行控制台命令（支持占位符 `{player}` `{achievement}`）及 `[close]` / `[previous]` / `[refresh]` 内置指令 |
| 10 | 分类进度 | 每个分类显示 `已完成/总数` + 可配置样式的字符进度条（颜色/符号/长度均可自定义） |
| 11 | PAPI + MiniMessage | 文本管线：PAPI → MiniMessage，全量 `messages.yml` 可自定义 |
| 12 | HeadDatabase 支持 | 成就图标可使用 HDB 自定义头颅（hdbId ≥ 0 时自动使用） |
| 13 | 外部 API | `BakaApi` 17+ 方法 + 自定义条件类型扩展 + Bukkit Events |
| 14 | LuckPerms 兼容 | 纯 Bukkit Permission，自动兼容 LP |
| 15 | 热重载 | `/bac reload [config/message/achievement/vanilla]` 无需重启 |
| 16 | 权限节点 | `bakaachievements.admin` / `.player` / `.share` / `.share.cooldown` / `.category.<path>` / `.achievement.<path>` |

## 依赖

- **Paper 1.21.11**+
- **Java 21**+
- （可选）**PlaceholderAPI 2.11.6**+
- （可选）**HeadDatabase 1.3.2**+

## 命令

根命令 `/bakaachievements`，别名 `bac` / `bakaachieve` / `bachieve`。

### 玩家命令 (`bakaachievements.player`)

| 命令 | 说明 |
|---|---|
| `/bac` | 打开成就箱子 GUI（根分类） |
| `/bac search <关键词>` | 模糊搜索成就（按名称/显示名/描述/条件显示名匹配） |
| `/bac check <玩家名称>` | 以只读模式查看某玩家已解锁成就状态 |
| `/bac tips` | 切换是否接收他人成就分享提示 |
| `/bac info` | 查看插件版本/成就总数/PAPI 挂载状态 |

### 管理员命令 (`bakaachievements.admin`)

| 命令 | 说明 |
|---|---|
| `/bac set <玩家> <节点> <true/false>` | 强制设置成就状态 |
| `/bac enable <节点>` | 启用被禁用的成就/分类 |
| `/bac disable <节点>` | 禁用成就/分类（从 GUI 隐藏整子树） |
| `/bac reload [config/message/achievement/vanilla]` | 重载指定配置（不指定则全部重载） |

## 配置文件

### `config.yml`

```yaml
debug: false

async:
  scan-interval-ticks: 60        # 成就条件扫描间隔（3秒）
  thread-pool-size: 2

storage:
  flush-interval-ticks: 600      # 批量刷盘间隔（30秒）
  cache-evict-after-offline-minutes: 5

chat:
  override-vanilla: true
  format: "<gold>{player_displayname}</gold> <gray>解锁了成就</gray> <green>{display}</green>"

share:
  enabled: true
  cooldown-seconds: 10           # 单成就分享冷却
  global-cooldown-seconds: 5     # 全局分享冷却
  allow-locked: true             # 是否允许分享未解锁成就

gui:
  rows: 6
  fill-empty: true
  refresh-interval-ticks: 600    # GUI 自动刷新间隔
  vanilla-category-first: true   # 原版成就分类排在首位
  click-sound: BLOCK_LEVER_CLICK # 点击音效（NONE 禁用）
  border-material: GRAY_STAINED_GLASS_PANE
  empty-search:
    material: PAPER
  empty-category:
    material: BARRIER
  action-bar:
    filler:
      material: GRAY_STAINED_GLASS_PANE
    prev:
      material: ARROW
    next:
      material: ARROW
    close:
      material: BARRIER
    stats:
      material: BOOK
    special:
      material: GRAY_STAINED_GLASS_PANE
    back:
      material: OAK_DOOR
  progress-bar:
    left: "["
    filled: "|"
    empty: "|"
    right: "]"
    length: 20
    left-color: "white"
    filled-color: "green"
    empty-color: "dark_gray"
    right-color: "white"
    percent-color: "aqua"
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
      test_mixed:
        type: "MIXED"               # 混合节点：既是分类又是成就
        name: "test_mixed"
        display: "<gold>混合节点</gold>"
        material: "NETHER_STAR"
        permission: false
        auto: false
        conditions:
          a:
            type: ">="
            display: "生命值 ≥ 20"   # 条件显示名（可选，支持 MiniMessage）
            target: "%player_health%"
            current: "20"
        son:
          sub_ach:
            type: "ACHIEVEMENT"
            name: "sub_ach"
            display: "<green>子成就</green>"
            material: "EMERALD"
            permission: false
            auto: true
            conditions:
              a:
                type: ">="
                target: "%player_level%"
                current: "30"
      test_ach_1:
        type: "ACHIEVEMENT"
        name: "test_ach_1"
        display: "<green>测试成就一</green>"
        material: "DIAMOND"         # 必填！缺失则 ERROR + 跳过
        hdbId: -1                   # HeadDatabase ID（-1 表示使用原版材质）
        permission: true
        auto: true
        descriptions:               # 成就描述（支持 MiniMessage）
          - "<gray>这是一个测试成就</gray>"
          - "<dark_gray>详细描述第二行</dark_gray>"
        commands:                   # 达成时执行的命令
          - "[close]"
          - "give {player} diamond 1"
        conditions:
          a:
            type: ">="
            display: "生命值 ≥ 20"
            target: "%player_health%"
            current: "20"
          b:
            type: "hasPermission"
            display: "拥有 VIP 权限"
            target: "vip.permission"
            current: "true"
```

### 节点类型

| 类型 | 说明 |
|---|---|
| `CATEGORY` | 纯分类节点，只包含子节点 |
| `ACHIEVEMENT` | 纯成就节点，有条件组 + 可达成 |
| `MIXED` | 混合节点，既有条件可达成，又包含子分类 |

### 条件操作符

| 操作符 | 说明 | 适用 |
|---|---|---|
| `=` / `>=` / `<=` / `>` / `<` | 数值/字符串比较 | 数字 / 字符串 |
| `|=` / `|>=` / `|<=` / `|>` / `|<` | 同上，current 四舍五入 | 数字 |
| `|=` | 字符串忽略大小写相等 | 字符串 |
| `!` 前缀 | 取反（如 `!=`） | 全部 |
| `hasPermission` | 检查权限节点（target=权限节点，current 值任意但必须存在） | — |

### `vanilla.yml` — 原版成就覆盖

```yaml
advancements:
  minecraft:story/mine_stone:
    display: "石器时代"
    descriptions:
      - "挖掘一块石头"
      - "这是获取圆石的第一步"
  minecraft:story/upgrade_tools:
    display: "获得升级"
    descriptions:
      - "制作一把石镐"
```

原版成就通过 `vanilla.yml` 提供中文显示名和多行描述覆盖，不配置则使用默认英文名称。

## 成就达成命令

成就达成时可执行命令列表，支持以下特殊命令和占位符：

| 命令 | 说明 |
|---|---|
| `[close]` | 关闭玩家当前打开的容器界面 |
| `[previous]` | 通过菜单历史栈返回上一级菜单 |
| `[refresh]` | 刷新当前打开的菜单 |
| 其他命令 | 以控制台身份执行 |

| 占位符 | 说明 |
|---|---|
| `{player}` | 玩家名 |
| `{player_displayname}` | 玩家显示名（纯文本） |
| `{achievement}` | 成就内部名称 |
| `{achievement_display}` | 成就显示名（纯文本） |

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

// ── 基础信息 ──────────────
String       version = api.getVersion();
boolean      papi    = api.isPlaceholderApiAvailable();
Component    comp    = api.parseMiniMessage("<green>hello</green>");
api.logInfo("something happened");

// ── 查询 ────────────────────
boolean             t = api.isUnlocked(uuid, "test.cus_1");
long                t = api.getAchieveTime(uuid, "test.cus_1");
int                 n = api.getUnlockedCount(uuid);
Collection<String>  paths = api.listAchievementPaths();
Collection<String>  cats  = api.listCategoryPaths();
Optional<Node>      node  = api.getNode("test.cus_1");

// ── 写入 ────────────────────
api.setStatus(uuid, "test.cus_1", true);         // 异步写盘，不触发事件
api.unlock(uuid, "test.cus_1");                  // 异步写盘 + 触发 AchievementUpdateEvent

// ── 扩展自定义条件操作符 ──
api.registerConditionType("playedTime", ctx -> {
    return ctx.targetResolved().equals("100");
});

// Bukkit 事件
// AchievementUnlockEvent (可取消) / AchievementUpdateEvent
```

## 目录结构

```
src/main/java/cc/oniacute/plugin/bakaachievements/
├── BakaAchievements.java              # 插件入口（JavaPlugin + Listener）
├── bootstrap/                          # 启动/关闭生命周期管理
│   ├── PluginBootstrap.java           # 服务构建 & 调度任务
│   ├── ServiceContainer.java          # 可变服务注册表
│   └── AchievementReloadService.java  # 热重载协调
├── achievement/                        # 成就数据模型 + 加载 + 进度
│   ├── AchievementNode.java           # sealed interface（3 种子类型）
│   ├── Achievement.java               # 纯成就 record
│   ├── Category.java                  # 纯分类 record（递归计数）
│   ├── MixedNode.java                 # 混合节点 record（分类+成就）
│   ├── AchievementRegistry.java       # ConcurrentHashMap 索引 + 根节点
│   ├── AchievementLoader.java         # YAML 解析 + material 必填校验
│   ├── AchievementTreeService.java    # 树遍历/可见性/进度统计
│   ├── PlayerAchievementData.java     # 单玩家状态
│   ├── ProgressService.java           # 主线程 PAPI → 异步评估 → 主线程解锁
│   └── condition/                     # 条件解析/评估/数据类型
│       ├── Condition.java             # record
│       ├── ConditionGroup.java        # record（条件列表）
│       ├── ConditionParser.java       # YAML → 条件对象
│       ├── ConditionEvaluator.java    # 评估 + 进度计算
│       └── ConditionContext.java      # 上下文 record
├── vanilla/                            # 原版成就同步
│   ├── VanillaSyncService.java        # Advancement → 镜像节点 + 玩家加入重同步
│   └── VanillaTreeBuilder.java        # 虚拟分类树构建
├── storage/                            # 数据存储
│   ├── PlayerDataStorage.java         # 存储接口
│   ├── PlayerDataRepository.java      # 仓库（加载/缓存/写回）
│   ├── YamlPlayerDataStorage.java     # playerdata/<uuid>.yml + LRU 缓存
│   └── DisabledRegistry.java         # 禁用节点注册表 + 祖先检查
├── gui/                                # 箱子 GUI（6行×9列 = 54槽）
│   ├── ItemFactory.java               # material/hdbId + 附魔光效 + Lore 构建
│   ├── MenuController.java            # 点击事件分发 + 菜单渲染 + 主线程安全
│   ├── MenuController.java            # 事件分发（InventoryClick/Drag/MoveItem/Close）
│   ├── MenuSessionService.java        # 菜单会话持久化（重连恢复）
│   ├── MenuSession.java               # 会话数据类型
│   ├── MenuRefreshScheduler.java      # GUI 定时自动刷新
│   ├── MenuStack.java                 # 菜单历史栈（返回上级导航）
│   └── ActionBarRenderer.java         # GUI 底部操作栏渲染（返回/翻页/Stats）
├── command/
│   ├── BakaCommand.java               # TabExecutor + 7 子命令分发
│   └── AchievementCommandRunner.java  # 成就达成时执行命令列表
├── chat/
│   ├── ChatBroadcastService.java      # 原版广播替换（MiniMessage 模板）
│   └── AchievementShareService.java   # Shift+左键分享成就 + 悬停卡片
├── hook/
│   ├── PapiHook.java                  # PAPI 软依赖注册
│   ├── BakaAchievementsExpansion.java # PAPI 扩展实现
│   ├── PermissionResolver.java        # 权限检查门面
│   └── HdbHook.java                   # HeadDatabase 软依赖 + 头颅获取
├── config/
│   ├── ConfigManager.java             # 5 份 YAML 统一管理（hconfig + vanillla + disabled）
│   └── Messages.java                  # 全语言文本门面（60+ 消息键）
├── api/
│   ├── BakaApi.java                   # 17+ 方法公共接口
│   ├── BakaAchievementsApi.java       # 接口实现
│   ├── event/                         # Bukkit 事件
│   │   ├── AchievementUnlockEvent.java
│   │   └── AchievementUpdateEvent.java
│   └── condition/
│       └── ConditionType.java         # 自定义条件扩展点
└── util/
    ├── AsyncExecutor.java             # 异步线程池（守护 + 拒绝策略）
    ├── PathUtil.java                  # 路径常量 + ROOT 标识
    ├── ProgressBarUtil.java           # 进度条样式 + 自定义渲染
    ├── PlaceholderResolver.java       # 占位符解析门面
    ├── MiniMessageUtil.java           # MiniMessage 解析工具
    └── ComponentUtil.java             # Adventure Component 辅助方法
```

### GUI 布局（6行 × 9列 = 54槽）

```
行1 ( 0- 8): ╔════════════ 周圈玻璃板 ════════════╗
行2 ( 9-17): 玻璃 | 内容[10-16] | 玻璃
行3 (18-26): 玻璃 | 内容[19-25] | 玻璃
行4 (27-35): 玻璃 | 内容[28-34] | 玻璃
行5 (36-44): ╚════════════ 周圈玻璃板 ════════════╝
行6 (45-53): [返回上级] [填] [填] [填] [Stats] [填] [填] [上一页] [下一页]
```

| 槽位 | 说明 |
|---|---|
| 45 | 返回上级（有上级时）/上一页（第二页起） |
| 49 | Stats — 显示当前分类名 + 进度；左键返回根目录，Shift+左键分享总进度 |
| 52 | 上一页（第二页起） |
| 53 | 下一页（有后续页时） |

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
