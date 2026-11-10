import { SelectField } from "@/components/select-field";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { ErrorNotice } from "@/components/common";
import { api, scoped, errorMessage } from "@/lib/api";
import { newId } from "@/lib/id";
import { useDraft } from "@/lib/drafts";
import type { Catalog } from "@/lib/authoring";
export function NpcDialog({
  classroomId,
  catalog,
  onClose,
  onSaved,
}: {
  classroomId: number;
  catalog: Catalog;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(""),
    [map, setMap] = useState(catalog.maps[0]?.key ?? ""),
    [x, setX] = useState(catalog.maps[0]?.objects[0]?.points[0]?.tile_x ?? 1),
    [y, setY] = useState(catalog.maps[0]?.objects[0]?.points[0]?.tile_y ?? 1),
    [text, setText] = useState("你好，欢迎来到小镇。"),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  useDraft(Boolean(name));
  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api.post(scoped("/teacher/npcs", classroomId), {
        key: newId("npc"),
        name,
        map_key: map,
        tile_x: x,
        tile_y: y,
        portrait: "",
        dialogue: [{ id: "greeting", speaker: name, text }],
      });
      onSaved();
      onClose();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      open
      onOpenChange={(v) => {
        if (!v && !busy) onClose();
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新增教学角色</DialogTitle>
          <DialogDescription>
            放置在系统地图内，使用系统默认角色外观；教学对白由流程编辑器组织。
          </DialogDescription>
        </DialogHeader>
        <form inert={busy} aria-busy={busy} onSubmit={save}>
          <ErrorNotice message={error} />
          <div className="field">
            <Label htmlFor="npc-name">角色名称</Label>
            <Input
              id="npc-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={80}
            />
          </div>
          <div className="field">
            <Label htmlFor="npc-map">所在地图</Label>
            <SelectField
              id="npc-map"
              value={map}
              onValueChange={(selectedValue) => {
                setMap(selectedValue);
                const p = catalog.maps.find((m) => m.key === selectedValue)
                  ?.objects[0]?.points[0];
                if (p) {
                  setX(p.tile_x);
                  setY(p.tile_y);
                }
              }}
            >
              {catalog.maps.map((m) => (
                <option key={m.key} value={m.key}>
                  {m.label}
                </option>
              ))}
            </SelectField>
          </div>
          <div className="field">
            <Label htmlFor="npc-position">位置参照</Label>
            <SelectField
              id="npc-position"
              value=""
              onValueChange={(selectedValue) => {
                const p = catalog.maps
                  .find((m) => m.key === map)
                  ?.objects.find((o) => o.id === selectedValue)?.points[0];
                if (p) {
                  setX(p.tile_x);
                  setY(p.tile_y);
                }
              }}
            >
              <option value="">选择现有地图对象的位置</option>
              {catalog.maps
                .find((m) => m.key === map)
                ?.objects.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.label}
                  </option>
                ))}
            </SelectField>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="field">
              <Label htmlFor="npc-x">横坐标（格）</Label>
              <Input
                id="npc-x"
                type="number"
                min={0}
                max={(catalog.maps.find((m) => m.key === map)?.width ?? 1) - 1}
                value={x}
                onChange={(e) => setX(Number(e.target.value))}
                required
              />
            </div>
            <div className="field">
              <Label htmlFor="npc-y">纵坐标（格）</Label>
              <Input
                id="npc-y"
                type="number"
                min={0}
                max={(catalog.maps.find((m) => m.key === map)?.height ?? 1) - 1}
                value={y}
                onChange={(e) => setY(Number(e.target.value))}
                required
              />
            </div>
          </div>
          <div className="field">
            <Label htmlFor="npc-greeting">初始日常对白</Label>
            <Textarea
              id="npc-greeting"
              rows={3}
              value={text}
              onChange={(e) => setText(e.target.value)}
              maxLength={8000}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={busy} onClick={onClose}>
              取消
            </Button>
            <Button type="submit" disabled={busy}>
              {busy ? "创建中…" : "创建角色"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
