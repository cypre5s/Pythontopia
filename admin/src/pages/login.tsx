import { useState, type FormEvent } from "react";
import { GraduationCap, ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ErrorNotice } from "@/components/common";
import { api, errorMessage, setToken } from "@/lib/api";
import type { AuthResult, User } from "@/lib/types";
export function Login({ onLogin }: { onLogin: (user: User) => void }) {
  const [id, setId] = useState(""),
    [password, setPassword] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const result = await api.post<AuthResult>("/auth/login", {
        student_id: id,
        password,
      });
      if (result.user.role === "student") {
        setError("此入口供教师和管理员使用。学生请从小镇入口登录。");
        return;
      }
      setToken(result.access_token);
      setPassword("");
      onLogin(result.user);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="login">
      <div className="login-intro">
        <div className="brand">
          <GraduationCap size={29} />
          <span>Pythontopia</span>
        </div>
        <h1>
          把课程，
          <br />
          变成探索的旅程。
        </h1>
        <p>
          组织课程内容，连接教学流程，
          <br />
          在同一个工作台里照看每个班级。
        </p>
        <small>教师教学工作台</small>
      </div>
      <div className="login-form">
        <form onSubmit={submit}>
          <p className="eyebrow">TEACHING STUDIO</p>
          <h2>登录工作台</h2>
          <p className="text-muted-foreground mb-8">
            使用你的教师或管理员账号。
          </p>
          <ErrorNotice message={error} />
          <div className="field">
            <Label htmlFor="login-id">学号</Label>
            <Input
              id="login-id"
              autoComplete="username"
              inputMode="numeric"
              pattern="[0-9]{1,32}"
              required
              value={id}
              onChange={(e) => setId(e.target.value)}
            />
          </div>
          <div className="field">
            <Label htmlFor="login-password">密码</Label>
            <Input
              id="login-password"
              autoComplete="current-password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <Button
            type="submit"
            className="w-full mt-3"
            size="lg"
            disabled={busy}
          >
            {busy ? "正在登录…" : "进入工作台"}
            <ArrowRight size={16} />
          </Button>
          <a
            className="text-sm text-muted-foreground mt-6 block text-center underline-offset-4 hover:underline"
            href="/"
          >
            返回学生入口
          </a>
        </form>
      </div>
    </main>
  );
}
