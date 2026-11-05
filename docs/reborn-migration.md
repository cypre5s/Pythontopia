# Reborn migration

## 后台改版与 reborn 集成（2026-11-05）

本轮以 reborn 的 b773f56a（章节脚本与配置地图）为基线，合入后台改版 0f2717ac，在隔离集成分支验证后更新本地 reborn，不推送或部署。原有未跟踪 deploy/piston-local/ 保留。合并保留新版剧情、地图、活动快照、旧任务兼容及教师后台的日期安排/分页/弹窗/章节完成数。新安排首次保存会重新读取任务定义，避免切换到编写页面后缓存旧版本；已有安排仍不自动升级。

验证使用新的临时 API18186、Web15286 与独立 SQLite/存储，迁移到 b63e1a4920d7；不升级原验收库或覆盖原前端构建。此处以下的15175/15176/15186等端口、旧分支路径、通过项数量和“当前/最新”措辞均属于各次历史验收，不能作为本次集成部署状态的证明。本轮最终集成验证：前端锁定npm ci、typecheck、lint、127项单测及独立输出构建通过；后端71项锁依赖匹配、ruff、168 passed/7 skipped（2个PostgreSQL专项与5个liveJudge0未配置），全新Alembic到b63e1a4920d7、重复seed后29表行数一致。地图校验2图/11建筑/2portal/1838可达格/353美术对象与68项资源存在审计通过。真实Chrome：后台专项、课程编写、私有媒体各1通过，旧两章1、新第一章桌面/窄屏2、地图定义/交互2，共8项通过。新建排课刷新定义的回归已纳入127项。最初后台分页用例依赖历史上传数据，在全新库失败；改为显式上传11份自制文本fixture后通过，不将首次失败计通过。未实测本轮真实AI/Judge/PG或PPTX转换。合并只更新本地源码，不替换已有验收数据库、容器或生产构建。

## 以下为历史记录


- Branch: `reborn`.
- Current local runtime (2026-11-05): stopped at the user's request. Independent `pythontopia-story-qa` web/API/db are all stopped; Web15176/API18083 no longer listen. Containers and data volumes are retained. Existing15175 demo was already stopped and left unchanged; Native15174/API18081 remain stopped.
- Source `origin/main`: `783f15d8d08fd739fea3f665ec83d819b5eebb51`.
- Current implementation checkout: `E:\AI\Pythontopia` on `reborn`. Unrelated untracked `deploy/piston-local/` remains untouched. Earlier managed-worktree records below are historical.
- No production service, database, object store, volume, repository setting, or main branch is in scope.

## Retention decisions

2026-11-05 最新玩家标记/家具修订：恢复地图上始终显示的青蓝玩家标记与“你在这里”，所有筛选保留。session.mapPosition独立内存记录Sprite即时浮点位置，打开地图同步快照，不等1.5秒保存；world.player整数权威位置/节流/跨图授权不变，账号班级与旧回调隔离。手机只滚map-stage使标记可见，不干扰焦点/手动平移。地图整页外层透明无大框，暖金标题、深绿与纸面卡片参考任务日志。

桌椅根因是原16×32长条误作短椅，且小桌取帧不适合该组合。实看source后改为2张完整48×36宽桌与4张16×16独立短木凳，桌脚完整、桌凳间留空，碰撞覆盖桌面与凳占格。137室内/1838总可达格、353对象，10书架锚点与旧存档通路保留。原PNG/world.json不改。本次最终前端103项、后端164 passed/7 skipped、整组真实浏览器5项通过；样式与三视口截图已实际开图检查，详见acceptance.md首节。验收后按用户要求停止全部本项目服务并提交推送reborn；保留数据卷与未纳入提交的deploy/piston-local目录。

2026-11-05 用户草图上一轮：地图按草图改为左大地图/右独立圆角卡，右卡顶部仅compass/building/people筛选；删除地图选择、位置/计数/图例说明，固定玩家当前图，小镇预览仅50×40逻辑范围，不画外景。图书馆改为10组满书书架、双阅览位、居中服务台、2行后墙和中央通道，去大毯/孤桌。147室内可达格，保留旧存档连通与关键坐标；原PNG不改。

所有书架保留library_shelf语义并配置10个points，点击具体书柜与普通就近导航都可读；服务端INTERACT_OBJECT核对任一真实锚点半径2，REACH_LOCATION保留原范围。旧任务/事件/脚本版本不改写，任务标题与对白中旧“西侧书架”在显示层改称。互动提示改短文字和随文宽度，按角色真实插值位置逐帧更新，移动中离开立即消失；未开放建筑无E或未开放弹字。任务日志左目录底部装饰句已删除。当前15176/API18083。

本次最终验证：前端99项、后端164 passed/7 skipped、PG竞争/历史迁移2项通过，类型/lint/构建、重复seed/Alembic check和地图生成/1848可达格/351对象/68资源审计通过。真实新旧章节3项及最终地图2项通过（分组执行）；持续按移动键时提示清除、远书架可读、当前图无选择器、窄屏和1464×1244日志删除均已实测/开图复核。首次地图用例失败仅为新增书目选择器不符合真实DOM，已修正并复测；细节见acceptance.md首节。未重置进度、未提交或推送。

2026-11-05 地图工作区上一轮：删除地点导航浮层，在学习资料与我的班级之间加入地图入口；中央真实预览、右侧目标、顶部建筑/人物筛选与地图选择。切换预览不移动角色，明确前往后才导航；不新增WASD/方向键选择，关闭回到世界焦点。房屋上方与地图列表仅保留名称，门口改8×8原始透明箭头。图书馆已替换为library.json的同源地板、砖墙、书柜/书本、桌椅、地毯与出口门，Phaser/地图/登录背景共用worldRender绘制。

小地图显式centered、resize保持居中；小镇逻辑50×40/原碰撞不变，render_bounds扩为(-18,-14,86,68)，外围草地/树林不可行走。前后端walk_bounds共同限制位置；新增家具阻挡保留旧室内存档走出邻格，不重写存档。新增3个原始PNG按需复制，保留生成器、原图和来源；没有批量导入源包或改图。当前预览仍为15176/API18083。

最新工作区验证：前端19文件95项通过，后端完整157 passed/7 skipped、配置最终18项通过，PG竞争/历史迁移2项通过；真实浏览器整组5 passed，最后美术/手机显示修正后地图2 passed，正常动效入场另行通过。地图可达1861格/334对象、资源存在审计68项。截图已实际打开检查。所有本轮源码仍未提交/推送，体验进度保留；当前页面需刷新获取新版。

2026-11-05 地图交互首轮：统一配置data/world/world.json涵盖2地图、11中文建筑和2双向portal，建筑锚点仅维护一次；前后端共同校验入口与落点，跨图不再使用任意位置PUT；旧室内存档保留。用户提供D:\godot-one并确认已购可用，现有184个SRW PNG可逐项重现；已增加source只读预检。来源与比对见assets/inbox/super-retro-world/SOURCES.md。

地图交互首轮复验：后端156 passed/7 skipped、前端81项通过；地图2项真实UI+新旧章节3项真实UI全部通过，PG竞争/历史升级2项通过。真实手机模拟回归发现并修复DOM触摸穿透到地图民居的问题，全部场景pointerdown统一canvas来源边界；屋顶/入口箭头仍接受真实canvas点击。没有重置体验进度或提交/推送本轮变更。最新工作区验收见acceptance.md首节。

2026-11-05：章节脚本首轮实现位于 `backend/content/chapters/`。原两章任务/对白/活动移入legacy.yaml，新第一章chapter01.yaml发布arrival v2 / first-steps v2，复用旧资料、练习与受限助教并新增独立章节测试。新增StoryDefinition/StorySession/ActivityProgress及迁移b63e1a4920d7；条件入口负责首访/提醒/续读，活动等待点处理主动关闭、完成与资料不可用，刷新恢复服务端页码。任务证据与剧情检查点同事务，revision/event_id处理重试；固定旧玩家版本。图书馆提前于镇长欢迎后开放，导航进馆后继续到馆员。章节完成后让出同NPC的第二章旧任务。

内容继续受真实课程约束：第34/42/43页为本轮阅读参与目标，3练习与3章测各自形成正式成绩，章测禁用助教；源PPT仍缺失，当前展示真实提取文本。后续在现有YAML中迭代并发布新版本，格式与操作见content-authoring.md。卡片、镜头和可视化编辑器尚未扩展。本轮实际检查与限制见acceptance.md最新章节演出条目。

收尾版本审查修正：每个活动版本显式固定question_ids；修订题目使用新ID，避免旧题集漂移或静默忽略修改。当前waiting演出优先绑定其活动版本，工作区/请求/助教缓存同步携带版本；已有助教会话保持创建版本。暂停章候选与会话不再挡住独立开放章节，本地工作区在403/404时可退出，服务器检查点保留。后台完整139 passed/7 skipped、前端71项通过，PostgreSQL竞争/历史迁移在最终镜像2 passed。

最终API/Web已更新至独立15176预览；最终镜像浏览器整组3 passed（2.1分钟）：新版第一章桌面与窄屏、旧版两章回归。体验账号校验arrival v2、welcome ready、已完成目标0，仍处于起点。未提交或推送本轮修改；保留deploy/piston-local/。

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

2026-11-05：教师后台独立改版为本地shadcn-vue Button/Card/Table/Dialog + Reka UI + Tailwind4，无Preflight，工具类@scope仅后台/门户弹窗。导航三组，列表优先、表单按需展开、搜索筛选/空状态、班级弹窗与成员分页/记录、题目结构化表单与高级JSON。KeepAlive保留后台编辑草稿，退出路由卸载；权限/接口/版本语义维持服务端现有规则。学生生产界面无改动。新增adminManagement浏览器专项和teacherContent6项单测，适配既有authoring及student E2E到当前交互。独立SQLite/API18086和前端15186实际通过后台8页×4视口、课程编写、材料私有版本/阅读回流、两章旅程及UI回归；外部PostgreSQL/Judge0/PPTX转换仍明确跳过。后端113通过/7跳过，fresh迁移与重复seed通过；细节见acceptance.md，启动见development.md。基于reborn89d1802c工作区改动，未提交/推送或替换原演示部署。

教师后台改版最终检查：npm ci/typecheck/lint/build通过，前端61项通过、后端113通过/7跳过；5项真实浏览器用例通过，PPTX转换用例显式跳过。界面截图保留在忽略目录.tmp/teacher-admin-desktop.png，仅为本机验收产物。

2026-11-05用户批注修订：以教师日常操作重排后台，学习记录仅简单总人数+成员/已完成章节，去掉统计卡/状态分布。新增后端completed_chapters与前置门控共用主线需求查询、117通过/7跳过，无迁移。章节管理按行设置解锁日期，隐藏引擎版本且保留原内容绑定；课程标签页+独立分页，新建编辑使用弹窗，材料/题库分页与新增弹窗。活动/NPC/任务配置按用户要求暂缓。前端78项与后台/课程/媒体live通过，继续沿用15186验收入口及原账号；详情见acceptance.md，不另建计划文档。

2026-11-05继续按界面批注精简：主标题前后的分类/描述删除；班级列表重复标题与介绍删除。学情、章节管理、课程子标签、材料、题库以无边框工具栏+单一数据/分页面板呈现，删除重复面板标题/介绍。业务与弹窗保持；仅前端展示结构，本轮不重跑无关后端/学生旅程。沿用15186验收账号，build后刷新可验收。
