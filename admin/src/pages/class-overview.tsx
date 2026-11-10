import { useState } from "react";
import { Plus, ArrowDownToLine, BookOpen, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PageHeader, ErrorNotice, Loading } from "@/components/common";
import { api, errorMessage } from "@/lib/api";
import { useResource } from "@/lib/use-resource";
import type { Classroom, CourseInstance } from "@/lib/types";
export function ClassOverview({
  classroom,
  onContent,
  onImport,
}: {
  classroom: Classroom;
  onContent: () => void;
  onImport: () => void;
}) {
  const course = useResource<CourseInstance | null>(
    `/teacher/classrooms/${classroom.id}/course`,
  );
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function initialize() {
    if (
      !window.confirm(
        "为此班级创建一份空课程，然后自行添加章节、资料和教学活动？",
      )
    )
      return;
    setBusy(true);
    setError("");
    try {
      await api.post(`/teacher/classrooms/${classroom.id}/course/empty`, {
        confirmed: true,
        name: `${classroom.name}的课程`,
      });
      course.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader
        title={classroom.name}
        description="当前班级的课程与教学安排。"
      />
      <ErrorNotice message={error || course.error} />
      <div className="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
        <section className="surface p-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="icon-box">
              <BookOpen />
            </div>
            <h2 className="text-lg font-semibold">班级课程</h2>
          </div>
          {course.loading ? (
            <Loading />
          ) : course.data ? (
            <>
              <p className="mb-3 flex items-center gap-2 text-sm text-muted-foreground">
                <CheckCircle2 size={15} className="shrink-0 text-emerald-700" />
                此班级的课程独立维护，修改不会同步到其他班级。
              </p>
              <h3 className="mb-6 text-2xl font-semibold">{course.data.name}</h3>
              <Button onClick={onContent}>管理课程内容</Button>
            </>
          ) : !course.error ? (
            <div>
              <h3 className="font-medium">还没有班级课程</h3>
              <p className="text-muted-foreground my-3">
                导入课程包建立本班的独立副本，或者创建空课程开始编辑。
              </p>
              <div className="flex flex-wrap gap-2">
                <Button onClick={onImport}>导入课程包</Button>
                <Button
                  variant="outline"
                  onClick={() => void initialize()}
                  disabled={busy}
                >
                  <Plus />
                  {busy ? "正在创建…" : "创建空课程"}
                </Button>
              </div>
            </div>
          ) : null}
        </section>
        <section className="surface p-6">
          <h2 className="text-lg font-semibold mb-3">邀请学生加入</h2>
          <p className="text-muted-foreground text-sm">
            学生可在小镇中使用加入码加入本班，也可以由教师导入名册。
          </p>
          <div className="join-code">
            {classroom.join_code ?? "无可用加入码"}
          </div>
          {classroom.join_code ? (
            <Button
              variant="outline"
              onClick={() => {
                navigator.clipboard
                  .writeText(classroom.join_code!)
                  .catch(() => setError("复制失败，请手动选择加入码复制。"));
              }}
            >
              <ArrowDownToLine />
              复制加入码
            </Button>
          ) : null}
        </section>
      </div>
    </>
  );
}
