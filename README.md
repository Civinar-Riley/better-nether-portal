# 更好的地狱门（better_nether_portal）

Minecraft **1.21.1 / NeoForge** 模组——让地狱门不再限于矩形。

用任意闭合形状的框架点亮地狱门：圆环、十字、爱心、字母，只要内部连通闭合都能成门；传送时对面没有现成的门，还会按你这扇门的形状在对面重建，并且成对的门会记住彼此。

## 功能

- **任意闭合形状**：圆环、十字、爱心、字母……只要内部连通闭合，用打火石/火焰弹点亮。
- **竖直 X / Z 平面**：门必须立在竖直平面内；默认内径 ≤64 格、包围盒 ≤21。
- **框架标签**：`#better_nether_portal:portal_frames` 默认含黑曜石 + 哭泣黑曜石；数据包可追加模组方块。
- **对面复制门形**：目标维度没有现成门时，按源门形状寻找安全落点重建；失败回退原版 2×3。
- **成对持久链接**：成对门记在 `better_nether_portal_links` SavedData 里，附近有别的门也不会互相抢人；拆门自动失效重配。

## 安装

- 前置：Minecraft **1.21.1**、NeoForge **21.1.252** 及以上（客户端与服务端都要装）。
- 把 `build/libs/better_nether_portal-<版本>.jar` 放进 `mods/` 目录。

## 配置

服务端配置 `config/better_nether_portal-server.toml`：

| 键 | 默认 | 说明 |
|---|---|---|
| `portal.max_interior_cells` | 64 | 单扇门内部最大格数（1–441） |
| `portal.max_span` | 21 | 内部包围盒单轴最大跨度（1–21） |
| `portal.copy_shape_to_destination` | true | 对面新建门时复制源门形状 |
| `portal.persistent_links` | true | 成对门持久链接 |

## 构建与测试

```bash
./gradlew build              # 产物 build/libs/better_nether_portal-1.0.0.jar
./gradlew runGameTestServer  # GameTest（12 个：形状/标签/蓝图/链接/mixin）
```

开发客户端：`./gradlew runClient`（需正版登录）。

## 兼容性

- 只支持竖直 X/Z 平面。

## 许可

**All Rights Reserved** © Riley
