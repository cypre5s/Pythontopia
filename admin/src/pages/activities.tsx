import { SelectField } from "@/components/select-field";
import { useState, type FormEvent } from "react";
import { Plus, Pencil, ArrowUp, ArrowDown, ListPlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableHeader,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { PageHeader, ErrorNotice, Loading, Empty, TableSearch } from "@/components/common";
import { api, scoped, errorMessage } from "@/lib/api";
import { useResource } from "@/lib/use-resource";
import { useDraft } from "@/lib/drafts";
import { newId } from "@/lib/id";
import type { Activity, Course, Material, Question } from "@/lib/types";
const kindName: Record<string, string> = {
  lesson: "阅读",
  reading: "阅读",
  practice: "练习",
  assessment: "测验",
};
interface Highlight {
  start: number;
  end: number;
  label: string;
}
export function Activities({ classroomId }: { classroomId: number }) {
  const course = useResource<Course>(scoped("/teacher/courses", classroomId)),
    materials = useResource<Material[]>(
      scoped("/teacher/materials", classroomId),
    ),
    questions = useResource<Question[]>(
      scoped("/teacher/questions", classroomId),
    );
  const [editing, setEditing] = useState<Activity | null>(null),
    [query, setQuery] = useState(""),
    [chapterFilter, setChapterFilter] = useState("all"),
    [typeFilter, setTypeFilter] = useState("all"),
    [open, setOpen] = useState(false),
    [questionMode, setQuestionMode] = useState(false),
    [title, setTitle] = useState(""),
    [id, setId] = useState(""),
    [chapter, setChapter] = useState(0),
    [kind, setKind] = useState<Activity["kind"]>("lesson"),
    [material, setMaterial] = useState<number | null>(null),
    [pointIds, setPointIds] = useState<number[]>([]),
    [npc, setNpc] = useState(""),
    [assistant, setAssistant] = useState(true),
    [rule, setRule] = useState("keep"),
    [start, setStart] = useState(1),
    [end, setEnd] = useState(1),
    [seconds, setSeconds] = useState(60),
    [pageHighlights, setPageHighlights] = useState<Highlight[]>([]),
    [videoHighlights, setVideoHighlights] = useState<Highlight[]>([]),
    [selected, setSelected] = useState<string[]>([]),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [dirty, setDirty] = useState(false);
  useDraft(dirty);
  function edit(a: Activity | null, pick = false) {
    if (dirty && !window.confirm("放弃当前活动的未保存修改？")) return;
    setEditing(a);
    setId(a?.id ?? newId("activity"));
    setTitle(a?.title ?? "");
    setChapter(a?.chapter_id ?? course.data?.chapters[0]?.id ?? 0);
    setKind(a?.kind ?? "lesson");
    setMaterial(a?.material_id ?? null);
    setPointIds(a?.knowledge_point_ids ?? []);
    setNpc(a?.npc_key ?? course.data?.npcs[0]?.key ?? "");
    setAssistant(a?.assistant_allowed ?? true);
    setRule(a ? "keep" : "all");
    const highlights = a?.learning.highlights as
      | {
          pages?: { start_page: number; end_page: number; label: string }[];
          video?: {
            start_seconds: number;
            end_seconds: number;
            label: string;
          }[];
        }
      | undefined;
    setPageHighlights(
      highlights?.pages?.map((h) => ({
        start: h.start_page,
        end: h.end_page,
        label: h.label,
      })) ?? [],
    );
    setVideoHighlights(
      highlights?.video?.map((h) => ({
        start: h.start_seconds,
        end: h.end_seconds,
        label: h.label,
      })) ?? [],
    );
    setSelected(a?.question_ids ?? []);
    setQuestionMode(pick);
    setError("");
    setDirty(false);
    setOpen(true);
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (questionMode) {
        await api.put(
          scoped(
            `/teacher/activities/${encodeURIComponent(id)}/questions`,
            classroomId,
          ),
          { question_ids: selected },
        );
      } else {
        const learning: Record<string, unknown> =
          rule === "keep"
            ? { ...(editing?.learning ?? {}) }
            : {
                ...(editing?.learning ?? {}),
                reading: undefined,
                video: undefined,
                ...(rule === "all"
                  ? { reading: { scope: "all" } }
                  : rule === "range"
                    ? {
                        reading: {
                          scope: "range",
                          start_page: start,
                          end_page: end,
                        },
                      }
                    : { video: { minimum_seconds: seconds } }),
              };
        if (kind === "lesson" || kind === "reading")
          learning.highlights = {
            pages: pageHighlights.map((h) => ({
              start_page: h.start,
              end_page: h.end,
              label: h.label,
            })),
            video: videoHighlights.map((h) => ({
              start_seconds: h.start,
              end_seconds: h.end,
              label: h.label,
            })),
          };
        const body = {
          title,
          npc_key: npc,
          assistant_allowed: kind === "assessment" ? false : assistant,
          material_id: material,
          knowledge_point_ids: pointIds,
          ...(learning ? { learning } : {}),
        };
        if (editing)
          await api.put(
            scoped(
              `/teacher/activities/${encodeURIComponent(id)}`,
              classroomId,
            ),
            body,
          );
        else
          await api.post(scoped("/teacher/activities", classroomId), {
            id,
            chapter_id: chapter,
            kind,
            ...body,
          });
      }
      setOpen(false);
      setDirty(false);
      course.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  function move(i: number, delta: number) {
    const next = [...selected];
    [next[i], next[i + delta]] = [next[i + delta], next[i]];
    setSelected(next);
    setDirty(true);
  }
  const candidates = (questions.data ?? []).filter(
    (q) => q.chapter_ids.includes(chapter) && q.status === "ready",
  );
  const activities = (course.data?.activities ?? []).filter((activity) =>
    activity.title.toLowerCase().includes(query.trim().toLowerCase()) &&
    (chapterFilter === "all" || String(activity.chapter_id) === chapterFilter) &&
    (typeFilter === "all" || kindName[activity.kind] === typeFilter),
  );
  return (
    <>
      <PageHeader
        title="学习活动"
        description="把资料和题目组织成阅读、练习与测验。"
      >
        <Button
          onClick={() => edit(null)}
          disabled={!course.data?.chapters.length}
        >
          <Plus />
          新增活动
        </Button>
      </PageHeader>
      <ErrorNotice
        message={
          course.error ||
          materials.error ||
          questions.error ||
          (!open ? error : "")
        }
      />
      <div className="surface">
        <div className="toolbar">
          <TableSearch label="搜索活动" placeholder="搜索活动名称" value={query} onValueChange={setQuery} />
          <SelectField aria-label="活动所属章节" value={chapterFilter} onValueChange={setChapterFilter}>
            <option value="all">全部章节</option>
            {course.data?.chapters.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
          </SelectField>
          <SelectField aria-label="活动类型" value={typeFilter} onValueChange={setTypeFilter}>
            <option value="all">全部类型</option>
            {["阅读", "练习", "测验"].map((label) => <option key={label} value={label}>{label}</option>)}
          </SelectField>
        </div>
        {course.loading ? (
          <Loading />
        ) : !activities.length ? (
          <Empty title={course.data?.activities.length ? "没有匹配的活动" : "尚无学习活动"} />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>活动名称</TableHead>
                <TableHead>章节</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>内容</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {activities.map((a) => (
                <TableRow key={a.id}>
                  <TableCell className="font-medium">{a.title}</TableCell>
                  <TableCell>
                    {
                      course.data?.chapters.find((c) => c.id === a.chapter_id)
                        ?.title
                    }
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{kindName[a.kind]}</Badge>
                  </TableCell>
                  <TableCell>
                    {a.material_id
                      ? materials.data?.find((m) => m.id === a.material_id)
                          ?.title
                      : `${a.question_ids.length} 道题`}
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-1">
                      {a.kind === "practice" || a.kind === "assessment" ? (
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`选题${a.title}`}
                          onClick={() => edit(a, true)}
                        >
                          <ListPlus />
                        </Button>
                      ) : null}
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`编辑${a.title}`}
                        onClick={() => edit(a)}
                      >
                        <Pencil />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>
      <Dialog
        open={open}
        onOpenChange={(v) => {
          if (!busy) setOpen(v);
        }}
      >
        <DialogContent className="editor-dialog">
          <DialogHeader>
            <DialogTitle>
              {questionMode
                ? `活动选题：${title}`
                : editing
                  ? "编辑活动"
                  : "新增活动"}
            </DialogTitle>
            <DialogDescription>
              {questionMode
                ? "只可选用当前章节的可用题目，顺序决定学生看到的题目顺序。"
                : "完成条件由服务端依据真实学习记录判定。"}
            </DialogDescription>
          </DialogHeader>
          <form inert={busy} aria-busy={busy} onSubmit={save} onChange={() => setDirty(true)}>
            <div className="dialog-scroll">
              <ErrorNotice message={error} />
              {questionMode ? (
                <>
                  <div className="field">
                    <Label>题库</Label>
                    <div className="check-list">
                      {candidates.map((q) => (
                        <label key={q.id}>
                          <input
                            type="checkbox"
                            checked={selected.includes(q.id)}
                            disabled={
                              !selected.includes(q.id) &&
                              selected.length >=
                                (kind === "assessment" ? 100 : 5000)
                            }
                            onChange={() =>
                              setSelected((v) =>
                                v.includes(q.id)
                                  ? v.filter((id) => id !== q.id)
                                  : [...v, q.id],
                              )
                            }
                          />
                          {q.title}
                        </label>
                      ))}
                    </div>
                  </div>
                  <div className="field">
                    <Label>题目顺序 · {selected.length} 题</Label>
                    {selected.map((key, i) => (
                      <div className="list-row border rounded-md" key={key}>
                        <span>
                          {i + 1}.{" "}
                          {questions.data?.find((q) => q.id === key)?.title ??
                            "题目已不可用，请移除"}
                        </span>
                        <div className="flex">
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            aria-label={`上移第${i + 1}题`}
                            disabled={i === 0}
                            onClick={() => move(i, -1)}
                          >
                            <ArrowUp />
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            aria-label={`下移第${i + 1}题`}
                            disabled={i === selected.length - 1}
                            onClick={() => move(i, 1)}
                          >
                            <ArrowDown />
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setSelected((v) => v.filter((k) => k !== key));
                              setDirty(true);
                            }}
                          >
                            移除
                          </Button>
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              ) : (
                <>
                  <div className="field">
                    <Label htmlFor="a-title">活动名称</Label>
                    <Input
                      id="a-title"
                      required
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div className="field">
                      <Label htmlFor="a-chapter">章节</Label>
                      <SelectField
                        id="a-chapter"
                        disabled={Boolean(editing)}
                        value={chapter}
                        onValueChange={(value) => {
                          setChapter(Number(value));
                          setMaterial(null);
                          setPointIds([]);
                          setDirty(true);
                        }}
                      >
                        {course.data?.chapters.map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.title}
                          </option>
                        ))}
                      </SelectField>
                    </div>
                    <div className="field">
                      <Label htmlFor="a-kind">类型</Label>
                      <SelectField
                        id="a-kind"
                        disabled={Boolean(editing)}
                        value={kind}
                        onValueChange={(value) => {
                          setKind(value as Activity["kind"]);
                          setRule("keep");
                          setMaterial(null);
                          setDirty(true);
                        }}
                      >
                        <option value="lesson">阅读</option>
                        {kind === "reading" && (
                          <option value="reading">阅读</option>
                        )}
                        <option value="practice">练习</option>
                        <option value="assessment">测验</option>
                      </SelectField>
                    </div>
                  </div>
                  <div className="field">
                    <Label htmlFor="a-npc">教学角色</Label>
                    <SelectField
                      id="a-npc"
                      value={npc}
                      onValueChange={(value) => { setNpc(value); setDirty(true); }}
                      required
                    >
                      {course.data?.npcs.map((n) => (
                        <option key={n.key} value={n.key}>
                          {n.name}
                        </option>
                      ))}
                    </SelectField>
                  </div>
                  <fieldset className="field">
                    <legend>关联知识点</legend>
                    <div className="check-list">
                      {course.data?.knowledge_points
                        .filter((p) => p.chapter_id === chapter)
                        .map((p) => (
                          <label key={p.id}>
                            <input
                              type="checkbox"
                              checked={pointIds.includes(p.id)}
                              onChange={() => {
                                setPointIds((v) =>
                                  v.includes(p.id)
                                    ? v.filter((id) => id !== p.id)
                                    : [...v, p.id],
                                );
                                setDirty(true);
                              }}
                            />
                            {p.title}
                          </label>
                        ))}
                    </div>
                  </fieldset>
                  {kind === "lesson" || kind === "reading" ? (
                    <>
                      <div className="field">
                        <Label htmlFor="a-material">阅读资料</Label>
                        <SelectField
                          id="a-material"
                          value={material ?? ""}
                          onValueChange={(value) => {
                            setDirty(true);
                            setMaterial(
                              value ? Number(value) : null,
                            );
                            setRule(
                              materials.data?.find(
                                (m) => m.id === Number(value),
                              )?.kind === "video"
                                ? "video"
                                : "all",
                            );
                          }}
                        >
                          <option value="">选择资料</option>
                          {materials.data
                            ?.filter(
                              (m) =>
                                m.chapter_ids.includes(chapter) &&
                                m.status === "ready",
                            )
                            .map((m) => (
                              <option key={m.id} value={m.id}>
                                {m.title}
                              </option>
                            ))}
                        </SelectField>
                      </div>
                      <div className="field">
                        <Label htmlFor="a-rule">完成条件</Label>
                        <SelectField
                          id="a-rule"
                          value={rule}
                          onValueChange={(value) => { setRule(value); setDirty(true); }}
                        >
                          <option value="keep">保留当前条件</option>
                          <option value="all">阅读整份资料</option>
                          <option value="range">阅读指定页范围</option>
                          <option value="video">有效视频观看时长</option>
                        </SelectField>
                      </div>
                      {rule === "range" ? (
                        <div className="grid grid-cols-2 gap-4">
                          <div className="field">
                            <Label htmlFor="a-start">起始页</Label>
                            <Input
                              id="a-start"
                              type="number"
                              min={1}
                              value={start}
                              onChange={(e) => setStart(Number(e.target.value))}
                            />
                          </div>
                          <div className="field">
                            <Label htmlFor="a-end">结束页</Label>
                            <Input
                              id="a-end"
                              type="number"
                              min={start}
                              value={end}
                              onChange={(e) => setEnd(Number(e.target.value))}
                            />
                          </div>
                        </div>
                      ) : rule === "video" ? (
                        <div className="field">
                          <Label htmlFor="a-seconds">最低有效观看秒数</Label>
                          <Input
                            id="a-seconds"
                            type="number"
                            min={1}
                            max={86400}
                            value={seconds}
                            onChange={(e) => setSeconds(Number(e.target.value))}
                          />
                        </div>
                      ) : null}
                      <HighlightEditor
                        title="重点页标记"
                        rows={pageHighlights}
                        onChange={(v) => {
                          setPageHighlights(v);
                          setDirty(true);
                        }}
                      />
                      <HighlightEditor
                        title="重点视频片段（秒）"
                        rows={videoHighlights}
                        onChange={(v) => {
                          setVideoHighlights(v);
                          setDirty(true);
                        }}
                        video
                      />
                    </>
                  ) : null}
                  {kind !== "assessment" ? (
                    <label className="flex items-center gap-2 mb-4">
                      <input
                        type="checkbox"
                        checked={assistant}
                        onChange={(e) => setAssistant(e.target.checked)}
                      />
                      允许学生主动向助教提问
                    </label>
                  ) : (
                    <p className="hint">测验不开放助教。</p>
                  )}
                </>
              )}
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                disabled={busy}
                onClick={() => setOpen(false)}
              >
                取消
              </Button>
              <Button type="submit" disabled={busy}>
                {busy ? "保存中…" : "保存"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
function HighlightEditor({
  title,
  rows,
  onChange,
  video = false,
}: {
  title: string;
  rows: Highlight[];
  onChange: (v: Highlight[]) => void;
  video?: boolean;
}) {
  return (
    <fieldset className="field">
      <legend>{title}</legend>
      <p className="hint">提示学生重点内容，不会自动视为完成。</p>
      {rows.map((r, i) => (
        <div className="border rounded p-3 space-y-2 mb-2" key={i}>
          <Input
            aria-label={`${title}说明 ${i + 1}`}
            placeholder="重点说明"
            value={r.label}
            maxLength={200}
            onChange={(e) =>
              onChange(
                rows.map((old, j) =>
                  i === j ? { ...old, label: e.target.value } : old,
                ),
              )
            }
          />
          <div className="grid grid-cols-2 gap-2">
            <Input
              aria-label={`${title}起点 ${i + 1}`}
              type="number"
              min={video ? 0 : 1}
              value={r.start}
              onChange={(e) =>
                onChange(
                  rows.map((old, j) =>
                    i === j ? { ...old, start: Number(e.target.value) } : old,
                  ),
                )
              }
            />
            <Input
              aria-label={`${title}终点 ${i + 1}`}
              type="number"
              min={r.start + (video ? 1 : 0)}
              value={r.end}
              onChange={(e) =>
                onChange(
                  rows.map((old, j) =>
                    i === j ? { ...old, end: Number(e.target.value) } : old,
                  ),
                )
              }
            />
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onChange(rows.filter((_, j) => i !== j))}
          >
            移除标记
          </Button>
        </div>
      ))}
      <Button
        variant="outline"
        size="sm"
        disabled={rows.length >= 100}
        onClick={() =>
          onChange([
            ...rows,
            { start: video ? 0 : 1, end: video ? 10 : 1, label: "" },
          ])
        }
      >
        添加标记
      </Button>
    </fieldset>
  );
}
