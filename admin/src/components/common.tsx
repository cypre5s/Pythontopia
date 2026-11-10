import { SelectField } from "@/components/select-field";
import { AlertCircle, LoaderCircle, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
export function ErrorNotice({ message }: { message: string }) {
  return message ? (
    <div role="alert" className="notice-error">
      <AlertCircle size={17} />
      <span>{message}</span>
    </div>
  ) : null;
}
export function Loading() {
  return (
    <div role="status" className="empty">
      <LoaderCircle className="animate-spin" size={20} />
      正在加载…
    </div>
  );
}
export function Empty({
  title,
  description,
}: {
  title: string;
  description?: string;
}) {
  return (
    <div className="empty">
      <p className="font-medium text-foreground">{title}</p>
      {description ? <p>{description}</p> : null}
    </div>
  );
}
export function PageHeader({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <h1>{title}</h1>
        {description ? <p>{description}</p> : null}
      </div>
      <div className="flex flex-wrap gap-2">{children}</div>
    </div>
  );
}
export function TableSearch({ label, placeholder, value, onValueChange }: {
  label: string;
  placeholder?: string;
  value: string;
  onValueChange: (value: string) => void;
}) {
  return (
    <div className="search">
      <Search size={16} aria-hidden="true" />
      <Input
        aria-label={label}
        placeholder={placeholder ?? label}
        value={value}
        onChange={(e) => onValueChange(e.target.value)}
      />
    </div>
  );
}
export function Pager({
  page,
  size,
  total,
  onPage,
  onSize,
}: {
  page: number;
  size: number;
  total: number;
  onPage: (page: number) => void;
  onSize: (size: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <div className="pager">
      <span>共 {total} 条</span>
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2">
          每页{" "}
          <SelectField
            aria-label="每页条数"
            value={size}
            onValueChange={(value) => onSize(Number(value))}
          >
            {[10, 25, 50, 100].map((v) => (
              <option key={v}>{v}</option>
            ))}
          </SelectField>{" "}
          条
        </div>
        <Button
          variant="outline"
          size="sm"
          disabled={page <= 1}
          onClick={() => onPage(page - 1)}
        >
          上一页
        </Button>
        <span>
          {page} / {pages}
        </span>
        <Button
          variant="outline"
          size="sm"
          disabled={page >= pages}
          onClick={() => onPage(page + 1)}
        >
          下一页
        </Button>
      </div>
    </div>
  );
}
