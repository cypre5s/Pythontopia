<template>
  <section class="town-workspace library-workspace">
    <div class="town-book">
      <aside class="town-sidebar library-sidebar">
        <p class="library-directory-label">章节目录</p>
        <nav class="town-directory" aria-label="资料章节">
          <button class="town-directory-item" :class="{ 'is-current': chapter === 'all' }" :aria-pressed="chapter === 'all'" @click="selectChapter('all')">
            <GameIcon name="grid"/><span>全部章节</span><small>{{ materials.length }}</small>
          </button>
          <button v-for="item in chapters" :key="item.id" class="town-directory-item" :class="{ 'is-current': chapter === item.id }" :aria-pressed="chapter === item.id" @click="selectChapter(item.id)">
            <span class="library-chapter-number" aria-hidden="true">{{ item.order === Number.MAX_SAFE_INTEGER ? '·' : String(item.order).padStart(2, '0') }}</span>
            <span>{{ item.title }}</span><small>{{ item.materials.length }}</small>
          </button>
        </nav>
      </aside>
      <main ref="content" class="town-content library-content" :aria-busy="loading">
        <div class="town-toolbar library-toolbar">
          <label class="town-field library-search"><span>搜索资料</span><input v-model="query" type="search" placeholder="搜索资料名称或章节" autocomplete="off" @input="resetScroll"/></label>
          <div class="library-type-filters game-segmented" role="group" aria-label="资料类型">
            <button v-for="item in types" :key="item.id" :class="{ 'is-current': type === item.id }" :aria-pressed="type === item.id" @click="selectType(item.id)">{{ item.label }}</button>
          </div>
          <span class="library-result-count" role="status" aria-live="polite">{{ filteredCount }} 份资料</span>
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-else-if="loading" class="town-empty" role="status">正在加载资料…</p>
        <template v-else>
          <section v-for="group in filteredChapters" :key="group.id" class="library-chapter" :aria-labelledby="`library-chapter-${group.id}`">
            <header class="library-chapter-heading"><h3 :id="`library-chapter-${group.id}`">{{ group.title }}</h3><span>{{ group.materials.length }} 份</span></header>
            <div class="library-list">
              <button v-for="material in group.materials" :key="material.id" class="library-card" :disabled="material.status !== 'ready'" @click="open(material)">
                <span class="library-material-icon" :class="{ 'is-video': material.kind === 'video' }" aria-hidden="true"><GameIcon :name="material.kind === 'video' ? 'video' : 'book'"/></span>
                <span class="library-card-copy"><strong>{{ material.title }}</strong><small>{{ kindLabels[material.kind] ?? '课程资料' }}<span v-if="duration(material)"> · {{ duration(material) }}</span></small></span>
                <span class="library-card-action">{{ material.status === 'ready' ? material.kind === 'video' ? '观看' : '阅读' : material.status === 'failed' ? '处理失败' : '处理中' }}<GameIcon v-if="material.status === 'ready'" name="arrowRight"/></span>
              </button>
            </div>
          </section>
          <div v-if="!filteredCount" class="town-empty library-empty"><GameIcon name="book"/><p>{{ materials.length ? '没有找到匹配的资料' : '当前班级还没有开放资料' }}</p><button v-if="materials.length" class="town-secondary" @click="clearFilters">清除筛选</button></div>
        </template>
      </main>
    </div>
  </section>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { api } from "../shared/api";
import { useSession } from "../shared/session";
import type { Material } from "../shared/types";

type MaterialType = "all" | "lesson" | "video";
type ChapterSelection = "all" | number;
interface SavedView { query?: string; type?: MaterialType; chapter?: ChapterSelection; scroll?: number; }
const session = useSession(), content = ref<HTMLElement>(), materials = ref<Material[]>([]), error = ref(""), loading = ref(true);
const query = ref(""), type = ref<MaterialType>("all"), chapter = ref<ChapterSelection>("all");
const types = [{ id: "all", label: "全部资料" }, { id: "lesson", label: "课件" }, { id: "video", label: "视频" }] as const;
const kindLabels: Record<string, string> = { text: "文本课件", pdf: "PDF 课件", presentation: "演示课件", video: "课程视频" };
let ownerKey = "", generation = 0, disposed = false, scrollPosition = 0;
const chapters = computed(() => {
  const groups = new Map<number, Material[]>();
  for (const material of materials.value) {
    const ids = material.chapter_ids?.length ? material.chapter_ids : [material.chapter_id ?? 0];
    for (const id of ids) {
      const list = groups.get(id) ?? [];
      list.push(material);
      groups.set(id, list);
    }
  }
  return [...groups].map(([id, items]) => {
    const details = session.world?.chapters.find(item => item.id === id);
    return { id, title: details?.title ?? "其他资料", order: details?.order_index ?? Number.MAX_SAFE_INTEGER, materials: items };
  }).sort((a, b) => a.order - b.order || a.id - b.id);
});
const normalize = (value: string) => value.trim().toLocaleLowerCase().replace(/\s+/g, "");
const filteredChapters = computed(() => chapters.value.filter(group => chapter.value === "all" || group.id === chapter.value).map(group => ({
  ...group,
  materials: group.materials.filter(material =>
    (type.value === "all" || (type.value === "video" ? material.kind === "video" : ["text", "pdf", "presentation"].includes(material.kind))) &&
    normalize(`${material.title} ${group.title} ${kindLabels[material.kind] ?? ""}`).includes(normalize(query.value))),
})).filter(group => group.materials.length));
const filteredCount = computed(() => new Set(filteredChapters.value.flatMap(group => group.materials.map(material => material.id))).size);
function duration(material: Material) {
  const seconds = material.duration_seconds;
  if (material.kind !== "video" || typeof seconds !== "number" || !Number.isFinite(seconds) || seconds <= 0) return "";
  return `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, "0")}`;
}
function saveView() {
  if (!ownerKey) return;
  try {
    const scroll = loading.value ? scrollPosition : content.value?.scrollTop ?? scrollPosition;
    scrollPosition = scroll;
    sessionStorage.setItem(`library-view:${ownerKey}`, JSON.stringify({ query: query.value, type: type.value, chapter: chapter.value, scroll }));
    sessionStorage.setItem(`library-scroll:${ownerKey}`, String(scroll));
  } catch { /* Browsing also works when optional device storage is unavailable. */ }
}
function resetScroll() { scrollPosition = 0; if (content.value) content.value.scrollTop = 0; }
function selectChapter(id: ChapterSelection) { chapter.value = id; resetScroll(); }
function selectType(id: MaterialType) { type.value = id; resetScroll(); }
function clearFilters() { query.value = ""; type.value = "all"; chapter.value = "all"; resetScroll(); }
function open(material: Material) { saveView(); session.navigate({ kind: "material", material_id: material.id }); }
watch([query, type, chapter], saveView, { flush: "post" });
watch(() => [session.user?.id, session.classroomId] as const, async ([userId, classroomId]) => {
  saveView();
  const current = ++generation;
  ownerKey = userId !== undefined && classroomId !== null ? `${userId}:${classroomId}` : "";
  materials.value = [];
  error.value = "";
  loading.value = true;
  let saved: SavedView = {};
  try {
    const parsed = JSON.parse(sessionStorage.getItem(`library-view:${ownerKey}`) ?? "{}");
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) saved = parsed;
  } catch { /* Ignore invalid optional preferences. */ }
  query.value = typeof saved.query === "string" ? saved.query : "";
  type.value = types.some(item => item.id === saved.type) ? saved.type! : "all";
  chapter.value = saved.chapter === "all" || typeof saved.chapter === "number" ? saved.chapter : "all";
  const restoredScroll = Number(saved.scroll ?? sessionStorage.getItem(`library-scroll:${ownerKey}`) ?? 0);
  scrollPosition = Number.isFinite(restoredScroll) ? Math.max(0, restoredScroll) : 0;
  if (!ownerKey) { loading.value = false; return; }
  try {
    const result = await api.get<Material[]>(`/library?classroom_id=${classroomId}`);
    if (disposed || current !== generation || session.user?.id !== userId || session.classroomId !== classroomId) return;
    materials.value = result;
    if (chapter.value !== "all" && !chapters.value.some(item => item.id === chapter.value)) chapter.value = "all";
  } catch (reason) {
    if (!disposed && current === generation) error.value = reason instanceof Error ? reason.message : "资料加载失败";
  } finally {
    if (!disposed && current === generation) {
      loading.value = false;
      await nextTick();
      if (!disposed && current === generation && content.value) content.value.scrollTop = scrollPosition;
    }
  }
}, { immediate: true });
onBeforeUnmount(() => { saveView(); disposed = true; generation++; });
</script>
<style scoped>
.library-directory-label { margin:0 0 18px; color:#bfae8a; font-size:.875rem; letter-spacing:.04em; }
.library-sidebar .town-directory-item { display:grid; grid-template-columns:30px minmax(0,1fr) auto; align-items:start; gap:12px; text-align:left; }
.library-sidebar .town-directory-item > span:nth-child(2) { line-height:1.6; }
.library-sidebar .town-directory-item > small { align-self:center; font-size:.875rem; opacity:.7; }
.library-sidebar .town-directory-item > svg { width:21px; height:21px; margin-top:3px; }
.library-chapter-number { align-self:center; font-size:.875rem; font-variant-numeric:tabular-nums; opacity:.7; }
.library-toolbar { align-items:flex-end; gap:20px; margin-bottom:28px; }
.library-search { flex:1; min-width:min(240px,100%); max-width:520px; }
.library-search > span { display:block; margin-bottom:8px; font-size:.875rem; color:#76604c; }
.library-search > input { width:100%; }
.library-result-count { align-self:center; margin-left:auto; padding-top:22px; font-size:.875rem; color:#8a775c; white-space:nowrap; }
.library-chapter { margin-bottom:32px; }
.library-chapter-heading { display:flex; align-items:baseline; justify-content:space-between; gap:16px; padding-bottom:13px; margin-bottom:17px; border-bottom:1px solid #d9c9ad; }
.library-chapter-heading h3 { margin:0; color:#3b5147; font-size:1.18rem; line-height:1.5; }
.library-chapter-heading > span { flex-shrink:0; color:#8a775c; font-size:.875rem; }
.library-list { display:grid; grid-template-columns:repeat(auto-fill,minmax(min(100%,270px),1fr)); gap:15px; }
.library-workspace .library-list > .library-card { display:grid; grid-template-columns:50px minmax(0,1fr); gap:14px; align-items:start; min-height:172px; padding:20px; text-align:left; color:#354d42; border:1px solid #dbcbb1; border-radius:12px; background:linear-gradient(135deg,#fffdf5,#f9f1e1); box-shadow:none; }
.library-workspace .library-list > .library-card:hover:not(:disabled) { background:#fffdf5; border-color:#ad9972; box-shadow:none; }
.library-material-icon { display:grid; place-items:center; width:48px; height:55px; background:#e1e8d7; border:1px solid #c1cdb2; border-radius:7px; color:#5d7953; }
.library-material-icon.is-video { color:#9b7143; background:#f0dfbe; border-color:#d9c19a; }
.library-material-icon svg { width:26px; height:26px; }
.library-card-copy { display:flex; flex-direction:column; gap:9px; min-width:0; }
.library-workspace .library-list strong { font-size:1rem; line-height:1.6; overflow-wrap:anywhere; }
.library-workspace .library-list small { color:#88775d; font-size:.875rem; line-height:1.5; }
.library-workspace .library-list > button > .library-card-action { grid-column:1/-1; display:flex; align-items:center; justify-content:flex-end; gap:8px; margin-top:auto; color:#6c7d57; font-size:.875rem; }
.library-card-action svg { width:18px; height:18px; }
.library-empty { padding:64px 20px; }
.library-empty > svg { width:44px; height:44px; color:#9e8b68; }
.library-empty p { margin:16px 0 20px; }
@media(max-width:1000px) { .library-toolbar { gap:14px; }.library-result-count { padding-top:0; }.library-workspace .library-list > .library-card { padding:16px; } }
@media(max-width:700px) { .library-directory-label { display:none; }.library-sidebar .town-directory-item { display:flex; align-items:center; min-width:max-content; }.library-sidebar .town-directory-item > span:nth-child(2) { max-width:200px; white-space:normal; }.library-sidebar .town-directory-item > svg,.library-chapter-number { display:none; }.library-search { max-width:none; flex-basis:100%; }.library-toolbar { margin-bottom:22px; }.library-result-count { margin-left:auto; }.library-list { grid-template-columns:minmax(0,1fr); }.library-workspace .library-list > .library-card { min-height:140px; } }
</style>
