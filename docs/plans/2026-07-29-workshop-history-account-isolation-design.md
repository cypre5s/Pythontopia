# 百晓工坊历史缓存账户隔离设计

> 日期：2026-07-29
> 状态：已由用户提供的重构审阅结论批准
> 所属方向：P0-1 前端服务器状态按用户隔离

## 1. 目标与边界

本次只修复百晓工坊聊天历史在同一浏览器切换账号时可能串读的问题，不同时改
Embedding Profile、周报 SQL、OpenAPI、SSE 或工坊业务编排。

验收结果：

- 内存缓存以真实 `userId + limit` 隔离；
- A 用户已加载的历史不能被 B 用户从缓存或组件局部状态读到；
- 登出或成功登录时取消历史在途读取并清空缓存；
- 换账号后 `KeepAlive` 不保留上一账号的工坊对话流；
- 同一用户、同一 limit 的既有 60 秒缓存和手动刷新行为保持不变；
- 后端接口、DTO 和数据库不变。

## 2. 方案比较

### 方案 A：只在登出时清空现有缓存

改动最少，但缓存仍只按 `limit` 标识；同一标签页直接完成新登录、在途请求晚到，以及
`KeepAlive` 中的局部历史状态都可能越过清理边界。拒绝。

### 方案 B：API 模块自行读取 token 或 localStorage 用户名

可以避免修改调用方，但让纯 API 模块隐式依赖认证持久化格式；测试和未来原生端 adapter
也无法从函数签名看出缓存所有者。拒绝。

### 方案 C：显式所有者键 + 会话生命周期清理

所有历史缓存读写都必须显式提供真实 `userId`，缓存键使用 `userId + limit`。会话登录/登出
统一取消在途读取并清空缓存；历史栏监听所有者变化并丢弃旧请求结果；应用级 `KeepAlive`
按账号重建，清除工坊对话流等组件局部状态。采用。

## 3. 组件与数据流

```text
session.user.id
  ├─> WorkshopPage
  │     ├─> ChatHistoryRail(userId)
  │     │     ├─> list(userId, limit)
  │     │     └─> remember(userId, turn, limit)
  │     └─> remember(userId, savedTurn, limit)
  └─> App KeepAlive key

session.login/logout
  └─> clearWorkshopChatHistoryCache()
        ├─> abort active history GET requests
        └─> clear all userId+limit entries
```

`workshopHistory.ts` 仍是轻量 Query Repository，不引入 Vue Query。请求使用 Axios 已支持的
`AbortSignal`；取消属于账户生命周期，不显示“历史加载失败”。历史栏以请求世代号和当前
`userId` 双重校验晚到结果，即使测试替身或某个 transport 没有响应 abort，也不能把 A 的结果
写入 B 的局部状态。

Repository 自身也维护清理世代号：请求开始时捕获 generation，返回时只有 generation 未变化
才允许写缓存。这样即使 transport 忽略 abort，登出前发起的旧请求也不能在清理后重新灌回 A 的
缓存条目。

## 4. 行为兼容

- 历史后端端点和请求参数仍为 `limit`，`userId` 只用于前端身份隔离，不发送给后端作为授权依据；
- 服务器继续从 JWT 决定历史所有者，前端 `userId` 不是安全凭证；
- 同一用户重复挂载仍命中原缓存；
- `force` 仍绕过缓存；
- 历史保存失败继续不打断工坊主流程；
- 未登录时不发历史请求，也不使用 `0`、`anon` 等伪用户缓存。

## 5. 测试设计

严格执行 RED → GREEN：

1. API 单测先证明不同 `userId` 不共享缓存、清理会取消在途请求；
2. 历史栏组件测试证明 `userId` 变化立即清空旧列表，晚到响应不能覆盖新账号；
3. Session 单测证明登录和登出都调用统一清理；
4. App/WorkshopPage 单测证明真实用户 id 被透传且 `KeepAlive` 随账号重建；
5. 真实 Chromium 在同一 page 中执行 A 登录并加载 A 历史、退出、B 登录、重进工坊，
   断言 B 看不到 A 的唯一文本；
6. 运行前端全量 Vitest、`typecheck`、`lint` 和定向 Playwright。

## 6. 记录与回滚

实现过程中把 RED/GREEN 命令和结果实时写入
`docs/plans/2026-07-29-incremental-refactoring-worklog.md`；通过完整验证后再向
`docs/project/CHANGELOG.md` 追加本次小步重构条目。

本次不改后端和数据，回滚只需恢复前端函数签名、调用方与会话清理挂钩；服务器历史记录不受影响。
