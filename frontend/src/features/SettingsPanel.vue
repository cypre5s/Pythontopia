<template>
  <section class="town-workspace settings-workspace">
    <div class="town-book">
      <aside class="town-sidebar">
        <nav class="town-directory settings-directory" role="tablist" aria-label="设置分类" aria-orientation="vertical">
          <button v-for="category in categories" :id="`settings-tab-${category.id}`" :key="category.id"
            class="town-directory-item" :class="{ 'is-current': selected === category.id }"
            role="tab" :aria-selected="selected === category.id" :aria-controls="`settings-panel-${category.id}`"
            :tabindex="selected === category.id ? 0 : -1" @click="selected = category.id" @keydown="moveTab($event, category.id)">
            <GameIcon :name="category.icon" /><span>{{ category.label }}</span><GameIcon name="chevron" />
          </button>
        </nav>
      </aside>
      <main :id="`settings-panel-${selected}`" class="town-content settings-content" role="tabpanel" :aria-labelledby="`settings-tab-${selected}`">
        <header class="settings-category-heading"><h3>{{ currentCategory.label }}</h3></header>
        <template v-if="selected === 'account'">
          <section class="town-section account-card" aria-label="当前账户">
            <div class="account-avatar" aria-hidden="true">{{ userInitial }}</div>
            <div class="account-details"><strong>{{ session.user?.name ?? '未登录' }}</strong><span>{{ session.user?.student_id }}</span></div>
            <span v-if="session.user" class="account-role">{{ roleName }}</span>
          </section>
          <section class="town-section"><PasswordForm /></section>
          <section class="town-section settings-group">
            <div v-if="isTeacher" class="setting-row"><div class="setting-copy"><strong>教师工作台</strong><p class="setting-description">管理班级与教学内容。</p></div><RouterLink to="/teacher" class="town-secondary">进入工作台 <GameIcon name="arrowRight" /></RouterLink></div>
            <div class="setting-row"><div class="setting-copy"><strong>退出登录</strong><p class="setting-description">学习进度会保留，下次登录后可继续。</p></div><button class="settings-logout" :disabled="loggingOut || !session.user" @click="logout"><GameIcon name="logout" />{{ loggingOut ? '正在退出…' : '退出账号' }}</button></div>
          </section>
        </template>
        <section v-else-if="selected === 'interface'" class="town-section settings-group">
          <div class="setting-row"><label for="settings-text-scale" class="setting-copy"><strong>文字大小</strong><span class="setting-description">调整学习内容和菜单的文字大小。</span></label><select id="settings-text-scale" v-model="preferences.textScale"><option v-for="scale in textScales" :key="scale" :value="scale">{{ scale * 100 }}%{{ scale === 1 ? ' · 标准' : '' }}</option></select></div>
          <div class="setting-row"><label for="settings-reduce-motion" class="setting-copy"><strong>减少界面动效</strong><span id="settings-motion-description" class="setting-description">减少过渡动画，对话直接显示全文。</span><span v-if="systemMotionOnly" class="setting-status">设备已启用减少动态效果</span></label><span class="game-switch"><input id="settings-reduce-motion" v-model="preferences.reduceMotion" type="checkbox" role="switch" aria-describedby="settings-motion-description" /><span class="game-switch-track" aria-hidden="true"></span></span></div>
        </section>
        <section v-else-if="selected === 'music'" class="town-section settings-group">
          <div class="setting-row"><label for="settings-music-enabled" class="setting-copy"><strong>播放小镇环境音乐</strong><span class="setting-description">在小镇中播放背景音乐。</span></label><span class="game-switch"><input id="settings-music-enabled" v-model="session.audioEnabled" type="checkbox" role="switch" /><span class="game-switch-track" aria-hidden="true"></span></span></div>
          <div class="setting-row setting-volume-row"><label for="settings-music-volume" class="setting-copy"><strong>音乐音量</strong><span class="setting-description">{{ session.audioEnabled ? '调整环境音乐的播放音量。' : '开启环境音乐后可调整。' }}</span></label><div class="volume-control"><input id="settings-music-volume" v-model.number="preferences.musicVolume" type="range" min="0" max="1" step="0.01" :disabled="!session.audioEnabled" :aria-valuetext="`${volumePercent}%`" /><output for="settings-music-volume">{{ volumePercent }}%</output></div></div>
        </section>
        <section v-else-if="selected === 'controls'" class="town-section settings-controls" aria-label="操作指南">
          <dl><div><dt>移动</dt><dd><kbd>W</kbd><kbd>A</kbd><kbd>S</kbd><kbd>D</kbd><span>或方向键，也可点击道路。</span></dd></div><div><dt>互动</dt><dd><kbd>E</kbd><span>也可点击附近人物，或使用触屏“互动”。</span></dd></div><div><dt>任务日志</dt><dd><kbd>J</kbd><span>查看和追踪当前任务。</span></dd></div><div><dt>返回</dt><dd><kbd>Esc</kbd><span>返回上一级；在小镇中打开设置。</span></dd></div></dl>
          <p class="setting-description">打开学习内容或对话时，角色会暂停移动。</p>
        </section>
        <section v-else class="town-section settings-about"><div class="about-product"><GameIcon name="building" /><strong>Pythontopia</strong><span>Python 学习小镇</span></div><LicenseContent /></section>
      </main>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";
import GameIcon from "../shared/GameIcon.vue";
import LicenseContent from "./LicenseContent.vue";
import PasswordForm from "./PasswordForm.vue";
import { useSession } from "../shared/session";
import { api } from "../shared/api";
import { useUiPreferences, textScales } from "../shared/uiPreferences";
const categories = [
  { id: "account", label: "账户", icon: "people" },
  { id: "interface", label: "界面", icon: "settings" },
  { id: "music", label: "音乐", icon: "music" },
  { id: "controls", label: "操作", icon: "compass" },
  { id: "about", label: "关于", icon: "book" },
] as const;
type Category = typeof categories[number]["id"];
const selected = ref<Category>("account"), loggingOut = ref(false);
const preferences = useUiPreferences(), session = useSession(), router = useRouter();
const currentCategory = computed(() => categories.find(category => category.id === selected.value)!);
const userInitial = computed(() => Array.from(session.user?.name ?? "?")[0]);
const roleName = computed(() => ({ student: "学生", teacher: "教师", admin: "管理员" })[session.user?.role as "student" | "teacher" | "admin"] ?? "用户");
const isTeacher = computed(() => session.user?.role === "teacher" || session.user?.role === "admin");
const volumePercent = computed(() => Math.round(preferences.musicVolume * 100));
const systemMotionOnly = computed(() => preferences.reducedMotion && !preferences.reduceMotion);
async function moveTab(event: KeyboardEvent, id: Category) {
  const index = categories.findIndex(category => category.id === id);
  const offset = event.key === "ArrowDown" || event.key === "ArrowRight" ? 1 : event.key === "ArrowUp" || event.key === "ArrowLeft" ? -1 : 0;
  if (!offset && event.key !== "Home" && event.key !== "End") return;
  event.preventDefault();
  const category = categories[event.key === "Home" ? 0 : event.key === "End" ? categories.length - 1 : (index + offset + categories.length) % categories.length];
  if (!category) return;
  selected.value = category.id;
  await nextTick();
  document.getElementById(`settings-tab-${selected.value}`)?.focus();
}
async function logout() {
  if (loggingOut.value) return;
  loggingOut.value = true;
  try { await api.post("/auth/logout", {}); }
  catch { /* Local sign-out still clears access when the server is unavailable. */ }
  finally { session.reset(); await router.push("/"); loggingOut.value = false; }
}
</script>
<style scoped>
.settings-workspace { overflow:hidden; }
.settings-directory .town-directory-item > svg:last-child { width:16px; height:16px; margin-left:auto; opacity:.65; }
.settings-content { color:#4d4939; }
.settings-category-heading { margin:0 0 1.65rem; padding-bottom:1.2rem; border-bottom:1px solid #d4c2a3; }
.settings-category-heading h3 { font-family:Georgia,'Noto Serif SC',serif; color:#314d40; font-size:1.45rem; margin:0; }
.settings-group { padding:0 1.65rem; }
.setting-row { display:flex; align-items:center; justify-content:space-between; gap:2rem; min-height:112px; padding:1.6rem 0; }
.setting-row + .setting-row { border-top:1px solid #ded1b9; }
.setting-copy { display:flex; flex-direction:column; gap:.5rem; min-width:0; color:#445140; cursor:default; }
.setting-copy > strong { font-size:1.05rem; font-weight:600; }
.setting-description { display:block; color:#7c715d; line-height:1.75; font-size:.95rem; font-weight:400; margin:0; }
.setting-status { color:#34614e; font-size:.875rem; }
.setting-row select { min-width:168px; }
.volume-control { display:flex; align-items:center; gap:1rem; min-width:280px; }
.volume-control input { width:210px; }
.volume-control input:disabled { opacity:.45; cursor:not-allowed; }.volume-control output { width:3.5rem; text-align:right; color:#485841; font-variant-numeric:tabular-nums; }
.account-card { display:flex; align-items:center; gap:1.35rem; padding:2rem; margin-bottom:1.4rem; background:linear-gradient(110deg,#e4e9d8,#f9f3e5); }
.account-avatar { width:68px; height:68px; display:grid; place-items:center; border-radius:18px; color:#fff4d6; background:#375c4b; font-size:1.75rem; flex-shrink:0; }
.account-details { display:flex; flex-direction:column; gap:.55rem; min-width:0; }.account-details > strong { font-size:1.35rem; color:#304e3e; }.account-details > span { color:#7b705d; overflow-wrap:anywhere; font-size:.95rem; }
.account-role { margin-left:auto; padding:.4rem .85rem; color:#526348; border:1px solid #bcc8ac; border-radius:20px; font-size:.875rem; white-space:nowrap; }
.settings-logout { min-height:46px; display:inline-flex; align-items:center; gap:.6rem; color:#9d5443; background:#fff6ec; border:1px solid #cfae98; border-radius:8px; padding:.65rem 1.1rem; font:inherit; white-space:nowrap; }
.settings-logout svg,.setting-row a svg { width:18px; height:18px; }.settings-logout:hover { background:#f8e7da; }.settings-logout:disabled { opacity:.5; cursor:not-allowed; }
.setting-row a { display:inline-flex; align-items:center; gap:.6rem; white-space:nowrap; }
.settings-controls { padding:1.3rem 2rem 1.6rem; }.settings-controls dl { margin:0; }.settings-controls dl > div { display:grid; grid-template-columns:125px minmax(0,1fr); gap:1.5rem; padding:1.5rem 0; border-bottom:1px solid #ddd0b8; }.settings-controls dt { color:#455440; font-weight:600; }.settings-controls dd { display:flex; flex-wrap:wrap; align-items:center; gap:.45rem; margin:0; color:#776950; line-height:1.9; }.settings-controls dd > span { margin-left:.35rem; }.settings-controls kbd { min-width:32px; padding:.1rem .45rem; text-align:center; color:#53604a; background:#fcf8ee; border:1px solid #cbbda3; border-bottom-width:3px; border-radius:5px; font-family:inherit; }.settings-controls > p { margin-top:1.4rem; }
.settings-about { padding:1.7rem 2rem; }.about-product { display:flex; align-items:center; flex-wrap:wrap; gap:.8rem; padding-bottom:1.5rem; margin-bottom:1.5rem; border-bottom:1px solid #d4c4a8; color:#34533f; }.about-product svg { width:32px; height:32px; }.about-product strong { font-size:1.3rem; }.about-product span { margin-left:auto; color:#82745c; font-size:.95rem; }
@media(max-width:900px) { .setting-row { gap:1rem; }.volume-control { min-width:220px; }.volume-control input { width:150px; }.account-card { padding:1.5rem; }.settings-group { padding:0 1.2rem; } }
@media(max-width:700px) { .settings-category-heading { margin-bottom:1.2rem; }.setting-row { flex-wrap:wrap; min-height:0; padding:1.3rem 0; }.setting-copy { flex:1 1 190px; }.setting-volume-row .setting-copy { flex-basis:100%; }.volume-control { width:100%; min-width:0; }.volume-control input { flex:1; width:auto; }.account-card { flex-wrap:wrap; gap:1rem; }.account-avatar { width:52px; height:52px; border-radius:13px; }.account-details { flex:1; }.account-details strong { font-size:1.1rem; }.account-role { margin-left:calc(52px + 1rem); }.settings-controls { padding:1rem 1.2rem; }.settings-controls dl > div { grid-template-columns:1fr; gap:.65rem; }.settings-about { padding:1.2rem; }.about-product span { margin-left:0; width:100%; } }
</style>
