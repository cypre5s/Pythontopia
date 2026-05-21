<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import type { WorkshopResourceListItemDto } from '@/api/dto/Workshop';

const learning = useLearningStore();
const router = useRouter();

const items = computed<WorkshopResourceListItemDto[]>(() => learning.resources.list);

onMounted(async () => {
  await learning.loadResources();
});

function openDetail(id: number): void {
  void router.push({ path: '/workshop', query: { id: String(id) } });
}
</script>

<template>
  <aside class="list">
    <header>
      <h3>已生成资源</h3>
      <button
        type="button"
        class="link"
        @click="learning.loadResources()"
      >
        刷新
      </button>
    </header>
    <p
      v-if="learning.resources.loading"
      class="hint"
    >
      载入资源列表中…
    </p>
    <ul
      v-else-if="items.length > 0"
      data-testid="resource-list"
    >
      <li
        v-for="item in items"
        :key="item.id"
      >
        <button
          type="button"
          class="card"
          @click="openDetail(item.id)"
        >
          <span class="title">{{ item.title }}</span>
          <span class="meta">
            {{ item.type }} · {{ item.groundingStatus }} ·
            {{ item.feedbackStatus === 'NONE' ? '未反馈' : item.feedbackStatus }}
          </span>
          <span class="time">{{ item.createdAt }}</span>
        </button>
      </li>
    </ul>
    <p
      v-else
      class="hint"
    >
      还没有生成任何资源，先在左侧挑选 KC 与类型试试。
    </p>
  </aside>
</template>

<style scoped>
.list {
  background: #fff8df;
  border: 2px solid #c9a86b;
  border-radius: 10px;
  padding: 14px;
  display: grid;
  gap: 10px;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.link {
  background: none;
  border: none;
  color: #6e4f1d;
  cursor: pointer;
  text-decoration: underline;
}
ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 8px;
}
.card {
  display: grid;
  gap: 4px;
  text-align: left;
  padding: 10px;
  border: 1px solid #c9a86b;
  border-radius: 8px;
  background: #fffbe9;
  cursor: pointer;
}
.card:hover {
  background: #fff2c9;
}
.title {
  font-weight: 600;
}
.meta {
  font-size: 12px;
  color: #6e4f1d;
}
.time {
  font-size: 11px;
  color: #9b7b3e;
}
.hint {
  color: #6e4f1d;
  font-size: 12px;
}
</style>
