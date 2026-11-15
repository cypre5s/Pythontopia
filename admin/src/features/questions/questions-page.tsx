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
import { scoped } from "@/lib/api";
import type { Course, Material, Question } from "@/lib/types";
import { useResource } from "@/lib/use-resource";
import { Pencil, Plus, Search, Trash2 } from "lucide-react";
import { useState } from "react";
import { QuestionDialog } from "./editor-dialog";
import { kinds, statuses } from "./types";
import { useQuestionEditor } from "./use-editor";
export function Questions({ classroomId }: { classroomId: number }) {
  const list = useResource<Question[]>(
      scoped("/teacher/questions", classroomId),
    ),
    course = useResource<Course>(scoped("/teacher/courses", classroomId)),
    materials = useResource<Material[]>(
      scoped("/teacher/materials", classroomId),
    );
  const editor = useQuestionEditor(classroomId, list.reload);
  const { open, error, edit } = editor;

  const [query, setQuery] = useState(""),
    [filter, setFilter] = useState("all"),
    [sourceFilter, setSourceFilter] = useState("all"),
    [kindFilter, setKindFilter] = useState("all"),
    [page, setPage] = useState(1),
    [size, setSize] = useState(10);
  const rows = (list.data ?? []).filter(
    (q) =>
      (q.title.toLowerCase().includes(query.trim().toLowerCase()) ||
        q.prompt.toLowerCase().includes(query.trim().toLowerCase())) &&
      (kindFilter === "all" || q.kind === kindFilter) &&
      (filter === "all" || q.status === filter) &&
      (sourceFilter === "all" || q.source_type === sourceFilter),
  );
  const safePage = Math.min(page, Math.max(1, Math.ceil(rows.length / size)));
  const visible = rows.slice((safePage - 1) * size, safePage * size);
  const deletion = useTableDelete<Question>(
    (q) =>
      scoped(`/teacher/questions/${encodeURIComponent(q.id)}`, classroomId),
    list.reload,
    { classroomId, kind: () => "question" },
  );
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
          <SelectField
            aria-label="题目类型"
            value={kindFilter}
            onValueChange={(value) => {
              setKindFilter(value);
              setPage(1);
              deletion.clear();
            }}
          >
            <option value="all">全部题型</option>
            {Object.entries(kinds).map(([key, value]) => (
              <option key={key} value={key}>
                {value}
              </option>
            ))}
          </SelectField>
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
                <TableHead className="w-12">
                  <SelectTablePage actions={deletion} rows={visible} />
                </TableHead>
                <TableHead>题目</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>来源</TableHead>
                <TableHead>状态</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.map((q) => (
                <TableRow
                  key={q.id}
                  data-state={deletion.isSelected(q) ? "selected" : undefined}
                >
                  <TableCell>
                    <SelectTableRow actions={deletion} row={q} />
                  </TableCell>
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
      <QuestionDialog editor={editor} course={course} materials={materials} />
    </>
  );
}
