import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Trash2 } from "lucide-react";
import { Case } from "./types";
export function Cases({
  title,
  rows,
  setRows,
}: {
  title: string;
  rows: Case[];
  setRows: (rows: Case[]) => void;
}) {
  return (
    <fieldset className="field">
      <legend>{title}</legend>
      {rows.map((row, i) => (
        <div className="case-row" key={i}>
          <div>
            <Label htmlFor={`${title}-in-${i}`}>输入 {i + 1}</Label>
            <Textarea
              id={`${title}-in-${i}`}
              value={row.input}
              onChange={(e) =>
                setRows(
                  rows.map((r, j) =>
                    j === i ? { ...r, input: e.target.value } : r,
                  ),
                )
              }
            />
          </div>
          <div>
            <Label htmlFor={`${title}-out-${i}`}>预期输出</Label>
            <Textarea
              id={`${title}-out-${i}`}
              value={row.expected_output}
              onChange={(e) =>
                setRows(
                  rows.map((r, j) =>
                    j === i ? { ...r, expected_output: e.target.value } : r,
                  ),
                )
              }
            />
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={`删除${title}${i + 1}`}
            onClick={() => setRows(rows.filter((_, j) => i !== j))}
          >
            <Trash2 />
          </Button>
        </div>
      ))}
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={rows.length >= 20}
        onClick={() => setRows([...rows, { input: "", expected_output: "" }])}
      >
        添加用例
      </Button>
    </fieldset>
  );
}
