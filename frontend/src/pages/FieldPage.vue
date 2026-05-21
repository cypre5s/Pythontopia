<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import FieldCanvas from '@/components/game/FieldCanvas.vue';
import KcDetailPanel from '@/components/field/KcDetailPanel.vue';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';

const learning = useLearningStore();
const toast = useToastStore();
const route = useRoute();
const router = useRouter();
const requestingBootstrap = ref<boolean>(false);

const hasProfile = computed(() => learning.profile !== null);
const totalCount = computed(() => learning.field.crops.length);
const unlockedCount = computed(() => learning.unlockedCrops.length);
const matureCount = computed(
  () => learning.field.crops.filter((crop) => crop.status === 'MATURE').length,
);
const witheredCount = computed(
  () => learning.field.crops.filter((crop) => crop.status === 'WITHERED').length,
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
</script>

<template>
  <AppLayout>
    <section class="field-page">
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
</style>
