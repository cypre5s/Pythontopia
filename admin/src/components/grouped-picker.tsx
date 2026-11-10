import { Check, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

export type PickerChoice = { value: string; label: string };
export type PickerGroup = { id: string; label: string; choices: PickerChoice[] };

export function GroupedPicker({ id, label, value, groups, standalone = [], placeholder = "请选择", className, disabled, onChange }: {
  id?: string;
  label: string;
  value?: string;
  groups: PickerGroup[];
  standalone?: PickerChoice[];
  placeholder?: string;
  className?: string;
  disabled?: boolean;
  onChange: (value: string) => void;
}) {
  const selected = [...standalone, ...groups.flatMap((group) => group.choices)].find((item) => item.value === value);
  const renderChoice = ({ value: choice, label: name }: PickerChoice) => (
    <DropdownMenuItem key={choice} aria-current={choice === value || undefined} onClick={() => onChange(choice)} className="justify-between gap-3 px-2 py-1.5">
      <span className="min-w-0 break-words">{name}</span>
      {choice === value && <Check aria-hidden="true" className="size-4 shrink-0" />}
    </DropdownMenuItem>
  );
  return (
    <DropdownMenu>
      <DropdownMenuTrigger render={
        <Button id={id} type="button" aria-label={label} variant="outline" disabled={disabled || (!groups.length && !standalone.length)}
          className={cn("min-w-0 max-w-full justify-between font-normal", className)} />
      }>
        <span className="truncate" title={selected?.label}>{selected?.label ?? placeholder}</span>
        <ChevronDown aria-hidden="true" className="text-muted-foreground" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-40 max-w-[min(24rem,calc(100vw-2rem))]">
        {standalone.map(renderChoice)}
        {groups.map((group) => (
          <DropdownMenuSub key={group.id}>
            <DropdownMenuSubTrigger className="gap-3 px-2 py-1.5">
              <span className="min-w-0 break-words">{group.label}</span>
            </DropdownMenuSubTrigger>
            <DropdownMenuSubContent className="min-w-40 max-w-[min(24rem,calc(100vw-2rem))]">
              {group.choices.map(renderChoice)}
            </DropdownMenuSubContent>
          </DropdownMenuSub>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
