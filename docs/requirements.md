# 已确认需求与每轮维护

本文件记录用户已经确定、后续开发需要持续遵守的要求，以及改变这些要求的原因。它是复核入口，不重复 API 字段、YAML 完整格式、设计令牌或启动命令；这些细节由下表链接的规范维护。实现与测试入口用于核对，列出入口不代表本轮已运行或通过。

## 优先级与变更原则

1. 用户最新的明确要求优先；已撤销方案、讨论中的建议和历史验收中的旧行为不重新成为需求。
2. 本文件保存当前决定，产品、架构、接口、内容编写和设计文档分别拥有各自细节。发现不一致时核对原要求和实现，修正相关文档，不能用旧代码或旧验收反向覆盖用户决定。
3. 需求变化时更新原条目，保留稳定编号并简述改变原因；不要再增加一份平行需求或续做计划。尚未确定的想法不能写成已完成或必须实施的要求。
4. 数据保留、权限、教学证据和生产隔离是变更边界；界面重排、脚本导入或文档整理不能顺带重置学生、恢复旧任务或修改既有成绩。
5. 每轮都复核本文件，并在文末记录影响范围和结论。无新增需求也要明确记录，不能以“没有改需求”为由跳过维护。

## 产品与数据

| 编号 | 已确定要求与决策原因 | 细节归属 | 核对入口 |
| --- | --- | --- | --- |
| DATA-01 | 不保留教学内容快照、内容版本管理或给学生选择版本；采用当前配置，降低早期内容维护复杂度。`schema_version` 是格式，`revision` 是并发控制；试卷本人答案与判定凭据是学习证据，均不代表恢复内容版本。 | [架构](architecture.md)、[内容配置](content-authoring.md) | `backend/app/models.py`、`backend/tests/test_current_content_migration.py` |
| DATA-02 | YAML 经校验后按稳定 ID 更新当前定义；保留账号、班级关系、任务历史、阅读进度、提交与成绩，不重新评分旧提交。正常重启可重复导入，删除当前定义与保留学生历史分别处理。 | [内容配置](content-authoring.md)、[开发手册](development.md) | `backend/app/seed.py`、`backend/tests/test_chapter_content.py`、`backend/tests/test_current_import.py` |
| DATA-03 | 唯一内置主线是 `arrival`“初来小镇：第一堂 Python 课”；其他旧内置委托退出，不因开放其他章节复活。保留完整课程资源与教师自有任务，通用分支验证使用测试定义。 | [产品](product.md)、[内容配置](content-authoring.md) | `backend/content/chapters/`、`backend/tests/test_retired_tasks.py`、`frontend/e2e/rebornJourney.spec.ts` |
| DATA-04 | 接入真实材料包：7 份 PDF 替换学生纯文本课件展示，原材料 ID、561 页页面 ID 与页码保留；34 个视频先作为资料库补充材料，不新增主线或影响原任务、进度和成绩。后台提取文本继续用于检索与引用。 | [内容配置](content-authoring.md)、[架构](architecture.md) | `backend/app/course_media.py`、`backend/tests/test_course_media.py`、`frontend/e2e/courseMediaPack.spec.ts` |
| DATA-05 | 全系统使用学号替代邮箱作为登录账号；学生注册、教师/管理员登录、账户展示、学生搜索和管理命令统一使用该字段。学号为1–32位数字字符串，保留前导零。普通迁移保留旧用户及证据，旧账号待分配真实学号；用户本轮另明确授权清空当前15176本地预览环境的全部旧账号及关联记录，不扩展至其他环境。 | [产品](product.md)、[架构](architecture.md)、[HTTP契约](api-contract.md)、[开发手册](development.md) | `backend/app/account_ids.py`、`backend/app/auth.py`、`backend/tests/test_student_id_auth.py`、`frontend/src/features/EntryPage.vue` |
| DATA-06 | 首个管理员在首次部署时由环境变量创建，重启不覆盖已有密码；所有角色可修改自己的密码，注册与自助改密最低6位。教师可按学号创建/表格导入学生，重置本班学生密码为其学号，批量移出班级并保留账号和学业记录；初始/重置密码须先修改再学习。 | [产品](product.md)、[架构](architecture.md)、[HTTP契约](api-contract.md)、[开发手册](development.md) | `backend/app/bootstrap.py`、`backend/app/student_accounts.py`、`frontend/src/features/TeacherStudents.vue` |
| DATA-07 | 资料与题目独立管理，通过结构化关联归入多个章节，可先上传或录入再归类；资料不设“必读/补充”分类。活动继续定义具体阅读范围和完成条件。题目可供多个练习/测验引用，选题与顺序只有一个关系来源，成绩仍按活动隔离。 | [架构](architecture.md)、[内容配置](content-authoring.md)、[HTTP契约](api-contract.md) | 资料/题库模型、关系迁移、教师内容管理与学习接口 |
| DATA-08 | 题目来源类型只保留“AI生成/导入”，后台手动录入归入导入；参考资料及页码为可选依据。草稿与可用/停用状态分开，未来AI出题进入审核流程；本轮不伪造真实AI生成能力。 | [内容配置](content-authoring.md)、[后台设计](design/teacher-interface.md) | 教师题库与来源字段 |
| DATA-09 | 教学内容通过显式导入或后台维护，与程序更新解耦；启动不自动导入固定课程和归档旧题。保留现有数据库与私有文件持久卷。清理指定开发环境的废弃内容，当前第一堂课使用内容迁入新结构，正式提交和成绩不通过普通迁移删除。 | [架构](architecture.md)、[开发手册](development.md)、[验收记录](acceptance.md) | 显式课程导入、开发清理命令、独立环境与重启检查 |

## 教学、测验与脚本


| 编号 | 已确定要求与决策原因 | 细节归属 | 核对入口 |
| --- | --- | --- | --- |
| LEARN-01 | 阅读、练习、测验等是活动；任务定义要完成的目标与依赖，剧情组织人物回应和活动进出。以 YAML 维护当前脚本，不另建必须使用的编辑器。 | [架构](architecture.md)、[内容配置](content-authoring.md) | `backend/app/quests.py`、`backend/app/story.py`、`backend/content/chapters/chapter01.yaml` |
| LEARN-02 | YAML 可配置整份或指定页范围阅读、视频最低有效观看时长、重点页和重点片段。后端统一验证与累计证据，跳页、拖动播放位置不能冒充完成。 | [内容配置](content-authoring.md)、[HTTP 契约](api-contract.md) | `backend/app/learning_rules.py`、`backend/tests/test_learning_rules.py`、`frontend/e2e/learningRules.spec.ts` |
| LEARN-03 | 学习目标展示真实阅读、练习或观看进度；阅读位置与目标完成度分开，参与记录不称为知识掌握率。保留原主线指定页与已有学习证据。 | [游戏设计](design/game-interface.md)、[产品](product.md) | `LearningSupportPanel.vue`、`backend/app/courses.py`、`frontend/e2e/storyChapter.spec.ts` |
| LEARN-04 | 练习只用单题模式，提供上一题、下一题和侧栏目录跳转；每题“提交答案”，错误可改正后重新提交，跳题保留各题草稿和反馈。 | [游戏设计](design/game-interface.md)、[内容配置](content-authoring.md) | `PracticeWorkspace.vue`、`PracticeQuestionCard.vue`、`backend/tests/test_practice.py`、`frontend/e2e/assessmentPaper.spec.ts` |
| LEARN-05 | 单元测验支持单题与整卷两种排版，共用答案；全部作答后从题目目录上方统一交卷。交卷后服务器锁卷，展示逐题对错，不能修改或绕过整卷接口逐题提交。 | [HTTP 契约](api-contract.md)、[游戏设计](design/game-interface.md) | `backend/app/assessments.py`、`backend/tests/test_assessments.py`、`frontend/e2e/assessmentPaper.spec.ts` |
| LEARN-06 | 测验满分 100，按 `correct_count * 100 // total` 向下取整；未判完为 `null`，不能显示成 0 分。所有题目真实判定结束即完成测验，全对另行表示；错误答案不阻断一次性测验剧情，基础设施故障不算答错。 | [产品](product.md)、[HTTP 契约](api-contract.md) | `backend/app/assessments.py`、`backend/tests/test_assessments.py`、`frontend/e2e/rebornJourney.spec.ts` |
| LEARN-07 | NPC 可按目标、任务、章节状态与真实测验分数回应；镇长结束回复区分成绩。条件组合、入口、分支与选项均受限并在服务端鉴权，显示过的选项提交时也要复验。 | [内容配置](content-authoring.md)、[架构](architecture.md) | `backend/app/story_conditions.py`、`backend/tests/test_story_enrichment.py`、`frontend/e2e/storyChapter.spec.ts` |
| LEARN-08 | 任务结束后仍可进行脚本日常聊天，条件满足时出现更多选项。日常独立于任务，复用演出引擎；随机结果落入会话，恢复与幂等重试不重抽。当前 NPC 的可执行教学和等待活动优先；闲聊不新增任务、学习或成绩记录、世界解锁标记及 AI 调用。 | [内容配置](content-authoring.md)、[架构](architecture.md) | `backend/content/ambient/town-daily.yaml`、`backend/tests/test_story_enrichment.py`、`frontend/e2e/rebornJourney.spec.ts` |

## 学生界面

| 编号 | 已确定要求与决策原因 | 细节归属 | 核对入口 |
| --- | --- | --- | --- |
| UI-01 | 任务日志、学习资料、地图、班级、设置、阅读与练习沿用任务日志风格：近满屏、单一图标标题、统一返回和“回到小镇”，透明外壳与独立纸面卡片之间可见小镇。同一时刻只有一个主要工作区，教师后台另用自己的规范。 | [设计索引](design/README.md)、[游戏设计](design/game-interface.md)、[后台设计](design/teacher-interface.md) | `OverlayHost.vue`、`gameDesign.css`、`townWorkspaces.css`、`frontend/e2e/gameDesign.spec.ts` |
| UI-02 | 按钮、单选、多选、下拉框、滚动条等复用简洁控件；按钮与结果卡使用平面背景、细边线，取消厚底和厚侧边。状态使用统一 Lucide 图标，不用字符勾号或临时手绘图标代替。 | [游戏设计](design/game-interface.md) | `GameIcon.vue`、`gameDesign.css`、`frontend/e2e/gameDesign.spec.ts` |
| UI-03 | PDF 整页适配左侧阅读卡片，不出现左侧整体滚动条；题名、原始页码、上一页/下一页及位置进度放在底部。学生不看转换文本；长文本和试卷仍按内容需要滚动，优先保证可读性。 | [游戏设计](design/game-interface.md) | `PdfReader.vue`、`LessonWorkspace.vue`、`frontend/e2e/readingPosition.spec.ts` |
| UI-04 | 学习支持用“学习目标 / 文档或题目目录 / 当前角色”标签页；文档目录显示各页标题、页码与重点，题目目录负责跳题，不在正文重复排列题目列表。切换保留阅读位置、草稿与助教消息。 | [游戏设计](design/game-interface.md) | `LearningSupportPanel.vue`、`frontend/e2e/learningRules.spec.ts`、`frontend/e2e/assessmentPaper.spec.ts` |
| UI-05 | 仅阅读、练习和测验的学习支持侧卡可折叠、拖动调宽，收起后正文扩展，页头提供展开入口；隐藏区域不接收焦点、不卸载草稿。任务日志、学习资料列表、地图、我的班级、设置等普通工作区不支持侧栏折叠。卡片间距统一，不恢复“间距”菜单。 | [游戏设计](design/game-interface.md) | `LearningSplitLayout.vue`、`SidebarToggle.vue`、`workspaceSidebar.ts`、`frontend/tests/workspaceSidebar.spec.ts` |
| UI-06 | 日志筛选集中在目录内，去掉顶部大统计和空白工具行；“委托目录”清楚可读，无多余房屋图标，封面渐隐露出地图；无结果时居中显示“没有委托”。 | [游戏设计](design/game-interface.md) | `QuestJournal.vue`、`frontend/e2e/gameDesign.spec.ts` |
| UI-07 | 学习资料页面只保留“小镇图书馆”主标题，按章节组织，侧边小目录、顶部搜索和类型筛选；返回恢复原列表状态，只展示已授权资料。 | [产品](product.md)、[游戏设计](design/game-interface.md) | `LibraryWorkspace.vue`、`frontend/e2e/townWorkspaces.spec.ts` |
| UI-08 | 我的班级先介绍当前班级和本人学习进度，再提供切换班级与加入入口；切换成功后才替换当前状态，失败保留原班级。设置账户优先，按分类统一设置项，来源与许可归入关于，不再跳转班级或材料列表。 | [产品](product.md)、[游戏设计](design/game-interface.md) | `ClassroomPanel.vue`、`SettingsPanel.vue`、`frontend/e2e/townWorkspaces.spec.ts` |
| UI-09 | 删除重复标题、无用计数、提交 UUID、作答长提示等干扰；保留真实错误、等待、冲突与权限信息。结果继续到学生主动确认，不能立即关闭反馈；放大文字后内容和必要操作仍清楚可达。 | [游戏设计](design/game-interface.md) | `PracticeQuestionCard.vue`、`frontend/e2e/assessmentPaper.spec.ts`、`frontend/e2e/uiUsability.spec.ts` |
| UI-10 | 教师学生账号管理沿用shadcn后台风格，提供适合上百学生的分页、搜索、状态筛选、跨页选择与明确批量范围；左下角账号菜单提供修改密码弹窗和退出，移除独立密码导航页。暂不展示学习体验、活动与NPC、任务配置入口，后续另行决定。 | [后台设计](design/teacher-interface.md)、[产品](product.md) | `frontend/src/features/TeacherStudents.vue`、`frontend/src/components/admin/TeacherAccountMenu.vue` |

## 助教

| 编号 | 已确定要求与决策原因 | 细节归属 | 核对入口 |
| --- | --- | --- | --- |
| AI-01 | 各学习页面统一简洁聊天排版；使用实际活动角色名称，阅读角色为图书管理员。授权角色淡立绘作背景，文本清晰可读，底部输入提示“向助教提问”，向上箭头发送；不常显字数上限，超限保留原文并报错，不静默截断。切换标签保留消息与输入。 | [游戏设计](design/game-interface.md) | `AssistantPanel.vue`、`LearningSupportPanel.vue`、`frontend/e2e/gameDesign.spec.ts` |
| AI-02 | 只有学生在已授权活动或图书馆资料阅读中明确发送问题才调用助教。走路、NPC 剧情与日常、翻页、播放、任务推进、切换标签和布局均零模型调用；没有配置时显示真实不可用，不伪造回复。内置章节测验不开放助教。 | [产品](product.md)、[架构](architecture.md)、[HTTP 契约](api-contract.md) | `backend/app/assistant.py`、`backend/tests/test_assistant.py`、`frontend/e2e/rebornJourney.spec.ts` |
| AI-03 | 助教上下文、会话与引用按用户、班级、活动或资料隔离，只引用当前授权材料；不授予评分、解锁或发布能力。图书馆直接阅读资料可使用图书管理员助教，无需绑定活动；恢复会话和发送消息均重新鉴权，测验禁用规则保持。 | [架构](architecture.md)、[HTTP 契约](api-contract.md) | `backend/tests/test_assistant.py`、`backend/tests/test_content_security.py` |

## 开发与协作

| 编号 | 已确定要求与决策原因 | 细节归属 | 核对入口 |
| --- | --- | --- | --- |
| DEV-01 | 开发重玩入口是现有菜单中的“开发工具”，不另造页面角落入口。仅同时设置 `REBORN_ENV=development` 和 `REBORN_DEV_TOOLS=1` 才有效，生产不注册重置路由，前端依据服务端能力显示。 | [开发手册](development.md)、[HTTP 契约](api-contract.md)、[游戏设计](design/game-interface.md) | `backend/app/dev_tools.py`、`backend/tests/test_dev_tools.py`、`frontend/e2e/devTools.spec.ts` |
| DEV-02 | 重玩需明确确认，只清认证学生在所选已加入班级的学习与成绩记录，保留账号、成员关系和教学配置；先等待保存与异步操作，保留失败信息。验收新建隔离学生，不能顺手清真实演示账号。 | [开发手册](development.md)、[HTTP 契约](api-contract.md) | `backend/app/dev_tools.py`、`frontend/src/shared/session.ts`、`frontend/e2e/devTools.spec.ts` |
| DEV-03 | Git 提交标题和正文使用中文；保留无关本地修改。仅在 `reborn` 工作，不自动合并 `main`，不改旧库、旧卷或生产。许可和素材来源继续保留，不能编造授权或验收结论。 | [根协作约定](../AGENTS.md)、[开发手册](development.md) | 提交前分支、diff 与暂存范围复核；许可原文件 |
| DEV-04 | 每轮同步需求及对应拥有文档，删除废弃代码和旧说法并检查引用。按变更运行适当验证；通过、失败、跳过、真实运行与契约测试分开记录，证据统一链接验收文档，不复制多套计数和部署状态。 | 本文、[开发手册](development.md)、[验收记录](acceptance.md) | 本文维护记录、对应规范、相关测试入口与实际验收证据 |

表中未写完整路径的 Vue、CSS 和共享模块入口，以[游戏设计的共享入口表](design/game-interface.md#共享入口与样式职责)及相应工作区组件为准；不是要求再建同名实现。

## 每轮维护流程与完成标准

1. **开始前复核。** 阅读本文件及将修改部分的拥有文档，确定本轮影响的编号；核对最新用户要求是否替代旧决定。仅讨论过但未接受的方案不进入要求表。
2. **实现时同步。** 需求变化修改相应原条目及原因；行为或字段变化更新对应产品、架构、API、YAML 或设计规范。操作命令只在开发手册维护，删除过期入口和交叉引用。
3. **检查与验证。** 根据影响选择代码、内容、权限、数据保留和界面检查；文档变更检查事实一致性及链接即可，不把历史功能验证算作本轮重跑。真实测试结果、失败、跳过和证据只记在[验收记录](acceptance.md)，这里链接其结论。
4. **结束前记录。** 在下方写明日期、本轮影响编号、需求是否变化、同步的文档及核对结论。无新增需求仍记录影响范围与“要求保持”；尚未解决的不一致明确标为待处理，不宣称完成。
5. **提交前核对。** 需求条目与拥有文档一致、没有恢复已撤销方案、相关入口有效、验证范围与实际执行相符，才算文档维护完成。提交信息按 DEV-03 使用中文。

近期维护记录只保留最近 5 轮；更早记录合并到 `docs/archive/` 的既有按月文档，保留日期和引用，不为每次修改再建计划或报告。本文件保留当前决定与近期复核，完整历史证据由验收文档及其归档承接。

## 近期维护记录

| 日期与范围 | 需求变化 | 本轮核对与限制 |
| --- | --- | --- |
| 2026-11-08 · 提交推送复核，复核 DATA-05、UI-05、AI-01–03、DEV-03–04 | 要求保持，无新增功能需求。 | 按用户要求提交学号登录、普通侧栏调整及资料助教改动；复核文档、暂存范围与已有验收结果。仅推送reborn，无关目录与本机凭据不纳入；本轮未重复执行功能测试。 |
| 2026-11-08 · 管理员初始化与学生账号管理，复核 DATA-02、DATA-05–06、DEV-02–04 | 新增DATA-06；首位管理员环境变量初始化、自助改密、教师创建/导入/重置密码及批量移出学生。其他要求保持。 | 同步产品、架构、API、两套界面规范、开发、验收与续做；验证与未执行项见[本轮核验](acceptance.md#账号管理核验2026-11-08)。保留已有账号和学习记录。 |
| 2026-11-08 · 密码与后台交互调整，复核 DATA-06、UI-10、DEV-03–04 | 按用户批注将注册/改密最低长度调整为6位，明确大班名册、账号菜单改密弹窗及暂撤学习体验入口；其余要求保持。 | 已先提交上一轮账号功能；同步产品、API、设计、开发与验收，验证见[本轮核验](acceptance.md#密码与后台交互核验2026-11-08)。本轮无数据模型变更，不重置既有账号和记录。 |
| 2026-11-08 · Actions浏览器回归修复，复核 DATA-03、UI-10、DEV-03–04 | 要求保持，无新增产品行为；暂撤入口不恢复，私有测试定义不变为内置主线。 | 同步浏览器用例、开发手册、验收及续做；复现CI种子材料环境，完整执行浏览器套件，结果与未执行范围见[本轮核验](acceptance.md#actions浏览器回归修复2026-11-08)。未改应用或数据模型。 |
| 2026-11-08 · HTTP部署UUID兼容，复核 DEV-03–04 | 要求保持；修复非安全上下文缺少randomUUID导致的客户端异常，不改变权限、证据或幂等重试规则。 | 同步架构、开发手册、验收和续做；覆盖所有5处业务UUID调用及真实浏览器HTTP兼容专项；按用户要求提交推送前复核文档、空白和改动范围，不重复计入功能测试，执行范围见[本轮核验](acceptance.md#http部署uuid兼容核验2026-11-08)。 |

本次运行复核（2026-11-08，DEV-03–04）：要求保持，无应用或产品需求变更。同步验收中的“隔离环境启动尝试2026-11-08”；启动与构建通过，浏览器受环境权限阻断，未完成页面验证。

本次实施入口（2026-11-09，DATA-07–09、DEV-03–04）：用户确认资料库/题库独立化、两种来源和内容发布解耦，并授权实施及分阶段提交；按后台规范实现。此处记录确认范围，不代表功能或验证已完成。各阶段结果统一维护验收记录。

2026-11-09阶段复核（DATA-07–09、DEV-03–04）：模型、关系迁移、运行接口与显式导入/开发清理已实现，需求保持。同步架构、API、内容、开发与产品文档；专项和旧开发库副本升级结果见[内容库核验](acceptance.md#独立内容库核验2026-11-09)。教师界面及完整回归仍在收尾，未将其标记为通过。

2026-11-09后端收尾复核：要求保持，补齐跨教师依赖可用性、共享内容元信息过滤、引用对象并发锁及测验100题上限，后端365通过/10外部专项跳过，空库启动不seed验证通过；见同一内容库验收节。

2026-11-09本轮完成复核（DATA-07–09、UI-10、DEV-03–04）：资料库/题库后台按现有后台组件与作用域主题完成；两种来源、无必读/补充分类、可选知识点/来源依据保持。后端365通过/10外部专项跳过，前端244项与类型/lint/build通过，文档与diff检查通过；24项e2e仅完成发现检查，真实浏览器/PG/外部服务及远端发布仍未完成，详见同一验收节。

2026-11-09推送准备复核（DEV-03–04）：用户已明确授权提交并推送reborn；要求保持。修复共享资料状态徽章浏览器断言歧义，发现检查与该文件lint通过，未宣称浏览器实跑。当前GitHub写入403且CLI未登录，远端仍未更新，等待账号认证。
