<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { listKnowledgeComponents } from '@/api/course';
import type { KcDto } from '@/api/dto/KcDto';

const props = defineProps<{
  kcCode: string | null;
}>();
const emit = defineEmits<{ (e: 'close'): void }>();

const router = useRouter();
const learning = useLearningStore();
const toast = useToastStore();
const detail = ref<KcDto | null>(null);
const loading = ref<boolean>(false);
const indexCache: Record<string, KcDto> = {};

const masterySummary = computed(() => learning.activeKcMastery);
const cropTile = computed(() =>
  learning.field.crops.find((crop) => crop.kcCode === props.kcCode) ?? null,
);

async function ensureCache(): Promise<void> {
  if (Object.keys(indexCache).length > 0) {
    return;
  }
  const list = await listKnowledgeComponents(43);
  list.forEach((kc) => {
    indexCache[kc.kcCode] = kc;
  });
}

watch(
  () => props.kcCode,
  async (next) => {
    detail.value = null;
    if (!next) {
      return;
    }
    loading.value = true;
    try {
      await ensureCache();
      detail.value = indexCache[next] ?? null;
    } catch (error) {
      toast.push({ kind: 'error', text: `载入 KC ${next} 失败` });
      void error;
    } finally {
      loading.value = false;
    }
  },
  { immediate: true },
);

const masteryPercent = computed(() => {
  if (!masterySummary.value) {
    return 0;
  }
  return Math.round(Number(masterySummary.value.masteryScore) * 100);
});

function gotoWorkshop(): void {
  if (!props.kcCode) {
    return;
  }
  emit('close');
  learning.selectKc(null);
  void router.push({ path: '/workshop', query: { kc: props.kcCode } });
}
</script>

<template>
  <aside
    v-if="props.kcCode"
    class="kc-panel"
  >
    <header>
      <div>
        <span class="badge">KC</span>
        <h3>{{ detail?.kcName ?? props.kcCode }}</h3>
        <p class="muted">
          {{ props.kcCode }} · 第 {{ detail?.chapterOrder ?? '-' }} 章
        </p>
      </div>
      <button
        type="button"
        class="close"
        @click="emit('close')"
      >
        ×
      </button>
    </header>
    <section class="body">
      <p
        v-if="loading"
        class="muted"
      >
        载入 KC 详情中…
      </p>
      <p
        v-else-if="detail?.summaryMarkdown"
        class="summary"
      >
        {{ detail.summaryMarkdown }}
      </p>
      <p
        v-else
        class="muted"
      >
        该 KC 暂无可读摘要。
      </p>
    </section>
    <section class="mastery">
      <header>
        <span>掌握度</span>
        <strong>{{ masteryPercent }}%</strong>
      </header>
      <div class="bar">
        <div
          class="bar-fill"
          :style="{ width: `${masteryPercent}%` }"
        />
      </div>
      <ul class="meta">
        <li>状态: {{ cropTile?.status ?? '未解锁' }}</li>
        <li>最近事件: {{ masterySummary?.latestSource ?? '尚无' }}</li>
        <li>记录时间: {{ masterySummary?.lastEventAt ?? '尚无' }}</li>
      </ul>
    </section>
    <footer>
      <button
        type="button"
        class="primary"
        :disabled="!cropTile?.unlocked"
        @click="gotoWorkshop"
      >
        前往资源工坊
      </button>
      <small class="hint">在资源工坊可生成讲义、思维导图、练习题、代码案例、拓展阅读、动画脚本。</small>
    </footer>
  </aside>
</template>

<style scoped>
.kc-panel {
  width: 320px;
  background: linear-gradient(180deg, #fff7e6 0%, #fdebc0 100%);
  border: 2px solid #6e4f1d;
  border-radius: 10px;
  box-shadow: 0 6px 18px rgba(48, 31, 12, 0.25);
  padding: 16px;
  color: #1d1108;
  font-family: 'PingFang SC', 'Noto Sans CJK SC', monospace;
}
.kc-panel header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: flex-start;
}
.kc-panel h3 {
  margin: 4px 0 0;
  font-size: 18px;
}
.badge {
  display: inline-block;
  font-size: 12px;
  background: #6e4f1d;
  color: #fff7e6;
  padding: 1px 8px;
  border-radius: 999px;
}
.muted {
  color: #5e4923;
  font-size: 12px;
  margin-top: 4px;
}
.close {
  background: none;
  border: none;
  font-size: 22px;
  cursor: pointer;
  color: #6e4f1d;
}
.body {
  margin: 12px 0;
  font-size: 14px;
  line-height: 1.5;
}
.summary {
  white-space: pre-wrap;
}
.mastery header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 12px;
}
.bar {
  width: 100%;
  height: 8px;
  background: #d8c89a;
  border-radius: 4px;
  margin-top: 4px;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #a3c47b 0%, #4f8a3e 100%);
}
.meta {
  list-style: none;
  padding: 0;
  margin: 10px 0;
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: #5e4923;
}
footer {
  display: grid;
  gap: 8px;
  margin-top: 8px;
}
.primary {
  background: #6e4f1d;
  color: #fff7e6;
  border: none;
  border-radius: 4px;
  padding: 8px 12px;
  font-weight: 600;
  cursor: pointer;
}
.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.hint {
  color: #6e4f1d;
  font-size: 11px;
}
</style>
