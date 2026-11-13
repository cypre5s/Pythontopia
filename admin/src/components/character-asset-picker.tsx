import { CharacterPreview } from "./character-preview";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import type { CharacterAsset } from "@/lib/authoring";

export function CharacterAssetPicker({ id, assets, value, onChange }: {
  id: string; assets: CharacterAsset[]; value: string; onChange: (value: string) => void;
}) {
  return <Select value={value} items={assets.map(asset => ({ value: asset.id, label: asset.name }))}
    onValueChange={next => { if (next) onChange(next); }}>
    <SelectTrigger id={id} className="w-full"><SelectValue placeholder="选择素材" /></SelectTrigger>
    <SelectContent align="start" alignItemWithTrigger={false} className="max-h-80">
      {assets.map(asset => <SelectItem key={asset.id} value={asset.id}>
        <CharacterPreview asset={asset} compact />
        <span className="flex flex-col justify-center"><span>{asset.name}</span>
          <span className="text-xs text-muted-foreground">{asset.id.startsWith("builtin:") ? "系统素材" : "此角色的自定义素材"}
            {asset.kind === "sprite" ? ` · ${asset.frame_width ?? 16}×${asset.frame_height ?? 20}` : ""}</span>
        </span>
      </SelectItem>)}
    </SelectContent>
  </Select>;
}
