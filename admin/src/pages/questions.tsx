import { SelectField } from "@/components/select-field";
import { DeleteSelection, SelectTablePage, SelectTableRow, useTableDelete } from "@/components/table-delete";
import { useState, type FormEvent } from "react";
import { Plus, Pencil, Trash2, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
import {
  PageHeader,
  ErrorNotice,
  Loading,
  Empty,
  Pager,
} from "@/components/common";
import { api, scoped, errorMessage } from "@/lib/api";
import { useResource } from "@/lib/use-resource";
import { useDraft } from "@/lib/drafts";
import { newId } from "@/lib/id";
import type { Question, Course, Material } from "@/lib/types";
const kinds = {
  single_choice: "单选题",
  boolean: "判断题",
  fill: "填空题",
  python: "编程题",
};
const statuses = { draft: "待审核", ready: "可用", disabled: "停用" };
type Case = { input: string; expected_output: string };
export function Questions({ classroomId }: { classroomId: number }) {
  const list = useResource<Question[]>(
      scoped("/teacher/questions", classroomId),
    ),
    course = useResource<Course>(scoped("/teacher/courses", classroomId)),
    materials = useResource<Material[]>(
      scoped("/teacher/materials", classroomId),
    );
  const [query, setQuery] = useState(""),
    [filter, setFilter] = useState("all"),
    [sourceFilter, setSourceFilter] = useState("all"),
    [kindFilter, setKindFilter] = useState("all"),
    [page, setPage] = useState(1),
    [size, setSize] = useState(10),
    [editing, setEditing] = useState<Question | null>(null),
    [open, setOpen] = useState(false),
    [id, setId] = useState(""),
    [title, setTitle] = useState(""),
    [prompt, setPrompt] = useState(""),
    [hints, setHints] = useState(""),
    [starterCode, setStarterCode] = useState(""),
    [kind, setKind] = useState<Question["kind"]>("single_choice"),
    [status, setStatus] = useState<Question["status"]>("draft"),
    [options, setOptions] = useState<string[]>(["", ""]),
    [answer, setAnswer] = useState(""),
    [chapterIds, setChapterIds] = useState<number[]>([]),
    [pointIds, setPointIds] = useState<number[]>([]),
    [referenceMaterial, setReferenceMaterial] = useState<number | null>(null),
    [referencePage, setReferencePage] = useState(""),
    [cases, setCases] = useState<Case[]>([]),
    [samples, setSamples] = useState<Case[]>([]),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [dirty, setDirty] = useState(false);
  useDraft(dirty);
  const rows = (list.data ?? []).filter(
    (q) =>
      (q.title.toLowerCase().includes(query.trim().toLowerCase()) || q.prompt.toLowerCase().includes(query.trim().toLowerCase())) &&
      (kindFilter === "all" || q.kind === kindFilter) &&
      (filter === "all" || q.status === filter) &&
      (sourceFilter === "all" || q.source_type === sourceFilter),
  );
  const safePage = Math.min(page, Math.max(1, Math.ceil(rows.length / size)));
  const visible = rows.slice((safePage - 1) * size, safePage * size);
  const deletion = useTableDelete<Question>((q) => scoped(`/teacher/questions/${encodeURIComponent(q.id)}`, classroomId), list.reload, { classroomId, kind: () => "question" });
  function edit(q: Question | null) {
    if (dirty && !window.confirm("放弃当前题目的未保存修改？")) return;
    setEditing(q);
    setId(q?.id ?? newId("question"));
    setTitle(q?.title ?? "");
    setPrompt(q?.prompt ?? "");
    setHints(q?.hints?.join("\n") ?? "");
    setStarterCode(q?.starter_code ?? "");
    setKind(q?.kind ?? "single_choice");
    setStatus(q?.status ?? "draft");
    setOptions(q?.options?.length ? q.options : ["", ""]);
    const value =
      q?.answer &&
      typeof q.answer === "object" &&
      !Array.isArray(q.answer) &&
      "value" in q.answer
        ? (q.answer as { value: unknown }).value
        : q?.answer;
    setAnswer(
      Array.isArray(value)
        ? value.join("\n")
        : value == null
          ? ""
          : String(value),
    );
    setChapterIds(q?.chapter_ids ?? []);
    setPointIds(q?.knowledge_point_ids ?? []);
    setReferenceMaterial(
      typeof q?.source.material_id === "number" ? q.source.material_id : null,
    );
    setReferencePage(
      typeof q?.source.page_no === "number" ? String(q.source.page_no) : "",
    );
    setCases(q?.hidden_cases?.map(c=>({input:c.input,expected_output:c.expected_output??c.output??""})) ?? []);
    setSamples(
      q?.samples?.map((c) => ({ input: c.input, expected_output: c.output??c.expected_output??"" })) ??
        [],
    );
    setError("");
    setDirty(false);
    setOpen(true);
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const source = { ...(editing?.source ?? {}) };
      delete source.material_id;
      delete source.page_no;
      if (referenceMaterial !== null) {
        source.material_id = referenceMaterial;
        if (referencePage) source.page_no = Number(referencePage);
      }
      const body = {
        title,
        kind,
        prompt,
        chapter_ids: chapterIds,
        knowledge_point_ids: pointIds,
        source_type: editing?.source_type ?? "imported",
        status,
        options: kind === "single_choice" ? options : [],
        answer:
          kind === "boolean"
            ? answer === "true"
              ? true
              : answer === "false"
                ? false
                : null
            : kind === "fill"
              ? answer.split("\n").filter(Boolean)
              : kind === "python"
                ? (editing?.answer ?? null)
                : answer,
        hints: kind === "python" ? hints.split("\n").filter(x=>x.trim()) : [],
        starter_code: kind === "python" ? starterCode : "",
        hidden_cases: kind === "python" ? cases : [],
        samples:
          kind === "python"
            ? samples.map((c) => ({
                input: c.input,
                output: c.expected_output,
              }))
            : [],
        source,
      };
      if (editing)
        await api.put(
          scoped(`/teacher/questions/${encodeURIComponent(id)}`, classroomId),
          body,
        );
      else
        await api.post(scoped("/teacher/questions", classroomId), {
          id,
          ...body,
        });
      setOpen(false);
      setDirty(false);
      list.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader title="题库" description="维护独立题目，再由练习与测验选用。">
        <Button onClick={() => edit(null)}>
          <Plus />
          新增题目
        </Button>
      </PageHeader>
      <ErrorNotice
        message={list.error || course.error || (!open ? error : "")}
      />
      <div className="surface">
        <div className="toolbar">
          <div className="search">
            <Search size={16} />
            <Input
              aria-label="搜索题目"
              placeholder="搜索题目或题干"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setPage(1);
                deletion.clear();
              }}
            />
          </div>
          <SelectField aria-label="题目类型" value={kindFilter} onValueChange={value=>{setKindFilter(value);setPage(1);deletion.clear();}}><option value="all">全部题型</option>{Object.entries(kinds).map(([key,value])=><option key={key} value={key}>{value}</option>)}</SelectField>
          <SelectField
            aria-label="题目状态"
            value={filter}
            onValueChange={(value) => {
              setFilter(value);
              setPage(1);
              deletion.clear();
            }}
          >
            <option value="all">全部状态</option>
            {Object.entries(statuses).map(([key, value]) => (
              <option key={key} value={key}>
                {value}
              </option>
            ))}
          </SelectField>
          <SelectField
            aria-label="题目来源"
            value={sourceFilter}
            onValueChange={(value) => {
              setSourceFilter(value);
              setPage(1);
              deletion.clear();
            }}
          >
            <option value="all">全部来源</option>
            <option value="imported">导入</option>
            <option value="ai_generated">AI 生成</option>
          </SelectField>
        </div>
        <DeleteSelection actions={deletion} />
        {list.loading ? (
          <Loading />
        ) : !rows.length ? (
          <Empty title="没有匹配的题目" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-12"><SelectTablePage actions={deletion} rows={visible} /></TableHead>
                <TableHead>题目</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>来源</TableHead>
                <TableHead>状态</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.map((q) => (
                <TableRow key={q.id} data-state={deletion.isSelected(q) ? "selected" : undefined}>
                  <TableCell><SelectTableRow actions={deletion} row={q} /></TableCell>
                  <TableCell>
                    <p className="font-medium">{q.title}</p>
                    <p className="text-xs text-muted-foreground mt-1">
                      {q.chapter_ids
                        .map(
                          (id) =>
                            course.data?.chapters.find((c) => c.id === id)
                              ?.title ?? "章节",
                        )
                        .join("、") || "未归类"}
                    </p>
                  </TableCell>
                  <TableCell>{kinds[q.kind]}</TableCell>
                  <TableCell>
                    {q.source_type === "ai_generated" ? "AI 生成" : "导入"}
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={q.status === "ready" ? "outline" : "secondary"}
                    >
                      {statuses[q.status]}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`编辑${q.title}`}
                        onClick={() => edit(q)}
                      >
                        <Pencil />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`删除${q.title}`}
                        onClick={() => deletion.ask([q])}
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <Pager
          page={safePage}
          size={size}
          total={rows.length}
          onPage={setPage}
          onSize={(v) => {
            setSize(v);
            setPage(1);
          }}
        />
      </div>
      <Dialog
        open={open}
        onOpenChange={(v) => {
          if (!busy) setOpen(v);
        }}
      >
        <DialogContent className="editor-dialog">
          <DialogHeader>
            <DialogTitle>{editing ? "编辑题目" : "新增题目"}</DialogTitle>
            <DialogDescription>
              私有答案与用例仅用于教师维护和服务端判定。
            </DialogDescription>
          </DialogHeader>
          <form inert={busy} aria-busy={busy} onSubmit={save} onChange={() => setDirty(true)}>
            <div className="dialog-scroll">
              <ErrorNotice message={error} />
              <div className="field">
                <Label htmlFor="q-title">题目名称</Label>
                <Input
                  id="q-title"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                  maxLength={200}
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="field">
                  <Label htmlFor="q-kind">题型</Label>
                  <SelectField
                    id="q-kind"
                    value={kind}
                    onValueChange={(value) => { setKind(value as Question["kind"]); setDirty(true); }}
                  >
                    {Object.entries(kinds).map(([k, v]) => (
                      <option key={k} value={k}>
                        {v}
                      </option>
                    ))}
                  </SelectField>
                </div>
                <div className="field">
                  <Label htmlFor="q-status">状态</Label>
                  <SelectField
                    id="q-status"
                    value={status}
                    onValueChange={(value) => { setStatus(value as Question["status"]); setDirty(true); }}
                  >
                    {Object.entries(statuses).map(([k, v]) => (
                      <option key={k} value={k}>
                        {v}
                      </option>
                    ))}
                  </SelectField>
                </div>
              </div>
              <div className="field">
                <Label htmlFor="q-prompt">题干</Label>
                <Textarea
                  id="q-prompt"
                  rows={5}
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  required
                  maxLength={30000}
                />
              </div>
              {kind === "single_choice" ? (
                <fieldset className="field">
                  <legend>选项与正确答案</legend>
                  {options.map((option, i) => (
                    <div className="flex gap-2 items-center my-2" key={i}>
                      <input
                        type="radio"
                        name="correct-option"
                        aria-label={`选项${i + 1}为正确答案`}
                        checked={answer === option && option !== ""}
                        onChange={() => {
                          setAnswer(option);
                          setDirty(true);
                        }}
                      />
                      <Input
                        aria-label={`选项${i + 1}`}
                        value={option}
                        onChange={(e) => {
                          setOptions((v) =>
                            v.map((o, j) => (j === i ? e.target.value : o)),
                          );
                          if (answer === option) setAnswer(e.target.value);
                        }}
                        required
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label={`删除选项${i + 1}`}
                        disabled={options.length <= 2}
                        onClick={() => {
                          setOptions((v) => v.filter((_, j) => j !== i));
                          setDirty(true);
                        }}
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  ))}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={options.length >= 20}
                    onClick={() => {
                      setOptions((v) => [...v, ""]);
                      setDirty(true);
                    }}
                  >
                    添加选项
                  </Button>
                </fieldset>
              ) : kind === "boolean" ? (
                <div className="field">
                  <Label htmlFor="q-answer">正确答案</Label>
                  <SelectField
                    id="q-answer"
                    value={answer}
                    onValueChange={(value) => { setAnswer(value); setDirty(true); }}
                  >
                    <option value="">请选择</option>
                    <option value="true">正确</option>
                    <option value="false">错误</option>
                  </SelectField>
                </div>
              ) : kind === "fill" ? (
                <div className="field">
                  <Label htmlFor="q-answer">可接受答案（每行一个）</Label>
                  <Textarea
                    id="q-answer"
                    value={answer}
                    onChange={(e) => setAnswer(e.target.value)}
                  />
                </div>
              ) : (
                <>
                  <div className="field"><Label htmlFor="q-starter">初始 Python 代码（标准输入输出）</Label><Textarea id="q-starter" value={starterCode} maxLength={20000} onChange={e=>{setStarterCode(e.target.value);setDirty(true);}} /></div>
                  <div className="field"><Label htmlFor="q-hints">分步提示（每行一条，最多10条）</Label><Textarea id="q-hints" value={hints} onChange={e=>{setHints(e.target.value);setDirty(true);}} /></div>
                  <Cases
                    title="公开样例"
                    rows={samples}
                    setRows={(v) => {
                      setSamples(v);
                      setDirty(true);
                    }}
                  />
                  <Cases
                    title="私有判题用例"
                    rows={cases}
                    setRows={(v) => {
                      setCases(v);
                      setDirty(true);
                    }}
                  />
                </>
              )}
              <div className="field">
                <Label htmlFor="question-source">参考资料（可选）</Label>
                <SelectField
                  id="question-source"
                  value={referenceMaterial ?? ""}
                  onValueChange={(value) => {
                    setDirty(true);
                    setReferenceMaterial(
                      value ? Number(value) : null,
                    );
                    setReferencePage("");
                  }}
                >
                  <option value="">无参考资料</option>
                  {materials.data?.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.title}
                    </option>
                  ))}
                </SelectField>
              </div>
              {referenceMaterial !== null && (
                <div className="field">
                  <Label htmlFor="question-page">参考页码（可选）</Label>
                  <Input
                    id="question-page"
                    type="number"
                    min={1}
                    step={1}
                    value={referencePage}
                    onChange={(e) => setReferencePage(e.target.value)}
                  />
                  <p className="hint">
                    按原始资料页码填写，保存时会检查页面是否存在。
                  </p>
                </div>
              )}
              <fieldset className="field">
                <legend>关联章节</legend>
                <div className="check-list">
                  {course.data?.chapters.map((c) => (
                    <label key={c.id}>
                      <input
                        type="checkbox"
                        checked={chapterIds.includes(c.id)}
                        onChange={() => {
                          setChapterIds((v) =>
                            v.includes(c.id)
                              ? v.filter((id) => id !== c.id)
                              : [...v, c.id],
                          );
                          setPointIds((v) =>
                            v.filter(
                              (id) =>
                                course.data?.knowledge_points.find(
                                  (p) => p.id === id,
                                )?.chapter_id !== c.id,
                            ),
                          );
                        }}
                      />
                      {c.title}
                    </label>
                  ))}
                </div>
              </fieldset>
              <fieldset className="field">
                <legend>知识点（可选）</legend>
                <div className="check-list">
                  {course.data?.knowledge_points
                    .filter((p) => chapterIds.includes(p.chapter_id))
                    .map((p) => (
                      <label key={p.id}>
                        <input
                          type="checkbox"
                          checked={pointIds.includes(p.id)}
                          onChange={() =>
                            setPointIds((v) =>
                              v.includes(p.id)
                                ? v.filter((id) => id !== p.id)
                                : [...v, p.id],
                            )
                          }
                        />
                        {p.title}
                      </label>
                    ))}
                </div>
              </fieldset>
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
                {busy ? "保存中…" : "保存题目"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
function Cases({
  title,
  rows,
  setRows,
}: {
  title: string;
  rows: Case[];
  setRows: (rows: Case[]) => void;
}) {
  return (
    <fieldset className="field">
      <legend>{title}</legend>
      {rows.map((row, i) => (
        <div className="case-row" key={i}>
          <div>
            <Label htmlFor={`${title}-in-${i}`}>输入 {i + 1}</Label>
            <Textarea
              id={`${title}-in-${i}`}
              value={row.input}
              onChange={(e) =>
                setRows(
                  rows.map((r, j) =>
                    j === i ? { ...r, input: e.target.value } : r,
                  ),
                )
              }
            />
          </div>
          <div>
            <Label htmlFor={`${title}-out-${i}`}>预期输出</Label>
            <Textarea
              id={`${title}-out-${i}`}
              value={row.expected_output}
              onChange={(e) =>
                setRows(
                  rows.map((r, j) =>
                    j === i ? { ...r, expected_output: e.target.value } : r,
                  ),
                )
              }
            />
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={`删除${title}${i + 1}`}
            onClick={() => setRows(rows.filter((_, j) => i !== j))}
          >
            <Trash2 />
          </Button>
        </div>
      ))}
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={rows.length >= 20}
        onClick={() => setRows([...rows, { input: "", expected_output: "" }])}
      >
        添加用例
      </Button>
    </fieldset>
  );
}
