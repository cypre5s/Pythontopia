// 代码草稿本地自动保存：按「登录用户 + 题目 id」存到 localStorage，刷新/切页/断网不丢草稿。
// 仅本地，不入库；正式提交仍走服务端判题。SSR/隐私模式读写失败一律静默降级。
//
// 键必须带用户标识（`pyt_code_draft_<problemId>@u:<username>`）：公用机房同一浏览器换账号时，
// 只按题号存会让 B 打开题目直接看到 A 的代码草稿。登出时由 session.logout 清掉该用户命名空间。

import { userScopedKey } from '@stores/session';

const PREFIX = 'pyt_code_draft_';

function keyOf(problemId: number): string {
  return userScopedKey(`${PREFIX}${problemId}`);
}

export function loadCodeDraft(problemId: number): string | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  try {
    return localStorage.getItem(keyOf(problemId));
  } catch {
    return null;
  }
}

export function saveCodeDraft(problemId: number, code: string): void {
  if (typeof localStorage === 'undefined') {
    return;
  }
  try {
    localStorage.setItem(keyOf(problemId), code);
  } catch {
    // 配额超限 / 隐私模式：放弃保存，不打扰用户。
  }
}

export function clearCodeDraft(problemId: number): void {
  if (typeof localStorage === 'undefined') {
    return;
  }
  try {
    localStorage.removeItem(keyOf(problemId));
  } catch {
    // 忽略
  }
}
