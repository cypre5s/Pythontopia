import {
  Empty,
  ErrorNotice,
  Loading,
  PageHeader,
  TableSearch,
} from "@/components/common";
import { SelectField } from "@/components/select-field";
import { DeleteSelection, useTableDelete } from "@/components/table-delete";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { scoped } from "@/lib/api";
import type { Activity, Course, Material, Question } from "@/lib/types";
import { useResource } from "@/lib/use-resource";
import { ListPlus, Pencil, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { ActivityDialog } from "./editor-dialog";
import { kindName } from "./types";
import { useActivityEditor } from "./use-editor";
export function Activities({ classroomId }: { classroomId: number }) {
  const course = useResource<Course>(scoped("/teacher/courses", classroomId)),
    materials = useResource<Material[]>(
      scoped("/teacher/materials", classroomId),
    ),
    questions = useResource<Question[]>(
      scoped("/teacher/questions", classroomId),
    );
  const editor = useActivityEditor(classroomId, course.reload, course.data);
  const { open, chapter, error, edit } = editor;

  const [query, setQuery] = useState(""),
    [chapterFilter, setChapterFilter] = useState("all"),
    [typeFilter, setTypeFilter] = useState("all");
  const deletion = useTableDelete<Activity>(
    (a) =>
      scoped(`/teacher/activities/${encodeURIComponent(a.id)}`, classroomId),
    course.reload,
    { classroomId, kind: () => "activity" },
  );
  const candidates = (questions.data ?? []).filter(
    (q) => q.chapter_ids.includes(chapter) && q.status === "ready",
  );
  const activities = (course.data?.activities ?? []).filter(
    (activity) =>
      activity.title.toLowerCase().includes(query.trim().toLowerCase()) &&
      (chapterFilter === "all" ||
        String(activity.chapter_id) === chapterFilter) &&
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
          <TableSearch
            label="搜索活动"
            placeholder="搜索活动名称"
            value={query}
            onValueChange={setQuery}
          />
          <SelectField
            aria-label="活动所属章节"
            value={chapterFilter}
            onValueChange={setChapterFilter}
          >
            <option value="all">全部章节</option>
            {course.data?.chapters.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </SelectField>
          <SelectField
            aria-label="活动类型"
            value={typeFilter}
            onValueChange={setTypeFilter}
          >
            <option value="all">全部类型</option>
            {["阅读", "练习", "测验"].map((label) => (
              <option key={label} value={label}>
                {label}
              </option>
            ))}
          </SelectField>
        </div>
        {course.loading ? (
          <Loading />
        ) : !activities.length ? (
          <Empty
            title={
              course.data?.activities.length ? "没有匹配的活动" : "尚无学习活动"
            }
          />
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
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`删除${a.title}`}
                        title="删除活动"
                        onClick={() => deletion.ask([a])}
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
      </div>
      <DeleteSelection actions={deletion} />
      <ActivityDialog
        editor={editor}
        course={course}
        materials={materials}
        questions={questions}
        candidates={candidates}
      />
    </>
  );
}
