<script setup lang="ts">
/**
 * R-13 §-1 资源工坊改造（用户原话 A 方案）：
 *   1. 不再限制「已解锁 KC」—— 拉课程包 43 全部 65 个 KC，按章分组展示；
 *   2. 视觉换星露谷牛皮纸 9-slice 风（与 DialogModal 协调）；
 *   3. 紧凑布局，fit 标准 viewport（无内部滚动），所有 KC 折叠 chip 风格；
 *   4. 已"种"过的 KC 在 chip 上叠 ★ 标识（来自 learning.unlockedCrops），
 *      鼓励玩家先种再用，但不强制。
 *
 * 业务保留：
 *   - 资源类型 6 种（HANDOUT / MINDMAP / PRACTICE / CODE_LAB / READING / STORYBOARD）；
 *   - learning.createResource 不变，仅 KC 来源换成全集。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { playSfx } from '@/composables/useAudio';
import { listChapters, listKnowledgeComponents } from '@/api/course';
import type { ChapterDto } from '@/api/dto/ChapterDto';
import type { KcDto } from '@/api/dto/KcDto';
import type { WorkshopCreateRequest, WorkshopResourceType } from '@/api/dto/Workshop';

const RESOURCE_TYPES: { value: WorkshopResourceType; label: string; hint: string; icon: string }[] = [
  { value: 'HANDOUT', label: '讲义', hint: '段落 + 课件页引用', icon: '✎' },
  { value: 'MINDMAP', label: '思维导图', hint: 'Mermaid 文本，浏览器渲染', icon: '✦' },
  { value: 'PRACTICE', label: '练习题', hint: '1-3 道题，不含完整答案', icon: '✪' },
  { value: 'CODE_LAB', label: '代码案例', hint: '可在 Pyodide 中跑通', icon: '⚒' },
  { value: 'READING', label: '拓展阅读', hint: '至少 2 个延伸阅读卡片', icon: '☼' },
  { value: 'STORYBOARD', label: '动画脚本', hint: '3-5 个分镜，不渲染视频', icon: '⌘' },
];

interface ChapterGroup {
  chapter: ChapterDto;
  kcs: KcDto[];
}

const learning = useLearningStore();
const toast = useToastStore();
const router = useRouter();

const selectedType = ref<WorkshopResourceType>('HANDOUT');
const focusHint = ref<string>('');
const submitting = ref<boolean>(false);
const selectedKcs = ref<string[]>([]);

// R-13 改造：全部 65 KC（按章分组），不再受 unlockedCrops 限制。
const allKcs = ref<KcDto[]>([]);
const chapters = ref<ChapterDto[]>([]);
const loadingCatalog = ref<boolean>(true);
const catalogError = ref<string | null>(null);
const expandedChapters = ref<Set<number>>(new Set());

const unlockedCodeSet = computed<Set<string>>(
  () => new Set(learning.unlockedCrops.map((crop) => crop.kcCode)),
);

const chapterGroups = computed<ChapterGroup[]>(() => {
  const byOrder = chapters.value.slice().sort((a, b) => a.orderIndex - b.orderIndex);
  return byOrder.map((chapter) => ({
    chapter,
    kcs: allKcs.value
      .filter((kc) => kc.chapterId === chapter.chapterId)
      .sort((a, b) => a.kcCode.localeCompare(b.kcCode)),
  }));
});

const totalKcCount = computed<number>(() => allKcs.value.length);
const canSubmit = computed(() => selectedKcs.value.length > 0 && !submitting.value);

function toggleKc(code: string): void {
  const idx = selectedKcs.value.indexOf(code);
  if (idx === -1) {
    selectedKcs.value = [...selectedKcs.value, code];
  } else {
    selectedKcs.value = selectedKcs.value.filter((entry) => entry !== code);
  }
}

function toggleChapter(chapterId: number): void {
  const next = new Set(expandedChapters.value);
  if (next.has(chapterId)) {
    next.delete(chapterId);
  } else {
    next.add(chapterId);
  }
  expandedChapters.value = next;
}

async function loadCatalog(): Promise<void> {
  loadingCatalog.value = true;
  catalogError.value = null;
  try {
    const [chs, kcs] = await Promise.all([
      listChapters(43),
      listKnowledgeComponents(43),
    ]);
    chapters.value = chs;
    allKcs.value = kcs;
    // 默认展开前两章，避免初次进入空白。
    expandedChapters.value = new Set(
      chs.slice(0, 2).map((c) => c.chapterId),
    );
  } catch (error) {
    catalogError.value = error instanceof Error ? error.message : String(error);
    toast.show(`课程目录加载失败：${catalogError.value}`, 'error');
  } finally {
    loadingCatalog.value = false;
  }
}

async function submit(): Promise<void> {
  if (selectedKcs.value.length === 0) {
    toast.show('请选择至少一个 KC。', 'error');
    return;
  }
  submitting.value = true;
  try {
    const trimmed = focusHint.value.trim();
    const payload: WorkshopCreateRequest =
      trimmed.length > 0
        ? { kcCodes: selectedKcs.value, type: selectedType.value, focusHint: trimmed }
        : { kcCodes: selectedKcs.value, type: selectedType.value };
    const response = await learning.createResource(payload);
    playSfx('sfx_success');
    playSfx('sfx_coin');
    toast.show(`资源生成成功（runId=${response.runId}）`, 'success');
    await router.push({ path: '/workshop', query: { id: String(response.resourceId) } });
  } catch (error) {
    playSfx('sfx_error');
    const message = error instanceof Error ? error.message : '资源生成失败';
    toast.show(message, 'error');
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  void loadCatalog();
});
</script>

<template>
  <section
    class="panel"
    data-testid="workshop-panel"
  >
    <header class="panel__head">
      <h2 class="panel__title">
        资源工坊
      </h2>
      <p class="panel__sub">
        在 65 个 KC 中任意挑选（无需先解锁），多智能体协作生成资源
      </p>
    </header>
    <section class="panel__body">
      <div class="block block--kc">
        <div class="block__head">
          <span class="block__label">课程包 43 · {{ totalKcCount }} 个 KC</span>
          <span
            v-if="selectedKcs.length > 0"
            class="block__count"
            data-testid="selected-count"
          >已选 {{ selectedKcs.length }} 项</span>
        </div>
        <p
          v-if="loadingCatalog"
          class="empty"
        >
          课程目录加载中…
        </p>
        <p
          v-else-if="catalogError"
          class="empty error"
        >
          {{ catalogError }}
        </p>
        <ul
          v-else
          class="chapter-list"
          data-testid="kc-chapter-list"
        >
          <li
            v-for="group in chapterGroups"
            :key="group.chapter.chapterId"
            class="chapter-item"
            :class="{ 'chapter-item--open': expandedChapters.has(group.chapter.chapterId) }"
          >
            <button
              type="button"
              class="chapter-head"
              @click="toggleChapter(group.chapter.chapterId)"
            >
              <span class="chapter-no">第 {{ group.chapter.orderIndex }} 章</span>
              <span class="chapter-title">{{ group.chapter.title }}</span>
              <span class="chapter-meta">{{ group.kcs.length }} KC</span>
              <span
                class="chapter-toggle"
                aria-hidden="true"
              >{{ expandedChapters.has(group.chapter.chapterId) ? '▾' : '▸' }}</span>
            </button>
            <ul
              v-if="expandedChapters.has(group.chapter.chapterId)"
              class="kc-grid"
            >
              <li
                v-for="kc in group.kcs"
                :key="kc.kcCode"
              >
                <label
                  class="kc-chip"
                  :class="{
                    'kc-chip--selected': selectedKcs.includes(kc.kcCode),
                    'kc-chip--unlocked': unlockedCodeSet.has(kc.kcCode),
                  }"
                  :title="kc.summaryMarkdown ?? kc.kcName"
                >
                  <input
                    type="checkbox"
                    :checked="selectedKcs.includes(kc.kcCode)"
                    :data-testid="`kc-checkbox-${kc.kcCode}`"
                    @change="toggleKc(kc.kcCode)"
                  >
                  <span class="kc-code">{{ kc.kcCode }}</span>
                  <span class="kc-name">{{ kc.kcName }}</span>
                  <span
                    v-if="unlockedCodeSet.has(kc.kcCode)"
                    class="kc-star"
                    aria-label="已解锁"
                  >★</span>
                </label>
              </li>
            </ul>
          </li>
        </ul>
      </div>
      <div class="block block--type">
        <span class="block__label">资源类型</span>
        <ul
          class="type-row"
          data-testid="type-list"
        >
          <li
            v-for="entry in RESOURCE_TYPES"
            :key="entry.value"
          >
            <label
              class="type-card"
              :class="{ 'type-card--active': selectedType === entry.value }"
            >
              <input
                v-model="selectedType"
                type="radio"
                :value="entry.value"
                :data-testid="`type-${entry.value}`"
              >
              <span
                class="type-card__icon"
                aria-hidden="true"
              >{{ entry.icon }}</span>
              <span class="type-card__label">{{ entry.label }}</span>
              <small class="type-card__hint">{{ entry.hint }}</small>
            </label>
          </li>
        </ul>
      </div>
      <div class="block block--focus">
        <label class="focus-label">
          <span class="block__label">学习重点（可选）</span>
          <textarea
            v-model="focusHint"
            maxlength="240"
            rows="2"
            placeholder="例：重点 for 循环 break/continue 区别"
          />
        </label>
      </div>
    </section>
    <footer class="panel__foot">
      <button
        type="button"
        class="primary"
        :disabled="!canSubmit"
        @click="submit"
      >
        {{ submitting ? '生成中…' : `生成资源（${selectedKcs.length} 个 KC）` }}
      </button>
      <span
        v-if="learning.resources.phase === 'error' && learning.resources.lastError"
        class="error"
      >
        {{ learning.resources.lastError }}
      </span>
    </footer>
  </section>
</template>

<style scoped>
/* R-13 §-1 资源工坊 9-slice 牛皮纸面板 */
.panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  background: linear-gradient(180deg, #f9e7bc 0%, #f1d896 100%);
  border: 4px solid #6e4f1d;
  border-radius: 10px;
  color: #3a2a14;
  box-shadow: 0 4px 0 rgba(0, 0, 0, 0.18), inset 0 0 0 2px rgba(255, 247, 230, 0.55);
  font-family: 'PythontopiaPixel', 'PingFang SC', sans-serif;
  /* 关键：让面板高度 fit 父容器，不再撑高浏览器 */
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}
.panel__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  border-bottom: 2px dashed rgba(110, 79, 29, 0.45);
  padding-bottom: 4px;
}
.panel__title {
  margin: 0;
  font-size: 18px;
  color: #4d3613;
}
.panel__sub {
  margin: 0;
  font-size: 11px;
  color: #6e4f1d;
}
.panel__body {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 10px;
  flex: 1 1 auto;
  min-height: 0;
}
.block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
}
.block--kc {
  grid-row: span 2;
  overflow: hidden;
}
.block__head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.block__label {
  font-size: 12px;
  font-weight: 700;
  color: #4d3613;
}
.block__count {
  font-size: 11px;
  color: #2d5b1b;
  background: rgba(120, 180, 80, 0.25);
  border: 1px solid #6e4f1d;
  padding: 0 6px;
  border-radius: 4px;
}
.chapter-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow: auto;
  max-height: 100%;
  scrollbar-width: thin;
}
.chapter-item {
  border: 2px solid #c9a86b;
  border-radius: 6px;
  background: #fff7e6;
}
.chapter-head {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 6px 10px;
  background: transparent;
  border: none;
  cursor: pointer;
  font: inherit;
  color: #4d3613;
  text-align: left;
}
.chapter-head:hover {
  background: rgba(241, 196, 83, 0.2);
}
.chapter-no {
  font-weight: 700;
  font-size: 12px;
  color: #6e4f1d;
}
.chapter-title {
  font-size: 13px;
}
.chapter-meta {
  font-size: 11px;
  color: #8a6c2c;
}
.chapter-toggle {
  font-size: 13px;
  color: #6e4f1d;
}
.kc-grid {
  list-style: none;
  margin: 0;
  padding: 0 10px 8px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 4px;
}
.kc-chip {
  display: grid;
  grid-template-columns: auto auto 1fr auto;
  gap: 4px;
  align-items: center;
  padding: 4px 8px;
  background: #fdf3d0;
  border: 1px solid #c9a86b;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: background 100ms, border-color 100ms;
}
.kc-chip:hover {
  background: #f5e2c1;
}
.kc-chip--selected {
  background: #ffe098;
  border-color: #6e4f1d;
  box-shadow: inset 0 0 0 1px #6e4f1d;
}
.kc-chip--unlocked .kc-code {
  color: #2d5b1b;
}
.kc-chip input {
  margin: 0;
}
.kc-code {
  font-family: monospace;
  font-size: 10px;
  color: #6e4f1d;
}
.kc-name {
  font-size: 12px;
  color: #3a2a14;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kc-star {
  color: #d4af37;
  font-size: 12px;
}
.block--type .type-row {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
}
.type-card {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  gap: 2px 8px;
  padding: 6px 8px;
  background: #fff7e6;
  border: 2px solid #c9a86b;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.type-card input {
  grid-row: span 2;
  grid-column: 1;
  align-self: center;
}
.type-card__icon {
  display: none;
}
.type-card__label {
  grid-column: 2;
  font-weight: 700;
  color: #4d3613;
}
.type-card__hint {
  grid-column: 2;
  color: #6e4f1d;
  font-size: 10px;
  line-height: 1.3;
}
.type-card--active {
  background: #ffe098;
  border-color: #6e4f1d;
  box-shadow: inset 0 0 0 1px #6e4f1d;
}
.block--focus textarea {
  width: 100%;
  padding: 6px;
  background: #fff7e6;
  border: 2px solid #c9a86b;
  border-radius: 4px;
  resize: vertical;
  font: inherit;
  font-size: 12px;
  color: #3a2a14;
}
.focus-label {
  display: grid;
  gap: 4px;
}
.panel__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 2px dashed rgba(110, 79, 29, 0.45);
  padding-top: 8px;
}
.primary {
  font-family: inherit;
  font-size: 13px;
  padding: 6px 14px;
  background: #f7d774;
  color: #2a1a08;
  border: 3px solid #8c6a23;
  border-radius: 4px;
  box-shadow: 0 3px 0 #8c6a23;
  cursor: pointer;
  font-weight: 700;
}
.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.primary:active:not(:disabled) {
  transform: translateY(2px);
  box-shadow: 0 1px 0 #8c6a23;
}
.empty {
  padding: 12px;
  text-align: center;
  background: #fff7e6;
  border: 2px dashed #c9a86b;
  border-radius: 6px;
  font-size: 12px;
  color: #6e4f1d;
  margin: 0;
}
.error {
  color: #b3402a;
  font-size: 12px;
}
@media (max-width: 1024px) {
  .panel__body {
    grid-template-columns: 1fr;
  }
  .block--kc {
    grid-row: auto;
  }
}
</style>
