import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Highlight } from "./types";
export function HighlightEditor({
  title,
  rows,
  onChange,
  video = false,
}: {
  title: string;
  rows: Highlight[];
  onChange: (v: Highlight[]) => void;
  video?: boolean;
}) {
  return (
    <fieldset className="field">
      <legend>{title}</legend>
      <p className="hint">提示学生重点内容，不会自动视为完成。</p>
      {rows.map((r, i) => (
        <div className="border rounded p-3 space-y-2 mb-2" key={i}>
          <Input
            aria-label={`${title}说明 ${i + 1}`}
            placeholder="重点说明"
            value={r.label}
            maxLength={200}
            onChange={(e) =>
              onChange(
                rows.map((old, j) =>
                  i === j ? { ...old, label: e.target.value } : old,
                ),
              )
            }
          />
          <div className="grid grid-cols-2 gap-2">
            <Input
              aria-label={`${title}起点 ${i + 1}`}
              type="number"
              min={video ? 0 : 1}
              value={r.start}
              onChange={(e) =>
                onChange(
                  rows.map((old, j) =>
                    i === j ? { ...old, start: Number(e.target.value) } : old,
                  ),
                )
              }
            />
            <Input
              aria-label={`${title}终点 ${i + 1}`}
              type="number"
              min={r.start + (video ? 1 : 0)}
              value={r.end}
              onChange={(e) =>
                onChange(
                  rows.map((old, j) =>
                    i === j ? { ...old, end: Number(e.target.value) } : old,
                  ),
                )
              }
            />
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onChange(rows.filter((_, j) => i !== j))}
          >
            移除标记
          </Button>
        </div>
      ))}
      <Button
        variant="outline"
        size="sm"
        disabled={rows.length >= 100}
        onClick={() =>
          onChange([
            ...rows,
            { start: video ? 0 : 1, end: video ? 10 : 1, label: "" },
          ])
        }
      >
        添加标记
      </Button>
    </fieldset>
  );
}
