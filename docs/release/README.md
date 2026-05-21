# Pythontopia Beta v1 提交包导览

> 阶段 15 收尾交付物。源码 tarball / docs / images / images_tar 通过 `docs/release/` 串联，
> 评审机器只需要 `git archive` 与 `docker compose up -d --build`。

## 1. 目录约定

```text
docs/release/
├── README.md                 # 当前文件
├── beta_acceptance_checklist.md  → 链接到 ../beta_acceptance_checklist.md
├── known_issues.md
├── regression_report.md
├── demo_v1.md                # 视频剪辑时间轴 + 字幕清单（视频本体不入 git）
├── demo_v1.mp4               # 7 分钟演示视频（提交包内，不入 git）
├── Pythontopia_v1.pptx       # PPT 终稿（提交包内，不入 git）
├── Pythontopia_v1.pdf        # PPT PDF 备份
├── source/                   # git archive 产物，evaluation 时解包
└── images/                   # 关键截图，演示账号录制
```

## 2. 制作流程

```bash
# 1) 全量回归
cd /home/cypress/Pythontopia
bash deploy/scripts/demo_smoke.sh                                           # 演示链路冒烟

# 2) 准备源码 tarball
mkdir -p docs/release/source
git -C /home/cypress/Pythontopia archive --format=tar.gz -o docs/release/source/Pythontopia-beta-v1.tar.gz HEAD

# 3) 镜像 tarball（可选）
mkdir -p docs/release/images_tar
docker save -o docs/release/images_tar/backend.tar pythontopia-backend:dev
docker save -o docs/release/images_tar/frontend.tar pythontopia-frontend:dev
docker save -o docs/release/images_tar/graph.tar pythontopia-graph:dev
docker save -o docs/release/images_tar/rag.tar pythontopia-rag:dev

# 4) checksum
cd docs/release/source && sha256sum *.tar.gz > checksum.txt
cd ../images_tar && sha256sum *.tar > checksum.txt
```

> 注意：源码 tarball 必须排除 `.env`、`node_modules`、`target/`、`.venv/`。git archive 默认遵守
> `.gitignore`，无须手动剔除。

## 3. 提交包内容清单

| 类别 | 文件 | 大小（估） |
| --- | --- | --- |
| 源码 | `docs/release/source/Pythontopia-beta-v1.tar.gz` | ~5MB |
| 视频 | `docs/release/demo_v1.mp4` | < 200MB |
| PPT | `docs/release/Pythontopia_v1.pptx` / `.pdf` | < 25MB |
| 镜像（可选） | `docs/release/images_tar/*.tar` + `checksum.txt` | ~1.2GB |
| 文档 | `docs/system_design.md`、`docs/test_report.md`、`docs/ai_coding_disclosure.md`、`docs/open_source.md`、`docs/demo_script.md`、`docs/ppt_outline.md`、`docs/release/*.md` | <1MB |

## 4. 评审机器复现

```bash
mkdir -p /tmp/pyt-review
tar xzf Pythontopia-beta-v1.tar.gz -C /tmp/pyt-review
cd /tmp/pyt-review/Pythontopia
cp deploy/.env.example deploy/.env
# 填入 PYT_DB_*, PYT_JWT_SECRET, PYT_TEACHER_INVITE_CODE, PYT_LLM_PROVIDER 等
docker compose -f deploy/docker-compose.yml up -d --build
bash deploy/scripts/init_rag_index.sh
bash deploy/scripts/seed_demo_classroom.sh
bash deploy/scripts/demo_smoke.sh
```

打开 <http://localhost:8080> 即可登录演示账号体验完整闭环。

## 5. 演示后清理

```bash
bash deploy/scripts/teardown_demo.sh
docker compose -f deploy/docker-compose.yml down -v
```

`-v` 会删除 `pytopia_pg` volume，新一轮演示需重跑 Flyway 与 RAG 索引重建。

## 6. 合规复核（提交前 2 人复核）

- 是否包含真实学生 PII？必须为 `demo-` 前缀虚构身份。
- 是否包含真实 LLM 密钥？`.env` 不入提交包；只保留 `.env.example`。
- 是否所有依赖 License 都在 `docs/open_source.md` 中列出？
- 是否所有 AI 协作模块在 `docs/ai_coding_disclosure.md` 中说明？
- `docs/release/regression_report.md` 是否反映本次发布的最新结果？
