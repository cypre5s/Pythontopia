# 2026-06-24 浏览器全流程点击验收

## 目标

在本地完整栈（Vite + Spring Boot + graph + RAG + Postgres + Redis）上补齐真实浏览器验收，覆盖学生主链路与教师/班级闭环，并用失败前后对比证明修正有效。

## 环境

- 前端：`http://127.0.0.1:5173`
- 后端：`http://127.0.0.1:18080`
- Graph：`http://127.0.0.1:18091`
- RAG：`http://127.0.0.1:18092`
- 启动方式：`./dev.sh`
- 健康复核：`/healthz` 四端均返回 `{"status":"ok"}`

## 修前发现

| 项 | 修前现象 | 根因 |
| --- | --- | --- |
| `/guide` 桌面锚点 | `publicAcceptance` 25 passed / 1 flaky / 1 failed，失败用例卡 120s | E2E 选择器把 AppLayout 的 skip-link 当成指南目录锚点点击，元素在视口外 |
| 图书馆 RAG `input函数` | 首次 flaky，retry 通过 | 等待条件只看容器 visible，未等答案引用或片段条目真正带页码出现 |
| 教师成员表 | 学生已成功加入，教师成员 tab 仍显示“暂无数据” | `ClassroomMembersTab` 在 setup 阶段把 `props.members` 固定成普通常量，异步刷新后表格不更新 |
| 班级任务 evidence | 教师绑定题目发布任务后，学生公告板没有“选择提交”证据入口 | `ClassroomQuestService` fan-out 到学生 `quest` 时未设置 `required_evidence_type='submission'` 与 `target_problem_ids` |

## 代码修正

- `frontend/e2e/publicAcceptance.spec.ts`
  - 指南锚点限定到 `navigation[aria-label="章节导航"]`，避开无障碍 skip-link。
  - RAG 验收改为轮询“AI 引用或片段列表任一出现第 N 页证据”。
- `frontend/src/components/teacher/ClassroomMembersTab.vue`
  - `rows` 改为 `computed`，保持成员列表对异步 props 更新响应。
- `backend/src/main/java/com/pythontopia/classroom/quest/service/ClassroomQuestService.java`
  - 题目型班级任务 fan-out 时写入 `requiredEvidenceType=submission` 与 `targetProblemIds=[targetProblemId]`。
- `backend/src/test/java/com/pythontopia/classroom/quest/ClassroomQuestServiceTest.java`
  - 捕获 `QuestInsert`，断言 submission evidence、目标题目、训练场 deep link。
- `frontend/e2e/teacherStudentFullFlow.spec.ts`
  - 新增教师/学生班级闭环浏览器验收：教师创建班级、学生加入、教师发布题目任务、学生训练场 AC、公告板提交证据、教师看板同步 1/1。

## 修后量化结果

| 验证命令 | 结果 |
| --- | --- |
| `mvn -s .mvn/settings.build.xml -q -Dtest=ClassroomQuestServiceTest test` | 通过 |
| `BASE_URL=http://127.0.0.1:5173 PYT_E2E_REGISTER=1 PYT_E2E_TEACHER_INVITE_CODE=pytopia-teacher npx playwright test e2e/teacherStudentFullFlow.spec.ts --project=chromium --retries=0` | 1 passed，10.4s |
| `BASE_URL=http://127.0.0.1:5173 PYT_E2E_REGISTER=1 npx playwright test e2e/publicAcceptance.spec.ts --project=chromium --retries=0` | 27 passed，8.3m |
| `npm run typecheck` | 通过 |

## 浏览器覆盖面

- 认证：未登录守卫、注册/登录、快速体验。
- 世界：`town_hub` Phaser 画布与 HUD。
- 主线：地图进入学习防点击穿透。
- 指南：桌面锚点、图片放大、移动端横向溢出。
- AI：多轮 SSE、澄清分支、失败态与重试。
- 工坊：向导深链到工坊、真实流式回复。
- RAG：`input函数`、`for循环`、`列表` 三问均返回带页码证据。
- 训练场：AC / WA / RE 三态，错误面不泄露内部信息。
- 复盘/画像：复盘大厅与画像页可见。
- 深链刷新：`/guide`、`/workshop`、`/mainline-map`。
- 安全错误面：登录失败与 API 错误响应不泄密。
- 教师班级：创建班级、成员同步、发布任务、看板 tab 切换、学生完成证据、教师完成率 1/1 同步。

## 证据路径

- `qa-screens/public-acceptance-2026-06-24_09-53-16/`
- `qa-screens/teacher-student-full-flow-2026-06-24_09-52-58/`

## 剩余风险

- 本次是本地完整栈浏览器验收，不等同公网 ECS 验收。
- 测试过程中生成了临时 E2E 班级、学生、教师和提交记录，均带 `E2E` 前缀，后续可按需清理。
