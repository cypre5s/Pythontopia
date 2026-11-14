import { SelectField } from "@/components/select-field";
import { DeleteSelection, SelectTablePage, SelectTableRow, useTableDelete } from "@/components/table-delete";
import { useEffect, useState, type FormEvent } from "react";
import { Plus, Pencil, Trash2, Search, Eye, FileUp } from "lucide-react";
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
import {
  PageHeader,
  ErrorNotice,
  Loading,
  Empty,
  Pager,
} from "@/components/common";
import { api, scoped, errorMessage, fileBlob } from "@/lib/api";
import { useResource } from "@/lib/use-resource";
import { useDraft } from "@/lib/drafts";
import type { Material, Course } from "@/lib/types";
interface MaterialPreview {
  title: string;
  kind: string;
  media_url?: string;
  pages: {
    id?: string;
    page_no?: number;
    title?: string;
    text?: string;
    content?: string;
  }[];
  notice?: string;
}
export function Materials({ classroomId }: { classroomId: number }) {
  const list = useResource<Material[]>(
    scoped("/teacher/materials", classroomId),
  );
  const course = useResource<Course>(scoped("/teacher/courses", classroomId));
  const [query, setQuery] = useState(""),
    [kind, setKind] = useState("all"),
    [status, setStatus] = useState("all"),
    [page, setPage] = useState(1),
    [size, setSize] = useState(10),
    [editing, setEditing] = useState<Material | null>(null),
    [open, setOpen] = useState(false),
    [title, setTitle] = useState(""),
    [chapterIds, setChapterIds] = useState<number[]>([]),
    [file, setFile] = useState<File | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [dirty, setDirty] = useState(false),
    [previewData, setPreviewData] = useState<MaterialPreview | null>(null);
  useDraft(dirty);
  useEffect(() => {
    if (
      list.data?.some((m) => m.status === "queued" || m.status === "processing")
    ) {
      const timer = window.setTimeout(list.reload, 2000);
      return () => window.clearTimeout(timer);
    }
  }, [list.data, list.reload]);
  const rows = (list.data ?? []).filter(
    (m) =>
      m.title.toLowerCase().includes(query.trim().toLowerCase()) &&
      (status === "all" || status === m.status || (status === "converting" && ["queued", "processing"].includes(m.status))) &&
      (kind === "all" ||
        (kind === "unassigned" ? !m.chapter_ids.length : m.kind === kind)),
  );
  const safePage = Math.min(page, Math.max(1, Math.ceil(rows.length / size)));
  const visible = rows.slice((safePage - 1) * size, safePage * size);
  const deletion = useTableDelete<Material>((m) => scoped(`/teacher/materials/${m.id}`, classroomId), list.reload, { classroomId, kind: () => "material" });
  function edit(m: Material | null) {
    if (dirty && !window.confirm("放弃当前资料的未保存修改？")) return;
    setEditing(m);
    setTitle(m?.title ?? "");
    setChapterIds(m?.chapter_ids ?? []);
    setFile(null);
    setDirty(false);
    setError("");
    setOpen(true);
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (file) {
        const body = new FormData();
        body.append("file", file);
        body.append("title", title);
        body.append("chapter_ids", JSON.stringify(chapterIds));
        if (editing) body.append("material_id", String(editing.id));
        await api.upload(
          scoped("/teacher/materials/upload", classroomId),
          body,
        );
      } else if (editing) {
        await api.put(scoped(`/teacher/materials/${editing.id}`, classroomId), {
          title,
          chapter_ids: chapterIds,
        });
      } else throw new Error("请选择需要上传的文件");
      setOpen(false);
      setDirty(false);
      list.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function preview(m: Material) {
    setBusy(true);
    setError("");
    try {
      const result = await api.get<MaterialPreview>(
        scoped(`/teacher/materials/${m.id}`, classroomId),
      );
      setPreviewData(result);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader title="资料库" description="资料独立保存，可关联多个章节。">
        <Button onClick={() => edit(null)}>
          <Plus />
          上传资料
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
              aria-label="搜索资料"
              placeholder="搜索资料名称"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setPage(1);
                deletion.clear();
              }}
            />
          </div>
          <SelectField
            aria-label="资料类型"
            value={kind}
            onValueChange={(value) => {
              setKind(value);
              setPage(1);
              deletion.clear();
            }}
          >
            <option value="all">全部资料</option>
            <option value="unassigned">未归类</option>
            <option value="pdf">PDF</option>
            <option value="presentation">演示文稿</option>
            <option value="text">文本</option>
            <option value="video">视频</option>
          </SelectField>
          <SelectField aria-label="资料状态" value={status} onValueChange={(value) => { setStatus(value); setPage(1); deletion.clear(); }}>
            <option value="all">全部状态</option>
            <option value="ready">可用</option>
            <option value="converting">转换中</option>
            <option value="failed">转换失败</option>
          </SelectField>
        </div>
        <DeleteSelection actions={deletion} />
        {list.loading ? (
          <Loading />
        ) : !rows.length ? (
          <Empty title="没有匹配的资料" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-12"><SelectTablePage actions={deletion} rows={visible} /></TableHead>
                <TableHead>资料名称</TableHead>
                <TableHead>章节</TableHead>
                <TableHead>状态</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.map((m) => (
                <TableRow key={m.id} data-state={deletion.isSelected(m) ? "selected" : undefined}>
                  <TableCell><SelectTableRow actions={deletion} row={m} /></TableCell>
                  <TableCell>
                    <p className="font-medium">{m.title}</p>
                    <small className="text-muted-foreground uppercase">
                      {m.kind}
                    </small>
                  </TableCell>
                  <TableCell>
                    {m.chapter_ids
                      .map(
                        (id) =>
                          course.data?.chapters.find((c) => c.id === id)
                            ?.title ?? "章节",
                      )
                      .join("、") || "未归类"}
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={m.status === "ready" ? "outline" : "secondary"}
                    >
                      {m.status === "ready"
                        ? "可用"
                        : m.status === "failed"
                          ? "转换失败"
                          : m.status === "queued"
                            ? "等待处理"
                            : m.status === "processing"
                              ? "处理中"
                              : m.status}
                    </Badge>
                    {m.error ? (
                      <p className="text-xs text-destructive mt-1">{m.error}</p>
                    ) : null}
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`预览${m.title}`}
                        disabled={busy || m.status !== "ready"}
                        onClick={() => void preview(m)}
                      >
                        <Eye />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`编辑${m.title}`}
                        onClick={() => edit(m)}
                      >
                        <Pencil />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`删除${m.title}`}
                        onClick={() => deletion.ask([m])}
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
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? "编辑资料" : "上传资料"}</DialogTitle>
            <DialogDescription>
              文件保存在私有存储中，仅向有权限的学生开放。
            </DialogDescription>
          </DialogHeader>
          <form inert={busy} aria-busy={busy} onSubmit={save} onChange={() => setDirty(true)}>
            <ErrorNotice message={error} />
            <div className="field">
              <Label htmlFor="material-title">资料名称</Label>
              <Input
                id="material-title"
                required
                maxLength={200}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </div>
            <div className="field">
              <Label htmlFor="material-file">
                {editing ? "替换文件（可选）" : "文件"}
              </Label>
              <Input
                id="material-file"
                type="file"
                required={!editing}
                accept=".pdf,.txt,.md,.ppt,.pptx,.mp4,.webm"
                onChange={(e) => {
                  const f = e.target.files?.[0] ?? null;
                  setFile(f);
                  if (f && !title) setTitle(f.name.replace(/\.[^.]+$/, ""));
                }}
              />
            </div>
            <fieldset className="field">
              <legend>关联章节</legend>
              <div className="check-list">
                {course.data?.chapters.map((c) => (
                  <label key={c.id}>
                    <input
                      type="checkbox"
                      checked={chapterIds.includes(c.id)}
                      onChange={() =>
                        setChapterIds((v) =>
                          v.includes(c.id)
                            ? v.filter((id) => id !== c.id)
                            : [...v, c.id],
                        )
                      }
                    />
                    {c.title}
                  </label>
                ))}
              </div>
              <p className="hint">可以暂不归类，之后再关联章节。</p>
            </fieldset>
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
                <FileUp />
                {busy ? "处理中…" : "保存资料"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
      {previewData && (
        <MaterialViewer
          material={previewData}
          onClose={() => setPreviewData(null)}
        />
      )}
    </>
  );
}
function MaterialViewer({
  material,
  onClose,
}: {
  material: MaterialPreview;
  onClose: () => void;
}) {
  const [url, setUrl] = useState(""),
    [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    let objectUrl = "";
    if (
      material.media_url &&
      (material.kind === "pdf" ||
        material.kind === "presentation" ||
        material.kind === "video")
    ) {
      fileBlob(material.media_url.replace(/^\/api/, ""))
        .then((blob) => {
          if (active) {
            objectUrl = URL.createObjectURL(blob);
            setUrl(objectUrl);
          }
        })
        .catch((e) => {
          if (active) setError(errorMessage(e));
        });
    }
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [material]);
  return (
    <Dialog
      open
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent className="sm:max-w-5xl">
        <DialogHeader>
          <DialogTitle>{material.title}</DialogTitle>
          <DialogDescription>
            资料预览 · {material.kind.toUpperCase()}
          </DialogDescription>
        </DialogHeader>
        <ErrorNotice message={error} />
        {material.notice && <p className="hint">{material.notice}</p>}
        {url ? (
          material.kind === "video" ? (
            <video controls src={url} className="w-full max-h-[65vh]" />
          ) : (
            <iframe
              title={material.title}
              src={url}
              className="w-full h-[65vh] rounded border"
            />
          )
        ) : material.pages?.length ? (
          <div className="max-h-[65vh] overflow-y-auto space-y-6">
            {material.pages.map((p, i) => (
              <section key={p.id ?? i}>
                <h3 className="font-semibold">
                  {p.title || `第 ${p.page_no ?? i + 1} 页`}
                </h3>
                <p className="whitespace-pre-wrap leading-7 text-sm mt-3">
                  {p.text ?? p.content ?? "暂无提取文字"}
                </p>
              </section>
            ))}
          </div>
        ) : error ? null : material.media_url &&
          (material.kind === "pdf" ||
            material.kind === "presentation" ||
            material.kind === "video") ? (
          <Loading />
        ) : (
          <Empty title="此资料暂无可预览内容" />
        )}
      </DialogContent>
    </Dialog>
  );
}
