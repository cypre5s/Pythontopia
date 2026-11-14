# 第三方依赖与素材许可清单（阶段 14）

> 历史范围说明：本清单保留旧技术栈的上游与素材来源，含已退出Reborn的Spring Boot、Graph和RAG条目，不是当前依赖清单。当前范围见[产品说明](../product.md)，实际依赖以[后端锁文件](../../backend/requirements.lock)、[前端锁文件](../../frontend/package-lock.json)及相应上游许可核对；本轮仅整理入口，未改写下方授权与署名记录。

> 比赛合规所需的"第三方依赖 + License + 素材来源"汇总。本文件按子项目分块。
> 它是第三方许可清单，不是 Pythontopia 项目的开源授权文件。

## 当前学生编辑器与字体补充

- go-judge v1.14.1：Yang Gao / criyle，MIT；仅用于独立 Python 判题容器。[固定版本源码](https://github.com/criyle/go-judge/tree/v1.14.1)、[保留的许可原文](../../deploy/go-judge/LICENSE)。构建将许可复制至 `/opt/licenses/`；Python 使用原始官方 Python 3.13 slim 镜像并保留其上游许可文件。

- Pyodide 0.27.7：Pyodide 项目，MPL-2.0；核心运行时通过锁定的 npm 包原样本地分发。[上游源码](https://github.com/pyodide/pyodide/tree/0.27.7)、[保留的许可原文](../../frontend/scripts/PYODIDE-LICENSE.txt)。构建复制许可至运行时静态目录，Python 标准库归属保留在上游分发文件中。

- Monaco Editor 0.57.0：Microsoft，MIT。[保留的许可原文](../../frontend/public/legal/monaco-editor-LICENSE.txt)。仅按需加载编辑器与 Python 语言注册。
- JetBrains Mono 5.3.0（Fontsource 分发）：字体作者为 The JetBrains Mono Project Authors，SIL Open Font License 1.1。[保留的许可原文](../../frontend/public/legal/jetbrains-mono-OFL.txt)。拉丁字形随应用本地分发，中文采用系统回退。

- markdown-it 15.0.3：Vitaly Puzrin、Alex Kocharin，MIT。[保留的许可原文](../../frontend/public/legal/markdown-it-LICENSE.txt)。用于助教回复的 Markdown 渲染，禁用原始 HTML 和图片解析。

## 当前教师后台依赖补充

- 节点编辑器使用 `@xyflow/react`，并通过该库加载 `@xyflow/system`。两者同用 webkid GmbH 的 MIT 许可，已保留原文。[React Flow 许可](../../admin/legal/react-flow-LICENSE.md) · [后台锁文件](../../admin/package-lock.json)

## 1. 项目自身 License

当前阶段采用 **Source-Available / All rights reserved**（见仓库根 [`LICENSE`](../../LICENSE)）。
仓库公开可见仅用于评审、演示、课程验收与团队协作；未经项目维护者书面许可，不得复制、复用、再发布、商用、部署或二次创作 Pythontopia 原创代码、文档、产品设计、品牌、原创美术、截图、生成媒体、课程集成与演示材料。

下方列出的第三方依赖与第三方素材仍由各自上游 license 管理；本文件不会收回上游作者已经授予的权利，也不会额外授权 Pythontopia 的原创集成、选择编排、品牌或业务代码。

## 2. 后端 (Spring Boot)

来源：`backend/pom.xml`。主要直接依赖：

- `org.springframework.boot:spring-boot-starter-web` (Apache-2.0)
- `org.springframework.boot:spring-boot-starter-security` (Apache-2.0)
- `org.springframework.boot:spring-boot-starter-validation` (Apache-2.0)
- `org.springframework.boot:spring-boot-starter-jdbc` (Apache-2.0)
- `org.flywaydb:flyway-core` (Apache-2.0)
- `org.postgresql:postgresql` (BSD-2-Clause)
- `com.fasterxml.jackson.core:*` (Apache-2.0)
- `io.jsonwebtoken:jjwt-*` (Apache-2.0)
- `org.springframework.boot:spring-boot-starter-test` (Apache-2.0)
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` (Apache-2.0)
- Testcontainers (`org.testcontainers:postgresql`、`junit-jupiter`) (MIT)

## 3. 前端 (Vue 3 + Vite)

来源：`frontend/package.json`。主要直接依赖：

- `vue` (MIT)
- `vue-router` (MIT)
- `pinia` (MIT)
- `phaser` (MIT)
- `axios` (MIT)
- `marked` (MIT)
- `mermaid` (MIT)
- 开发：`vite` (MIT)、`vitest` (MIT)、`vue-tsc` (MIT)、`@vue/test-utils` (MIT)、`eslint` (MIT)、
  `prettier` (MIT)

## 4. Python 服务

### 4.1 `pythontopia-graph`

- `fastapi` (MIT)
- `uvicorn` (BSD-3-Clause)
- `pydantic` (MIT)
- `httpx` (BSD-3-Clause)
- 开发：`pytest` (MIT)、`pytest-asyncio` (MIT)、`ruff` (MIT)

### 4.2 `pythontopia-rag`

- `fastapi` (MIT)
- `uvicorn` (BSD-3-Clause)
- `pydantic` (MIT)
- `psycopg[binary]` (LGPL-3 或商业双许可，使用 binary wheel 模式即可)
- `numpy` (BSD-3-Clause)
- 开发：`pytest` (MIT)、`ruff` (MIT)

## 5. 像素素材

详见 `assets/pixel/LICENSE.md`、`frontend/public/assets/pixel/LICENSE.md` 与根目录 `LICENSE-ART.md`。Beta 阶段引入的第三方素材包括：

- OpenGameArt `Farming Set (Pixel-Art)` by ScratchIO（CC0）。
- OpenGameArt `Top Down season/environment Tileset` by marionline（CC0）。

**禁止**使用《星露谷物语》官方素材或 `Notyourbing/StardewValley` 仓库下的图片资源。任何素材必须有
明确 license 且写入 `assets/pixel/LICENSE.md`。

## 6. 字体

- 中文：`霞鹜文楷` / `LXGW WenKai`（SIL OFL 1.1）。
- 代码：`JetBrains Mono`（OFL 1.1）。

字体不打包入仓库，演示机自行安装；fallback 到 `PingFang SC`、`Microsoft YaHei`、`system-ui`。

## 7. 第三方服务

- DeepSeek API（文本生成与图片理解）；凭据放 `.env`，不入 git。
- 讯飞图像生成、图片理解、语音识别与语音合成服务固定关闭，不配置或调用这些外部 API。

## 8. 数据来源

- `packId=43`《Python语言基础》：内部教材数据，仅限本项目教学、评审与演示语境使用。
- 不复制 Alethicode 老前端/数据库直接产物，仅抽取必要 schema 与算法思路。

## 9. 提交合规复核

- `mvn dependency:list` / `npm ls --all` / `pip list` 输出快照建议在演示前打包。
- 自动检查命令（开发期）：

  ```bash
  cd backend && mvn dependency:tree -Dscope=runtime | head -200
  cd ../frontend && npm ls --prod --depth=0
  cd ../services/pythontopia-graph && .venv/bin/python -m pip list
  cd ../pythontopia-rag && .venv/bin/python -m pip list
  ```

任何新增依赖请同步更新本文件并提交；任何项目原创内容如需对外授权，必须先更新根目录 `LICENSE` 与相关素材清单。
