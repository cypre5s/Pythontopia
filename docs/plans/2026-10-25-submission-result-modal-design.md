# 判题结果弹窗设计

日期：2026-10-25

## 目标

训练场正式提交判题完成后，以可关闭的详情弹窗清晰呈现 AC、WA、RE、TLE、CE、MLE 和 SYSTEM_ERROR。AC 结果在真实的运行时间与峰值内存之外，展示同题同语言的匿名百分位。AI 导学助手读取服务端记录的当前题提交结果，并自动进入 AC 复盘与迁移，或进入对应的错因反馈。

## 交互

- 训练场正式提交、导学迁移题提交、同 KC 变式提交和 Bug 矿洞重试完成后自动打开；Pyodide 预览不打开，页面初次加载已有记录时不自动打开。
- 弹窗使用训练场现有暖纸色与像素 UI tokens，AC 用深绿突出，失败用珊瑚红/琥珀色；信息布局采用 LeetCode 提交详情的 verdict、运行时间、Beats、内存、Beats 层级。
- 关闭按钮、Escape 和点击遮罩均可关闭。关闭后训练场保留紧凑的结果摘要入口；Bug 矿洞保留原有状态区，用户可重新查看本次详情。变式 AC 结果在关闭后沿用现有流程返回 Bug 矿洞。
- 展示通过用例数/总用例数、提交时间、语言、本次耗时和内存。失败时展示后端已有的安全失败原因，以及现有的 Bug 矿洞操作。
- 不展示隐藏用例输入、期望输出或其他学生的代码、姓名。
- AC 的运行时间和内存分别用 0–100% 条展示“快于 X% 提交”和“内存占用少于 X% 提交”。
- 新提交只自动处理一次：AC 先生成复盘卡，再由 AI 生成迁移题草稿；服务端检查参考解并用隔离判题器覆盖公开样例和隐藏用例，通过后才创建学生私有题；WA、RE、TLE、CE、MLE 自动触发错因诊断；SYSTEM_ERROR 不算学生错误，也不改变导学阶段。
- 导学服务端快照返回最新提交 ID、判定和提交题目 ID，前端据此恢复未处理事件，避免只依赖页面内临时状态。诊断请求仍由后端校验提交归属、题目绑定和 verdict。
- AI 迁移题按学生、导学会话和 Graph run 绑定并幂等创建；仅题目所有者可查看题面、提交代码和读取隐藏测试。公开课程列表、相似题检索、时间线卡片、回放 JSON 和 SSE 均不包含参考解或隐藏用例，不新增课程种子题，不影响 39 道题计数。

## AI 导学节点与端到端适配

实现复用 Pythontopia 已有的 `validate_transition → coach_decide → fetch_evidence → generate_card → guardrail → decide_actions` 流程与 Vue 卡片体系，对照 Alethicode Tutor Graph 的事件路由、证据组装、卡片校验、教练计划、判题反馈和持久化节点接线，不复制其 LangGraph、Java 内部工具 API 或旧前端实现。读取其 [Graph builder](https://github.com/cypre5s/Alethicode/blob/main/services/tutor-graph/app/graph/builder.py)、[transfer nodes](https://github.com/cypre5s/Alethicode/blob/main/services/tutor-graph/app/nodes/transfer.py) 与 [transition rules](https://github.com/cypre5s/Alethicode/blob/main/services/tutor-graph/app/graph/transitions.py) 作为行为参考。

- 主学习事件：`READING`、`IDEATING`、`SKELETON`、`CODING`、`ERROR_FEEDBACK`、`AC_REVIEW`、`TRANSFER`。
- 辅助节点/事件：`CHAT`、`COMPACT`、`KNOWLEDGE_REVIEW`、`EXECUTION_TRACE`、`VISUALIZE`、`PARSONS`、教练学习计划与反馈。
- 服务端会话快照、提交事件、证据/画像输入、Graph 检查点和卡片回放仍是权威链路；前端恢复未处理提交反馈并使用既有学习卡片渲染。
- TRANSFER 的模型私有草稿只交给服务端自测/入库；Java 投影先脱敏再写时间线、回放与 SSE。Graph 每次 run 前按 Java 持久化快照恢复，避免投影失败后内存阶段超前。

## 统计口径

- 比较范围：全站、同一道题、同一语言、判定为 AC 的正式提交。
- 样本单位：提交记录。同一用户的多次 AC 提交分别计入样本。
- 运行时间：当前判题器对各测试用例实测 wall time 的累计值，沿用现有 `runtime_ms` 口径。
- 内存：各测试用例实测 peak RSS 的最大值，转换为 KB 后写入已有 `memory_kb` 字段。项目固定的 Piston 3.1 执行响应不提供资源用量；镜像中的轻量 `wait4(2)` helper 返回进程峰值 RSS（Linux KiB），API 以字节提供给后端。缺少采集值时保留 null，不伪造数据。运行时间由 Piston 进程的单调时钟实测。
- 两项指标独立统计：至少有 10 条可用 AC 提交时才返回对应百分位；数量未达门槛时仍展示本次真实指标，但隐藏排名与样本积累文案。
- 百分位为比较样本中指标值严格大于本次值的记录占比；同值记录不算“击败”。提交本身位于 AC 样本中，样本数量也计入本次提交。
- API 只返回百分位与达标后的样本数，不返回逐条提交数据。

## 后端与前端范围

- 后端从项目 Piston 镜像返回的运行时间和峰值 RSS 读取指标，扩展判题结果聚合，并持久化每题累计耗时及最大内存；已有数据库字段可复用，无需迁移。
- 新增仅提交者可访问的提交性能接口。接口从数据库读取归属、判定和指标，不接受客户端传来的排名输入。
- 导学提交反馈写回最新 submission id/verdict 和会话阶段；AC 提交的 AC_REVIEW 卡与后续 TRANSFER 卡、失败的 error_diagnosis 卡都关联源 submission id，以便安全恢复且不重复生成。迁移题使用学生私有 `course_problem` 记录、真实判题器与参考解自测，不映射到静态课程题。
- 前端新增可复用结果详情弹窗组件和明确 DTO/API 类型；TrainingPanel 接收普通与导学迁移题判题结果，BugMinePanel 接收重试结果，训练场变式流程也使用该弹窗。
- 课程共享客户端 API 契约、项目 CHANGELOG 和工作日志随实现同步更新。新增 API 需有 DTO、统一权限检查和测试。
- 不新增第三方依赖、排行榜姓名展示、历史提交列表或素材文件。

## 可验证结果

- 正式 Piston 镜像返回正数运行时间和峰值 RSS；改变程序分配量会反映到峰值 RSS。测量缺失时仍能判题且保存 null。
- 运行时间和内存分别基于同题同语言 AC 提交计数；小于 10 条隐藏百分位，达到 10 条显示正确的“Beats”百分位。
- 提交者可以访问自己的聚合结果；其他学生不能读取该提交的排名接口。
- TrainingPanel、TutorWorkflowPanel 提交、TrainingPanel 变式和 BugMinePanel 重试均能显示各 verdict；训练场可关闭并重开，变式 AC 关闭后沿用原导航；移动尺寸下可用，隐藏用例信息不泄露。
- 圆面积题 AC 后自动出现复盘和 AI 迁移题；迁移题参考解未通过样例/隐藏用例时拒绝入库，成功后可用正式判题；WA/RE 等自动出现与真实提交绑定的诊断；SYSTEM_ERROR 不推进个人学习状态；刷新后不会重复发卡或卡在错误 Graph 阶段。
- 发版以 `web-vYYYYMMDD.N` tag 触发 `.github/workflows/publish-ecs-release.yml`，该工作流重建并校验完整 Web 发布包后上传 GitHub Release。

## 参考

- [LeetCode 新版提交详情说明](https://leetcode.com/discuss/post/2238519/share-your-feedback-to-the-new-question-detail-page/)：运行时间/内存分布视图与提交详情布局。
- [LeetCode 判题与提交说明](https://support.leetcode.com/hc/en-us/articles/360012016874-Start-your-Coding-Practice)：正式提交结果、通过历史及失败结果展示。
