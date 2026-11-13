import { SelectField } from "@/components/select-field";
import { GroupedPicker } from "@/components/grouped-picker";
import { npcGroups } from "@/lib/authoring-pickers";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ConditionEditor } from "@/components/condition-editor";
import { newId } from "@/lib/id";
import {
  nodeTitle,
  workspaceNames,
  type Catalog,
  type Flow,
  type Route,
  type StoryNode,
} from "@/lib/authoring";
export function NodeTarget({
  value,
  onChange,
  nodes,
  exclude,
  optional = false,
  label = "后续节点",
}: {
  value: string;
  onChange: (v: string) => void;
  nodes: StoryNode[];
  exclude?: string;
  optional?: boolean;
  label?: string;
}) {
  return (
    <SelectField
      aria-label={label}
      value={value}
      onValueChange={(selectedValue) => onChange(selectedValue)}
    >
      <option value="" disabled={!optional}>
        {optional ? "无" : "选择节点"}
      </option>
      {nodes
        .filter((n) => n.id !== exclude)
        .map((n, i) => (
          <option key={n.id} value={n.id}>
            {i + 1}. {nodeTitle(n)}
          </option>
        ))}
    </SelectField>
  );
}
export function RoutesEditor({
  routes,
  onChange,
  flow,
  catalog,
  exclude,
}: {
  routes: Route[];
  onChange: (v: Route[]) => void;
  flow: Flow;
  catalog: Catalog;
  exclude?: string;
}) {
  return (
    <div className="space-y-3">
      {routes.map((r, i) => (
        <section key={i} className="route-card">
          <div className="flex justify-between items-center mb-2">
            <strong className="text-xs">
              {i === routes.length - 1 ? "否则（兜底）" : `条件 ${i + 1}`}
            </strong>
            {i < routes.length - 1 && (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="移除分支"
                onClick={() => onChange(routes.filter((_, j) => i !== j))}
              >
                <Trash2 />
              </Button>
            )}
          </div>
          {i < routes.length - 1 && (
            <ConditionEditor
              value={r.when}
              onChange={(when) =>
                onChange(
                  routes.map((old, j) => (i === j ? { ...old, when } : old)),
                )
              }
              catalog={catalog}
              flow={flow}
            />
          )}
          <NodeTarget
            nodes={flow.content.nodes}
            exclude={exclude}
            value={r.node}
            onChange={(node) =>
              onChange(
                routes.map((old, j) => (i === j ? { ...old, node } : old)),
              )
            }
          />
        </section>
      ))}
      <Button
        variant="outline"
        size="sm"
        disabled={routes.length >= 30}
        onClick={() =>
          onChange([
            ...routes.slice(0, -1),
            {
              when: {
                chapter: {
                  id: catalog.chapters[0]?.id ?? "",
                  status: "complete",
                },
              },
              node: "",
            },
            routes[routes.length - 1] ?? { when: { always: true }, node: "" },
          ])
        }
      >
        <Plus />
        添加条件分支
      </Button>
    </div>
  );
}
export function NodeEditor({
  node,
  flow,
  catalog,
  onChange,
  onFlowChange,
  onDelete,
  onConvertActionText,
}: {
  node: StoryNode;
  flow: Flow;
  catalog: Catalog;
  onChange: (n: StoryNode) => void;
  onFlowChange: (f: Flow) => void;
  onDelete: () => void;
  onConvertActionText: () => void;
}) {
  const goals = catalog.quests.find((q) => q.id === flow.quest_id)?.nodes ?? [];
  const rounds = (node.type === "dialogue" ? node.text : "").split("\n\n");
  return (
    <div className="node-properties">
      <div className="flex justify-between items-center gap-3 mb-4">
        <h3 className="line-clamp-2 font-semibold" title={nodeTitle(node, catalog, flow)}>{nodeTitle(node, catalog, flow)}</h3>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="删除节点"
          onClick={onDelete}
        >
          <Trash2 />
        </Button>
      </div>
      {node.type === "dialogue" ? (
        <>
          <div className="field">
            <Label htmlFor="node-speaker">说话人</Label>
            <GroupedPicker label="选择说话 NPC" placeholder="按位置选择 NPC" groups={npcGroups(catalog)}
              onChange={(id) => {
                const npc = catalog.npcs.find((item) => item.id === id);
                if (npc) { const next = { ...node, speaker_npc_id: npc.id }; delete next.speaker; onChange(next); }
              }} />
            <Input
              id="node-speaker"
              value={node.speaker_npc_id ? catalog.npcs.find((npc) => npc.id === node.speaker_npc_id)?.name ?? "" : node.speaker ?? ""}
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
            <p className="hint">每个输入框是一轮对白。玩家逐轮继续，最后一轮显示选项并完成交谈目标。</p>
            {rounds.map((text, index) => (
              <div key={index} className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <Label htmlFor={`node-text-${index}`}>对白 {index + 1}</Label>
                  <Button variant="ghost" size="icon-sm" disabled={rounds.length === 1}
                    aria-label={`删除对白 ${index + 1}`}
                    onClick={() => onChange({ ...node, text: rounds.filter((_, i) => i !== index).join("\n\n") })}>
                    <Trash2 aria-hidden="true" />
                  </Button>
                </div>
                <Textarea id={`node-text-${index}`} rows={3} value={text}
                  maxLength={Math.max(0, 8000 - (node.text?.length ?? 0) + text.length)}
                  onChange={(e) => onChange({ ...node, text: rounds.map((round, i) =>
                    i === index ? e.target.value.replace(/\r\n/g, "\n").replace(/\n{2,}/g, "\n") : round,
                  ).join("\n\n") })} />
              </div>
            ))}
            <Button variant="outline" size="sm" disabled={(node.text?.length ?? 0) + 2 >= 8000}
              onClick={() => onChange({ ...node, text: [...rounds, ""].join("\n\n") })}>
              <Plus aria-hidden="true" />添加对白
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
      ) : node.type === "branch" ? (
        <RoutesEditor
          routes={node.routes}
          onChange={(routes) => onChange({ ...node, routes })}
          flow={flow}
          catalog={catalog}
          exclude={node.id}
        />
      ) : node.type === "activity" ? (
        <>
          <div className="field">
            <Label htmlFor="node-activity">学习活动</Label>
            <SelectField
              id="node-activity"
              value={flow.content.bindings[node.binding]?.activity_id ?? ""}
              onValueChange={(selectedValue) =>
                onFlowChange({
                  ...flow,
                  content: {
                    ...flow.content,
                    bindings: {
                      ...flow.content.bindings,
                      [node.binding]: {
                        ...flow.content.bindings[node.binding],
                        activity_id: selectedValue,
                      },
                    },
                  },
                })
              }
            >
              <option value="">选择活动</option>
              {catalog.activities.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.title}
                </option>
              ))}
            </SelectField>
          </div>
          {flow.quest_id && (
            <div className="field">
              <Label htmlFor="node-goal">任务目标</Label>
              <SelectField
                id="node-goal"
                value={flow.content.bindings[node.binding]?.goal_id ?? ""}
                onValueChange={(selectedValue) =>
                  onFlowChange({
                    ...flow,
                    content: {
                      ...flow.content,
                      bindings: {
                        ...flow.content.bindings,
                        [node.binding]: {
                          ...flow.content.bindings[node.binding],
                          goal_id: selectedValue,
                        },
                      },
                    },
                  })
                }
              >
                <option value="">选择目标</option>
                {goals
                  .filter((g) =>
                    [
                      "STUDY_LESSON",
                      "COMPLETE_PRACTICE",
                      "PASS_ASSESSMENT",
                    ].includes(g.type),
                  )
                  .map((g) => (
                    <option key={g.id} value={g.id}>
                      {g.title ?? g.type}
                    </option>
                  ))}
              </SelectField>
            </div>
          )}
          <p className="hint mb-4">
            只有服务端验证真实完成后才走“完成”分支。关闭活动走“暂停”分支。
          </p>
          {(["completed", "suspended", "unavailable"] as const).map((key) => (
            <div className="field" key={key}>
              <Label>
                {
                  {
                    completed: "真实完成后",
                    suspended: "暂停后",
                    unavailable: "活动不可用时",
                  }[key]
                }
              </Label>
              <NodeTarget
                label={key}
                value={node.returns[key]}
                nodes={flow.content.nodes}
                exclude={node.id}
                onChange={(next) =>
                  onChange({
                    ...node,
                    returns: { ...node.returns, [key]: next },
                  })
                }
              />
            </div>
          ))}
        </>
      ) : node.type === "action" ? (
        <>
          <div className="field">
            <Label htmlFor="node-action">操作类型</Label>
            <SelectField
              id="node-action"
              value={node.action}
              onValueChange={(selectedValue) =>
                onChange(
                  selectedValue === "enter_building"
                    ? {
                        id: node.id,
                        type: "action",
                        action: "enter_building",
                        building_id:
                          catalog.buildings.find((b) => b.has_interior)?.id ??
                          "",
                        text: node.text,
                        next: node.next,
                      }
                    : {
                        id: node.id,
                        type: "action",
                        action: "open_workspace",
                        workspace: "library",
                        text: node.text,
                        next: node.next,
                      },
                )
              }
            >
              <option value="open_workspace">打开工作区</option>
              <option value="enter_building">进入建筑</option>
            </SelectField>
          </div>
          {node.action === "enter_building" ? (
            <div className="field">
              <Label htmlFor="node-enter-building">目标建筑</Label>
              <SelectField
                id="node-enter-building"
                value={node.building_id}
                onValueChange={(selectedValue) =>
                  onChange({ ...node, building_id: selectedValue })
                }
              >
                {catalog.buildings
                  .filter((b) => b.has_interior)
                  .map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.label}
                    </option>
                  ))}
              </SelectField>
              <p className="hint">
                使用系统维护的入口与落点，并检查建筑开放权限。
              </p>
            </div>
          ) : (
            <div className="field">
              <Label htmlFor="node-workspace">打开页面</Label>
              <SelectField
                id="node-workspace"
                value={node.workspace}
                onValueChange={(selectedValue) =>
                  onChange({
                    ...node,
                    workspace: selectedValue as
                      "library" | "journal" | "classroom",
                  })
                }
              >
                {Object.entries(workspaceNames).map(([key, name]) => (
                  <option key={key} value={key}>{name}</option>
                ))}
              </SelectField>
            </div>
          )}
          {node.text && (
            <div className="route-card space-y-2 mb-4">
              <p className="hint">此节点保留了旧版提示：</p>
              <p className="text-sm whitespace-pre-wrap">{node.text}</p>
              <Button variant="outline" size="sm" disabled={flow.content.nodes.length >= 200} onClick={onConvertActionText}>
                转为前置对白
              </Button>
            </div>
          )}
          <details>
            <summary className="cursor-pointer text-sm font-medium">后续连接</summary>
            <div className="mt-2">
              <NodeTarget
                value={node.next}
                nodes={flow.content.nodes}
                exclude={node.id}
                onChange={(next) => onChange({ ...node, next })}
              />
            </div>
          </details>
        </>
      ) : node.type === "unlock_building" ? (
        <>
          <div className="field">
            <Label htmlFor="node-building">解锁建筑</Label>
            <SelectField
              id="node-building"
              value={node.building_id}
              onValueChange={(selectedValue) =>
                onChange({ ...node, building_id: selectedValue })
              }
            >
              {catalog.buildings
                .filter((b) => b.has_interior)
                .map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.label}
                  </option>
                ))}
            </SelectField>
          </div>
          <p className="hint mb-3">
            解锁必须满足真实完成证据，不能无条件授予进度。
          </p>
          <ConditionEditor
            value={node.when}
            catalog={catalog}
            flow={flow}
            onChange={(when) => onChange({ ...node, when })}
          />
          <NodeTarget
            value={node.next}
            nodes={flow.content.nodes}
            exclude={node.id}
            onChange={(next) => onChange({ ...node, next })}
          />
        </>
      ) : node.type === "random" ? (
        <div className="space-y-3">
          {node.next.map((target, i) => (
            <div className="flex gap-1" key={i}>
              <NodeTarget
                value={target}
                nodes={flow.content.nodes}
                exclude={node.id}
                onChange={(next) =>
                  onChange({
                    ...node,
                    next: node.next.map((old, j) => (i === j ? next : old)),
                  })
                }
              />
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="删除随机分支"
                onClick={() =>
                  onChange({
                    ...node,
                    next: node.next.filter((_, j) => i !== j),
                  })
                }
              >
                <Trash2 />
              </Button>
            </div>
          ))}
          <Button
            variant="outline"
            size="sm"
            disabled={node.next.length >= 20}
            onClick={() => onChange({ ...node, next: [...node.next, ""] })}
          >
            添加随机分支
          </Button>
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">
          到达此节点后结束本次交互。玩家下次触发入口时会重新判断条件。
        </p>
      )}
    </div>
  );
}
