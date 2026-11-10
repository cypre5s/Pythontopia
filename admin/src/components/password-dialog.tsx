import { useState, type FormEvent } from "react";
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
import { ErrorNotice } from "@/components/common";
import { api, errorMessage, setToken } from "@/lib/api";
import type { AuthResult, User } from "@/lib/types";
export function PasswordDialog({
  open,
  onClose,
  onUser,
  required = false,
}: {
  open: boolean;
  onClose: () => void;
  onUser: (user: User) => void;
  required?: boolean;
}) {
  const [old, setOld] = useState(""),
    [next, setNext] = useState(""),
    [confirm, setConfirm] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function save(e: FormEvent) {
    e.preventDefault();
    if (next !== confirm) {
      setError("两次新密码不一致");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const result = await api.post<AuthResult>("/auth/password", {
        current_password: old,
        new_password: next,
      });
      setToken(result.access_token);
      onUser(result.user);
      setOld("");
      setNext("");
      setConfirm("");
      onClose();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        if (!value && !busy && !required) onClose();
      }}
    >
      <DialogContent showCloseButton={!required}>
        <DialogHeader>
          <DialogTitle>修改密码</DialogTitle>
          <DialogDescription>
            修改后其他登录会话将失效，当前工作台保持登录。
          </DialogDescription>
        </DialogHeader>
        <form inert={busy} aria-busy={busy} onSubmit={save}>
          <ErrorNotice message={error} />
          <div className="field">
            <Label htmlFor="old-password">当前密码</Label>
            <Input
              id="old-password"
              type="password"
              autoComplete="current-password"
              value={old}
              onChange={(e) => setOld(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <Label htmlFor="new-password">新密码</Label>
            <Input
              id="new-password"
              type="password"
              autoComplete="new-password"
              minLength={6}
              value={next}
              onChange={(e) => setNext(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <Label htmlFor="confirm-password">确认新密码</Label>
            <Input
              id="confirm-password"
              type="password"
              autoComplete="new-password"
              minLength={6}
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              required
            />
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              disabled={busy || required}
            >
              取消
            </Button>
            <Button type="submit" disabled={busy}>
              保存密码
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
