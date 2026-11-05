# Reborn migration

- Branch: `reborn`.
- Current demo: Docker http://localhost:15175 / API18082 only. Native15174/API18081 were stopped at the user's request; preserve their data and do not automatically restart them.
- Source `origin/main`: `783f15d8d08fd739fea3f665ec83d819b5eebb51`.
- Original checkout: `E:\AI\Pythontopia`; its existing changes remain untouched. It is detached at the source commit to release the branch for this managed worktree.
- No production service, database, object store, volume, repository setting, or main branch is in scope.

## Retention decisions

| Capability | Source | Reborn decision | Verification |
| --- | --- | --- | --- |
| Pixel world | frontend/public/assets/maps/town_hub.json, SRW assets | reuse map and legal art; replace retired route metadata | asset audit, pathfinding, browser |
| Courses | data/course-pack-43/json | retain original source IDs, seed versioned private materials | 7 documents, 561 pages, 7 chapters, 65 KC, 46 examples, 39 original problems |
| Media | old document paths/video manifests | source media absent; extracted text remains accurately labeled | upload authorized test media to verify viewers |
| Auth/classes/learning/quests | Java backend | replace with FastAPI modular monolith, separate database | permission, persistence, idempotency and graph tests |
| AI | old graph services | replace with one configured teaching assistant using create_agent | isolated provider tests; real invocation only with credentials |
| Code execution | old runner | Judge0 adapter only | contract tests; live Judge0 validation separately recorded |
| Student UI | old route pages | world plus a single replaceable workspace | frontend tests and browser |
| Native clients/research/old planners | clients, research, services, old docs/scripts | delete from reborn | tracked-file and import audit |
| Licenses/sources | LICENSE, LICENSE-ART.md, assets and legal notices | retain attribution and source obligations | asset dependency checks |

## Content audit

Chapter 179, document 186: 计算工具与计算思维. New concept questions are authored from real pages 10715, 10723 and 10724 and labeled as such. Chapter 180, document 187: Python 语言基础(2); pages 10731–10736 cover language history and page 10750 Python characteristics. Optional coding practice 476 uses page 10771 plus print/input pages 10786–10787. Original question 477 has contradictory expected output and must remain unpublished pending review.

No original PDF/PPT/PPTX/MP4/WebM was found. Old absolute Linux media paths are not usable resources. The 34-item video manifest is metadata, not evidence of playable video.

## Progress / resume

Implementation and local/independent-container validation are complete for the available environment. Remote frontend/backend/browser CI passed for baseline code commit 8070d859c88d30af06f8982d60573eba6909b4fb (GitHub Actions run 41636978216). The UI follow-up below changes frontend runtime code and has its own validation record in acceptance.md. External Judge0/real-model/S3 limits remain explicitly documented below and in acceptance.md. Continue from actual code and this file; do not automatically merge main.

## 当前实现与续做（2026-11-03）

A–F的仓库隔离、Python单体、单世界/唯一工作区、两章任务、教学/练习、有限Agent、教师课程/KC/材料/任务/发布、私有存储/PPT转换、清理与独立部署已落地。最后结果见acceptance.md；不要以旧报告或跳过替代实际验证。

新基线迁移221d0f8caf6e；教师内容所有权增量7d4b982ce106，不修改旧基线。原始课程仍7章65KC，教师自有扩展不改变源文件计数。源码目录frontend/src/{app,features,game,shared}与backend/app模块。

当前用户预览为本机15174/API18081/独立SQLite开发库，账号与用户已保存进度保留。备用15175/API18082使用Reborn PostgreSQL及私有文档转换器。仅操作Reborn容器，不替换main服务。Docker使用授权已恢复。

下一步外部条件：提供兼容cgroupv1的独立Judge0环境后跑5项真实执行验收；配置真实模型后小额度流式/引用/取消验证；需要S3时再接真实服务；补原课件媒体与SRW授权书面凭据。禁止为判题绕过隔离或恢复旧Piston。main仍不自动合并，未来UI改动须逐个审查移植。

## 游戏UI与交互增量（2026-11-03）

参考game-ui-design、game-ui-ux后已实现：三行安全视口HUD、真实任务步骤与学习入口、可收起委托卡、底部菜单、统一焦点/返回、100%–200%文字与减少界面动效；窄屏内容/助教互斥，材料版本阅读位置与列表位置恢复。后台迟到响应不能重新打开已离开的对白或覆盖新账号，长按Esc不连续退多级；世界canvas不因工作区切换重建。

当前前端51单测、后端113通过/7默认跳过；Docker真实UI四视口4项通过，三视口两章加教师媒体/创编/PPTX另6项通过。本机Vite因trace临时HTML触发连续重载，已排除测试产物监听，最终三视口两章3项通过。放大阅读视觉与远端CI见acceptance.md，不能使用旧基线CI替代本增量。仅更新Reborn web，开发Vite有一次已核实PID重启，未重建数据库或替换原体验数据；main不合并。

UI代码d450fc19501a85fb715c6a6ad02e7db29318437d已通过远端CI运行41650312453的frontend/backend/browser三个job；浏览器9通过、1因无PPTX fixture明确跳过。后续仅补验收记录。原15174体验学生仍可登录，4个已完成节点保留；15175为独立数据。截图在qa-screens/reborn-ui，测试凭据与trace不入Git。

## 最新运行状态：仅新版演示（2026-11-03）

用户要求只保留新版：已核实并停止本项目Vite91928、Uvicorn71776及其82212启动进程，15174/18081不再监听，Docker Desktop与Reborn四容器继续运行，其他项目未操作。唯一界面15175、API18082，端口已持久写入本机deploy/.env。

已建立独立干净演示教师及演示班（classroom31），两个学生分别为从头开始/两章已完成。真实Chrome完整旅程1 passed（36.4秒），教师学情含2成员、6提交、14事件；第二章对新手按第一章完成门槛开放。凭据/加入码/核查结果在忽略的.tmp/reborn-demo.json。这里覆盖前文曾同时运行15174与15175的历史状态，不恢复旧预览、不删除历史数据。

### 2026-11-03 教师管理后台设计
- 新增 frontend/src/features/teacherUi.css，教师端采用独立管理后台视觉；共享 GameIcon 统一线宽、尺寸与颜色继承，文字标签保留。
- 班级/学习记录/发布、课程资源、学习体验按组排列；支持搜索、复制与移出确认；切班清空旧记录并忽略过期响应。
- 继续使用唯一 Docker 入口 http://localhost:15175/；演示账号和数据保留。验证结果见 acceptance.md。

## 2026-11-04 主工作区对话 UI

DialogueLayer 改为 main/DialogTree 同位置的左上立绘舞台与下方纵向回应列表，增加快进到选项的跳过操作。变更限于对话组件、异步导航回归测试和验收记录；未合并 main 的业务代码。主工作区预览使用独立 Compose 项目 pythontopia-reborn-workspace、忽略配置 deploy/.env.workspace.local，入口 localhost:15175。52 项前端测试及三种浏览器视口检查通过，细节见 acceptance.md；当前修改未提交。

2026-11-04：暂不做任务驱动的场景表现，world adapter 已移除完成状态文字；不要再用悬浮标签模拟点灯或场景变化。

2026-11-04 HUD 按用户草稿重排：任务左上、菜单左下、身份右下、提示底部中央，移除顶部品牌栏，窄屏身份信息避开菜单。GameHUD scoped 样式负责 HUD，gameUi.css 末尾负责提示定位；预览 15175 已更新，检查见 acceptance.md。

2026-11-04：取消空闲时常驻移动说明，附近有交互对象才显示提示，空提示框同时隐藏。

2026-11-04：任务区按用户要求仅留带阴影文字，右下身份无阴影；WASD 将菜单残留焦点交回 world-canvas，勿全局禁用键盘焦点样式。

2026-11-04 入场流程按三张用户草图实现：TownBackdrop、ArrivalWelcome、arrival.css 共用地图展示与样式，EntryPage/WorldPage/WorldCanvas 接通黑场、世界就绪和欢迎输入门控。装饰背景使用现有地图素材，无新增外部资源；邮箱规则保持。主工作区 15175 已部署，53 项测试和真实登录/注册/入班/教师路线通过。

2026-11-04：左上任务不再折叠，消息图标+紧凑常显文本，所有任务文字带阴影；右下身份样式不变。15175 已更新。

2026-11-04：入场从黑场改为预加载+卡片下沉+快速镜头交接。App持有跨路由TownBackdrop，shared/arrival.ts维护短暂过渡状态；WorldCanvas透传adapter首帧相机视口。53项测试及真实浏览器入场通过，15175已更新。

2026-11-04：ArrivalWelcome改为底部通栏渐变与左右文字布局，取消右侧卡片和‘或点击这里’副文案，点击继续仍有效。15175已更新。

2026-11-04：欢迎等待阶段通过相机followOffset轻缓漂移，继续后归零锁定玩家；GameHUD增加错峰入场动画，减少动效时关闭。浏览器验证等待漂移、继续稳定与6个HUD动画通过，15175已更新。

2026-11-04：WorldPage每次挂载均进入欢迎状态，不再依赖reborn-arrival标记；未进行入场时调用startArrival。TownBackdrop等待地图绘制就绪后交接。刷新和直接导航/world已实测通过，15175已部署。

2026-11-04：以本条为准，欢迎等待不再提前飞到玩家，也不启用玩家附近漂移。WorldPage暂存首帧目标，继续后才传给TownBackdrop；arrivalFinished后展示HUD。登录与直接/world复用同一个town-tour巡游及飞行动画，15175已更新。

2026-11-04：欢迎层Teleport到body，避免fixed世界容器的层叠上下文将文字压在巡游背景下。以后验收同时检查实际截图/遮挡，不能只检查元素存在。15175已更新。

2026-11-04：用户要求改版前提交，已完成95191530。任务日志新试版集中于QuestJournal.vue：左目录右详情、羊皮纸/暖金/深绿、窄屏纵向布局，数据与操作仍用现有任务系统，未添加奖励经济。15175本地预览已更新；此次日志试版暂留工作区便于用户调整。

2026-11-04：日志进度改为连续条；world.ts的town-ground缓存纹理合成当前地图静态地面，针对WebGL分块接缝，不改碰撞/NPC/建筑图层。15175已部署，当前浏览器检查未见黑线，用户原始间歇场景待复核。

2026-11-04：DialogueLayer按用户参考图调整为暖色纸面左右分栏与紫金姓名牌，立绘放大、右上跳过。仍使用现有角色资源和真实对白，未添加自动播放或虚构选项；15175已更新。

2026-11-05：最新对白以深色金边参考图为准，点击显示全文/继续，继续不再作为选项。选项只在typing结束后显示，按钮点击不冒泡推进。55项测试通过，15175已更新。

2026-11-05：姓名纯文字；立绘尺寸上限按1080P收紧。world adapter在NPC互动/暂停清除pendingTarget，防止关闭后残留自动互动，E键忽略重复事件。15175已部署并实测关闭不重开。

2026-11-05：对白取消跳过入口，统一点击正文/Enter继续。

2026-11-05：OverlayHost同步引入日志/资料/班级/设置，Lesson/Practice使用WorkspaceLoadState显示加载和错误，防止静默空白；回到小镇统一公共样式。旧浏览器需刷新获取新版本，15175已更新。
