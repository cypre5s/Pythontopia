import { api, token, TOKEN_KEY } from "@/lib/api";

export async function openTownPreview(classroomId: number) {
  const teacherToken = token();
  const tab = window.open("about:blank", "_blank");
  if (!tab) throw new Error("浏览器拦截了小镇标签页，请允许打开新标签页后重试");

  try {
    tab.opener = null;
    tab.sessionStorage.removeItem(TOKEN_KEY);
    tab.document.title = "教师体验 · Pythontopia";
    tab.document.body.textContent = "正在进入小镇体验存档…";
    const result = await api.post<{ access_token: string }>(`/teacher/classrooms/${classroomId}/town-session`, {});
    if (teacherToken !== token()) throw new Error("教师账号已切换，请重新进入小镇");
    if (tab.closed) return;
    if (tab.location.href !== "about:blank") throw new Error("小镇标签页已跳转，请重新进入小镇");
    tab.sessionStorage.setItem(TOKEN_KEY, result.access_token);
    tab.location.replace(new URL("/world", window.location.origin).href);
  } catch (error) {
    tab.close();
    throw error;
  }
}
