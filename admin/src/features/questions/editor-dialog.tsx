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
import { Textarea } from "@/components/ui/textarea";
import type { Course, Material, Question } from "@/lib/types";
import { Trash2 } from "lucide-react";
import { Cases } from "./cases-editor";
import { kinds, statuses } from "./types";
import type { useQuestionEditor } from "./use-editor";
export function QuestionDialog({
  editor,
  course,
  materials,
}: {
  editor: ReturnType<typeof useQuestionEditor>;
  course: { data: Course | null };
  materials: { data: Material[] | null };
}) {
  const {
    editing,
    open,
    setOpen,
    title,
    setTitle,
    prompt,
    setPrompt,
    hints,
    setHints,
    starterCode,
    setStarterCode,
    kind,
    setKind,
    status,
    setStatus,
    options,
    setOptions,
    answer,
    setAnswer,
    chapterIds,
    setChapterIds,
    pointIds,
    setPointIds,
    referenceMaterial,
    setReferenceMaterial,
    referencePage,
    setReferencePage,
    cases,
    setCases,
    samples,
    setSamples,
    error,
    busy,
    setDirty,
    save,
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
          <DialogTitle>{editing ? "编辑题目" : "新增题目"}</DialogTitle>
          <DialogDescription>
            私有答案与用例仅用于教师维护和服务端判定。
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
                  onValueChange={(value) => {
                    setKind(value as Question["kind"]);
                    setDirty(true);
                  }}
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
                  onValueChange={(value) => {
                    setStatus(value as Question["status"]);
                    setDirty(true);
                  }}
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
                  onValueChange={(value) => {
                    setAnswer(value);
                    setDirty(true);
                  }}
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
                <div className="field">
                  <Label htmlFor="q-starter">
                    初始 Python 代码（标准输入输出）
                  </Label>
                  <Textarea
                    id="q-starter"
                    value={starterCode}
                    maxLength={20000}
                    onChange={(e) => {
                      setStarterCode(e.target.value);
                      setDirty(true);
                    }}
                  />
                </div>
                <div className="field">
                  <Label htmlFor="q-hints">
                    分步提示（每行一条，最多10条）
                  </Label>
                  <Textarea
                    id="q-hints"
                    value={hints}
                    onChange={(e) => {
                      setHints(e.target.value);
                      setDirty(true);
                    }}
                  />
                </div>
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
                  setReferenceMaterial(value ? Number(value) : null);
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
  );
}
