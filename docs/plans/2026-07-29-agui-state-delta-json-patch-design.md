# AG-UI STATE_DELTA JSON Patch 契约设计

日期：2026-07-29

状态：已确认，待 TDD 实施

## 背景

当前 Graph 已使用 `STATE_DELTA` 事件名，但载荷仍是项目自定义对象：

```json
{"delta":{"plan_steps":[]}}
```

前端 `useSseStream` 通过浅合并消费它。AG-UI 当前官方事件合同要求 `delta` 是
RFC 6902 JSON Patch 操作数组。事件名一致但载荷不一致，会让标准客户端无法执行该状态流，
也使跨语言测试只能验证字符串存在，不能验证状态变换。

官方参考：

- <https://docs.ag-ui.com/sdk/js/core/events>
- <https://docs.ag-ui.com/concepts/state>

## 当前仓库事实

- 唯一生产 `STATE_DELTA` 数据源是
  `OrchestratorGraph._node_dispatch` 的 `plan_steps` 进度；
- 每次事件都携带完整 `plan_steps` 当前值，而不是数组内部的局部差异；
- 前端 `WorkshopChat` 只读取归一化后的 `state.plan_steps`；
- 既有测试只断言事件名和 `plan_steps` 字符串，没有验证 RFC 6902 形状；
- 前端没有 JSON Patch 依赖；npm 当前可取得 MIT 许可的
  `fast-json-patch@3.1.1`，无需自写补丁执行器。

## 目标

1. Graph 的 canonical `STATE_DELTA.delta` 输出 RFC 6902 操作数组；
2. 前端使用成熟库执行完整 JSON Patch，而不是浅合并；
3. 保持 `state.plan_steps` 对现有组件的最终投影不变；
4. 保留对旧对象载荷的输入兼容，支持前后端滚动发布；
5. 以 Python 路由测试和前端状态投影测试组成可执行跨语言合同。

## 非目标

- 本切片不补 `RUN_STARTED/RUN_FINISHED` 的 `threadId/runId`；
- 不统一全部 SSE transport、心跳、取消和终态；
- 不引入 `@ag-ui/core` 或替换现有 `AguiEvent` 扩展类型；
- 不改变 orchestrator 计划、步骤状态或 UI；
- 不修改 Java 中转层、数据库、API 路径或鉴权。

## 方案

### Graph 输出

`plan_steps` 每次仍发送完整当前值，但编码为根对象属性的 `add` 操作：

```json
{
  "delta": [
    {
      "op": "add",
      "path": "/plan_steps",
      "value": []
    }
  ]
}
```

RFC 6902 的 `add` 对对象成员既可创建也可替换，因此同一操作可覆盖首帧和后续更新，
无需根据前端是否已有该属性切换 `add/replace`。

### 前端边界归一

`AguiStateDelta.delta` 统一为 JSON Patch 数组。`toAguiEvent`：

- canonical 数组原样进入类型化事件；
- 旧对象载荷只在兼容边界转换为根属性 `add` 操作；
- 其他非法形状返回空操作数组，不把任意对象伪装成合法 patch。

`useSseStream` 用 `fast-json-patch` 在不修改旧引用的模式下应用 patch，并开启操作校验与
prototype modification 防护。执行失败沿既有 stream error 路径显式进入 `STREAM_ERROR`，
不静默吞掉违约状态。

## 验收

### RED

- Graph 路由测试解析每条 `STATE_DELTA`，要求 `delta` 为数组且每项具备
  `op/path/value`；当前对象载荷应失败。
- 前端测试发送嵌套 `replace`、`remove`、`add` 操作，要求得到正确最终状态；
  当前浅合并应失败。

### GREEN

- `tests/test_sse_agui.py` 与 `tests/test_orchestrator_route.py` 通过；
- `frontend/tests/composables/useSseStream.spec.ts` 通过；
- `frontend/tests/components/workshop/WorkshopChat.spec.ts` 的计划卡状态消费通过；
- Graph 全量 pytest、前端全量 Vitest、TypeScript strict、ESLint 和格式检查按影响范围通过。

## 风险与回滚

- 风险：旧 Graph + 新前端并行期间仍会发对象载荷。通过边界转换维持兼容。
- 风险：非法 JSON Pointer 或操作会使流失败。该行为是协议违约的显式失败，优于静默错态。
- 回滚：回退 Graph 单一 producer、前端解析/应用和依赖变更即可；无持久化数据迁移。
