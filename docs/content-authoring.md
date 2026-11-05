# 内容配置与发布

## 章节演出文本（2026-11-05）

默认任务、NPC对白和活动绑定现在位于 `backend/content/chapters/`，不再在 `app/seed.py` 编写剧情。`legacy.yaml` 保留原两章的版本1；`chapter01.yaml` 是新第一章：章节179、任务 `arrival` 版本2、演出 `first-steps` 版本2。题目答案留在后端 `backend/content/questions/concepts.private.yaml`，不得复制进前端或公开章节响应。

先在backend目录执行 `python -m app.chapter_content --check` 校验文本，再经 Alembic 和 `python -m app.seed` 导入不可变快照。已存在的任务、演出或活动版本发生变化会拒绝覆盖；修改稿件应一起增加章节包、任务和演出版本，并按需生成新的活动版本。编辑文本不会实时改写学生正在使用的快照。第一章新版需在教师“章节发布”中设置任务版本2；已有学生任务实例继续固定原版本，新学生领取新版，不自动重置旧成绩。

章节包包含 `schema_version/id/version/chapter_id/quest/story/activities`。`quest` 仍使用既有任务DAG；节点可配置 `navigation_target` 指向NPC。`story` 包含：

- `bindings`：命名活动绑定，每项明确 `activity_id/version/goal_id`。
- `entries`：每个NPC有从上到下匹配的入口列表，最后一项必须为 `when: {always: true}`。支持 `goal_complete`、`goal_ready`、`activity_started` 和 `always`；反复访问通过重新选入口实现。
- `nodes`：本轮支持 `dialogue`、`activity`、`end`。对白含稳定ID、说话者、文本、next或带ID的选项。`checkpoint` 只能引用交谈目标，并由服务端确认。单次演出无环，缺失跳转和不可达节点拒绝发布。
- `activity`：引用binding，必须定义 `returns.completed/suspended/unavailable`。打开活动保存等待点，玩家主动关闭后由服务端检查证据决定返回分支；刷新直接恢复等待活动。
- `checkpoint_effects`：本轮只允许明确的 `library_unlocked` 门禁效果；欢迎对白完整确认后提前开放图书馆。

新第一章依次为：镇长欢迎→馆员介绍→指定阅读→教练介绍→概念练习→镇长测试说明→独立章节测试→章节总结。镇长重复拜访只提醒；馆员记录中途离开与重返；练习和测试分别保存正式结果。章节测试不开放助教。

每个活动版本必须显式固定 `content.question_ids`，阅读活动使用空列表。增加题目时使用新题目ID并创建活动新版本，把新题加入新版本题集；旧版本列表保持原样。导入器拒绝同ID题面/答案变化，不静默忽略修订。演出等待期间活动、正式提交与助教默认使用等待点绑定的版本，前端同时显式传递版本并隔离助教会话缓存。

`ch1-lesson` 版本2绑定资料186版本1，馆员作为教学身份；完成规则为 `content.completion.required_page_ids: [10715,10723,10724]`，对应真实第34、42、43页。界面提供指定页导航并说明这只是阅读参与目标，掌握情况由练习和测试判定。服务器按用户、班级、活动版本保存页码和参与页集合；单条participation记录、访问末页或关闭窗口不能替代完成规则。完整文本资料仍可自由查阅。当前源PPT缺失，继续明确展示真实提取文本；已上传且转换ready的PPT/PDF仍使用既有私有阅读器。

本轮尚未实现可视化演出编辑器、任意循环、卡片节点或脚本化镜头节点；跨地图自动导航已衔接小镇与图书馆，地图渲染仍使用现有适配器。未来扩展应增加受限指令和验证，不允许在脚本中执行代码。YAML拒绝重复字段、锚点、别名、自定义对象及过深/过大的数据。

权威 HTTP 字段见 api-contract.md。真实课程种子位于 data/course-pack-43/json；保留原始文档、页面、章节和知识点 ID。种子可以重复运行，不创建默认教师或班级。39 道旧编程题全部保留为后端内容，未经审查者不开放；476 圆面积题经过题面与用例核对。477 存在题面与隐藏输出冲突，保持未发布。

## 教师操作

地图配置位于 `data/world/world.json`，与章节文本分工：地图定义建筑、门和地点，章节脚本引用稳定的NPC/地点ID。maps中的建筑必须配置中文label、purpose、status、object_name及可达anchor；open建筑绑定portal，未开放室内使用closed/landmark并给出真实用途，不能虚构可进入场景。portal声明from、to、required_flags和blocked_message；源/目标都必须落在可达位置，并提供返回路线。建筑anchor不再复制进interactions。调整后运行 `node scripts/validate-world.mjs` 与 `node scripts/audit-assets.mjs`，再重建前后端。新增地图仍需使用现有支持的renderer或增加相应渲染实现。

当前11栋建筑都有屋顶名称及地图工作区目标，屋顶不附加开放状态文字；入口/出口使用小型素材箭头。地图工作区固定当前图，右侧卡顶部筛选建筑/人物，前往才触发实际导航；预览只画逻辑地图，外围景观只保留在实际场景与登录背景。未开放建筑不显示E提示或未开放弹字。图书馆门禁在新版镇长欢迎后开放，室内家具布局位于library.json。

同一物件可用`points: [{map_key, tile_x, tile_y}, ...]`替代单点字段，1至64个唯一真实位置，不能混用两种形式。每个位置都须在可走区，并与美术对象的interaction_id及脚底锚点一致。图书馆10组书架统一绑定library_shelf，地图只显示一个语义项；点击柜体优先去该柜前，普通导航选择最近可达柜前。物件证据半径2，不能用一个大范围点覆盖整个房间。旧任务版本/事件保持原值，显示名称别名由前端兼容。

地图可声明walk_bounds（可行走矩形）、render_bounds（景观及有界镜头矩形）与camera_mode（centered/bounded）。小型室内使用centered，角色保持居中；大型室外扩展render_bounds但不扩展walk_bounds。静态素材和碰撞须保持一致，出生、NPC、交互与传送位置均须在行走范围内，保留旧存档坐标。新增素材保留原始PNG和来源记录，用图集帧或图层配置排布。

管理员通过 CLI 显式创建教师账号。教师登录后台，创建班级并分发加入码；学生注册后加入。教师上传文件，等待 ready 或查看 failed 原因，再创建自己的教学活动，绑定章节、NPC 和材料。共享种子不能被教师原地覆盖；新内容使用自有活动和新任务版本。

任务使用 JSON 节点列表编辑。先校验再保存，版本已存在时拒绝覆盖。节点支持 TALK_TO_NPC、PLAY_DIALOGUE、REACH_LOCATION、INTERACT_OBJECT、STUDY_LESSON、COMPLETE_PRACTICE、PASS_ASSESSMENT、REPORT_BACK。dependencies 是节点 ID 列表，aggregation 默认 ALL，可用 ANY；optional 是可选目标。学习/练习节点必须绑定实际 activity_id 和 activity_version。

分支父节点给出 choices（id、label），子节点用 condition: {node, choice} 引用直接父节点。任务完成只要求实际选择的支路；不得用任意代码或 SQL 作为条件。effects 只接受 set_world_flag。重复 ID、悬空依赖、环、错误内容、非法效果应在保存前被拒绝。

NPC 的教学身份由活动 npc_key 配置决定，不创建第二套页面或 Agent。对白是确定性脚本，不调用模型。新增 NPC/活动/任务通过配置 API 完成。世界坐标须经地图可达校验，图书馆室内边界为18×14。

教师按班级发布章节，指定 published/paused、带时区 opens_at、版本与可选前置。界面默认 Asia/Shanghai，服务端按当前时间即时判定。暂停不删除成绩；当前策略为暂停后拒绝继续访问该章，重新开放后从已保存状态继续。教师预览不可写入学生成绩。

## 媒体与安全

文本使用 UTF-8；PDF 支持私有阅读；MP4/WebM 支持鉴权文件流与 Range；PPT/PPTX 必须经过配置的隔离 LibreOffice 转换。未安装转换环境时保留失败状态，不能伪报成功。不要将私有材料复制到 frontend/public，不要把隐藏答案写进题干、公开样例或助教可见上下文。文件更新必须新版本，旧引用保留归属。

## 自有章节与知识点

教师后台“课程与知识点”可新增和编辑自有章节/KC；共享的原7章/65KC只有管理员可修改。新增内容仍属于Python语言基础单一课程，并非另建多课程平台。无引用的自有条目可删，有活动、任务、发布或历史快照引用时返回409。

活动可多选同章的共享/自有KC。绑定进入新活动版本并保留真实源代码与页码；不能引用另一位教师的私有KC。新增题目也会生成活动新版本，界面会显示新版本号，任务JSON须引用该版本，避免发布旧空题组。

任务日志按主/支线和进行中/已完成筛选。侧线不隐式阻挡主线的章节前置。教学、练习与其他工作区共享同一实现，新增绑定不复制页面。

排课界面时区由frontend的VITE_TIME_ZONE或容器构建参数REBORN_TIME_ZONE配置，默认Asia/Shanghai。所有API时间戳带明确偏移，数据库保存UTC；DST不存在或重复的本地时刻会被界面拒绝，避免静默错排。
