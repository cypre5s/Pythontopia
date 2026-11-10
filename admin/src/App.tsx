import { SelectField } from "@/components/select-field";
import { useState, useEffect, lazy, Suspense } from "react";
import {
  GraduationCap,
  Users,
  BookOpen,
  LibraryBig,
  ListChecks,
  Layers3,
  ChevronDown,
  Plus,
  LogOut,
  KeyRound,
  PanelLeftClose,
  PanelLeftOpen,
  Package,
  ArrowUpRight,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { ErrorNotice, Loading, Empty } from "@/components/common";
import { PasswordDialog } from "@/components/password-dialog";
import { api, errorMessage, setToken, token } from "@/lib/api";
import { DraftProvider, useCanLeave } from "@/lib/drafts";
import { openTownPreview } from "@/lib/town-preview";
import type { User, Classroom } from "@/lib/types";
import { Login } from "@/pages/login";
import { Students } from "@/pages/students";
import { ClassOverview } from "@/pages/class-overview";
import { Curriculum } from "@/pages/curriculum";
import { Materials } from "@/pages/materials";
import { Questions } from "@/pages/questions";
import { Activities } from "@/pages/activities";
import { Packages } from "@/pages/packages";
import { Teaching } from "@/pages/teaching";
const Authoring = lazy(() =>
  import("@/pages/authoring").then((module) => ({ default: module.Authoring })),
);
const nav = [
  { key: "classroom", title: "班级概况", icon: GraduationCap },
  { key: "students", title: "学生账号", icon: Users },
  { key: "teaching", title: "教学管理", icon: GraduationCap },
  { key: "curriculum", title: "课程结构", icon: Layers3 },
  { key: "materials", title: "资料库", icon: LibraryBig },
  { key: "questions", title: "题库", icon: ListChecks },
  { key: "activities", title: "学习活动", icon: BookOpen },
  { key: "authoring", title: "教学流程", icon: Layers3 },
  { key: "packages", title: "课程包", icon: Package },
] as const;
type Page = (typeof nav)[number]["key"];
function currentPage(): Page {
  const part = location.pathname.split("/")[2];
  return nav.some((n) => n.key === part) ? (part as Page) : "classroom";
}
export default function App() {
  return (
    <DraftProvider>
      <Workbench />
    </DraftProvider>
  );
}
function Workbench() {
  const [user, setUser] = useState<User | null>(null),
    [classes, setClasses] = useState<Classroom[]>([]),
    [classroomId, setClassroomId] = useState<number | null>(null),
    [loading, setLoading] = useState(Boolean(token())),
    [error, setError] = useState(""),
    [page, setPage] = useState<Page>(currentPage),
    [passwordOpen, setPasswordOpen] = useState(false),
    [newClass, setNewClass] = useState(false),
    [className, setClassName] = useState(""),
    [courseStart, setCourseStart] = useState("import"),
    [busy, setBusy] = useState(false),
    [openingTown, setOpeningTown] = useState(false),
    [sidebar, setSidebar] = useState(true);
  const canLeave = useCanLeave();
  useEffect(() => {
    let active = true;
    const captured = token();
    if (captured)
      api
        .get<User>("/auth/me")
        .then((u) => {
          if (active && captured === token()) {
            if (u.role === "student") {
              setToken(null);
              setError("请使用教师账号登录");
            } else setUser(u);
          }
        })
        .catch((e) => {
          if (active) setError(errorMessage(e));
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    const expire = () => {
      setUser(null);
      setClasses([]);
      setClassroomId(null);
      setError("登录已失效，请重新登录。");
    };
    window.addEventListener("session-expired", expire);
    return () => window.removeEventListener("session-expired", expire);
  }, []);
  useEffect(() => {
    if (!user || user.must_change_password) return;
    const controller = new AbortController();
    const captured = token();
    api
      .get<Classroom[]>("/classrooms", controller.signal)
      .then((rows) => {
        if (controller.signal.aborted || captured !== token()) return;
        setClasses(rows);
        const saved = Number(sessionStorage.getItem(`classroom:${user.id}`));
        setClassroomId(
          rows.some((c) => c.id === saved) ? saved : (rows[0]?.id ?? null),
        );
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(errorMessage(e));
      });
    return () => controller.abort();
  }, [user?.id, user?.must_change_password]);
  useEffect(() => {
    const pop = () => {
      if (canLeave()) setPage(currentPage());
      else history.pushState({}, "", `/teacher/${page}`);
    };
    window.addEventListener("popstate", pop);
    return () => window.removeEventListener("popstate", pop);
  }, [canLeave, page]);
  function navigate(next: Page) {
    if (!canLeave()) return;
    history.pushState({}, "", `/teacher/${next}`);
    setPage(next);
  }
  function changeClass(id: number) {
    if (id === classroomId || !canLeave()) return;
    setClassroomId(id);
    if (user) sessionStorage.setItem(`classroom:${user.id}`, String(id));
    setError("");
  }
  async function createClass() {
    if (!canLeave()) return;
    const capturedToken = token();
    setBusy(true);
    setError("");
    try {
      const row = await api.post<Classroom>("/classrooms", { name: className });
      if (capturedToken !== token()) return;
      setClasses((v) => [...v, row]);
      setClassName("");
      setNewClass(false);
      if (user) sessionStorage.setItem(`classroom:${user.id}`, String(row.id));
      if (courseStart === "empty") {
        try {
          await api.post(`/teacher/classrooms/${row.id}/course/empty`, {
            confirmed: true,
            name: `${row.name}的课程`,
          });
        } catch (e) {
          if (capturedToken === token()) setClassroomId(row.id);
          throw e;
        }
      }
      if (capturedToken !== token()) return;
      setClassroomId(row.id);
      const next = courseStart === "import" ? "packages" : "classroom";
      history.pushState({}, "", `/teacher/${next}`);
      setPage(next);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function logout() {
    if (!canLeave()) return;
    setBusy(true);
    try {
      await api.post("/auth/logout", {});
      setToken(null);
      setUser(null);
      setClasses([]);
      setClassroomId(null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function enterTown(id: number) {
    const capturedToken = token();
    setOpeningTown(true);
    setError("");
    try {
      await openTownPreview(id);
    } catch (e) {
      if (capturedToken === token()) setError(errorMessage(e));
    } finally {
      setOpeningTown(false);
    }
  }
  if (loading) return <Loading />;
  if (!user)
    return (
      <>
        <ErrorNotice message={error} />
        <Login
          onLogin={(u) => {
            setError("");
            setUser(u);
          }}
        />
      </>
    );
  const classroom = classes.find((c) => c.id === classroomId);
  return (
    <div className={`workbench ${sidebar ? "" : "sidebar-collapsed"}`}>
      <aside className="sidebar">
        <a
          href="/teacher/"
          className="brand"
          onClick={(e) => {
            e.preventDefault();
            navigate("classroom");
          }}
        >
          <span className="brand-mark">
            <GraduationCap size={22} />
          </span>
          <span>
            Pythontopia<small>教学工作台</small>
          </span>
        </a>
        <div className="class-picker">
          <Label htmlFor="active-class">当前班级</Label>
          <SelectField
            id="active-class"
            disabled={busy || classes.length === 0}
            value={classroomId ?? ""}
            onValueChange={(value) => changeClass(Number(value))}
          >
            <option value="" disabled>
              选择班级
            </option>
            {classes.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </SelectField>
          <Button variant="ghost" size="sm" onClick={() => setNewClass(true)}>
            <Plus size={15} />
            创建班级
          </Button>
        </div>
        <nav aria-label="教师导航">
          {nav.map(({ key, title, icon: Icon }, i) => (
            <div key={key}>
              {i === 2 ? <p className="nav-label">课程创作</p> : null}
              <a
                href={`/teacher/${key}`}
                aria-current={page === key ? "page" : undefined}
                onClick={(e) => {
                  e.preventDefault();
                  navigate(key);
                }}
              >
                <Icon size={18} />
                {title}
              </a>
            </div>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <DropdownMenu>
            <DropdownMenuTrigger render={<button className="account" />}>
              <span className="avatar">{user.name.slice(0, 1)}</span>
              <span>
                {user.name}
                <small>{user.role === "admin" ? "管理员" : "教师"}</small>
              </span>
              <ChevronDown size={15} />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start">
              <DropdownMenuItem onClick={() => setPasswordOpen(true)}>
                <KeyRound />
                修改密码
              </DropdownMenuItem>
              <DropdownMenuItem disabled={busy} onClick={() => void logout()}>
                <LogOut />
                退出账号
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <Button
            variant="ghost"
            size="icon"
            aria-label={sidebar ? "收起导航" : "展开导航"}
            onClick={() => setSidebar((v) => !v)}
          >
            {sidebar ? <PanelLeftClose /> : <PanelLeftOpen />}
          </Button>
          <span className="text-muted-foreground">
            {classroom?.name ?? "教学工作台"}
          </span>
          <span className="text-border">/</span>
          <span>{nav.find((n) => n.key === page)?.title}</span>
          <Button
            className="ml-auto shrink-0"
            variant="outline"
            size="sm"
            disabled={!classroom || openingTown || user.must_change_password}
            onClick={() => classroom && void enterTown(classroom.id)}
          >
            {openingTown ? "正在进入小镇…" : "进入小镇"}
            <ArrowUpRight />
          </Button>
        </header>
        <main className="main-content">
          <ErrorNotice message={error} />
          {classroom ? (
            <div key={`${user.id}:${classroom.id}:${page}`}>
              {page === "classroom" ? (
                <ClassOverview
                  classroom={classroom}
                  onContent={() => navigate("curriculum")}
                  onImport={() => navigate("packages")}
                />
              ) : page === "students" ? (
                <Students classroomId={classroom.id} />
              ) : page === "teaching" ? (
                <Teaching classroomId={classroom.id} />
              ) : page === "curriculum" ? (
                <Curriculum classroomId={classroom.id} />
              ) : page === "materials" ? (
                <Materials classroomId={classroom.id} />
              ) : page === "questions" ? (
                <Questions classroomId={classroom.id} />
              ) : page === "activities" ? (
                <Activities classroomId={classroom.id} />
              ) : page === "authoring" ? (
                <Suspense fallback={<Loading />}>
                  <Authoring classroomId={classroom.id} />
                </Suspense>
              ) : (
                <Packages classroomId={classroom.id} />
              )}
            </div>
          ) : (
            <div className="surface">
              <Empty
                title="创建你的第一个班级"
                description="班级拥有独立课程，可以分别维护资料和教学流程。"
              />
              <div className="flex justify-center pb-8">
                <Button onClick={() => setNewClass(true)}>
                  <Plus />
                  创建班级
                </Button>
              </div>
            </div>
          )}
        </main>
      </div>
      <PasswordDialog
        open={passwordOpen || user.must_change_password}
        onClose={() => setPasswordOpen(false)}
        onUser={setUser}
        required={user.must_change_password}
      />
      <Dialog
        open={newClass}
        onOpenChange={(v) => {
          if (!busy) setNewClass(v);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建班级</DialogTitle>
            <DialogDescription>
              为班级导入课程包，或从空课程开始编辑。
            </DialogDescription>
          </DialogHeader>
          <form
            inert={busy}
            aria-busy={busy}
            onSubmit={(e) => {
              e.preventDefault();
              void createClass();
            }}
          >
            <ErrorNotice message={error} />
            <div className="field">
              <Label htmlFor="class-name">班级名称</Label>
              <Input
                id="class-name"
                value={className}
                onChange={(e) => setClassName(e.target.value)}
                required
                maxLength={100}
              />
            </div>
            <div className="field">
              <Label htmlFor="class-course-start">课程来源</Label>
              <SelectField
                id="class-course-start"
                value={courseStart}
                onValueChange={(value) => setCourseStart(value)}
              >
                <option value="import">创建后导入课程包</option>
                <option value="empty">创建空课程</option>
                <option value="later">稍后配置课程</option>
              </SelectField>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setNewClass(false)}
              >
                取消
              </Button>
              <Button type="submit" disabled={busy}>
                创建班级
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
