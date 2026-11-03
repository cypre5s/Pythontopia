# Pythontopia Reborn

面向 Python 初学者的像素学习 RPG。学生在小镇与人物交谈、接取任务、读课件和完成真实练习；教师通过后台管理班级、材料、任务、发布规则与学习记录。

此分支使用 Vue 3 + TypeScript + Phaser 3 与 Python FastAPI 模块化单体。正式存储为独立 PostgreSQL；本机轻量开发可使用 SQLite。普通游戏行为不调用大模型，学习场景的主动提问使用统一 LangChain create_agent。正式代码执行仅接入隔离 Judge0。

## 运行

当前源代码不依赖旧 Java、旧 Graph/RAG 服务或非 Web 客户端。

本机开发：backend 中创建 Python 3.12/3.13 venv，安装 requirements.lock，执行 alembic upgrade head、python -m app.seed，再运行 uvicorn app.main:app --host 127.0.0.1 --port 18081。frontend 中 npm ci 后 npm run dev -- --host 127.0.0.1 --port 15174。默认 SQLite 仅供开发，正式部署必须配置独立 PostgreSQL。没有默认教师账号，使用显式 CLI 创建。

容器配置与完整本机命令见 [development.md](docs/development.md)。本机开发与独立 Compose 部署均可使用。当前预览使用本机开发库，容器验收使用另一个独立数据库，不会替换当前预览账号或其他项目。

- [产品与交互](docs/product.md)
- [模块与数据流](docs/architecture.md)
- [内容配置与教师操作](docs/content-authoring.md)
- [HTTP 契约](docs/api-contract.md)
- [启动与测试](docs/development.md)
- [实际验收与限制](docs/acceptance.md)
- [重建基线与续做位置](docs/reborn-migration.md)

## 两章演示

教师创建班级并开放179章；学生加入，南侧广场找到镇长，听完来信，打开计算思维课程，到东侧练习场完成三道概念题，返回镇长点亮设施和图书馆。

教师发布180章后刷新进度：镇长委托 → 北侧林教授 → Python与程序设计语言课程 → 概念/编程二选一路线 → 图书馆馆员与西侧书架两个目标 → 返回汇报。概念路线可在无AI/Judge0情况下完成；编程路线需要可用的隔离Judge0。

原仓库没有原始PPT/PDF和课程视频，当前种子准确标注为真实课件提取文本；教师可上传自己的合法材料。没有模型凭据时助教明确不可用。Judge0在当前Docker Desktop cgroup v2环境下实测执行失败，不能宣称已通过正式判题验收，详见acceptance.md。

## 目录

```text
backend/        FastAPI、领域模块、Alembic、依赖锁与测试
frontend/       世界、统一工作区、教师后台与浏览器验收
data/course-pack-43/json/  真实课程包原始导出
assets/         保留素材源及上游署名、授权记录
scripts/        地图生成、资源/可达检查与限定负载脚本
deploy/         独立部署、可选Judge0与文档转换器配置
docs/           当前系统说明和实际验收
```

代码和美术按 [LICENSE](LICENSE)、[LICENSE-ART.md](LICENSE-ART.md) 及各素材原许可执行，未改为MIT或作新的商业授权承诺。原小镇SRW素材的历史记录指出书面条款存档缺失，该限制仍需补齐；不把旧声明当作本次授权审计结果。main独立维护，不自动合并回reborn。
