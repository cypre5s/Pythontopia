import { SelectField } from "@/components/select-field";
import { useState, type FormEvent } from "react";
import { CalendarClock, Pencil, Eye } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Table,
  TableHeader,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import {
  PageHeader,
  ErrorNotice,
  Loading,
  Empty,
  Pager,
  TableSearch,
} from "@/components/common";
import { api, scoped, errorMessage } from "@/lib/api";
import { useResource } from "@/lib/use-resource";
import { useDraft } from "@/lib/drafts";
import type { Chapter, Course } from "@/lib/types";
import { formatTimestamp, isoToLocalDateTime, localDateTimeToIso, TIME_ZONE } from "@/lib/time";
interface Release {
  chapter_id: number;
  status: "published" | "paused";
  opens_at: string | null;
  prerequisite_chapter_id: number | null;
}
interface Progress {
  members: {
    id: number;
    student_id: string | null;
    name: string;
    completed_chapters: number;
    completed_nodes: number;
    submissions: number;
    records: number;
  }[];
}
interface StudentRecord {
  student: { name: string; student_id: string | null };
  events: { id: string; node_id: string; created_at: string }[];
  participation: {
    id: string;
    activity_id: string;
    kind: string;
    created_at: string;
    data: Record<string, unknown>;
  }[];
  submissions: {
    id: string;
    question_id: string;
    mode: string;
    status: string;
    answer: unknown;
    code: string | null;
    feedback: unknown;
    created_at: string;
  }[];
}
const date = formatTimestamp;
const releaseLabels = {
  unpublished: "未发布",
  paused: "已暂停",
  scheduled: "等待开放",
  published: "已发布",
};
export function Teaching({ classroomId }: { classroomId: number }) {
  const course = useResource<Course>(scoped("/teacher/courses", classroomId)),
    releases = useResource<Release[]>(
      `/teacher/classrooms/${classroomId}/releases`,
    ),
    progress = useResource<Progress>(
      `/teacher/classrooms/${classroomId}/progress`,
    );
  const [tab, setTab] = useState("releases"),
    [editing, setEditing] = useState<Chapter | null>(null),
    [status, setStatus] = useState<Release["status"]>("published"),
    [opensAt, setOpensAt] = useState(""),
    [previous, setPrevious] = useState<number | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [dirty, setDirty] = useState(false),
    [success, setSuccess] = useState(""),
    [student, setStudent] = useState<number | null>(null),
    [releaseQuery, setReleaseQuery] = useState(""),
    [releaseFilter, setReleaseFilter] = useState("all"),
    [query, setQuery] = useState(""),
    [progressFilter, setProgressFilter] = useState("all"),
    [page, setPage] = useState(1),
    [size, setSize] = useState(25);
  useDraft(dirty);
  function edit(chapter: Chapter) {
    const row = releases.data?.find((r) => r.chapter_id === chapter.id);
    setEditing(chapter);
    setStatus(row?.status ?? "published");
    setPrevious(row?.prerequisite_chapter_id ?? null);
    setOpensAt(
      row?.opens_at ? isoToLocalDateTime(row.opens_at) : "",
    );
    setDirty(false);
    setError("");
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setBusy(true);
    setError("");
    try {
      await api.put(
        `/teacher/classrooms/${classroomId}/releases/${editing.id}`,
        {
          status,
          opens_at: opensAt ? localDateTimeToIso(opensAt) : null,
          prerequisite_chapter_id: previous,
        },
      );
      setEditing(null);
      setDirty(false);
      setSuccess("章节安排已保存");
      releases.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  const releaseByChapter = new Map(releases.data?.map((r) => [r.chapter_id, r]));
  const chapters = (course.data?.chapters ?? []).map((chapter) => {
    const release = releaseByChapter.get(chapter.id);
    const state = !release ? "unpublished" : release.status === "paused" ? "paused"
      : release.opens_at && new Date(release.opens_at) > new Date() ? "scheduled" : "published";
    return { chapter, release, state } as const;
  }).filter(({ chapter, state }) =>
    chapter.title.toLowerCase().includes(releaseQuery.trim().toLowerCase()) &&
    (releaseFilter === "all" || releaseFilter === state),
  );
  const members = (progress.data?.members ?? []).filter((m) => {
    const hasRecords = Boolean(m.completed_chapters || m.completed_nodes || m.submissions || m.records);
    const search = query.trim().toLowerCase();
    return (m.name.toLowerCase().includes(search) || (m.student_id ?? "").includes(search)) &&
      (progressFilter === "all" || (progressFilter === "started") === hasRecords);
  });
  const safePage = Math.min(
    page,
    Math.max(1, Math.ceil(members.length / size)),
  );
  return (
    <>
      <PageHeader
        title="教学管理"
        description="安排章节开放，并查看本班真实学习记录。"
      />
      <ErrorNotice
        message={
          course.error ||
          releases.error ||
          progress.error ||
          (!editing ? error : "")
        }
      />
      {success && (
        <p role="status" className="success-notice">
          {success}
        </p>
      )}
      <Tabs value={tab} onValueChange={setTab} className="gap-4">
        <TabsList variant="line" aria-label="教学管理视图">
          <TabsTrigger value="releases">章节安排</TabsTrigger>
          <TabsTrigger value="progress">学习记录</TabsTrigger>
        </TabsList>
        <TabsContent value="releases">
        <div className="surface">
          <div className="toolbar">
            <TableSearch label="搜索章节" placeholder="搜索章节名称" value={releaseQuery} onValueChange={setReleaseQuery} />
            <SelectField aria-label="章节开放状态" value={releaseFilter} onValueChange={setReleaseFilter}>
              <option value="all">全部开放状态</option>
              {Object.entries(releaseLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </SelectField>
          </div>
          {course.loading || releases.loading ? (
            <Loading />
          ) : !chapters.length ? (
            <Empty title={course.data?.chapters.length ? "没有匹配的章节" : "还没有章节"} />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>章节</TableHead>
                  <TableHead>开放状态</TableHead>
                  <TableHead>开放时间</TableHead>
                  <TableHead>前置章节</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {chapters.map(({ chapter: c, release: r, state }) => (
                    <TableRow key={c.id}>
                      <TableCell className="font-medium">{c.title}</TableCell>
                      <TableCell>
                        <Badge
                          variant={
                            r?.status === "published" ? "outline" : "secondary"
                          }
                        >
                          {releaseLabels[state]}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {r?.opens_at ? date(r.opens_at) : r ? "立即开放" : "—"}
                      </TableCell>
                      <TableCell>
                        {course.data?.chapters.find(
                          (v) => v.id === r?.prerequisite_chapter_id,
                        )?.title ?? "无"}
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => edit(c)}
                        >
                          <Pencil />
                          设置
                        </Button>
                      </TableCell>
                    </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>
        </TabsContent>
        <TabsContent value="progress">
        <div className="surface">
          <div className="toolbar">
            <TableSearch label="搜索学习记录" placeholder="搜索学号或姓名" value={query} onValueChange={(value) => { setQuery(value); setPage(1); }} />
            <SelectField aria-label="学习记录状态" value={progressFilter} onValueChange={(value) => { setProgressFilter(value); setPage(1); }}>
              <option value="all">全部学习状态</option>
              <option value="started">已有学习记录</option>
              <option value="not_started">暂无学习记录</option>
            </SelectField>
          </div>
          {progress.loading ? (
            <Loading />
          ) : !members.length ? (
            <Empty title="没有匹配的学生" />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>学生</TableHead>
                  <TableHead>完成章节</TableHead>
                  <TableHead>完成节点</TableHead>
                  <TableHead>正式提交</TableHead>
                  <TableHead>参与记录</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {members
                  .slice((safePage - 1) * size, safePage * size)
                  .map((m) => (
                    <TableRow key={m.id}>
                      <TableCell>
                        <p className="font-medium">{m.name}</p>
                        <small className="text-muted-foreground">
                          {m.student_id ?? "未分配学号"}
                        </small>
                      </TableCell>
                      <TableCell>{m.completed_chapters}</TableCell>
                      <TableCell>{m.completed_nodes}</TableCell>
                      <TableCell>{m.submissions}</TableCell>
                      <TableCell>{m.records}</TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setStudent(m.id)}
                        >
                          <Eye />
                          查看记录
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          )}
          <Pager
            page={safePage}
            size={size}
            total={members.length}
            onPage={setPage}
            onSize={(v) => {
              setSize(v);
              setPage(1);
            }}
          />
        </div>
        </TabsContent>
      </Tabs>
      <Dialog
        open={!!editing}
        onOpenChange={(v) => {
          if (
            !v &&
            !busy &&
            (!dirty || window.confirm("放弃未保存的章节安排？"))
          ) {
            setEditing(null);
            setDirty(false);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>设置章节安排</DialogTitle>
            <DialogDescription>
              {editing?.title} · 开放时间采用当前设备时区
            </DialogDescription>
          </DialogHeader>
          <form inert={busy} aria-busy={busy} onSubmit={save} onChange={() => setDirty(true)}>
            <ErrorNotice message={error} />
            <div className="field">
              <Label htmlFor="release-status">状态</Label>
              <SelectField
                id="release-status"
                value={status}
                onValueChange={(value) => { setStatus(value as Release["status"]); setDirty(true); }}
              >
                <option value="published">发布</option>
                <option value="paused">暂停</option>
              </SelectField>
              <p className="hint">
                暂停后阻止新的学习请求，已经产生的历史记录保留。
              </p>
            </div>
            <div className="field">
              <Label htmlFor="release-time">开放时间（可选）</Label>
              <Input
                id="release-time"
                type="datetime-local"
                value={opensAt}
                onChange={(e) => setOpensAt(e.target.value)}
              />
              <p className="hint">时区：{TIME_ZONE}。留空则立即开放。</p>
            </div>
            <div className="field">
              <Label htmlFor="release-previous">前置章节</Label>
              <SelectField
                id="release-previous"
                value={previous ?? ""}
                onValueChange={(value) => { setPrevious(value ? Number(value) : null); setDirty(true); }}
              >
                <option value="">无</option>
                {course.data?.chapters
                  .filter((c) => c.id !== editing?.id)
                  .map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.title}
                    </option>
                  ))}
              </SelectField>
            </div>
            <DialogFooter>
              <Button type="submit" disabled={busy}>
                <CalendarClock />
                {busy ? "保存中…" : "保存安排"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
      {student !== null && (
        <RecordViewer
          classroomId={classroomId}
          studentId={student}
          course={course.data}
          onClose={() => setStudent(null)}
        />
      )}
    </>
  );
}
function readable(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "string") return value;
  if (typeof value === "boolean") return value ? "正确" : "错误";
  if (Array.isArray(value)) return value.map(readable).join("、");
  return Object.entries(value as Record<string, unknown>)
    .map(([k, v]) => `${k}：${readable(v)}`)
    .join("\n");
}
function RecordViewer({
  classroomId,
  studentId,
  course,
  onClose,
}: {
  classroomId: number;
  studentId: number;
  course: Course | null;
  onClose: () => void;
}) {
  const result = useResource<StudentRecord>(
      `/teacher/classrooms/${classroomId}/students/${studentId}`,
    ),
    [tab, setTab] = useState("submissions");
  return (
    <Dialog
      open
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent className="sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>
            {result.data?.student.name ?? "学生"}的学习记录
          </DialogTitle>
          <DialogDescription>
            {result.data?.student.student_id ?? "未分配学号"} · 当前班级
          </DialogDescription>
        </DialogHeader>
        <ErrorNotice message={result.error} />
        <Tabs value={tab} onValueChange={setTab}>
          <TabsList variant="line" aria-label="学生记录类型">
            <TabsTrigger value="submissions">作答记录</TabsTrigger>
            <TabsTrigger value="participation">活动参与</TabsTrigger>
            <TabsTrigger value="events">任务节点</TabsTrigger>
          </TabsList>
          <TabsContent value={tab} className="max-h-[60vh] space-y-4 overflow-y-auto">
            {result.loading ? (
              <Loading />
            ) : tab === "submissions" ? (
            result.data?.submissions.length ? (
              result.data.submissions
                .slice()
                .reverse()
                .map((s) => (
                  <details className="border rounded-lg p-4" key={s.id}>
                    <summary className="cursor-pointer flex flex-wrap gap-3">
                      <Badge variant="outline">
                        {s.mode === "submit" ? "正式提交" : "试运行"}
                      </Badge>
                      <span>{s.status}</span>
                      <time className="ml-auto text-xs text-muted-foreground">
                        {date(s.created_at)}
                      </time>
                    </summary>
                    <div className="mt-4 space-y-3">
                      <p className="text-sm">题目：{s.question_id}</p>
                      <pre className="whitespace-pre-wrap text-sm bg-muted rounded p-3">
                        {s.code || readable(s.answer)}
                      </pre>
                      <p className="whitespace-pre-wrap text-sm">
                        {readable(s.feedback)}
                      </p>
                    </div>
                  </details>
                ))
            ) : (
              <Empty title="暂无作答记录" />
            )
          ) : tab === "participation" ? (
            result.data?.participation.length ? (
              result.data.participation
                .slice()
                .reverse()
                .map((r) => (
                  <article className="border rounded-lg p-4" key={r.id}>
                    <div className="flex justify-between gap-4">
                      <h3 className="font-medium">
                        {course?.activities.find((a) => a.id === r.activity_id)
                          ?.title ?? "学习活动"}
                      </h3>
                      <time className="text-xs text-muted-foreground">
                        {date(r.created_at)}
                      </time>
                    </div>
                    <p className="text-sm text-muted-foreground mt-2">
                      {r.kind}
                    </p>
                    <p className="text-sm whitespace-pre-wrap mt-2">
                      {readable(r.data)}
                    </p>
                  </article>
                ))
            ) : (
              <Empty title="暂无参与记录" />
            )
          ) : result.data?.events.length ? (
            result.data.events.map((r) => (
              <article
                key={r.id}
                className="border rounded-lg p-4 flex justify-between gap-4"
              >
                <p>{r.node_id}</p>
                <time className="text-xs text-muted-foreground">
                  {date(r.created_at)}
                </time>
              </article>
            ))
          ) : (
            <Empty title="暂无任务记录" />
          )}
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}
