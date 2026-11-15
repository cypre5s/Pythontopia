import { ErrorNotice } from "@/components/common";
import { SelectField } from "@/components/select-field";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { KnowledgePointPicker } from "@/features/curriculum/knowledge-point-picker";
import type { Activity, Course, Material, Question } from "@/lib/types";
import { ArrowDown, ArrowUp } from "lucide-react";
import { ResourceEditor } from "./resource-editor";
import type { useActivityEditor } from "./use-editor";
export function ActivityDialog({
  editor,
  course,
  materials,
  questions,
  candidates,
}: {
  editor: ReturnType<typeof useActivityEditor>;
  course: { data: Course | null };
  materials: { data: Material[] | null };
  questions: { data: Question[] | null };
  candidates: Question[];
}) {
  const {
    editing,
    open,
    setOpen,
    questionMode,
    title,
    setTitle,
    id,
    chapter,
    setChapter,
    kind,
    setKind,
    resources,
    setResources,
    pointIds,
    setPointIds,
    npc,
    setNpc,
    assistant,
    setAssistant,
    selected,
    setSelected,
    error,
    busy,
    setDirty,
    save,
    move,
  } = editor;
  return (
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
        <form
          inert={busy}
          aria-busy={busy}
          onSubmit={save}
          onChange={() => setDirty(true)}
        >
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
                        setResources([]);
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
                        setResources([]);
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
                  <Label htmlFor="a-npc">答疑角色</Label>
                  <SelectField
                    id="a-npc"
                    placeholder="使用课程默认角色"
                    value={npc}
                    onValueChange={(value) => {
                      setNpc(value);
                      setDirty(true);
                    }}
                  >
                    <option value="">使用课程默认角色</option>
                    {course.data?.npcs.map((n) => (
                      <option key={n.key} value={n.key}>
                        {n.name}
                      </option>
                    ))}
                  </SelectField>
                </div>
                <KnowledgePointPicker
                  key={id}
                  chapters={course.data?.chapters ?? []}
                  points={course.data?.knowledge_points ?? []}
                  selected={pointIds}
                  initialChapterId={chapter}
                  onChange={(ids) => {
                    setPointIds(ids);
                    setDirty(true);
                  }}
                />
                {kind === "lesson" || kind === "reading" ? (
                  <ResourceEditor classroomId={editor.classroomId} resources={resources} materials={materials.data ?? []} chapterId={chapter}
                    onChange={(value) => { setResources(value); setDirty(true); }} />
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
  );
}
