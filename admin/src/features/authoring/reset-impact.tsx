import type { ResetImpact } from "./contracts";
export function ResetImpactList({ impact }: { impact: ResetImpact }) {
  const flowReason = {
    delete: "删除流程",
    edit: "修改流程",
    task_reset: "随任务重置",
  };
  return (
    <ul className="mt-2 max-h-44 space-y-2 overflow-auto text-sm">
      {impact.flows.map((flow) => (
        <li key={`flow:${flow.id}`}>
          <p className="font-medium">流程 · {flow.title}</p>
          <p>
            {flowReason[flow.reason]}，清理 {flow.sessions} 条会话（进行中{" "}
            {flow.active_sessions}，等待活动 {flow.waiting_sessions}）。
          </p>
        </li>
      ))}
      {impact.quests.map((quest) => (
        <li key={`quest:${quest.id}`}>
          <p className="font-medium">任务 · {quest.title}</p>
          <p>
            {quest.reason === "delete" ? "删除任务" : "修改目标结构"}，清理{" "}
            {quest.students} 人的任务进度。
          </p>
        </li>
      ))}
    </ul>
  );
}
