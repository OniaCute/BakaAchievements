# BakaAchievements

> Paper 1.21.4 插件开发模板，内置 MiniMessage & PlaceholderAPI 支持

## 功能

-   **MiniMessage** —— 开箱即用的 Adventure MiniMessage 工具类
-   **PlaceholderAPI** —— 软依赖支持，内置示例 PAPI 扩展
-   **简洁 API** —— 提供 `BakaApi` 接口供外部插件调用
-   **命令系统** —— 基础的 `/bakaachievements` 命令模板
-   **组件工具** —— `ComponentUtil` 快速构建 Adventure Component

## 依赖

-   Paper 1.21.4+
-   Java 21+
-   （可选）PlaceholderAPI 2.11.6+

## 命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/bakaachievements` | - | 查看插件信息 |
| `/bakaachievements reload` | `bakaachievements.admin` | 重载配置 |
| `/bakaachievements info` | - | 查看插件信息 |

## 占位符（需 PlaceholderAPI）

| 占位符 | 说明 |
|---|---|
| `%bakaachievements_version%` | 插件版本号 |
| `%bakaachievements_placeholderapi%` | PAPI 是否可用 |

## 开发

```java
// 获取 API
BakaApi api = BakaAchievements.getInstance().getApi();

// 解析 MiniMessage
Component msg = api.parseMiniMessage("<red>Hello World!</red>");

// 工具类
MiniMessageUtil.send(player, "<green>Welcome!</green>", plugin.getMiniMessage());
Component text = ComponentUtil.mini("<gradient:red:blue>Gradient Text</gradient>");
```

## License

MIT
