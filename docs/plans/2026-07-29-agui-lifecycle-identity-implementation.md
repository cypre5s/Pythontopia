# AG-UI 生命周期身份契约实施计划

> 执行准则：小步 TDD；只收敛主 orchestrator 成功生命周期，不夹带其他流端点或 transport 重构。

**目标：** 让主 orchestrator 流以稳定 thread 身份和请求级唯一 run 身份发送成对
`RUN_STARTED/RUN_FINISHED`，并由前端协议层保留。

**技术栈：** Python 3 / FastAPI / pytest，Vue 3 / TypeScript strict / Vitest。

---

## Task 1：建立 Graph 生命周期 RED

**修改：**

- `services/pythontopia-graph/tests/test_sse_agui.py`
- `services/pythontopia-graph/tests/test_orchestrator_route.py`

步骤：

1. 增加 helper 的有上下文终态期望，同时保留无上下文兼容测试；
2. 解析主路由 SSE 的 `RUN_STARTED/RUN_FINISHED` 数据；
3. 断言两端 `threadId/runId` 完整且相同；
4. 同一 session 连续发起两次请求，断言 run 唯一；
5. 运行定向 pytest，确认当前终态缺身份、重复 run 身份产生预期失败。

命令：

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_sse_agui.py tests/test_orchestrator_route.py
```

## Task 2：建立前端身份保留 RED

**修改：**

- `frontend/tests/composables/useSseStream.spec.ts`

步骤：

1. canonical 开始与结束事件加入相同 `threadId/runId`；
2. 断言归一化事件流保留字段；
3. 保留历史 `result` 和缺身份旧事件的兼容用例；
4. 运行定向 Vitest，确认当前 parser 丢字段产生预期失败。

命令：

```bash
cd frontend
npx vitest run tests/composables/useSseStream.spec.ts --coverage.enabled=false
```

## Task 3：最小实现

**修改：**

- `services/pythontopia-graph/pythontopia_graph/sse_agui.py`
- `services/pythontopia-graph/pythontopia_graph/routes/orchestrator.py`
- `frontend/src/composables/agui.ts`

步骤：

1. `run_finished/translate` 支持可选生命周期上下文，无上下文输出不变；
2. 主流生成请求级 UUID，并以闭包把身份注入翻译；
3. 开始与成功终态使用同一组身份；
4. 前端 canonical parser 保留身份，兼容类型允许旧流缺字段；
5. 重跑两端定向测试至 GREEN。

## Task 4：影响范围回归与记录

命令：

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_sse_agui.py tests/test_orchestrator_route.py
python3 -m pytest -q

cd frontend
npx vitest run tests/composables/useSseStream.spec.ts tests/stores/loopSlice.spec.ts \
  tests/composables/useToolLoop.spec.ts --coverage.enabled=false
npm run typecheck
npm run lint
npm run test
npm run build
```

完成后把 RED/GREEN、全量结果、静态检查边界、未验证项和比赛演示影响同步到增量工作日志与
changelog；只暂存本切片文件并审计 staged diff。
