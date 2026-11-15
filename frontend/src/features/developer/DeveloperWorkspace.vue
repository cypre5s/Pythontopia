<template>
  <section class="town-workspace developer-workspace">
    <div v-if="allowed" class="town-book developer-book">
      <aside class="town-sidebar">
        <nav class="town-directory" role="tablist" aria-label="开发工具分类" aria-orientation="vertical">
          <button
            v-for="tab in tabs" :id="`developer-tab-${tab.id}`" :key="tab.id"
            class="town-directory-item" :class="{ 'is-current': activeTab === tab.id }"
            role="tab" :aria-selected="activeTab === tab.id" :aria-controls="`developer-panel-${tab.id}`"
            :tabindex="activeTab === tab.id ? 0 : -1"
            @click="activeTab = tab.id" @keydown="moveTab($event, tab.id)"
          >
            <GameIcon :name="tab.icon" /><span>{{ tab.label }}</span><GameIcon name="chevron" />
          </button>
        </nav>
      </aside>
      <main class="town-content developer-content">
        <header class="developer-heading">
          <h3 class="game-card-title"><GameIcon :name="selected.icon" />{{ selected.label }}</h3>
        </header>
        <div :id="`developer-panel-${activeTab}`" role="tabpanel" :aria-labelledby="`developer-tab-${activeTab}`">
          <AccountPanel v-show="activeTab === 'account'" :key="identity" />
          <template v-if="!classroom">
            <p v-if="activeTab !== 'account'" class="developer-help">请先选择已加入的班级。</p>
          </template>
          <template v-else>
              <div v-if="activeTab !== 'account' && loading && !overview" class="town-empty" role="status">正在加载体验工具…</div>
              <div v-if="activeTab !== 'account' && loadError" class="developer-load-error">
                <ToolFeedback :error="loadError" />
                <button class="town-secondary" :disabled="busy || loading" @click="load">重新加载</button>
              </div>
              <template v-if="overview">
                <ContentPanel v-show="activeTab === 'content'" :key="`content-${identity}`" />
                <StagePanel v-show="activeTab === 'stage'" :key="`stage-${identity}`" />
                <CheckpointPanel v-show="activeTab === 'checkpoints'" :key="`checkpoints-${identity}`" />
              </template>
          </template>
        </div>
      </main>
    </div>
    <p v-else class="developer-unavailable" role="alert">当前账户未开放开发工具。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick } from "vue";
import GameIcon from "../../shared/GameIcon.vue";
import AccountPanel from "./AccountPanel.vue";
import ContentPanel from "./ContentPanel.vue";
import StagePanel from "./StagePanel.vue";
import CheckpointPanel from "./CheckpointPanel.vue";
import ToolFeedback from "./ToolFeedback.vue";
import { createDeveloperTools } from "./useDeveloperTools";
import type { DeveloperTab } from "./types";
import "./developer.css";

const tabs = [
  { id: "account", label: "账户概要", icon: "people" },
  { id: "content", label: "内容定位", icon: "book" },
  { id: "stage", label: "任务阶段", icon: "journal" },
  { id: "checkpoints", label: "体验检查点", icon: "save" },
] as const;
const { activeTab, allowed, classroom, overview, loading, loadError, busy, identity, load } = createDeveloperTools();
const selected = computed(() => tabs.find(tab => tab.id === activeTab.value)!);

async function moveTab(event: KeyboardEvent, id: DeveloperTab) {
  const index = tabs.findIndex(tab => tab.id === id);
  const offset = event.key === "ArrowDown" || event.key === "ArrowRight" ? 1
    : event.key === "ArrowUp" || event.key === "ArrowLeft" ? -1 : 0;
  if (!offset && event.key !== "Home" && event.key !== "End") return;
  event.preventDefault();
  const next = event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1
    : (index + offset + tabs.length) % tabs.length;
  activeTab.value = tabs[next]!.id;
  await nextTick();
  document.getElementById(`developer-tab-${activeTab.value}`)?.focus();
}
</script>
