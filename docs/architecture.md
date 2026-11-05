# 系统边界与数据流

`frontend` 使用 Vue 3、Pinia、TypeScript 和 Phaser 3。`game/adapters/world.ts` 是世界渲染边界；Vue 的 WorldCanvas、GameHUD、DialogueLayer 和 OverlayHost 管理画布、任务信息、脚本对白和唯一工作区。阅读器和编辑器延迟加载。API 契约见 api-contract.md。

学生界面由app/gameUi.css与features/learningUi.css提供响应式布局，HUD在安全视口内按三行容器流动，不根据每帧世界状态轮询。shared/modalFocus.ts统一当前弹层的初始焦点、可见控件Tab边界、Esc与调用入口恢复；背后的HUD为inert，窄屏的非当前学习视图同时hidden/inert。session.navigate/back保留最多6项返回历史，open作为新的顶层入口会清空历史，关闭/退出不保留旧路径。uiPreferences仅保存设备上的字号/动效选择，不产生模型请求。

`backend/app` 是一个 FastAPI 项目：auth、courses、curriculum、classrooms、quests、practice、assistant、storage 共用 SQLAlchemy 模型、数据库会话和鉴权。无旧 Java 服务和业务兼容层。PostgreSQL 是正式状态源，Alembic 是唯一迁移机制。SQLite 只用于轻量测试。登录使用随机不透明 bearer 会话令牌，数据库只存摘要；密码采用独立盐的 scrypt。

## 核心流

1. 学生携带会话和明确 classroom_id 请求世界。服务端检查成员、发布、开放时间和前置，为当前用户创建版本固定的任务实例。
2. Vue 发送带 event_id 的交互证据；任务引擎验证当前可执行节点、人物/地点或学习记录/正式提交，事务保存事件、节点状态和世界标记。唯一约束处理重复请求；ALL/ANY 和分支由白名单解释器计算。
3. 教学资料保留 material/version/page_id。上传进入私有存储与可恢复处理状态；文件流再次鉴权。阅读参与单独入库，不冒充掌握度。
4. 概念题在服务端比对私有答案。编程提交先入库，私有用例留在后端，再由 JudgeAdapter 发送 Judge0；后台轮询独立于浏览器。送交结果不确定时保留待对账状态，禁止盲目重送。基础设施故障与错误答案分开。
5. 主动助教消息进入活动范围隔离的会话。统一 create_agent 根据角色配置和授权真实内容构造上下文，只提供受限只读工具。词法检索返回真实版本/页码引用并再验证。凭据只存在服务端。

## 持久化

用户/会话、班级/成员、章节/KC、材料/版本、活动/版本、NPC、任务定义/版本/实例/节点、世界位置/标记、题目/私有用例/提交、学习事件/参与记录、章节发布、助教会话/调用记录分别建模。JSON 保存受校验的任务脚本和内容，不承载全部业务状态。

## 环境隔离

Reborn Compose 项目、数据库、卷、对象存储前缀和端口独立。主栈只有 db/api/web；Judge0 是可选独立隔离栈。默认数据库不对外发布，开发覆盖文件也只绑定 loopback。现有线上数据库、生产服务和 main 不参与迁移。CI 只检查 reborn，不发布生产。

教师后台 /teacher 使用独立的管理布局与本地 shadcn-vue 组件源码，组件仅由教师路由引用。Tailwind 不加载 Preflight，工具类使用 CSS @scope 限定在 .teacher-page / .teacher-dialog；弹窗门户同样携带主题类，避免导航离开后台后影响学生/登录界面。TeacherContent 与课程编辑用 KeepAlive 保留未提交草稿，退出后台账号后随路由卸载。表单、筛选与分页为本地界面状态，权限、发布、版本与成绩仍由原有服务端接口判定。
