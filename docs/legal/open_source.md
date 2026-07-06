# 第三方依赖与素材许可清单（阶段 14）

> 比赛合规所需的"第三方依赖 + License + 素材来源"汇总。本文件按子项目分块。
> 它是第三方许可清单，不是 Pythontopia 项目的开源授权文件。

## 1. 项目自身 License

当前阶段采用 **Source-Available / All rights reserved**（见仓库根 [`LICENSE`](../LICENSE)）。
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

- 讯飞 Spark API（Provider 接入，演示主链路），凭据放 `.env`，不入 git。
- OpenAI 兼容协议（开发期 fallback），同上。

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
