import { SelectField } from "@/components/select-field";
import { DeleteSelection, SelectTablePage, SelectTableRow, useTableDelete } from "@/components/table-delete";
import { useState, type FormEvent } from "react";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
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
import type { Course, Chapter, Point } from "@/lib/types";
export function Curriculum({ classroomId }: { classroomId: number }) {
  const course = useResource<Course>(scoped("/teacher/courses", classroomId));
  const [tab, setTab] = useState("chapters"),
    [query, setQuery] = useState(""),
    [pointChapter, setPointChapter] = useState("all"),
    [editing, setEditing] = useState<Chapter | Point | null>(null),
    [open, setOpen] = useState(false),
    [title, setTitle] = useState(""),
    [order, setOrder] = useState(1),
    [chapter, setChapter] = useState(0),
    [summary, setSummary] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [dirty, setDirty] = useState(false);
  useDraft(dirty);
  const deletion = useTableDelete<Chapter | Point>((item) => scoped(
    `/teacher/${"order_index" in item ? "chapters" : "knowledge-points"}/${item.id}`, classroomId,
  ), course.reload, { classroomId, kind: item => "order_index" in item ? "chapter" : null });
  function edit(item: Chapter | Point | null) {
    if (dirty && !window.confirm("放弃当前内容的未保存修改？")) return;
    setEditing(item);
    setTitle(item?.title ?? "");
    setOrder(
      item && "order_index" in item
        ? item.order_index
        : (course.data?.chapters.length ?? 0) + 1,
    );
    setChapter(
      item && "chapter_id" in item
        ? item.chapter_id
        : (course.data?.chapters[0]?.id ?? 0),
    );
    setSummary(
      item && "content" in item
        ? String(item.content.summary_markdown ?? "")
        : "",
    );
    setError("");
    setDirty(false);
    setOpen(true);
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    if (tab === "points" && !chapter) {
      setError("请选择所属章节。");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const body =
        tab === "chapters"
          ? { title, order_index: order }
          : {
              title,
              content: {
                ...(editing && "content" in editing ? editing.content : {}),
                summary_markdown: summary,
              },
              ...(!editing ? { chapter_id: chapter } : {}),
            };
      const endpoint = `/teacher/${tab === "chapters" ? "chapters" : "knowledge-points"}${editing ? `/${editing.id}` : ""}`;
      await (editing ? api.put : api.post)(scoped(endpoint, classroomId), body);
      setOpen(false);
      setDirty(false);
      course.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  const rows = ((tab === "chapters" ? course.data?.chapters : course.data?.knowledge_points) ?? []).filter((item) =>
    item.title.toLowerCase().includes(query.trim().toLowerCase()) &&
    (!("chapter_id" in item) || pointChapter === "all" || String(item.chapter_id) === pointChapter),
  );
  return (
    <>
      <PageHeader
        title="课程结构"
        description="组织章节与知识点，供资料、题目和活动引用。"
      >
        <Button onClick={() => edit(null)} disabled={course.loading}>
          <Plus />
          新增{tab === "chapters" ? "章节" : "知识点"}
        </Button>
      </PageHeader>
      <ErrorNotice message={course.error || (!open ? error : "")} />
      <Tabs value={tab} onValueChange={(value) => { setTab(value); setQuery(""); deletion.clear(); }} className="gap-4">
        <TabsList variant="line" aria-label="课程结构视图">
          <TabsTrigger value="chapters">章节</TabsTrigger>
          <TabsTrigger value="points">知识点</TabsTrigger>
        </TabsList>
        <TabsContent value={tab}>
          <div className="surface">
            <div className="toolbar">
              <TableSearch label={tab === "chapters" ? "搜索章节" : "搜索知识点"} value={query} onValueChange={(value) => { setQuery(value); deletion.clear(); }} />
              {tab === "points" && (
                <SelectField aria-label="知识点所属章节" value={pointChapter} onValueChange={(value) => { setPointChapter(value); deletion.clear(); }}>
                  <option value="all">全部章节</option>
                  {course.data?.chapters.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
                </SelectField>
              )}
            </div>
            <DeleteSelection actions={deletion} />
            {course.loading ? <Loading /> : !rows.length ? <Empty title="没有匹配的内容" /> : (
              <Table>
                <TableHeader><TableRow>
                  <TableHead className="w-12"><SelectTablePage actions={deletion} rows={rows} /></TableHead>
                  <TableHead>名称</TableHead>
                  <TableHead>{tab === "chapters" ? "顺序" : "所属章节"}</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow></TableHeader>
                <TableBody>
                  {rows.map((item) => (
                    <TableRow key={item.id} data-state={deletion.isSelected(item) ? "selected" : undefined}>
                      <TableCell><SelectTableRow actions={deletion} row={item} /></TableCell>
                      <TableCell className="font-medium">{item.title}</TableCell>
                      <TableCell>{"order_index" in item
                        ? `第 ${item.order_index} 章`
                        : course.data?.chapters.find((c) => c.id === item.chapter_id)?.title}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="icon" aria-label={`编辑${item.title}`} onClick={() => edit(item)}><Pencil /></Button>
                          <Button variant="ghost" size="icon" aria-label={`删除${item.title}`} disabled={busy} onClick={() => deletion.ask([item])}><Trash2 /></Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </div>
        </TabsContent>
      </Tabs>
      <Dialog
        open={open}
        onOpenChange={(value) => {
          if (!busy) setOpen(value);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editing ? "编辑" : "新增"}
              {tab === "chapters" ? "章节" : "知识点"}
            </DialogTitle>
            <DialogDescription>修改仅作用于当前班级课程。</DialogDescription>
          </DialogHeader>
          <form inert={busy} aria-busy={busy} onSubmit={save} onChange={() => setDirty(true)}>
            <ErrorNotice message={error} />
            <div className="field">
              <Label htmlFor="curr-title">名称</Label>
              <Input
                id="curr-title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={200}
                required
              />
            </div>
            {tab === "chapters" ? (
              <div className="field">
                <Label htmlFor="curr-order">章节顺序</Label>
                <Input
                  id="curr-order"
                  type="number"
                  min={1}
                  value={order}
                  onChange={(e) => setOrder(Number(e.target.value))}
                  required
                />
              </div>
            ) : (
              <>
                <div className="field">
                  <Label htmlFor="curr-chapter">所属章节</Label>
                  <SelectField
                    id="curr-chapter"
                    value={chapter}
                    disabled={Boolean(editing)}
                    onValueChange={(value) => { setChapter(Number(value)); setDirty(true); }}
                    required
                  >
                    <option value="">选择章节</option>
                    {course.data?.chapters.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.title}
                      </option>
                    ))}
                  </SelectField>
                </div>
                <div className="field">
                  <Label htmlFor="curr-summary">知识点说明</Label>
                  <Textarea
                    id="curr-summary"
                    value={summary}
                    onChange={(e) => setSummary(e.target.value)}
                  />
                </div>
              </>
            )}
            <DialogFooter>
              <Button
                variant="outline"
                type="button"
                onClick={() => setOpen(false)}
                disabled={busy}
              >
                取消
              </Button>
              <Button type="submit" disabled={busy}>
                保存
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
