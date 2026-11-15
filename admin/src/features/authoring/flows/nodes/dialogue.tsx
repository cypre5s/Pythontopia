import { GroupedPicker } from "@/components/grouped-picker";
import { SelectField } from "@/components/select-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ConditionEditor } from "@/features/authoring/conditions/condition-editor";
import {
  type Catalog,
  type Flow,
  type StoryNode,
} from "@/features/authoring/model";
import { npcGroups } from "@/features/authoring/pickers";
import { newId } from "@/lib/id";
import { Plus, Trash2 } from "lucide-react";
import { NodeTarget } from "../connections";
export function DialogueFields({
  node,
  flow,
  catalog,
  onChange,
}: {
  node: Extract<StoryNode, { type: "dialogue" }>;
  flow: Flow;
  catalog: Catalog;
  onChange: (node: StoryNode) => void;
}) {
  const rounds = node.text.split("\n\n");
  const goals = catalog.quests.find((q) => q.id === flow.quest_id)?.nodes ?? [];
  return (
    <>
      <div className="field">
        <Label htmlFor="node-speaker">说话人</Label>
        <GroupedPicker
          label="选择说话 NPC"
          placeholder="按位置选择 NPC"
          groups={npcGroups(catalog)}
          onChange={(id) => {
            const npc = catalog.npcs.find((item) => item.id === id);
            if (npc) {
              const next = { ...node, speaker_npc_id: npc.id };
              delete next.speaker;
              onChange(next);
            }
          }}
        />
        <Input
          id="node-speaker"
          value={
            node.speaker_npc_id
              ? (catalog.npcs.find((npc) => npc.id === node.speaker_npc_id)
                  ?.name ?? "")
              : (node.speaker ?? "")
          }
          maxLength={80}
          placeholder="自定义名称，留空沿用当前交互角色"
          onChange={(e) => {
            const next = { ...node };
            delete next.speaker_npc_id;
            if (e.target.value) next.speaker = e.target.value;
            else delete next.speaker;
            onChange(next);
          }}
        />
      </div>
      <div className="field">
        <p className="font-medium text-sm">对白内容</p>
        <p className="hint">
          每个输入框是一轮对白。玩家逐轮继续，最后一轮显示选项并完成交谈目标。
        </p>
        {rounds.map((text, index) => (
          <div key={index} className="space-y-2">
            <div className="flex items-center justify-between gap-2">
              <Label htmlFor={`node-text-${index}`}>对白 {index + 1}</Label>
              <Button
                variant="ghost"
                size="icon-sm"
                disabled={rounds.length === 1}
                aria-label={`删除对白 ${index + 1}`}
                onClick={() =>
                  onChange({
                    ...node,
                    text: rounds.filter((_, i) => i !== index).join("\n\n"),
                  })
                }
              >
                <Trash2 aria-hidden="true" />
              </Button>
            </div>
            <Textarea
              id={`node-text-${index}`}
              rows={3}
              value={text}
              maxLength={Math.max(
                0,
                8000 - (node.text?.length ?? 0) + text.length,
              )}
              onChange={(e) =>
                onChange({
                  ...node,
                  text: rounds
                    .map((round, i) =>
                      i === index
                        ? e.target.value
                            .replace(/\r\n/g, "\n")
                            .replace(/\n{2,}/g, "\n")
                        : round,
                    )
                    .join("\n\n"),
                })
              }
            />
          </div>
        ))}
        <Button
          variant="outline"
          size="sm"
          disabled={(node.text?.length ?? 0) + 2 >= 8000}
          onClick={() =>
            onChange({ ...node, text: [...rounds, ""].join("\n\n") })
          }
        >
          <Plus aria-hidden="true" />
          添加对白
        </Button>
      </div>
      {flow.quest_id && (
        <div className="field">
          <Label htmlFor="node-checkpoint">完成交谈目标（可选）</Label>
          <SelectField
            id="node-checkpoint"
            value={node.checkpoint ?? ""}
            onValueChange={(selectedValue) => {
              const next = { ...node };
              if (selectedValue) next.checkpoint = selectedValue;
              else delete next.checkpoint;
              onChange(next);
            }}
          >
            <option value="">不提交任务进度</option>
            {goals
              .filter((g) =>
                ["TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"].includes(
                  g.type,
                ),
              )
              .map((g) => (
                <option key={g.id} value={g.id}>
                  {g.title ?? g.type}
                </option>
              ))}
          </SelectField>
        </div>
      )}
      <div className="field">
        <Label>对话后续</Label>
        {node.choices?.length ? (
          node.choices.map((c, i) => (
            <div className="route-card mb-2" key={c.id}>
              <Input
                aria-label={`选项 ${i + 1}`}
                value={c.label}
                maxLength={200}
                onChange={(e) =>
                  onChange({
                    ...node,
                    choices: node.choices?.map((old, j) =>
                      i === j ? { ...old, label: e.target.value } : old,
                    ),
                  })
                }
              />
              <NodeTarget
                value={c.next}
                nodes={flow.content.nodes}
                exclude={node.id}
                onChange={(next) =>
                  onChange({
                    ...node,
                    choices: node.choices?.map((old, j) =>
                      i === j ? { ...old, next } : old,
                    ),
                  })
                }
              />
              {c.when && (
                <ConditionEditor
                  value={c.when}
                  catalog={catalog}
                  flow={flow}
                  onChange={(when) =>
                    onChange({
                      ...node,
                      choices: node.choices?.map((old, j) =>
                        i === j ? { ...old, when } : old,
                      ),
                    })
                  }
                />
              )}
              <div className="flex gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    onChange({
                      ...node,
                      choices: node.choices?.map((old, j) => {
                        if (i !== j) return old;
                        const v = { ...old };
                        if (v.when) delete v.when;
                        else v.when = { always: true };
                        return v;
                      }),
                    })
                  }
                >
                  {c.when ? "移除条件" : "设置条件"}
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    onChange({
                      ...node,
                      choices: node.choices?.filter((_, j) => i !== j),
                    })
                  }
                >
                  删除选项
                </Button>
              </div>
            </div>
          ))
        ) : (
          <NodeTarget
            optional
            value={node.next ?? ""}
            nodes={flow.content.nodes}
            exclude={node.id}
            onChange={(next) => onChange({ ...node, next })}
          />
        )}
        <Button
          className="mt-2"
          variant="outline"
          size="sm"
          disabled={(node.choices?.length ?? 0) >= 10}
          onClick={() => {
            const next = {
              ...node,
              choices: [
                ...(node.choices ?? []),
                {
                  id: newId("choice"),
                  label: "新的回答",
                  next: node.next ?? "",
                },
              ],
            };
            delete next.next;
            onChange(next);
          }}
        >
          <Plus />
          添加回答选项
        </Button>
      </div>
    </>
  );
}
