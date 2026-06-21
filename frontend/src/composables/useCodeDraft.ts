// 代码草稿本地自动保存：按题目 id 存到 localStorage，刷新/切页/断网不丢草稿。
// 仅本地，不入库；正式提交仍走服务端判题。SSR/隐私模式读写失败一律静默降级。

const PREFIX = 'pyt_code_draft_';

function keyOf(problemId: number): string {
  return `${PREFIX}${problemId}`;
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
