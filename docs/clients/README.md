# Pythontopia 客户端总览（Clients Index）

Pythontopia 面向 **4 类客户端**（Web / Android / Windows / 微信）共用 **同一套 Spring Boot 后端**（`:18080`，前缀 `/api`，JWT Bearer）。其中微信端含 **小程序**（学习陪伴）与 **小游戏**（像素小镇）两个独立工程，故共 **5 个客户端目录**。

所有客户端的后端集成方式以 [`api-contract.md`](./api-contract.md) 为唯一权威契约（base URL/端口、JWT、错误信封、端点表、SSE、CORS、不存在的能力）。

## 客户端矩阵

| 平台 | 目录 | 技术栈 | 范围 / 像素小镇 | 构建 · 运行 |
| --- | --- | --- | --- | --- |
| Web | `frontend/` | Vue 3 + Vite + TS strict + Pinia + Phaser 3 | 全功能学习闭环 ·**有像素小镇** | `npm ci && npm run dev`，详见 [`frontend/README.md`](../../frontend/README.md) |
| Android | `android/` | Kotlin + Jetpack Compose + LibGDX + Hilt | 全功能学习闭环 ·**有像素小镇**（产物 debug APK） | `./gradlew assembleDebug`，详见 [`android/README.md`](../../android/README.md) |
| Windows | `windows/` | .NET 8 + WinUI 3 + MonoGame | 桌面端学习闭环 ·**有像素小镇**（建设中） | `dotnet build` / Visual Studio 2022 打开，详见 `windows/README.md` |
| 微信小程序 | `wechat-miniprogram/` | 原生微信小程序（TS） | **学习陪伴**（画像/课程/答疑/任务）·**无像素小镇** | 微信开发者工具导入，详见 `wechat-miniprogram/README.md` |
| 微信小游戏 | `wechat-minigame/` | 原生微信小游戏（TS + canvas2d） | 像素小镇玩法 ·**有像素小镇** | 微信开发者工具（小游戏）导入，详见 `wechat-minigame/README.md` |

> `windows/`、`wechat-miniprogram/`、`wechat-minigame/` 由各自端的负责人/Agent 维护，构建命令与细节以各端 README 为准；本目录只做跨端索引与契约，不复述各端内部实现。

## 共享后端

| 组件 | 目录 | 作用 |
| --- | --- | --- |
| 后端 API | `backend/` | Spring Boot 唯一真理源（auth / game / learner / quests / course / submissions / classroom / tutor / orchestrator / workshop） |
| 多智能体 | `services/pythontopia-graph/` | LangGraph 资源生成与路径规划（SSE 事件源） |
| 课件 RAG | `services/pythontopia-rag/` | 课件检索与证据引用 |
| 课程数据 | `data/course-pack-43/` | 《Python 语言基础》课程包（packId 43）种子数据 |

本机起后端与依赖见根 [`README.md`](../../README.md) §5「快速开始」（`deploy/docker compose up` 或各服务本地启动）。

## 文档

- [`api-contract.md`](./api-contract.md) — 客户端集成契约（**接入前必读**）。
- 各端构建/运行细节 — 见上表对应客户端目录的 README。
