import { useState, type MouseEvent } from "react";
import { Bot, Plus, Settings2, Trash2 } from "lucide-react";
import { AuthoringInspector, AuthoringWorkspace } from "@/components/authoring-workspace";
import { Empty } from "@/components/common";
import { SelectField } from "@/components/select-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import { newId } from "@/lib/id";
import type { Authoring, Point } from "@/lib/authoring";

const limits = [
  ["model_calls", "模型调用次数", 1, 3],
  ["tool_calls", "资料工具调用次数", 0, 5],
  ["output_tokens", "回答长度（token）", 1, 512],
  ["request_tokens", "请求预算（token）", 1, 12000],
  ["timeout_seconds", "等待上限（秒）", 1, 45],
] as const;

interface Role {
  name: string;
  instructions: string;
  limits?: Partial<Record<(typeof limits)[number][0], number>>;
}
interface Policy {
  schema_version?: 1;
  enabled?: boolean;
  roles?: Record<string, Role>;
  default_role_id?: string;
  library_role_id?: string;
  activity_roles?: Record<string, string>;
  npc_roles?: Record<string, string>;
}
type Inspector = { kind: "role"; id: string; anchor: HTMLElement | Point } | { kind: "scope"; anchor: HTMLElement };

export function AssistantEditor({
  data,
  onChange,
  focusTarget,
}: {
  data: Authoring;
  onChange: (value: Authoring) => void;
  focusTarget?: { roleId?: string };
}) {
  const [manualInspector, setManualInspector] = useState<Inspector | null>(null);
  const [initialFocus, setInitialFocus] = useState(Boolean(focusTarget));
  const [focusAnchor, setFocusAnchor] = useState<HTMLButtonElement | null>(null);
  const policy = data.configuration.assistant as Policy;
  const roles = policy.roles ?? {};
  const focusRoleId = focusTarget?.roleId && Object.hasOwn(roles, focusTarget.roleId) ? focusTarget.roleId : undefined;
  const inspector: Inspector | null = initialFocus && focusAnchor
    ? focusRoleId ? { kind: "role", id: focusRoleId, anchor: focusAnchor } : { kind: "scope", anchor: focusAnchor }
    : manualInspector;
  const roleItems = Object.entries(roles);
  const selected = inspector?.kind === "role" ? inspector.id : "";
  const role = roles[selected];
  const roleOptions = roleItems.map(([id, item]) => <option key={id} value={id}>{item.name}</option>);
  const activities = data.catalog.activities.filter((item) => item.kind !== "assessment");
  const scopes = [
    ["activity_roles", "各活动助教", activities.map((item) => ({ id: item.id, name: item.title }))],
    ["npc_roles", "各教学角色助教", data.catalog.npcs],
  ] as const;

  function setInspector(next: Inspector | null) {
    setInitialFocus(false);
    setManualInspector(next);
  }

  function update(next: Policy) {
    onChange({
      ...data,
      configuration: {
        ...data.configuration,
        assistant: { ...next, schema_version: 1 },
      },
    });
  }
  function updateRole(next: Role) {
    update({ ...policy, roles: { ...roles, [selected]: next } });
  }
  function selectRole(field: "default_role_id" | "library_role_id", id: string) {
    const next = { ...policy };
    if (id) next[field] = id;
    else delete next[field];
    update(next);
  }
  function inspectRole(id: string, event: MouseEvent<HTMLButtonElement>) {
    const anchor = event.type === "contextmenu"
      ? { x: event.clientX, y: event.clientY }
      : event.currentTarget;
    event.preventDefault();
    setInspector({ kind: "role", id, anchor });
  }
  function addRole(anchor: HTMLElement) {
    const id = newId("role");
    update({
      ...policy,
      roles: {
        ...roles,
        [id]: {
          name: "新的教学助教",
          instructions: "以启发式问题引导学生理解知识点，并依据当前授权资料回答。",
        },
      },
    });
    setInspector({ kind: "role", id, anchor });
  }
  function removeRole() {
    if (!role || !window.confirm("删除角色并清除它的活动与角色绑定？")) return;
    const nextRoles = { ...roles };
    delete nextRoles[selected];
    const withoutRole = (bindings: Record<string, string> | undefined) =>
      Object.fromEntries(Object.entries(bindings ?? {}).filter(([, id]) => id !== selected));
    const next: Policy = {
      ...policy,
      roles: nextRoles,
      activity_roles: withoutRole(policy.activity_roles),
      npc_roles: withoutRole(policy.npc_roles),
    };
    if (next.default_role_id === selected) delete next.default_role_id;
    if (next.library_role_id === selected) delete next.library_role_id;
    update(next);
    setInspector(null);
  }

  return (
    <AuthoringWorkspace
      tools={
        <>
          <p className="px-2 pb-1 text-xs font-medium text-muted-foreground">助教工具</p>
          <Button className="justify-start" variant="ghost" disabled={roleItems.length >= 100}
            onClick={(event) => addRole(event.currentTarget)}>
            <Plus />新建助教角色
          </Button>
          <Button className="justify-start" variant={inspector?.kind === "scope" ? "secondary" : "ghost"}
            ref={initialFocus && !focusRoleId ? setFocusAnchor : undefined}
            onClick={(event) => setInspector({ kind: "scope", anchor: event.currentTarget })}>
            <Settings2 />使用范围
          </Button>
          {roleItems.length > 0 && (
            <>
              <Separator className="my-2" />
              <p className="px-2 pb-1 text-xs font-medium text-muted-foreground">助教角色</p>
              {roleItems.map(([id, item]) => (
                <Button key={id} className="min-w-0 justify-start" variant={selected === id ? "secondary" : "ghost"}
                  ref={initialFocus && focusRoleId === id ? setFocusAnchor : undefined}
                  title={item.name} aria-label={`编辑助教角色 ${item.name}`}
                  onClick={(event) => inspectRole(id, event)}
                  onContextMenu={(event) => inspectRole(id, event)}>
                  <Bot /><span className="truncate">{item.name}</span>
                </Button>
              ))}
            </>
          )}
        </>
      }
      toolbar={
        <>
          <Label className="px-2 gap-2">
            <Checkbox checked={policy.enabled ?? true} onCheckedChange={(enabled) => update({ ...policy, enabled })} />
            开放本班助教提问
          </Label>
          <Separator orientation="vertical" className="h-5" />
          <Label htmlFor="assistant-default_role_id">课程默认角色</Label>
          <SelectField id="assistant-default_role_id" className="max-w-52" value={policy.default_role_id ?? ""}
            onValueChange={(value) => selectRole("default_role_id", value)}>
            <option value="">系统默认教学说明</option>
            {roleOptions}
          </SelectField>
        </>
      }
    >
      <div className="space-y-3 p-4">
        <p className="hint">助教仅在学生主动提问时使用。教学角色提供引导方式，服务未配置时仍会明确显示不可用。</p>
        {roleItems.length ? (
          <div className="grid content-start gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {roleItems.map(([id, item]) => (
              <Button key={id} variant="outline"
                className="h-auto min-h-28 min-w-0 flex-col items-start justify-start gap-2 whitespace-normal p-4 text-left"
                onClick={(event) => inspectRole(id, event)} onContextMenu={(event) => inspectRole(id, event)}>
                <span className="flex w-full items-center gap-2">
                  <Bot className="text-muted-foreground" />
                  <span className="truncate">{item.name}</span>
                </span>
                <span className="line-clamp-2 text-xs font-normal text-muted-foreground">{item.instructions}</span>
                <span className="flex flex-wrap gap-1">
                  {policy.default_role_id === id && <Badge variant="secondary">课程默认</Badge>}
                  {policy.library_role_id === id && <Badge variant="outline">图书馆</Badge>}
                </span>
              </Button>
            ))}
          </div>
        ) : (
          <Empty title="尚未创建助教角色" description="从左侧新建角色，为不同教学场景设置引导方式。" />
        )}
      </div>
      {inspector && (
        <AuthoringInspector
          anchor={inspector.anchor}
          title={role ? "助教角色属性" : "使用范围"}
          onClose={() => setInspector(null)}
        >
          {role ? (
            <>
              <div className="field">
                <Label htmlFor="assistant-name">角色名称</Label>
                <Input id="assistant-name" value={role.name} maxLength={80}
                  onChange={(event) => updateRole({ ...role, name: event.target.value })} />
              </div>
              <div className="field">
                <Label htmlFor="assistant-instructions">教学说明</Label>
                <Textarea id="assistant-instructions" rows={6} value={role.instructions} maxLength={2000}
                  onChange={(event) => updateRole({ ...role, instructions: event.target.value })} />
              </div>
              <details>
                <summary className="cursor-pointer font-medium">单次提问资源上限</summary>
                <p className="hint mt-2">用于控制响应长度和等待时间，不能提高服务器固定上限。</p>
                <div className="mt-3 grid grid-cols-2 gap-3">
                  {limits.map(([key, label, min, max]) => (
                    <div className="field" key={key}>
                      <Label htmlFor={`limit-${key}`}>{label}</Label>
                      <Input id={`limit-${key}`} type="number" min={min} max={max} value={role.limits?.[key] ?? max}
                        onChange={(event) => updateRole({
                          ...role,
                          limits: { ...role.limits, [key]: Number(event.target.value) },
                        })} />
                    </div>
                  ))}
                </div>
              </details>
              <Separator className="my-3" />
              <Button variant="ghost" onClick={removeRole}><Trash2 />删除角色</Button>
            </>
          ) : (
            <>
              <div className="field">
                <Label htmlFor="assistant-library_role_id">图书馆角色</Label>
                <SelectField id="assistant-library_role_id" value={policy.library_role_id ?? ""}
                  onValueChange={(value) => selectRole("library_role_id", value)}>
                  <option value="">继承课程默认</option>
                  {roleOptions}
                </SelectField>
              </div>
              {scopes.map(([field, title, items]) => (
                <details key={field}>
                  <summary className="cursor-pointer py-3 font-medium">{title}</summary>
                  {items.map((item) => (
                    <div className="field" key={item.id}>
                      <Label htmlFor={`${field}-${item.id}`}>{item.name}</Label>
                      <SelectField id={`${field}-${item.id}`} value={policy[field]?.[item.id] ?? ""}
                        onValueChange={(value) => {
                          const bindings = { ...policy[field] };
                          if (value) bindings[item.id] = value;
                          else delete bindings[item.id];
                          update({ ...policy, [field]: bindings });
                        }}>
                        <option value="">继承默认</option>
                        {roleOptions}
                      </SelectField>
                    </div>
                  ))}
                </details>
              ))}
            </>
          )}
        </AuthoringInspector>
      )}
    </AuthoringWorkspace>
  );
}
