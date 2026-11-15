import { SelectField } from "@/components/select-field";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { Authoring, Quest } from "@/features/authoring/model";
export function QuestSettings({
  quest,
  data,
  onChange: replace,
}: {
  quest: Quest;
  data: Authoring;
  onChange: (quest: Quest) => void;
}) {
  return (
    <>
      <div className="field">
        <Label htmlFor="quest-title">任务名称</Label>
        <Input
          id="quest-title"
          value={quest.title}
          onChange={(e) => replace({ ...quest, title: e.target.value })}
        />
      </div>
      <div className="field">
        <Label htmlFor="quest-chapter">所属章节</Label>
        <SelectField
          id="quest-chapter"
          value={quest.chapter_id}
          onValueChange={(selectedValue) =>
            replace({ ...quest, chapter_id: selectedValue })
          }
        >
          {data.catalog.chapters.map((c) => (
            <option key={c.id} value={c.id}>
              {c.title}
            </option>
          ))}
        </SelectField>
      </div>
      <div className="field">
        <Label htmlFor="quest-kind">任务类型</Label>
        <SelectField
          id="quest-kind"
          value={quest.definition.kind ?? "main"}
          onValueChange={(selectedValue) =>
            replace({
              ...quest,
              definition: {
                ...quest.definition,
                kind: selectedValue as "main" | "side",
              },
            })
          }
        >
          <option value="main">主线</option>
          <option value="side">支线</option>
        </SelectField>
      </div>
      <p className="hint">
        任务定义与学生进度分开保存。有学习证据的任务会受到服务端保护；完成条件始终由服务端验证。
      </p>
    </>
  );
}
