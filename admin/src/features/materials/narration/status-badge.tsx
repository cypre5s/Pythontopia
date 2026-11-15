const STATUS: Record<string, { label: string; tone: string }> = {
  draft: { label: "待审", tone: "bg-amber-100 text-amber-900" },
  accepted: { label: "已接受", tone: "bg-emerald-100 text-emerald-900" },
  rejected: { label: "已拒绝", tone: "bg-rose-100 text-rose-900" },
  stale: { label: "已失效", tone: "bg-slate-200 text-slate-700" },
  unavailable: { label: "无法生成", tone: "bg-slate-200 text-slate-700" },
};
export function StatusBadge({ status }: { status: string | undefined }) {
  if (!status)
    return <span className="text-xs text-muted-foreground">未生成</span>;
  const item = STATUS[status] ?? {
    label: status,
    tone: "bg-slate-100 text-slate-800",
  };
  return (
    <span className={`rounded px-2 py-0.5 text-xs ${item.tone}`}>
      {item.label}
    </span>
  );
}
