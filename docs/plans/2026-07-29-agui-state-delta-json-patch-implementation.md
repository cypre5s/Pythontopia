# AG-UI STATE_DELTA JSON Patch 实施计划

> 执行准则：小步 TDD；每一步保持现有 UI 投影不变；不夹带 RUN 生命周期或 SSE transport 重构。

**目标：** 将 orchestrator 的 `STATE_DELTA` 改为可执行 RFC 6902 JSON Patch，并在前端安全应用。

**技术栈：** Python 3 / FastAPI / pytest，Vue 3 / TypeScript strict / Vitest，
`fast-json-patch@3.1.1`。

---

## Task 1：建立 Graph 线格式 RED

**修改：**

- `services/pythontopia-graph/tests/test_sse_agui.py`
- `services/pythontopia-graph/tests/test_orchestrator_route.py`

步骤：

1. 将 helper 期望改为 `delta: [{op, path, value}]`；
2. 解析 orchestrator stream 的全部 `STATE_DELTA`；
3. 断言数组形状、`/plan_steps` 路径和 pending/active 真实进度；
4. 运行定向 pytest，确认当前对象载荷产生预期失败。

命令：

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_sse_agui.py tests/test_orchestrator_route.py
```

## Task 2：建立前端执行语义 RED

**修改：**

- `frontend/tests/composables/useSseStream.spec.ts`

步骤：

1. 用 `STATE_SNAPSHOT` 建立嵌套状态；
2. 发送 RFC 6902 `replace/remove/add` 数组；
3. 断言嵌套更新、删除和新属性均正确；
4. 保留旧对象载荷兼容用例；
5. 运行定向 Vitest，确认当前浅合并产生预期失败。

命令：

```bash
cd frontend
npm run test -- tests/composables/useSseStream.spec.ts
```

## Task 3：最小实现

**修改：**

- `services/pythontopia-graph/pythontopia_graph/sse_agui.py`
- `services/pythontopia-graph/pythontopia_graph/graph/orchestrator_graph.py`
- `frontend/src/composables/agui.ts`
- `frontend/src/composables/useSseStream.ts`
- `frontend/package.json`
- `frontend/package-lock.json`

步骤：

1. Graph helper 接受 JSON Patch 操作列表；
2. orchestrator 以 `add /plan_steps` 发完整当前值；
3. 前端类型收敛为 JSON Patch 操作联合；
4. 旧对象载荷在边界转换为根属性 `add`；
5. 安装并使用 `fast-json-patch@3.1.1`，开启验证和 prototype 防护；
6. 重跑两端定向测试至 GREEN。

## Task 4：影响范围回归

命令：

```bash
cd services/pythontopia-graph
python3 -m pytest -q
ruff check .
ruff format --check .

cd frontend
npm run test -- tests/composables/useSseStream.spec.ts tests/components/workshop/WorkshopChat.spec.ts
npm run typecheck
npm run lint
npx prettier --check src/composables/agui.ts src/composables/useSseStream.ts tests/composables/useSseStream.spec.ts
npm run test
```

完成后把 RED/GREEN、全量结果、未验证项和比赛演示影响同步到增量工作日志与 changelog，
仅暂存本切片文件并审计 staged diff。
