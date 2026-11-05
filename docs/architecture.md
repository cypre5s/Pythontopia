# 系统边界与数据流

`frontend` 使用 Vue 3、Pinia、TypeScript 和 Phaser 3。`game/adapters/world.ts` 是世界渲染边界；Vue 的 WorldCanvas、GameHUD、DialogueLayer 和 OverlayHost 管理画布、任务信息、脚本对白和唯一工作区。阅读器和编辑器延迟加载。API 契约见 api-contract.md。

学生界面由app/gameUi.css与features/learningUi.css提供响应式布局，HUD在安全视口内按三行容器流动，不根据每帧世界状态轮询。shared/modalFocus.ts统一学习弹层的初始焦点、可见控件Tab边界、Esc与调用入口恢复；地图仅保留Esc关闭，不增加WASD/方向键选择或自动聚焦目标，关闭后回到世界焦点。背后的HUD为inert，窄屏的非当前学习视图同时hidden/inert。session.navigate/back保留最多6项返回历史，open作为新的顶层入口会清空历史，关闭/退出不保留旧路径。uiPreferences仅保存设备上的字号/动效选择，不产生模型请求。

`backend/app` 是一个 FastAPI 项目：auth、courses、curriculum、classrooms、quests、practice、assistant、storage 共用 SQLAlchemy 模型、数据库会话和鉴权。无旧 Java 服务和业务兼容层。PostgreSQL 是正式状态源，Alembic 是唯一迁移机制。SQLite 只用于轻量测试。登录使用随机不透明 bearer 会话令牌，数据库只存摘要；密码采用独立盐的 scrypt。

## 核心流

地图交互的权威配置为 `data/world/world.json`：地图尺寸/出生点、建筑名称/用途/门前锚点、普通交互点与portal源/目标/门禁。前端在构建时导入，后端加载同一文件；建筑交互点从建筑anchor派生，避免两处维护坐标。小镇和图书馆均由Tiled图层与素材对象渲染，建筑通过object_name关联。房屋贴图和仅含中文名称的名牌均可点击导航，门口显示小型透明素材箭头。

map工作区复用静态地图资源绘制预览，不创建额外Phaser.Game；固定当前玩家地图，筛选只改变标记，前往操作交给同一世界导航。paintMapPreview可接收逻辑裁剪范围，地图工作区仅画逻辑地图，登录背景默认仍画全render_bounds。walk_bounds限制客户端寻路和服务端位置校验，render_bounds控制外景与有界镜头范围；centered相机不在小地图边缘夹紧，bounded相机在外景边缘夹紧。逻辑地图尺寸与旧存档坐标保持一致。

玩家地图标记读取session.mapPosition：独立内存快照可保存Sprite当前浮点位置，打开地图通过WorldHandle.getMapPosition同步抓取，运动完成与暂停时刷新；不等待位置PUT成功，也不改变原1.5秒持久保存节流。world.player仍是整数服务器快照，浮点显示坐标不作为事件证据或保存payload。账号、班级、世界切换清空旧live位置并隔离旧回调，跨图仍由服务器destination决定。玩家标记始终显示，窄屏仅滚动map-stage使其可见，不移动页面焦点或重置用户筛选。

交互可用points声明同一语义的多处真实位置。library_shelf保留旧ID，绑定10组书架；地图右侧只呈现一个语义项，运行场景展开各物理锚点，点击指定书柜或导航时选择相应可达位置。服务端物件证据在任一真实锚点半径2内才接受；历史脚本与事件不改写，旧任务及对白中“西侧书架”只在显示层改称。场景每帧按实际插值位置更新附近提示，暂停、编辑输入或离开半径时清除；关闭建筑不进入E候选。

跨图通过POST /world/transition；服务端校验当前图、入口距离和required_flags，成功后返回配置落点与世界状态。普通PUT /world/position只允许同图更新，防止绕过入口。前端保存源位置后请求传送，收到权威落点才切图；网络结果不确定时读取世界对账，不盲目重复传送。旧室内存档可原位继续并出馆。

1. 学生携带会话和明确 classroom_id 请求世界。服务端检查成员、发布、开放时间和前置，为当前用户创建版本固定的任务实例。
2. Vue 发送带 event_id 的交互证据；任务引擎验证当前可执行节点、人物/地点或学习记录/正式提交，事务保存事件、节点状态和世界标记。唯一约束处理重复请求；ALL/ANY 和分支由白名单解释器计算。
3. 教学资料保留 material/version/page_id。上传进入私有存储与可恢复处理状态；文件流再次鉴权。阅读参与单独入库，不冒充掌握度。
4. 概念题在服务端比对私有答案。编程提交先入库，私有用例留在后端，再由 JudgeAdapter 发送 Judge0；后台轮询独立于浏览器。送交结果不确定时保留待对账状态，禁止盲目重送。基础设施故障与错误答案分开。
5. 主动助教消息进入活动范围隔离的会话。统一 create_agent 根据角色配置和授权真实内容构造上下文，只提供受限只读工具。词法检索返回真实版本/页码引用并再验证。凭据只存在服务端。

## 持久化

章节演出新增 StoryDefinition、StorySession、ActivityProgress。章节YAML在导入时校验并存JSON快照，任务和演出版本与玩家任务实例绑定；全局NPC身份不随章节发布覆盖。演出会话仅是持久化检查点，无每学生常驻后台循环。服务端按入口条件选择对白，前端只呈现当前节点/选项与等待活动。对白末句的检查点和学习/练习返回使用同一事务的任务证据服务；event_id去重、revision拒绝过期推进，每用户/班级只允许一个active或waiting演出。活动等待点在刷新后恢复；主动关闭才产生暂停台词。

Vue会话协调对话、唯一工作区和Phaser导航。新任务的navigation_target先带玩家走到NPC，通过对话选择打开活动；小镇进入图书馆后继续走到馆员，离馆后也能续走到目标。读页请求串行落库，返回剧情前等待保存完成。阅读位置、参与页、代码草稿和成绩各自保存；普通演出与页码保存均不调用模型。已完成章节演出在另一章有可执行NPC目标时交还该章交互，避免章节总结挡住后续任务。

用户/会话、班级/成员、章节/KC、材料/版本、活动/版本、NPC、任务定义/版本/实例/节点、世界位置/标记、题目/私有用例/提交、学习事件/参与记录、章节发布、助教会话/调用记录分别建模。JSON 保存受校验的任务脚本和内容，不承载全部业务状态。

## 环境隔离

Reborn Compose 项目、数据库、卷、对象存储前缀和端口独立。主栈只有 db/api/web；Judge0 是可选独立隔离栈。默认数据库不对外发布，开发覆盖文件也只绑定 loopback。现有线上数据库、生产服务和 main 不参与迁移。CI 只检查 reborn，不发布生产。
