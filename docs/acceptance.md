# Reborn 实际验收（2026-11-03）

本文件区分本机真实业务、隔离测试和外部服务实测。未通过与跳过均不算成功。

## 已验证结果

| 范围 | 实际结果 |
| --- | --- |
| 后端 | Ruff检查/格式通过，完整回归113 passed、7 skipped。7项为5个可选真实Judge、PG并发与PG迁移；两项PG测试已另外真实执行通过，Judge失败另述 |
| 前端 | typecheck、lint、9文件22单测、生产构建通过；Phaser独立包约1.48MB有包体提示，阅读器/编辑器按需加载 |
| 原小镇与任务 | 本机Chrome桌面1440×900、小笔记本1024×640、手机横屏844×390，两章全流程、选择分支/并列目标、图书馆进出、刷新/重登和账号隔离：3 passed（2.7分钟）；全程model_calls=0 |
| 正式部署旅程 | 备用15175站点（真实PostgreSQL17.11、Nginx、FastAPI镜像）再次完成两章、账号隔离、真实成绩/节点回流：1 passed（44.9秒） |
| 教师与媒体 | 本机最终媒体UI验收1 passed（1.4分钟）：教师建班、上传/处理/预览文本、两页PDF、自制WebM，绑定活动/任务/发布；学生真实PDF翻页与视频播放，3节点/3参与回流教师后台 |
| 课程创编 | 本机真实UI 1 passed（19.2秒）：自有章/KC新增修订、活动KC绑定、任务/班级发布、跨教师不可见及双403、真实CodeMirror键入wasd不移动角色、草稿恢复；不调用AI/正式Judge |
| PPTX完整链路 | 备用Docker站点真实UI 1 passed（17.3秒）：教师上传自制PPTX→私有LibreOffice HTTP转换ready→绑定/发布→学生PDF画布真实黑字及可复制文本→记录学习→教师真实回流；匿名401、鉴权Range206、0模型 |
| 本机PPT回退 | 真实受限Docker镜像转换，中文/空格Windows路径通过，实际API上传→ready→PDF1页24942B→绑定前403/绑定后200/匿名401/Range206；无fake converter |
| 版本权限 | 新v2即使已ready，未绑定时元数据/file双403；旧v1引用不漂移；跨班/跨教师草稿拒绝，任务保留活动/题组快照 |
| 数据库 | 独立PostgreSQL空库Alembic及双seed成功；PG临时schema真实并发重复事件只结算1次；baseline→有课程/阅读/成绩/事件→revision2真实升级通过。SQLite同样验证有数据升级、FK=ON、降级保护。用户体验账号/进度迁移后保留 |
| 世界资源 | 63项动态资源存在；1701个可达格，出生点/镇长/教授/练习场/图书馆门口5点通过 |
| 有限负载 | 本机SQLite，单一教师账号世界读取200次、10并发、0失败，p50=73ms/p95=91ms；不是AI/判题并发或数百真实学生容量证明 |
| 容器 | 最终API/Web镜像构建与独立备用端口启动通过；修复PDF worker的.mjs MIME并保持nosniff。基础镜像摘要、Python依赖、npm依赖已锁定 |

所有测试媒体明确标为Self-authored TEST MEDIA / not course content，不冒充缺失课件。媒体/创编/PPT浏览器验收不拦截或mock业务请求、不伪造前端进度。Native预览15174与备用Docker15175使用不同数据库。

## 远端 CI

代码提交`8070d859c88d30af06f8982d60573eba6909b4fb`的[GitHub Actions运行41636978216](https://github.com/cypre5s/Pythontopia/actions/runs/41636978216)已完成，frontend、backend、browser三个job均为success。CI使用独立临时测试环境，不修改当前体验数据；没有PPTX fixture的浏览器场景明确跳过，本机独立容器PPTX实测见上表。后续仅补充本验收记录，不改变已验证运行代码。

## 明确限制

- **正式编程Judge仍阻塞**：实际启动过Judge0 1.13.1，API200且47语言，但AC、WA、运行错误、超时、内存超限五项真实执行全部返回infrastructure_error。Docker Desktop的cgroup v2与其内置isolate需要cgroup v1不兼容（缺少/sys/fs/cgroup/memory和/box/script.py）。未关闭隔离、改共享主机cgroup或切旧执行器。需要兼容的独立Linux运行环境后重跑。5个默认skip不会覆盖此前失败。官方仍有未合并cgroupv2工作：[发布](https://github.com/judge0/judge0/releases/tag/v1.13.1)、[PR571](https://github.com/judge0/judge0/pull/571)。
- **AI/S3**：统一create_agent已用显式隔离测试模型验证流式、5只读工具、授权、预算、引用、超时取消和会话恢复；未调用真实付费模型，未连接真实S3。没有把测试provider作为正常运行降级。
- **源课件媒体**：tracked仓库无原PDF/PPT/PPTX/MP4/WebM。真实提取文本完整保留，34条视频清单不等于可播放媒体。PPTX已实测；原生旧PPT格式尚未单独端到端实测。
- **素材凭据**：原SRW源包与作者条款书面存档未找到；保留原世界和原许可记录，不宣称本次已审计为CC授权。
- **构建/容量**：Phaser/PDF阅读器存在已知大chunk提示；没有宣称支持数百人同时AI生成或判题。
- **依赖审计**：npm已最小修复undici7.30.0→7.30.1，仅改锁中该包；当前0 high/0 critical，剩余24 moderate均属开发依赖，生产依赖树0。未做无关大版本升级，也不宣称全系统安全。

## 可复现入口与证据

开发/配置见development.md，当前HTTP见api-contract.md。前端npm run typecheck/lint/test/build；后端ruff check/format --check与pytest -q --basetemp .pytest-final-delivery。

真实浏览器用显式E2E_TEACHER_EMAIL/PASSWORD、可选第二教师env、E2E_URL和PLAYWRIGHT_CHANNEL=chrome。journey覆盖三视口；media/curriculum/presentation为桌面，其中presentation须显式E2E_PPTX_PATH（无fixture时skip）。测试凭据与原始trace不提交。

真实截图归档qa-screens/reborn（忽略目录），包括三视口世界/图书馆/完成日志、PDF第二页/视频、教师创编/学情、代码草稿和PPTX真实PDF。PPTX原始结果位于仓库根目录.tmp-presentation-results/，其余浏览器原始结果位于frontend的.tmp-*结果目录。本机转换器API证据在secrets/converter-fixture，均不提交私人运行数据。

## 安全边界

源origin/main=783f15d8d08fd739fea3f665ec83d819b5eebb51；main、默认分支、权限、旧生产库/服务/卷未被本次修改。原E盘工作区9个tracked修改与8个untracked文件保留；开发在独立托管worktree。Docker曾按用户要求暂停，随后明确授权恢复；只操作Reborn自有容器。当前15174预览账号和进度不因备用容器验收被替换。
