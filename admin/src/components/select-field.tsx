import { Children, isValidElement, type ComponentProps, type ReactNode } from "react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

type Props = Pick<ComponentProps<"button">, "id" | "aria-label" | "aria-labelledby" | "disabled" | "className"> & {
  value: string | number;
  onValueChange: (value: string) => void;
  children: ReactNode;
  placeholder?: string;
  required?: boolean;
};

export function SelectField({ value, onValueChange, children, placeholder, required, disabled, ...trigger }: Props) {
  const options = Children.toArray(children).flatMap((child) => {
    if (!isValidElement<ComponentProps<"option">>(child) || child.type !== "option") return [];
    return [{
      value: String(child.props.value ?? child.props.children),
      label: child.props.children,
      disabled: child.props.disabled,
    }];
  });
  const selected = options.find((option) => option.value === String(value));
  return (
    <Select
      items={options}
      value={selected?.value ?? null}
      onValueChange={(next) => { if (next !== null) onValueChange(next); }}
      required={required}
      disabled={disabled}
    >
      <SelectTrigger {...trigger}>
        <SelectValue placeholder={placeholder ?? "请选择"} />
      </SelectTrigger>
      <SelectContent align="start" alignItemWithTrigger={false}>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
