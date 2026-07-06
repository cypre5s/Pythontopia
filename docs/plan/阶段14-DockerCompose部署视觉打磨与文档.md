# 阶段 14 — Docker Compose 部署、视觉打磨与文档

## 1. 时间窗口与定位

- 建议日期：2026-06-17 ~ 2026-06-21（第 4 周第 7-11 天，约 4 ~ 5 天，与 Beta 验收并行）。
- 角色：1 名 DevOps + 1 名前端（视觉） + 1 名文档负责人。
- 定位：把已实现的能力一键起来 + 美化 + 出文档；作为 Beta 验收的"最后一公里"。本阶段**不新增**业务功能；只做集成、视觉和交付物。

## 2. 本阶段目标

- 完成 Docker Compose 一键启动：单条命令拉起 Postgres + Redis + Backend + Graph + RAG + Frontend 静态 + 数据初始化。
- 完成视觉打磨：小镇地图、NPC、建筑、UI 配色统一；学生端、教师端各完成 1 轮 review。
- 完成文档：
  - 系统设计文档（架构图、模块说明、关键时序）。
  - 测试报告（覆盖范围、回归用例）。
  - AI Coding 工具说明（哪些代码是 AI 协助生成，哪些是人工，遵循比赛披露规范）。
  - 开源说明（License、第三方依赖清单）。
  - PPT 大纲与演示脚本（与阶段 15 共用初稿）。
- 跑全量回归测试，并修复阻断 Beta 演示链路的问题。

## 3. 上下游关系

- 上游：阶段 00 ~ 13 全部交付物。
- 下游：阶段 15 演示视频与 PPT 直接使用本阶段的 Docker Compose、文档与视觉。

## 4. Alethicode 借用来源

- 部署脚本风格参考：
  - `/home/cypress/Alethicode/`（如有 `deploy/`、`docker-compose.yml`）。

**抽取约束**：

- 仅参考 service 列表与端口约定，**禁止**复制 Alethicode 的镜像配置或环境变量名。
- Pythontopia 自维护 `pythontopia-*` 命名空间。

## 5. 任务清单

### 5.1 镜像与 Compose

- T14-01 完善各服务 Dockerfile：
  - `/home/cypress/Pythontopia/backend/Dockerfile`：多阶段构建（builder: `maven:3.9-eclipse-temurin-21`；runtime: `eclipse-temurin:21-jre`）；启动命令使用环境变量。
  - `/home/cypress/Pythontopia/frontend/Dockerfile`：构建阶段 `node:20-alpine`；runtime 用 `nginx:alpine` 提供静态文件 + `/api`、`/healthz` 反向代理到 backend。
  - `/home/cypress/Pythontopia/services/pythontopia-graph/Dockerfile`：见阶段 00；本阶段补 healthcheck `/healthz`。
  - `/home/cypress/Pythontopia/services/pythontopia-rag/Dockerfile`：见阶段 06；本阶段加 healthcheck，启动时若索引为空显示警告（不阻断启动）。
- T14-02 `/home/cypress/Pythontopia/deploy/docker-compose.yml`：
  - services：`postgres`、`redis`、`backend`、`frontend`、`pythontopia-graph`、`pythontopia-rag`。
  - 所有业务服务进入默认启动；移除阶段 00 的 `profiles: ["app"]` 限制。
  - depends_on + healthcheck 串联（postgres → backend → graph/rag → frontend）。
  - 端口：
    - 前端 `8080:80`（演示主入口）。
    - 后端 `18080:18080`（仅本地访问）。
    - graph `18091:18091`、rag `18092:18092`（仅本地访问）。
    - postgres `15432:5432`、redis `16379:6379`。
- T14-03 `deploy/.env.example`：补齐演示所需变量（讯飞 Provider 等），值留空；强调演示前生成 `.env`（不提交）。
- T14-04 `deploy/scripts/`：
  - `init_db.sh`：等待 postgres ready → 触发 Flyway（由 backend 启动时自动执行；脚本只做等待与检查）。
  - `init_rag_index.sh`：调用 `pythontopia-rag-index rebuild`。
  - `seed_demo_classroom.sh`：创建演示班级、演示学生、教师账号、绑定 packId=43。
  - `demo_smoke.sh`：包装阶段 11 的 `demo_smoke.py`，做演示前自检。

### 5.2 视觉打磨

- T14-10 小镇 UI 走查：
  - 小镇瓦片地图清晰，每个建筑有可识别标识（图书馆/训练场/Bug 矿洞/画像屋/资源工坊/教师屋/公告板/复盘大厅/教师屋）。
  - NPC 位置与玩家初始位置无遮挡。
  - 玩家移动顺滑（48 ~ 60 fps）。
- T14-11 颜色与字体统一：
  - 用 `frontend/src/styles/tokens.css`（或 SCSS）定义统一颜色（教育/像素混合风格）：主色、文本色、警示色、成功色、低对比度灰阶。
  - 字体：中文用"霞鹜文楷"或自由开源字体；代码用 `JetBrains Mono` 或等宽字体。
  - 在 `assets/pixel/LICENSE.md` 中标记字体 license。
- T14-12 关键面板视觉 review：
  - 画像屋面板、知识田 KC 详情、资源工坊详情、训练场、Bug 矿洞、公告板、复盘大厅、教师面板各完成 1 轮 review。
  - 形成 checklist `/home/cypress/Pythontopia/docs/ux_review_checklist.md`。
- T14-13 暗色/亮色：本阶段只做 1 套（默认亮色）；亮色 + 像素风。
- T14-14 性能优化：
  - 静态资源开启 nginx gzip。
  - Phaser 减少未使用的图集；按需懒加载场景。

### 5.3 文档

- T14-20 `/home/cypress/Pythontopia/docs/architecture/system_design.md`：
  - 总体架构图（Mermaid）：浏览器 → Nginx/Frontend → Backend → (Graph, RAG, Postgres, Redis)。
  - 模块说明：每个目录的职责、对应阶段编号。
  - 关键时序图：登录、画像访谈、资源生成、判题、Bug 矿洞、评估、教师任务发布。
- T14-21 `/home/cypress/Pythontopia/docs/testing/test_report.md`：
  - 测试范围（单元、集成、端到端）。
  - 覆盖率快照。
  - 已知缺陷与遗留风险。
- T14-22 `/home/cypress/Pythontopia/docs/project/ai_coding_disclosure.md`：
  - 哪些模块由 AI 协助生成（包括 ProfileAgent、ResourceDesignerAgent 等 prompt 模板）。
  - 哪些代码由人工编写。
  - 列出使用的 AI Coding 工具与开发场景。
- T14-23 `/home/cypress/Pythontopia/docs/legal/open_source.md`：
  - 第三方依赖清单（前端、后端、Python）。
  - 各依赖 license。
  - 项目自身 License 选择（默认 MIT 或 Apache 2.0，由用户最终确认）。
- T14-24 `/home/cypress/Pythontopia/docs/presentations/demo_script.md`：
  - 演示流程（7 分钟）：登录 → 进入小镇 → 画像 → 知识田 → 资源生成 → 训练场 → Bug 矿洞 → 复盘大厅 → 教师视角。
  - 每步预期截图与时间分配。
- T14-25 `/home/cypress/Pythontopia/docs/presentations/ppt_outline.md`：项目背景、技术架构、核心功能、教育闭环、技术亮点、未来工作。

### 5.4 演示数据

- T14-30 `/home/cypress/Pythontopia/data/demo/`：
  - `demo_users.json`：教师 + 3 名学生账号（密码使用一次性随机）。
  - `demo_classroom.json`：班级名、绑定 packId=43。
  - `demo_quests.json`：1 个班级任务样例。
  - 配套脚本 `data/demo/seed.py`：演示前一次性插入；幂等可重跑。
- T14-31 演示数据**禁止**直接写入种子 SQL（避免污染单元测试）；只在演示脚本中插入。

### 5.5 全量回归

- T14-40 `mvn test`、`npm run typecheck && npm run lint && npm run test`、两个 Python 服务 `pytest -q`。
- T14-41 端到端：`scripts/demo_smoke.sh`，覆盖：
  - 注册/登录。
  - 画像 → 知识田 → 资源生成（6 类）→ 路径规划 → 训练场提交 → Bug 矿洞修复 → 复盘大厅评估 → accept 新路径。
  - 教师视角：班级创建 → 学生加入 → 任务发布 → 看板浏览。
- T14-42 演示链路冒烟：
  - `docker compose up -d` → 10 分钟内全部 healthy。
  - `bash deploy/scripts/seed_demo_classroom.sh` → 10 秒内完成。
  - `bash deploy/scripts/demo_smoke.sh` → 6 分钟内通过。

### 5.6 演示安全

- T14-50 演示前关闭注册（或仅允许特定邀请码）；演示账号密码定期轮换。
- T14-51 演示后清理：
  - `deploy/scripts/teardown_demo.sh`：清理演示班级与学生数据，但不动种子。
- T14-52 演示中绝不展示密钥、log 中 PII；前端控制台 `console.log` 在生产构建中剥离（`drop_console: true`）。

## 6. 数据输入与输出

- 输入：阶段 00 ~ 13 的代码与种子数据。
- 输出：
  - 可运行的 Docker Compose 镜像与脚本。
  - 文档：系统设计 / 测试报告 / AI 披露 / 开源说明 / PPT 大纲 / 演示脚本。
  - 演示数据脚本与冒烟脚本。

## 7. 验收标准

### 7.1 部署验收

- 在干净的 Ubuntu 22.04 + Docker 24+ 上执行：
  ```bash
  cd /home/cypress/Pythontopia/deploy && cp .env.example .env && vim .env && docker compose up -d
  ```
  10 分钟内 6 个服务全部 `healthy`。
- 浏览器访问 `http://localhost:8080` 能登录、能进入小镇、能跑完冒烟流程。
- `bash deploy/scripts/init_rag_index.sh` 完成后 `GET http://localhost:18092/rag/index/status` 显示 chunk 数 ≥ 1500。

### 7.2 视觉验收

- 小镇核心场景在 1280×720 与 1920×1080 下显示无错位。
- 学生端核心面板与教师端核心面板配色、字体一致。
- 关键文案无错别字（中文校对 1 轮）。
- 像素素材均带 license。

### 7.3 文档验收

- 6 类文档完成并放在 `/home/cypress/Pythontopia/docs/` 下。
- AI Coding 披露完整、与实际工作一致。
- 第三方依赖清单包含所有 `package.json`、`pom.xml`、`pyproject.toml` 中的依赖。

### 7.4 测试验收

- `mvn test`、`npm run test`、`pytest -q` 全部通过。
- `demo_smoke.sh` 一次通过。
- 文档中"已知缺陷"清单明确列出剩余风险与影响范围。

## 8. 验证命令

```bash
cd /home/cypress/Pythontopia/deploy
cp .env.example .env
# 编辑 .env 填入 PYT_DB_*、PYT_JWT_SECRET、PYT_TEACHER_INVITE_CODE、PYT_LLM_PROVIDER=spark 等
docker compose up -d
docker compose ps

bash scripts/init_rag_index.sh
bash scripts/seed_demo_classroom.sh
bash scripts/demo_smoke.sh

# 各服务 healthcheck
curl -s http://localhost:18080/healthz
curl -s http://localhost:18091/healthz
curl -s http://localhost:18092/healthz
curl -s http://localhost:8080 -o /dev/null -w "%{http_code}\n"

# 全量回归
cd /home/cypress/Pythontopia/backend && mvn test
cd /home/cypress/Pythontopia/frontend && npm run typecheck && npm run lint && npm run test
cd /home/cypress/Pythontopia/services/pythontopia-graph && python -m pytest -q
cd /home/cypress/Pythontopia/services/pythontopia-rag && python -m pytest -q
```

## 9. 风险与回滚

- 风险：镜像构建在评审机器拉取慢；缓解：发布镜像 tar 包 `deploy/images.tar.gz` 备用，并写 `docker load` 步骤。
- 风险：演示账号被外部访问；缓解：演示前关闭注册或限制 IP；演示后清理。
- 风险：视觉走查遗漏；缓解：`ux_review_checklist.md` 列项 1 周内每日抽查 1 次。
- 回滚：本阶段全部为集成与文档，不引入业务表变更；回滚只需还原 docker-compose 与文档目录。

## 10. 不做事项

- 不新增业务功能。
- 不引入大型监控/APM（Beta 不需要）。
- 不做生产级 HA / 多副本。
- 不在仓库中提交真实密钥、Spark API key。
- 不为视觉打磨绕过 `@typescript-eslint/no-explicit-any` 规则。
- 不在文档中复制 Alethicode 项目的整段说明（必须重写为 Pythontopia 自有内容）。

