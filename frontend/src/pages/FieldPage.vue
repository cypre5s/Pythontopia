<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import FieldCanvas from '@/components/game/FieldCanvas.vue';
import KcDetailPanel from '@/components/field/KcDetailPanel.vue';
import { playSfx } from '@/utils/sfx';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';

const learning = useLearningStore();
const toast = useToastStore();
const route = useRoute();
const router = useRouter();
const requestingBootstrap = ref<boolean>(false);
const CHAPTER_HOVER_SFX_INTERVAL_MS = 300;

interface ChapterNavItem {
  slug: string;
  title: string;
  iconSrc: string;
  startKc: number;
  endKc: number;
}

interface ChapterNavView extends ChapterNavItem {
  firstKcCode: string | null;
  active: boolean;
}

const CHAPTER_NAV_ITEMS: ChapterNavItem[] = [
  {
    slug: 'chapter_basics',
    title: '基础启航',
    iconSrc: '/assets/pixel/icons_kc/chapter_basics/lorc__brain.png',
    startKc: 1,
    endKc: 7,
  },
  {
    slug: 'variable_assignment',
    title: '变量赋值',
    iconSrc: '/assets/pixel/icons_kc/variable_assignment/lorc__padlock.png',
    startKc: 8,
    endKc: 16,
  },
  {
    slug: 'data_types_number_string_bool',
    title: '数据类型',
    iconSrc: '/assets/pixel/icons_kc/data_types_number_string_bool/lorc__light-bulb.png',
    startKc: 17,
    endKc: 30,
  },
  {
    slug: 'list_tuple_set_dict',
    title: '容器结构',
    iconSrc: '/assets/pixel/icons_kc/list_tuple_set_dict/delapouite__chemical-tank.png',
    startKc: 31,
    endKc: 38,
  },
  {
    slug: 'control_flow_if_else',
    title: '条件分支',
    iconSrc: '/assets/pixel/icons_kc/control_flow_if_else/delapouite__choice.png',
    startKc: 39,
    endKc: 47,
  },
  {
    slug: 'loop_for_while',
    title: '循环结构',
    iconSrc: '/assets/pixel/icons_kc/loop_for_while/lorc__cycle.png',
    startKc: 48,
    endKc: 53,
  },
  {
    slug: 'function_def_args_return',
    title: '函数定义',
    iconSrc: '/assets/pixel/icons_kc/function_def_args_return/lorc__gears.png',
    startKc: 54,
    endKc: 65,
  },
];

let lastChapterHoverSfxAt = 0;

const hasProfile = computed(() => learning.profile !== null);
const totalCount = computed(() => learning.field.crops.length);
const unlockedCount = computed(() => learning.unlockedCrops.length);
const matureCount = computed(
  () => learning.field.crops.filter((crop) => crop.status === 'MATURE').length,
);
const witheredCount = computed(
  () => learning.field.crops.filter((crop) => crop.status === 'WITHERED').length,
);
const chapterNav = computed<ChapterNavView[]>(() =>
  CHAPTER_NAV_ITEMS.map((chapter) => {
    const firstKcCode = findFirstKcCodeInChapter(chapter);
    const activeKcNumber = parseKcNumber(learning.field.activeKcCode);
    return {
      ...chapter,
      firstKcCode,
      active:
        activeKcNumber !== null &&
        activeKcNumber >= chapter.startKc &&
        activeKcNumber <= chapter.endKc,
    };
  }),
);

onMounted(async () => {
  if (!hasProfile.value) {
    await learning.loadProfile();
  }
  await learning.loadField();
  if (typeof route.query.kc === 'string') {
    learning.selectKc(route.query.kc);
  }
});

watch(
  () => route.query.kc,
  (next) => {
    if (typeof next === 'string') {
      learning.selectKc(next);
    }
  },
);

async function onBootstrap(): Promise<void> {
  if (!hasProfile.value) {
    toast.show('请先在画像屋完成画像访谈，再来开垦知识田。', 'error');
    void router.push('/profile-room');
    return;
  }
  requestingBootstrap.value = true;
  try {
    const result = await learning.bootstrapField();
    toast.show(`已开垦知识田 (${result.unlockedCount}/${result.totalKcs} KC 已解锁)`, 'success');
  } catch (error) {
    toast.show('知识田初始化失败，请稍后再试。', 'error');
    void error;
  } finally {
    requestingBootstrap.value = false;
  }
}

function closePanel(): void {
  learning.selectKc(null);
  void router.replace({ path: '/field' });
}

function parseKcNumber(kcCode: string | null): number | null {
  if (!kcCode) {
    return null;
  }
  const match = /^PY43-KC-(\d{3})$/.exec(kcCode);
  if (!match) {
    return null;
  }
  return Number.parseInt(match[1]!, 10);
}

function findFirstKcCodeInChapter(chapter: ChapterNavItem): string | null {
  const crops = learning.field.crops
    .map((crop) => ({ crop, kcNumber: parseKcNumber(crop.kcCode) }))
    .filter(
      (entry): entry is { crop: typeof entry.crop; kcNumber: number } =>
        entry.kcNumber !== null &&
        entry.kcNumber >= chapter.startKc &&
        entry.kcNumber <= chapter.endKc,
    )
    .sort((left, right) => left.kcNumber - right.kcNumber);
  return crops[0]?.crop.kcCode ?? null;
}

function selectChapter(chapter: ChapterNavView): void {
  if (!chapter.firstKcCode) {
    return;
  }
  learning.selectKc(chapter.firstKcCode);
  void router.push({ path: '/field', query: { kc: chapter.firstKcCode } });
  window.requestAnimationFrame(() => {
    document.querySelector('.field-grid')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
}

function onChapterHover(): void {
  const now = Date.now();
  if (now - lastChapterHoverSfxAt < CHAPTER_HOVER_SFX_INTERVAL_MS) {
    return;
  }
  lastChapterHoverSfxAt = now;
  playSfx('hover');
}
</script>

<template>
  <AppLayout>
    <section class="field-page">
      <nav
        class="chapter-bar pyt-pixel-text"
        aria-label="Python 语言基础章节导航"
      >
        <button
          v-for="chapter in chapterNav"
          :key="chapter.slug"
          type="button"
          class="chapter-button"
          :class="{ active: chapter.active }"
          :disabled="!chapter.firstKcCode"
          :data-testid="`chapter-nav-${chapter.slug}`"
          @click="selectChapter(chapter)"
          @pointerenter="onChapterHover"
        >
          <img
            :src="chapter.iconSrc"
            :alt="chapter.title"
          >
          <span>{{ chapter.title }}</span>
        </button>
      </nav>
      <header class="field-header">
        <div>
          <h2>知识田</h2>
          <p class="hint">
            65 KC ·
            已解锁 {{ unlockedCount }} ·
            成熟 {{ matureCount }} ·
            枯萎 {{ witheredCount }}
          </p>
        </div>
        <div class="actions">
          <button
            type="button"
            class="primary"
            :disabled="requestingBootstrap || (learning.field.bootstrapped && totalCount > 0)"
            @click="onBootstrap"
          >
            {{ totalCount > 0 ? '已开垦' : '开垦知识田' }}
          </button>
          <button
            type="button"
            class="secondary"
            @click="learning.loadField()"
          >
            刷新作物
          </button>
        </div>
      </header>
      <div class="field-grid">
        <FieldCanvas class="field-canvas" />
        <div class="field-sidebar">
          <KcDetailPanel
            :kc-code="learning.field.activeKcCode"
            @close="closePanel"
          />
          <div
            v-if="!learning.field.activeKcCode"
            class="sidebar-empty"
          >
            点击作物可查看 KC 详情。
          </div>
        </div>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.field-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  color: #1d1108;
  background: linear-gradient(180deg, #fef7e3 0%, #f3e4be 100%);
}
.chapter-bar {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
  padding: 10px;
  border: 2px solid #a97734;
  border-radius: 8px;
  background: #fff7e6;
  box-shadow: inset 0 -3px 0 rgba(110, 79, 29, 0.14);
}
.chapter-button {
  display: flex;
  min-width: 0;
  min-height: 74px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 7px 5px;
  border: 2px solid #c9a86b;
  border-radius: 6px;
  background: #fef7e3;
  color: #3b2411;
  cursor: pointer;
}
.chapter-button:hover,
.chapter-button.active {
  border-color: #2f5a36;
  background: #e7f0cf;
}
.chapter-button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}
.chapter-button img {
  width: 34px;
  height: 34px;
  image-rendering: pixelated;
}
.chapter-button span {
  max-width: 100%;
  overflow-wrap: anywhere;
  text-align: center;
  font-size: 11px;
  line-height: 1.2;
}
.field-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
}
.field-header h2 {
  margin: 0;
  font-size: 22px;
  font-family: 'PingFang SC', 'Noto Sans CJK SC', monospace;
}
.field-header .hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6e4f1d;
}
.actions {
  display: flex;
  gap: 8px;
}
.actions button {
  padding: 8px 12px;
  border-radius: 4px;
  border: 2px solid #6e4f1d;
  background: #fff7e6;
  cursor: pointer;
  font-family: 'PingFang SC', 'Noto Sans CJK SC', monospace;
}
.actions .primary {
  background: #6e4f1d;
  color: #fff7e6;
}
.actions .primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.field-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
}
.field-canvas {
  width: 100%;
  min-height: 480px;
}
.field-sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sidebar-empty {
  border: 2px dashed #c9a86b;
  border-radius: 8px;
  padding: 24px;
  color: #6e4f1d;
  text-align: center;
}
@media (max-width: 900px) {
  .chapter-bar {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
  .field-grid {
    grid-template-columns: 1fr;
  }
}
</style>
