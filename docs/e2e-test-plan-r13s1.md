# R-13 §-1 E2E 测试计划（50 条核心链路）

> 测试工具：Cursor 内置 browser MCP + 视觉分析
> 前端 vitest: 1387 PASS / 后端 mvn test 参考

## 认证 (R13S1-001 ~ R13S1-005)

| ID | 链路 | 前置 | 验证点 | 状态 |
|---|---|---|---|---|
| R13S1-001 | LoginPage 渲染 | 无 | hero 场景 + 表单 + 快速体验按钮 | ✅ 视觉确认 |
| R13S1-002 | RegisterPage 渲染 | 无 | 学生/教师角色选择 + 5 字段表单 | ✅ 视觉确认 |
| R13S1-003 | 快速体验 demo 登录 | 后端运行 | 点击"演示账号"→ 跳转 /world | ⬜ 需后端 |
| R13S1-004 | 注册新学生账号 | 后端运行 | 填表→创建→自动登录→/world | ⬜ 需后端 |
| R13S1-005 | 教师注册+邀请码 | 后端运行 | TEACHER 角色→/teacher 路由 | ⬜ 需后端 |

## 世界地图 (R13S1-006 ~ R13S1-015)

| ID | 链路 | 验证点 | 状态 |
|---|---|---|---|
| R13S1-006 | WorldPage 初始渲染 | town_hub 地图加载 + 玩家 sprite | ⬜ 需后端 |
| R13S1-007 | WASD 移动 | 玩家朝向切换 + tile 移动 | ⬜ 需后端 |
| R13S1-008 | 地图边缘 fade 切换 | town_hub→farm_north 过渡 | ⬜ 需后端 |
| R13S1-009 | NPC 交互 E 键 | 按 E 弹出 DialogTree | ⬜ 需后端 |
| R13S1-010 | NPC 日程位置 | 不同时间 NPC 在不同 tile | ⬜ 需后端 |
| R13S1-011 | 天气粒子渲染 | 雨天粒子 + 移动减速 | ⬜ 需后端 |
| R13S1-012 | 日夜切换 tint | 傍晚/夜晚颜色过滤 | ⬜ 需后端 |
| R13S1-013 | HUD Q 按钮 | 打开任务面板 | ⬜ 需后端 |
| R13S1-014 | HUD I 按钮 | 打开背包面板 + 上下文菜单 | ⬜ 需后端 |
| R13S1-015 | HUD M 按钮 | 打开地图传送面板 | ⬜ 需后端 |

## 主线任务 (R13S1-016 ~ R13S1-020)

| ID | 链路 | 验证点 | 状态 |
|---|---|---|---|
| R13S1-016 | 主线面板渲染 | 7 章 × 5 任务 + boss 列表 | ⬜ 需后端 |
| R13S1-017 | 对话完成触发任务 | DialogClose → onDialogClose → completeSubQuest | ⬜ 需后端 |
| R13S1-018 | 资源生成触发任务 | createResource → onResourceCreated | ⬜ 需后端 |
| R13S1-019 | 提交通过触发任务 | submit passed → onSubmissionPassed | ⬜ 需后端 |
| R13S1-020 | Boss 通关推进章节 | defeatBoss → currentChapter+1 | ⬜ 需后端 |

## 闯关塔 (R13S1-021 ~ R13S1-028)

| ID | 链路 | 验证点 | 状态 |
|---|---|---|---|
| R13S1-021 | ArenaPage 7 章切换 | TabRow 7 个按钮 + 进度 | ⬜ 需后端 |
| R13S1-022 | 关卡阶梯 10 级 | 左侧列表 10 项 + kind 图标 | ⬜ 需后端 |
| R13S1-023 | 画像推荐弱项标记 | 紫色虚线描边 + "弱项" badge | ⬜ 需后端 |
| R13S1-024 | FillBlankCard 渲染 | 代码中 input 占位框 | ⬜ 需后端 |
| R13S1-025 | DebugCard 行号标记 | 点击行显示 🐛 标记 | ⬜ 需后端 |
| R13S1-026 | BugMineCard 30s 倒计时 | 计时条动画 + 到期自动提交 | ⬜ 需后端 |
| R13S1-027 | TeachBackCard 字数提示 | 写作提示 + 字数 badge | ⬜ 需后端 |
| R13S1-028 | DualTrackCard Pyodide 预览 | 本地运行 + 输出显示 | ⬜ 需后端 |

## Boss 副本 (R13S1-029 ~ R13S1-033)

| ID | 链路 | 验证点 | 状态 |
|---|---|---|---|
| R13S1-029 | BossPage 4 stage 进度 | Stage 1-4 进度条 | ⬜ 需后端 |
| R13S1-030 | Boss HP 血条 | 进度条动画 | ⬜ 需后端 |
| R13S1-031 | GSAP 招式特效 | 4 种阶段特效（Wave/Fire/Frost/Lightning）| ⬜ 需后端 |
| R13S1-032 | 伤害飘字 | -N 浮动动画 | ⬜ 需后端 |
| R13S1-033 | Victory 结算 | 奖励显示 + 4s 自动返回 | ⬜ 需后端 |

## 工坊 (R13S1-034 ~ R13S1-037)

| ID | 链路 | 验证点 | 状态 |
|---|---|---|---|
| R13S1-034 | WorkshopPage KC 选择 | 65 KC chip 列表 | ⬜ 需后端 |
| R13S1-035 | Mermaid 思维导图渲染 | mermaid.js SVG 输出 | ⬜ 需后端 |
| R13S1-036 | Storyboard GSAP 播放 | 5 幕自动播放 + 场景导航 | ⬜ 需后端 |
| R13S1-037 | 反馈 LIKED/DISLIKED | 按钮状态切换 + toast 确认 | ⬜ 需后端 |

## 对话与友谊 (R13S1-038 ~ R13S1-041)

| ID | 链路 | 验证点 | 状态 |
|---|---|---|---|
| R13S1-038 | DialogTree 打字机 | 逐字显示 + 跳过 | ⬜ 需后端 |
| R13S1-039 | 分支选项导航 | 点击选项 → 切换节点 | ⬜ 需后端 |
| R13S1-040 | ❤ 好感度显示 | friendship > 0 时显示 hearts | ⬜ 需后端 |
| R13S1-041 | 条件解锁特殊对话 | friendship_min 门控节点 | ⬜ 需后端 |

## 背包 (R13S1-042 ~ R13S1-043)

| ID | 链路 | 验证点 | 状态 |
|---|---|---|---|
| R13S1-042 | InventoryPanel 上下文菜单 | 使用/装备/赠送 3 选项 | ⬜ 需后端 |
| R13S1-043 | 赠送 NPC friendship +2 | gift emit → 好感度增加 | ⬜ 需后端 |

## 教师端 (R13S1-044 ~ R13S1-047)

| ID | 链路 | 验证点 | 状态 |
|---|---|---|---|
| R13S1-044 | TeacherHome 班级列表 | 班级卡片 + 创建按钮 | ⬜ 需后端 |
| R13S1-045 | KC 热力图渲染 | 7 章分组 + 颜色编码 | ⬜ 需后端 |
| R13S1-046 | AI 出题审核页 | 拉题 + 通过/驳回 | ⬜ 需后端 |
| R13S1-047 | 直播标签 SSE | 连接指示灯 + 事件时间线 | ⬜ 需后端 |

## 画像 (R13S1-048 ~ R13S1-049)

| ID | 链路 | 验证点 | 状态 |
|---|---|---|---|
| R13S1-048 | 画像访谈 8 维度 | 维度 grid + 问答 | ⬜ 需后端 |
| R13S1-049 | 画像影响出题 | profileSignals 接入 | ⬜ 需后端 |

## 移动端 (R13S1-050)

| ID | 链路 | 验证点 | 状态 |
|---|---|---|---|
| R13S1-050 | 414×896 LoginPage | 响应式布局 + 触摸目标 ≥44px | ⬜ 需设备 |

## 测试环境要求

- 前端 dev server: `npm run dev`（已验证可启动）
- 后端: `mvn spring-boot:run`（需 PostgreSQL + Redis）
- Graph: `services/pythontopia-graph` FastAPI
- 浏览器: Cursor 内置 browser MCP

## 已验证项

- R13S1-001: LoginPage hero 场景渲染正常（像素风 + 樱花 + 喷泉 + NPC）
- R13S1-002: RegisterPage 表单渲染正常（学生/教师选择 + 5 字段）
- 前端 vitest 1387 tests 全绿
- TypeScript 0 errors
