import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SelectField } from "@/components/select-field";
import { GroupedPicker, type PickerGroup } from "@/components/grouped-picker";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ConditionEditor } from "@/components/condition-editor";
import type { Catalog, Flow } from "@/lib/authoring";

export function FlowSettings({ flow, catalog, entries, onChange, onClose, onAddEntry }: {
  flow: Flow;
  catalog: Catalog;
  entries: PickerGroup[];
  onChange: (flow: Flow) => void;
  onClose: () => void;
  onAddEntry: (id: string) => void;
}) {
  const content = flow.content;
  const mode = content.mode ?? "task";
  function changeContent(patch: Partial<Flow["content"]>) {
    onChange({ ...flow, content: { ...content, ...patch } });
  }
  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>流程设置与入口</DialogTitle>
          <DialogDescription>设置流程所属章节、生效条件和触发入口。</DialogDescription>
        </DialogHeader>
        <div className="grid gap-x-5 sm:grid-cols-2">
          <div className="field">
            <Label htmlFor="flow-title">流程名称</Label>
            <Input id="flow-title" value={flow.title} onChange={(e) => onChange({ ...flow, title: e.target.value })} />
          </div>
          <div className="field">
            <Label htmlFor="flow-chapter">所属章节</Label>
            <SelectField id="flow-chapter" value={flow.chapter_id ?? ""} onValueChange={(value) =>
              onChange({ ...flow, chapter_id: value || null })
            }>
              <option value="">公共流程</option>
              {catalog.chapters.map((chapter) => <option key={chapter.id} value={chapter.id}>{chapter.title}</option>)}
            </SelectField>
          </div>
          <div className="field">
            <Label htmlFor="flow-mode">运行方式</Label>
            <SelectField id="flow-mode" value={mode} onValueChange={(value) =>
              onChange({ ...flow, quest_id: value === "task" ? flow.quest_id : null, content: { ...content, mode: value as typeof mode } })
            }>
              <option value="public">公共交互</option>
              <option value="ambient">日常对白</option>
              <option value="task">任务流程</option>
            </SelectField>
          </div>
          {mode === "task" && (
            <div className="field">
              <Label htmlFor="flow-quest">关联任务</Label>
              <SelectField id="flow-quest" value={flow.quest_id ?? ""} onValueChange={(value) =>
                onChange({ ...flow, quest_id: value || null })
              }>
                <option value="">选择任务</option>
                {catalog.quests.map((quest) => <option key={quest.id} value={quest.id}>{quest.title}</option>)}
              </SelectField>
            </div>
          )}
          <div className="field">
            <Label htmlFor="flow-priority">入口优先级</Label>
            <Input id="flow-priority" type="number" min={-1000} max={1000} value={content.priority ?? 0}
              onChange={(e) => changeContent({ priority: Number(e.target.value) })} />
            <p className="hint">多个流程满足条件时，数值较大的优先触发。</p>
          </div>
        </div>
        <div>
          <Label>流程生效条件</Label>
          <ConditionEditor value={content.when ?? { always: true }} catalog={catalog} flow={flow}
            onChange={(when) => changeContent({ when })} />
        </div>
        <div className="field">
          <Label htmlFor="add-flow-entry">添加触发入口</Label>
          <GroupedPicker id="add-flow-entry" label="添加触发入口" groups={entries}
            placeholder={entries.length ? "选择位置和入口" : "没有可添加的入口"}
            disabled={Object.keys(content.entries).length >= 30} onChange={(value) => {
            onAddEntry(value);
            onClose();
          }} />
          <p className="hint">添加后在画布上手动连接起始节点。右键入口卡片可编辑条件。</p>
        </div>
      </DialogContent>
    </Dialog>
  );
}
