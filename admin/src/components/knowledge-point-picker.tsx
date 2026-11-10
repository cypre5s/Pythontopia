import { useId, useState } from "react";
import { X } from "lucide-react";
import { SelectField } from "@/components/select-field";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import type { Chapter, Point } from "@/lib/types";

export function KnowledgePointPicker({ chapters, points, selected, initialChapterId, onChange }: {
  chapters: Chapter[];
  points: Point[];
  selected: number[];
  initialChapterId: number;
  onChange: (ids: number[]) => void;
}) {
  const id = useId();
  const [chapterId, setChapterId] = useState(String(initialChapterId));
  const uniquePoints = [...new Map(points.map((point) => [point.id, point])).values()];
  const selectedIds = [...new Set(selected)];
  const groups = [...chapters].sort((a, b) => a.order_index - b.order_index);
  const visible = uniquePoints.filter((point) => String(point.chapter_id) === chapterId);

  return (
    <fieldset className="field">
      <legend>关联知识点</legend>
      <Label htmlFor={`${id}-chapter`}>知识点所属章节</Label>
      <SelectField id={`${id}-chapter`} value={chapterId} onValueChange={setChapterId}>
        {groups.map((chapter) => <option key={chapter.id} value={chapter.id}>{chapter.title}</option>)}
      </SelectField>
      <p className="hint">先选择章节，再勾选知识点；可跨章节选择。</p>
      {visible.length ? (
        <div className="check-list" role="group" aria-label="当前章节知识点">
          {visible.map((point) => (
            <Label key={point.id} htmlFor={`${id}-point-${point.id}`} className="min-h-9 cursor-pointer">
              <Checkbox
                id={`${id}-point-${point.id}`}
                checked={selectedIds.includes(point.id)}
                disabled={!selectedIds.includes(point.id) && selectedIds.length >= 65}
                onCheckedChange={(checked) => onChange(checked
                  ? [...new Set([...selectedIds, point.id])]
                  : selectedIds.filter((key) => key !== point.id))}
              />
              {point.title}
            </Label>
          ))}
        </div>
      ) : <p className="hint">本章暂无知识点，可选择其他章节。</p>}
      <div className="space-y-2" role="group" aria-label="已选知识点">
        <p className="text-sm text-muted-foreground">已选 {selectedIds.length} / 65</p>
        <div className="flex flex-wrap gap-2">
          {selectedIds.map((key) => {
            const point = uniquePoints.find((item) => item.id === key);
            const chapter = groups.find((item) => item.id === point?.chapter_id);
            return (
              <span key={key} className="inline-flex max-w-full items-center gap-1 rounded-md border pl-2 text-sm">
                <span>{point?.title ?? "知识点已不可用"}{chapter ? <span className="ml-1 text-xs text-muted-foreground">· {chapter.title}</span> : null}</span>
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`移除${point?.title ?? "不可用知识点"}`} onClick={() => onChange(selectedIds.filter((value) => value !== key))}>
                  <X />
                </Button>
              </span>
            );
          })}
        </div>
      </div>
    </fieldset>
  );
}
