import { SelectField } from "@/components/select-field";
import { useState, type FormEvent } from "react";
import {
  Plus,
  Upload,
  RefreshCw,
  Search,
  KeyRound,
  UserMinus,
  Ellipsis,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableHeader,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  PageHeader,
  ErrorNotice,
  Loading,
  Empty,
  Pager,
} from "@/components/common";
import { api, errorMessage } from "@/lib/api";
import { useResource } from "@/lib/use-resource";
import { useDraft } from "@/lib/drafts";
import type { Student, StudentPreview } from "@/lib/types";
export function Students({ classroomId }: { classroomId: number }) {
  const base = `/teacher/classrooms/${classroomId}/students`;
  const list = useResource<Student[]>(base);
  const [query, setQuery] = useState(""),
    [status, setStatus] = useState("all"),
    [sort, setSort] = useState("student_id"),
    [page, setPage] = useState(1),
    [size, setSize] = useState(25),
    [selected, setSelected] = useState<string[]>([]),
    [targets, setTargets] = useState<string[]>([]),
    [modal, setModal] = useState<
      "create" | "import" | "reset-password" | "remove" | null
    >(null),
    [id, setId] = useState(""),
    [name, setName] = useState(""),
    [preview, setPreview] = useState<StudentPreview[]>([]),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false);
  useDraft(Boolean(id || name || preview.length));
  const rows = (list.data ?? [])
    .filter(
      (s) =>
        (s.name.toLowerCase().includes(query.trim().toLowerCase()) || s.student_id.includes(query.trim())) &&
        (status === "all" || (status === "pending") === s.must_change_password),
    )
    .sort((a, b) =>
      (sort === "name" ? a.name : a.student_id).localeCompare(
        sort === "name" ? b.name : b.student_id,
        "zh-CN",
        { numeric: true },
      ),
    );
  const safePage = Math.min(page, Math.max(1, Math.ceil(rows.length / size)));
  const visible = rows.slice((safePage - 1) * size, safePage * size);
  const allPage =
    visible.length > 0 && visible.every((s) => selected.includes(s.student_id));
  function filter(field: string, value: string) {
    setPage(1);
    setSelected([]);
    if (field === "query") setQuery(value);
    else if (field === "status") setStatus(value);
    else setSort(value);
  }
  function open(value: typeof modal) {
    setError("");
    setModal(value);
  }
  function openAction(action: "reset-password" | "remove", ids: string[]) {
    setTargets(ids);
    open(action);
  }
  async function create(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const students =
        modal === "import"
          ? preview.map(({ student_id, name }) => ({ student_id, name }))
          : [{ student_id: id, name }];
      const result = await api.post<{ created: number; existing: number }>(
        base,
        { students },
      );
      setMessage(
        `已创建 ${result.created} 名学生，跳过 ${result.existing} 名已有学生。`,
      );
      setModal(null);
      setId("");
      setName("");
      setPreview([]);
      list.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function importFile(file: File | undefined) {
    if (!file) return;
    setBusy(true);
    setError("");
    setPreview([]);
    try {
      const form = new FormData();
      form.append("file", file);
      const result = await api.upload<{ students: StudentPreview[] }>(
        `${base}/import-preview`,
        form,
      );
      setPreview(result.students);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function batch() {
    if ((modal !== "reset-password" && modal !== "remove") || !targets.length) return;
    setBusy(true);
    setError("");
    try {
      const result = await api.post<{ affected: number }>(`${base}/batch`, {
        student_ids: targets,
        action: modal,
      });
      setMessage(`已处理 ${result.affected} 名学生。`);
      setSelected((ids) => ids.filter((id) => !targets.includes(id)));
      setModal(null);
      list.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader
        title="学生账号"
        description="管理当前班级的学生，保留他们的学习记录。"
      >
        <Button variant="outline" onClick={() => open("import")}>
          <Upload />
          导入学生
        </Button>
        <Button onClick={() => open("create")}>
          <Plus />
          添加学生
        </Button>
      </PageHeader>
      <div className="surface">
        <div className="toolbar">
          <div className="search">
            <Search size={16} />
            <Input
              aria-label="搜索学生"
              placeholder="搜索学号或姓名"
              value={query}
              onChange={(e) => filter("query", e.target.value)}
            />
          </div>
          <SelectField
            aria-label="密码状态"
            value={status}
            onValueChange={(value) => filter("status", value)}
          >
            <option value="all">全部状态</option>
            <option value="pending">待修改初始密码</option>
            <option value="ready">已设置密码</option>
          </SelectField>
          <SelectField
            aria-label="学生排序"
            value={sort}
            onValueChange={(value) => filter("sort", value)}
          >
            <option value="student_id">按学号排序</option>
            <option value="name">按姓名排序</option>
          </SelectField>
          <Button
            variant="ghost"
            size="icon"
            aria-label="刷新学生"
            onClick={list.reload}
            disabled={list.loading}
          >
            <RefreshCw />
          </Button>
        </div>
        {selected.length > 0 ? (
          <div className="selection-bar">
            <span>已选择 {selected.length} 名学生</span>
            {rows.length <= 500 ? (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setSelected(rows.map((s) => s.student_id))}
              >
                选择全部筛选结果（{rows.length}）
              </Button>
            ) : null}
            <Button variant="ghost" size="sm" onClick={() => setSelected([])}>
              取消选择
            </Button>
            <div className="ml-auto flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => openAction("reset-password", selected)}
              >
                <KeyRound />
                重置密码
              </Button>
              <Button
                variant="destructive"
                size="sm"
                onClick={() => openAction("remove", selected)}
              >
                <UserMinus />
                移出班级
              </Button>
            </div>
          </div>
        ) : null}
        <ErrorNotice message={list.error || (!modal ? error : "")} />
        {message ? (
          <p role="status" className="notice-success">
            {message}
          </p>
        ) : null}
        {list.loading ? (
          <Loading />
        ) : rows.length === 0 ? (
          <Empty
            title="没有匹配的学生"
            description="添加学生或调整搜索条件。"
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-12">
                  <Checkbox
                    aria-label="选择本页学生"
                    checked={allPage}
                    indeterminate={!allPage && visible.some((s) => selected.includes(s.student_id))}
                    onCheckedChange={() =>
                      setSelected(
                        allPage
                          ? selected.filter(
                              (id) => !visible.some((s) => s.student_id === id),
                            )
                          : [
                              ...new Set([
                                ...selected,
                                ...visible.map((s) => s.student_id),
                              ]),
                            ].slice(0, 500),
                      )
                    }
                  />
                </TableHead>
                <TableHead>学号</TableHead>
                <TableHead>姓名</TableHead>
                <TableHead>密码状态</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.map((s) => (
                <TableRow key={s.id} data-state={selected.includes(s.student_id) ? "selected" : undefined}>
                  <TableCell>
                    <Checkbox
                      aria-label={`选择${s.student_id}`}
                      checked={selected.includes(s.student_id)}
                      disabled={
                        !selected.includes(s.student_id) &&
                        selected.length >= 500
                      }
                      onCheckedChange={() =>
                        setSelected((v) =>
                          v.includes(s.student_id)
                            ? v.filter((id) => id !== s.student_id)
                            : [...v, s.student_id],
                        )
                      }
                    />
                  </TableCell>
                  <TableCell className="font-mono">{s.student_id}</TableCell>
                  <TableCell>{s.name}</TableCell>
                  <TableCell>
                    <Badge
                      variant={s.must_change_password ? "secondary" : "outline"}
                    >
                      {s.must_change_password ? "待修改初始密码" : "已设置"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <DropdownMenu>
                      <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" aria-label={`${s.name}（${s.student_id}）的操作`} />}>
                        <Ellipsis />
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => openAction("reset-password", [s.student_id])}>
                          <KeyRound />重置密码
                        </DropdownMenuItem>
                        <DropdownMenuItem variant="destructive" onClick={() => openAction("remove", [s.student_id])}>
                          <UserMinus />移出班级
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <Pager
          page={safePage}
          size={size}
          total={rows.length}
          onPage={setPage}
          onSize={(v) => {
            setSize(v);
            setPage(1);
          }}
        />
      </div>
      <Dialog
        open={modal !== null}
        onOpenChange={(value) => {
          if (!value && !busy) setModal(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {modal === "create"
                ? "添加学生"
                : modal === "import"
                  ? "导入学生"
                  : modal === "remove"
                    ? "移出学生"
                    : "重置学生密码"}
            </DialogTitle>
            <DialogDescription>
              {modal === "create" || modal === "import"
                ? "初始密码为学号，首次登录后学生必须修改。"
                : modal === "remove"
                  ? `将${targets.length === 1 ? `学号 ${targets[0]} 的学生` : ` ${targets.length} 名学生`}移出当前班级，账号及学习记录保留。`
                  : `将${targets.length === 1 ? `学号 ${targets[0]} 的学生` : ` ${targets.length} 名学生`}的密码重置为其学号，现有登录会话将失效。`}
            </DialogDescription>
          </DialogHeader>
          <ErrorNotice message={error} />
          {modal === "create" || modal === "import" ? (
            <form inert={busy} aria-busy={busy} onSubmit={create}>
              {modal === "create" ? (
                <>
                  <div className="field">
                    <Label htmlFor="student-id">学号</Label>
                    <Input
                      id="student-id"
                      inputMode="numeric"
                      pattern="[0-9]{1,32}"
                      value={id}
                      onChange={(e) => setId(e.target.value)}
                      required
                    />
                  </div>
                  <div className="field">
                    <Label htmlFor="student-name">姓名</Label>
                    <Input
                      id="student-name"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      maxLength={80}
                      required
                    />
                  </div>
                </>
              ) : (
                <>
                  <div className="field">
                    <Label htmlFor="roster-file">CSV 或 XLSX 文件</Label>
                    <Input
                      id="roster-file"
                      type="file"
                      accept=".csv,.xlsx"
                      disabled={busy}
                      onChange={(e) => void importFile(e.target.files?.[0])}
                    />
                    <p className="hint">
                      首行依次为「学号」「姓名」，学号使用文本格式；最多 500
                      人，2 MB。
                    </p>
                  </div>
                  {preview.length ? (
                    <div className="preview-list">
                      <p>
                        将新建{" "}
                        {preview.filter((s) => s.action === "create").length}{" "}
                        人，跳过{" "}
                        {preview.filter((s) => s.action === "existing").length}{" "}
                        人。
                      </p>
                      {preview.map((s) => (
                        <p key={s.student_id}>
                          {s.student_id} · {s.name} ·{" "}
                          {s.action === "create" ? "新建" : "已存在"}
                        </p>
                      ))}
                    </div>
                  ) : null}
                </>
              )}
              <DialogFooter>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setModal(null)}
                  disabled={busy}
                >
                  取消
                </Button>
                <Button
                  type="submit"
                  disabled={busy || (modal === "import" && !preview.length)}
                >
                  {busy
                    ? "处理中…"
                    : modal === "import"
                      ? "确认导入"
                      : "创建学生"}
                </Button>
              </DialogFooter>
            </form>
          ) : (
            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => setModal(null)}
                disabled={busy}
              >
                取消
              </Button>
              <Button
                variant={modal === "remove" ? "destructive" : "default"}
                onClick={() => void batch()}
                disabled={busy}
              >
                确认操作
              </Button>
            </DialogFooter>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
