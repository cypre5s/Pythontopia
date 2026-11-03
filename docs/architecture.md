# 系统边界与数据流

`frontend` 使用 Vue 3、Pinia、TypeScript 和 Phaser 3。`game/adapters/world.ts` 是世界渲染边界；Vue 的 WorldCanvas、GameHUD、DialogueLayer 和 OverlayHost 管理画布、任务信息、脚本对白和唯一工作区。阅读器和编辑器延迟加载。API 契约见 api-contract.md。

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

