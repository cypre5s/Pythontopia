import {
  Empty,
  ErrorNotice,
  Loading,
  PageHeader,
  Pager,
} from "@/components/common";
import { SelectField } from "@/components/select-field";
import {
  DeleteSelection,
  SelectTablePage,
  SelectTableRow,
  useTableDelete,
} from "@/components/table-delete";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { NarrationPlayer } from "@/features/materials/narration/player";
import { NarrationReview } from "@/features/materials/narration/review";
import { api, errorMessage, scoped } from "@/lib/api";
import type { Course, Material } from "@/lib/types";
import { useResource } from "@/lib/use-resource";
import {
  Eye,
  Headphones,
  NotebookPen,
  Pencil,
  Plus,
  Search,
  Trash2,
} from "lucide-react";
import { useEffect, useState } from "react";
import { MaterialDialog } from "./editor-dialog";
import { MaterialViewer } from "./material-viewer";
import { MaterialPreview } from "./types";
import { useMaterialEditor } from "./use-editor";
export function Materials({ classroomId }: { classroomId: number }) {
  const list = useResource<Material[]>(
    scoped("/teacher/materials", classroomId),
  );
  const course = useResource<Course>(scoped("/teacher/courses", classroomId));
  const editor = useMaterialEditor(classroomId, list.reload);
  const { open, error, setError, busy, setBusy, edit } = editor;

  const [query, setQuery] = useState(""),
    [kind, setKind] = useState("all"),
    [status, setStatus] = useState("all"),
    [page, setPage] = useState(1),
    [size, setSize] = useState(10),
    [previewData, setPreviewData] = useState<MaterialPreview | null>(null),
    [narration, setNarration] = useState<Material | null>(null),
    [listening, setListening] = useState<Material | null>(null);
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
      (status === "all" ||
        status === m.status ||
        (status === "converting" &&
          ["queued", "processing"].includes(m.status))) &&
      (kind === "all" ||
        (kind === "unassigned" ? !m.chapter_ids.length : m.kind === kind)),
  );
  const safePage = Math.min(page, Math.max(1, Math.ceil(rows.length / size)));
  const visible = rows.slice((safePage - 1) * size, safePage * size);
  const deletion = useTableDelete<Material>(
    (m) => scoped(`/teacher/materials/${m.id}`, classroomId),
    list.reload,
    { classroomId, kind: () => "material" },
  );
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
          <SelectField
            aria-label="资料状态"
            value={status}
            onValueChange={(value) => {
              setStatus(value);
              setPage(1);
              deletion.clear();
            }}
          >
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
                <TableHead className="w-12">
                  <SelectTablePage actions={deletion} rows={visible} />
                </TableHead>
                <TableHead>资料名称</TableHead>
                <TableHead>章节</TableHead>
                <TableHead>状态</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.map((m) => (
                <TableRow
                  key={m.id}
                  data-state={deletion.isSelected(m) ? "selected" : undefined}
                >
                  <TableCell>
                    <SelectTableRow actions={deletion} row={m} />
                  </TableCell>
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
                      {["pdf", "presentation"].includes(m.kind) && (
                        <>
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`讲解${m.title}`}
                            disabled={busy || m.status !== "ready"}
                            onClick={() => setNarration(m)}
                          >
                            <NotebookPen />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`试听${m.title}`}
                            disabled={busy || m.status !== "ready"}
                            onClick={() => setListening(m)}
                          >
                            <Headphones />
                          </Button>
                        </>
                      )}
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
      <MaterialDialog editor={editor} course={course} />
      {previewData && (
        <MaterialViewer
          material={previewData}
          onClose={() => setPreviewData(null)}
        />
      )}
      {listening && (
        <NarrationPlayer
          key={`${classroomId}:${listening.id}`}
          classroomId={classroomId}
          materialId={listening.id}
          title={listening.title}
          onClose={() => setListening(null)}
        />
      )}
      {narration && (
        <NarrationReview
          key={`${classroomId}:${narration.id}`}
          classroomId={classroomId}
          materialId={narration.id}
          title={narration.title}
          onClose={() => setNarration(null)}
        />
      )}
    </>
  );
}
