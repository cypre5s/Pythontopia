# 开源依赖与素材清单（阶段 14）

> 比赛合规所需的"第三方依赖 + License + 素材来源"汇总。本文件按子项目分块。

## 1. 项目自身 License

默认采用 **MIT License**（`LICENSE` 占位中，正式提交前替换正式声明）。提交包内 `LICENSE` 必须存在且非空。

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

详见 `assets/pixel/LICENSE.md` 与 `frontend/public/assets/pixel/LICENSE.md`。Beta 阶段引入：

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

- `packId=43`《Python语言基础》：内部教材数据，已得到使用授权（教学用途）。
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

任何新增依赖请同步更新本文件并提交。
