import { useEffect, useId, useRef, useState } from "react";
import { Download, PackageCheck, Search, LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableHeader,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import { PageHeader, ErrorNotice } from "@/components/common";
import { SelectField } from "@/components/select-field";
import { errorMessage, request, token } from "@/lib/api";
import { useDraft } from "@/lib/drafts";
import { type TransferBytes } from "@/lib/package-transfer";
import { formatTimestamp } from "@/lib/time";
import { useResource } from "@/lib/use-resource";
interface AvailablePackages {
  items: { key: string; name: string; package_id: string; package_version: string; source: "mounted" | "uploaded"; directory: string; created_at: string; chapter_count: number; question_count: number }[];
  unavailable: number;
}
interface Preview {
  id: string;
  expected_revision: number;
  package_id: string;
  package_version: string;
  name: string;
  can_apply: boolean;
  expires_at: string;
  blockers: string[];
  diff: Record<
    string,
    { added: string[]; updated: string[]; removed: string[] }
  >;
  warnings: string[];
  learning_impact?: {
    kind: string;
    stable_id: string;
    affected_records: number;
    reason: string;
  }[];
}
function requestId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join(
    "",
  );
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
const labels: Record<string, string> = {
  chapter: "章节",
  knowledge_point: "知识点",
  material: "资料",
  question: "题目",
  activity: "活动",
  quest: "任务",
  story: "剧情",
  npc: "角色",
  example: "示例",
  chapters: "章节",
  knowledge_points: "知识点",
  materials: "资料",
  questions: "题目",
  activities: "活动",
  quests: "任务",
  stories: "剧情",
  npcs: "角色",
  examples: "示例",
  configuration: "课程配置",
  releases: "发布安排",
  assets: "资源文件",
  manifest: "包信息",
  answers: "答案与用例",
};
const phaseLabels = {
  uploading: "正在上传课程包",
  validating: "正在校验课程包并生成差异",
  applying: "正在导入课程和资源",
  packaging: "正在导出课程目录和资源",
  downloading: "正在下载课程包",
};
type Phase = keyof typeof phaseLabels;
type Operation = { phase: Phase; bytes?: TransferBytes };

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KiB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MiB`;
  return `${(bytes / 1024 ** 3).toFixed(2)} GiB`;
}

function OperationProgress({ operation }: { operation: Operation | null }) {
  const id = useId();
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    if (!operation) return;
    const started = Date.now();
    const timer = setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 1000);
    return () => clearInterval(timer);
  }, [operation]);
  const { transferred = 0, total = null } = operation?.bytes ?? {};
  const percent = total
    ? Math.min(100, Math.floor((100 * transferred) / total))
    : null;
  return (
    <div className={operation ? "mt-4 space-y-2 rounded-md border bg-muted/30 p-4" : undefined}>
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span id={id} aria-live="polite" className="font-medium">
          {operation ? phaseLabels[operation.phase] : ""}
        </span>
        {total && percent !== null ? (
          <span className="tabular-nums text-muted-foreground">
            {formatBytes(transferred)} / {formatBytes(total)} · {percent}%
          </span>
        ) : transferred > 0 ? (
          <span className="tabular-nums text-muted-foreground">
            已传输 {formatBytes(transferred)}
          </span>
        ) : null}
      </div>
      {operation && !total ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
          <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
          已等待 {elapsed} 秒 · 正在处理课程资料，请稍候
        </div>
      ) : operation ? (
        <progress
          aria-labelledby={id}
          max={total ?? undefined}
          value={total ? transferred : undefined}
          className="block h-2 w-full accent-primary"
        />
      ) : null}
    </div>
  );
}

export function Packages({ classroomId }: { classroomId: number }) {
  const base = `/teacher/classrooms/${classroomId}/course`;
  const available = useResource<AvailablePackages>(`${base}/packages`);
  const [packageSearch, setPackageSearch] = useState("");
  const pending = useRef<AbortController | null>(null);
  const [preview, setPreview] = useState<Preview | null>(null),
    [requestKey, setRequestKey] = useState(""),
    [error, setError] = useState<{ phase: Phase; message: string } | null>(null),
    [message, setMessage] = useState(""),
    [operation, setOperation] = useState<Operation | null>(null),
    [confirmed, setConfirmed] = useState(false),
    [search, setSearch] = useState(""),
    [changes, setChanges] = useState("all");
  const busy = operation !== null;
  useDraft(Boolean(preview || busy));
  useEffect(() => () => pending.current?.abort(), []);

  async function operate<T>(
    phase: Phase,
    execute: (signal: AbortSignal, report: (operation: Operation) => void) => Promise<T>,
    finish: (result: T) => void,
  ) {
    if (pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    const captured = token();
    const isCurrent = () => !controller.signal.aborted && captured === token();
    setOperation({ phase });
    setError(null);
    setMessage("");
    try {
      const result = await execute(controller.signal, (progress) => {
        if (isCurrent()) setOperation(progress);
      });
      if (isCurrent()) finish(result);
    } catch (e) {
      if (isCurrent()) setError({ phase, message: errorMessage(e) });
    } finally {
      if (pending.current === controller) pending.current = null;
      if (!controller.signal.aborted) setOperation(null);
    }
  }

  function inspectAvailable(packageKey: string) {
    if (pending.current) return;
    setPreview(null);
    setConfirmed(false);
    return operate(
      "validating",
      (signal) => request<Preview>(`${base}/imports/available/preview`, {
        method: "POST", body: JSON.stringify({ package_key: packageKey }), signal,
      }),
      (result) => {
        setPreview(result);
        setRequestKey(requestId());
        setSearch("");
        setChanges("all");
      },
    );
  }

  function apply() {
    if (!preview || !preview.can_apply || !confirmed) return;
    return operate(
      "applying",
      (signal) =>
        request(`${base}/imports/${encodeURIComponent(preview.id)}/apply`, {
          method: "POST",
          body: JSON.stringify({
            request_id: requestKey,
            expected_revision: preview.expected_revision,
          }),
          signal,
        }),
      () => {
        setPreview(null);
        setConfirmed(false);
        available.reload();
        setMessage("课程包已导入，班级课程已更新。");
      },
    );
  }

  function exportPackage() {
    return operate(
      "packaging",
      (signal) => request<{ path: string }>(`${base}/export`, { signal }),
      (result) => {
        setMessage(`课程已导出到 ${result.path}`);
        available.reload();
      },
    );
  }

  const searchText = search.trim().toLowerCase();
  const rows = Object.entries(preview?.diff ?? {}).filter(([key, row]) => {
    if (!(labels[key] ?? key).toLowerCase().includes(searchText)) return false;
    if (changes === "changed") return row.added.length + row.updated.length + row.removed.length > 0;
    if (changes === "added" || changes === "updated" || changes === "removed") {
      return row[changes].length > 0;
    }
    return true;
  });

  return (
    <>
      <PageHeader
        title="课程包"
        description="导出完整课程，或预览并导入课程更新。"
      >
        <Button
          variant="outline"
          disabled={busy}
          onClick={() => void exportPackage()}
        >
          <Download />
          导出课程包
        </Button>
      </PageHeader>
      <ErrorNotice message={error?.phase === "packaging" ? error.message : ""} />
      <OperationProgress
        operation={operation?.phase === "packaging" || operation?.phase === "downloading" ? operation : null}
      />
      {message ? (
        <div role="status" className="notice-success">
          {message}
        </div>
      ) : null}
      <section className="surface p-6 mt-4">
        <div className="flex gap-3 items-center mb-5">
          <div className="icon-box">
            <PackageCheck />
          </div>
          <div>
            <h2 className="font-semibold">导入课程包</h2>
            <p className="text-sm text-muted-foreground">
              先查看差异，再确认应用到当前班级。
            </p>
          </div>
        </div>
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Input aria-label="搜索课程包" placeholder="搜索课程名称或文件夹" className="max-w-sm"
              value={packageSearch} onChange={(event) => setPackageSearch(event.target.value)} />
            <Button variant="outline" size="sm" disabled={busy || available.loading} onClick={available.reload}>刷新列表</Button>
          </div>
          <ErrorNotice message={available.error} />
          <Table aria-label="可用课程包">
            <TableHeader><TableRow>
              <TableHead>课程名称</TableHead><TableHead>创建日期</TableHead>
              <TableHead>章节数</TableHead><TableHead>题目数</TableHead>
              <TableHead>文件夹</TableHead><TableHead className="text-right">操作</TableHead>
            </TableRow></TableHeader>
            <TableBody>
              {(available.data?.items ?? []).filter((item) =>
                (item.name + " " + item.directory).toLowerCase().includes(packageSearch.trim().toLowerCase()),
              ).map((item) => <TableRow key={item.key}>
                <TableCell className="min-w-48 max-w-80 whitespace-normal break-words font-medium">{item.name}</TableCell>
                <TableCell>{item.created_at ? formatTimestamp(item.created_at) : "—"}</TableCell>
                <TableCell className="tabular-nums">{item.chapter_count ?? "—"}</TableCell>
                <TableCell className="tabular-nums">{item.question_count ?? "—"}</TableCell>
                <TableCell className="min-w-48 max-w-80 whitespace-normal break-all text-muted-foreground">{item.directory}</TableCell>
                <TableCell className="text-right"><Button variant="outline" size="sm" disabled={busy || available.loading}
                  aria-label={`预览 ${item.directory}`} onClick={() => void inspectAvailable(item.key)}>预览导入</Button></TableCell>
              </TableRow>)}
            </TableBody>
          </Table>
          {available.loading ? <p role="status" className="hint">正在加载课程包…</p> : null}
          {!available.loading && !available.error && !available.data?.items.length ? <p className="hint">暂无课程包。请将解压后的课程包目录放入 data/course，再刷新列表。</p> : null}
          {available.data?.unavailable ? <p className="hint">有 {available.data.unavailable} 份课程包暂不可用，请检查包格式。</p> : null}
        </div>
        <ErrorNotice message={error?.phase === "validating" ? error.message : ""} />
        <OperationProgress
          operation={operation?.phase === "uploading" || operation?.phase === "validating" ? operation : null}
        />
      </section>
      {preview ? (
        <section className="surface mt-6">
          <div className="p-5 border-b">
            <h2 className="font-semibold">导入差异</h2>
            <p className="text-sm text-muted-foreground mt-1">
              {preview.name} · {preview.package_version}
            </p>
          </div>
          <div className="toolbar">
            <div className="search">
              <Search size={16} aria-hidden="true" />
              <Input
                aria-label="搜索差异内容"
                placeholder="搜索内容类型"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>
            <SelectField aria-label="变更类型" value={changes} onValueChange={setChanges}>
              <option value="all">全部变更</option>
              <option value="changed">仅显示有变化的内容</option>
              <option value="added">有新增</option>
              <option value="updated">有更新</option>
              <option value="removed">有移除</option>
            </SelectField>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>内容</TableHead>
                <TableHead>新增</TableHead>
                <TableHead>更新</TableHead>
                <TableHead>移除</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map(([key, row]) => (
                <TableRow key={key}>
                  <TableCell>{labels[key] ?? key}</TableCell>
                  <TableCell>{row.added.length}</TableCell>
                  <TableCell>{row.updated.length}</TableCell>
                  <TableCell>{row.removed.length}</TableCell>
                </TableRow>
              ))}
              {rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                    没有符合筛选条件的变更
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
          {preview.learning_impact?.length ? (
            <div className="p-5 border-t">
              <h3 className="font-semibold mb-3">学习记录影响</h3>
              {preview.learning_impact.map((item, i) => (
                <p className="text-sm my-2" key={i}>
                  {labels[item.kind] ?? item.kind} · 涉及{" "}
                  {item.affected_records} 条记录：{item.reason}
                </p>
              ))}
            </div>
          ) : null}
          {preview.blockers?.length ? (
            <div className="p-5">
              <ErrorNotice message={preview.blockers.join("；")} />
            </div>
          ) : null}
          {preview.warnings?.length ? (
            <div className="p-5 text-amber-800 bg-amber-50">
              {preview.warnings.map((w, i) => (
                <p key={i}>{w}</p>
              ))}
            </div>
          ) : null}
          <div className="p-5 border-t">
            <ErrorNotice message={error?.phase === "applying" ? error.message : ""} />
            <div className="flex items-center gap-2 mb-4">
              <Checkbox
                id="confirm-course-import"
                aria-labelledby="confirm-course-import-label"
                checked={confirmed}
                onCheckedChange={setConfirmed}
                disabled={busy || !preview.can_apply}
              />
              <Label htmlFor="confirm-course-import" id="confirm-course-import-label">
                我已核对差异，确认更新当前班级课程
              </Label>
            </div>
            <Button
              onClick={() => void apply()}
              disabled={!confirmed || busy || !preview.can_apply}
            >
              确认导入
            </Button>
            <OperationProgress operation={operation?.phase === "applying" ? operation : null} />
            <p className="hint mt-3">
              若课程已被其他操作修改，需要重新预览。导入不清除已有学习记录。
              预览有效期至{" "}
              {formatTimestamp(preview.expires_at)}。
            </p>
          </div>
        </section>
      ) : null}
    </>
  );
}
