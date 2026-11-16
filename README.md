<div align="center">

<h1>更好的地狱门</h1>

**Minecraft 1.21.1 / NeoForge 模组 · 自由门形 · 跨维度复制 · 持久配对**

[![Release](https://img.shields.io/github/v/release/Civinar-Riley/better-nether-portal?label=Release&style=flat-square)](https://github.com/Civinar-Riley/better-nether-portal/releases/latest)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.252%2B-EA8220?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-4373A6?style=flat-square)
![Side](https://img.shields.io/badge/Side-Client%20%26%20Server-7B6BB5?style=flat-square)
[![CI](https://img.shields.io/github/actions/workflow/status/Civinar-Riley/better-nether-portal/ci.yml?branch=main&label=CI&style=flat-square)](https://github.com/Civinar-Riley/better-nether-portal/actions/workflows/ci.yml)

[下载最新版](https://github.com/Civinar-Riley/better-nether-portal/releases/latest) · [报告问题](https://github.com/Civinar-Riley/better-nether-portal/issues)

[项目简介](#-项目简介) · [功能特性](#-功能特性) · [安装](#-安装) · [使用指南](#-使用指南) · [配置](#-配置) · [限制与兼容](#-限制与兼容) · [开发与测试](#-开发与测试) · [版本历史](#-版本历史)

</div>

---

## 📖 项目简介

**更好的地狱门**是 Minecraft 1.21.1 的 NeoForge 模组。圆环、十字、爱心、字母……只要框架在竖直平面内闭合、内部连通，就可以点亮。

目标维度没有可复用的门时，模组会尝试复制源门形状；传送成功后，两端建立持久配对，减少附近传送门之间的串门。模组 ID 为 `better_nether_portal`。

## ✨ 功能特性

### 造门与传送

| 功能 | 行为 |
| :--- | :--- |
| 🔥 自由门形 | 支持竖直 X/Z 平面内的闭合连通形状，最小内部仅需 1 格。 |
| 🧱 多种框架 | 默认支持黑曜石和哭泣黑曜石；数据包可追加框架方块。 |
| 🪞 复制出口 | 没有可复用的目标门时，尝试按源门轮廓重建。 |
| 🔗 持久配对 | 成功传送后保存双向链接；目标门拆除后重新匹配，扩建门后更新锚点。 |
| 🧍 出口检查 | 按实体尺寸选择门内空间；小门尝试前后有支撑的空位。 |
| 💬 失败提示 | 玩家传送失败时，通过操作栏说明出口堵塞、边界、尺寸或建门权限问题，提供中英文文本。 |

### 服务端与整合包

| 入口 | 能力 |
| :--- | :--- |
| 🔧 服务端配置 | 调整门内部格数、跨度上限，以及复制门形和持久配对开关。 |
| 📦 方块标签 | 通过 `#better_nether_portal:portal_frames` 扩展框架材料。 |
| 🛡 建门事件 | 在修改方块前检查点火、复制及原版回退，保护模组可取消整个创建过程。 |

## 📦 安装

| 环境 | 要求 |
| :--- | :--- |
| Minecraft | **1.21.1** |
| 加载器 | **NeoForge 21.1.252 及以上**，对应 Minecraft 1.21.1 |
| Java | **21** |
| 安装位置 | 客户端与服务端都要安装 |

1. 安装对应版本的 NeoForge。
2. 从 [Releases](https://github.com/Civinar-Riley/better-nether-portal/releases/latest) 下载 `better_nether_portal-<版本>.jar`。
3. 将 jar 放入游戏实例或服务器的 `mods/` 目录，启动游戏。

单人游戏安装在当前游戏实例；联机时，服务器与玩家客户端使用同一模组版本。

## 🚀 使用指南

### 第一次造门

1. 在主世界或下界，用黑曜石或哭泣黑曜石搭建一圈**竖直、闭合**的框架。
2. 保持内部空间连通。默认最多 **64 个内部格**，内部宽度和高度分别不超过 **21 格**。
3. 用打火石或火焰弹点火；门点亮后，按原版方式进入。
4. 目标维度没有可用门时，模组尝试复制轮廓。传送成功后，这一对门会记住彼此。

最小门只需 1 个内部格，以及同一平面内上、下、左、右四块框架。供玩家通行的小门应在前后留出净空和落脚处。

### 添加框架材料

在数据包的 `data/better_nether_portal/tags/block/portal_frames.json` 中追加方块 ID，例如允许石砖作为框架：

```json
{
  "replace": false,
  "values": ["minecraft:stone_bricks"]
}
```

默认标签见 [portal_frames.json](./src/main/resources/data/better_nether_portal/tags/block/portal_frames.json)。

## 🔧 配置

默认服务端配置文件：`config/better_nether_portal-server.toml`。单人游戏由当前游戏实例加载，联机以服务端配置为准。

```toml
[portal]
max_interior_cells = 64
max_span = 21
copy_shape_to_destination = true
persistent_links = true
```

| 配置键（位于 `[portal]`） | 默认值 | 说明 |
| :--- | :--- | :--- |
| `max_interior_cells` | `64` | 单扇门内部格数上限，范围 `1–441`。 |
| `max_span` | `21` | 内部包围盒的单轴跨度上限，范围 `1–21`。 |
| `copy_shape_to_destination` | `true` | 需要新建出口时，优先复制源门形状。 |
| `persistent_links` | `true` | 记录并优先使用成对门之间的持久链接。 |

配置定义见 [PortalConfig.java](./src/main/java/net/riley/betterportal/config/PortalConfig.java)。

## 🚧 限制与兼容

- **形状与维度**：点火支持主世界和下界中的竖直 X/Z 平面门；内部必须连通闭合，不支持水平门或不同轴向的门相互穿插。
- **复制与回退**：复制保留轮廓，重建框架统一使用黑曜石。找不到合适复制位置时尝试原版 `2×3` 出口，仍须满足尺寸配置；建门事件被取消后不会继续回退。
- **尺寸调整**：格数与跨度限制同时用于点火、门存活复检和目标门创建。降低上限后，已有超限门会在邻块更新时熄灭。
- **出口与提示**：找不到可容纳实体的落点时取消本次传送。失败提示每位玩家最多每 `100 tick` 显示一次，正常游戏速度下约为 5 秒。
- **整合验证**：建门事件取消行为已通过服务端 GameTest；具体领地模组组合与客户端画面尚未逐一实测。

### 保护模组接口

点火、复制出口及原版回退都会在第一次方块写入前发送 [`PortalCreateEvent`](./src/main/java/net/riley/betterportal/api/PortalCreateEvent.java)。它继承 NeoForge 的 `BlockEvent.PortalSpawnEvent`，已有父事件监听器也可以取消创建。

| 方法 | 提供的信息 |
| :--- | :--- |
| `getPlannedBlocks()` | 全部拟写入位置与状态，包含门格、框架、支撑及净空清理。 |
| `getPortalCells()` | 实际门方块的坐标集合。 |
| `getAxis()` | 门平面的水平轴：`X` 或 `Z`。 |
| `getReason()` | 创建原因：`IGNITION`、`COPY` 或 `FALLBACK`。 |

集合为不可变快照。需要保护整扇门及其周边修改范围时，应检查 `getPlannedBlocks()`；取消后本次创建不写入任何方块。已存在门之间的传送继续使用 NeoForge 的维度传送事件。

## 📁 仓库结构

```text
├── src/main/java/net/riley/betterportal/
│   ├── api/                  # 公开建门事件
│   ├── config/               # 服务端配置
│   ├── gametest/             # 游戏内回归测试
│   ├── mixin/                # 原版点火与传送入口
│   ├── portal/               # 形状扫描、蓝图、配对与出口计算
│   └── spec/                 # 方块标签定义
├── src/main/resources/       # 语言、标签、Mixin 配置和测试结构
├── src/main/templates/       # 模组元数据模板
├── docs/optimization-1.1.0.md # 性能对照与验证范围
├── .github/workflows/        # CI 与 Release 自动化
├── build.gradle             # 构建与开发运行配置
├── gradle.properties        # Minecraft / NeoForge 版本
└── mod.properties           # 模组版本及描述
```

`build/`、`run/` 和 `.gradle/` 为本地产物，不纳入版本控制。

## 🧪 开发与测试

使用 JDK 21，在仓库根目录执行：

```bash
./gradlew build
./gradlew runGameTestServer
```

Windows PowerShell 使用 `./gradlew.bat build` 和 `./gradlew.bat runGameTestServer`；Git Bash 可使用 `bash ./gradlew`。

| 命令 | 用途 |
| :--- | :--- |
| `build` | 构建 jar，输出到 `build/libs/better_nether_portal-<版本>.jar`。 |
| `runGameTestServer` | 运行服务端 GameTest，使用独立目录 `build/gametest/`。 |
| `runClient` | 启动开发客户端。 |
| `runServer` | 启动开发服务端。 |

当前包含 **33 个 GameTest**，覆盖形状、蓝图方块保护、出口碰撞、链接、配置、建门事件、提示节流和 Mixin。`build` 与 `runGameTestServer` 是两个独立任务；推送 `main`、提交 PR 或发布 `v*` 标签时，GitHub Actions 会执行两者。

1.1.0 的 Instabox 测试结果、64/441 格门性能对照及测量方法见 [优化验证](./docs/optimization-1.1.0.md)。

### 版本与发布约定

版本号以 [mod.properties](./mod.properties) 中的 `mod_version` 为准，标签必须为 `v<版本>`。修复递增修订号，新增功能递增次版本号，配置或存档不兼容的改动递增主版本号。

修改版本并完成验证后，提交、推送，再为该提交打标签。发布工作流会核对标签与版本号，构建、运行 GameTest，成功后上传 jar 到对应 Release。流程定义见 [release.yml](./.github/workflows/release.yml)。

## ❓ 常见问题

**为什么对面还是普通的 `2×3` 门？**

目标维度有可用门时会优先复用；需要新建门时，关闭复制或无法找到合适复制位置，都会进入原版矩形回退。

**门只有一格高，也能传送玩家吗？**

小门可以点亮。传送时会检查实体尺寸，并尝试门前后有支撑的空位；出口不足时，玩家留在源维度并收到操作栏提示。

**联机时只在服务端安装可以吗？**

客户端与服务端都需要安装本模组。环境要求见 [安装](#-安装)。

## 🕘 版本历史

<details>
<summary>展开查看已发布版本</summary>

| 发布日期 | 版本 | 主要变化 |
| :--- | :--- | :--- |
| 2026-11-16 | [1.1.0](https://github.com/Civinar-Riley/better-nether-portal/releases/tag/v1.1.0) | 建门保护事件、操作栏失败提示、落点搜索分配优化。 |
| 2026-11-16 | [1.0.2](https://github.com/Civinar-Riley/better-nether-portal/releases/tag/v1.0.2) | 修复出口碰撞、配对串门、蓝图方块覆盖与尺寸约束，补充回归测试。 |
| 2026-11-02 | [1.0.1](https://github.com/Civinar-Riley/better-nether-portal/releases/tag/v1.0.1) | 修复门识别、传送上下文与异形门落点，优化扫描。 |
| 2026-10-31 | [1.0.0](https://github.com/Civinar-Riley/better-nether-portal/releases/tag/v1.0.0) | 独立模组首次发布：任意形状门、复制出口、持久配对与配置。 |

</details>

日期与版本以 [GitHub Releases](https://github.com/Civinar-Riley/better-nether-portal/releases) 为准。

## 📜 声明与授权

**All Rights Reserved · 保留所有权利 © Riley**

授权声明见 [模组元数据](./mod.properties)。

## 🙏 致谢

[NeoForge](https://neoforged.net/) 提供模组加载与事件接口；[SpongePowered Mixin](https://github.com/SpongePowered/Mixin) 和 [MixinExtras](https://github.com/LlamaLad7/MixinExtras) 提供原版行为注入能力。
