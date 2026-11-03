<template>
  <header class="game-hud">
    <div class="brand">
      <span class="brand-mark">✦</span>
      <div>
        PYTHONTOPIA<small>{{
          session.classes.find((c) => c.id === session.classroomId)?.name
        }}</small>
      </div>
    </div>
    <div class="hud-actions">
      <button @click="session.open({ kind: 'journal' })">
        任务日志 <kbd>J</kbd></button
      ><button
        aria-label="打开设置"
        @click="session.open({ kind: 'settings' })"
      >
        ⚙
      </button>
    </div>
  </header>
  <aside v-if="tracked" class="quest-tracker">
    <p class="eyebrow">
      {{ tracked.status === "complete" ? "委托已完成" : "当前委托" }}
    </p>
    <h2>{{ tracked.title }}</h2>
    <p>{{ node?.title ?? "全部目标已达成" }}</p>
    <button
      v-if="node?.target"
      class="text-button"
      @click="$emit('guide', node.target)"
    >
      寻找目标 ↗
    </button>
  </aside>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { useSession } from "../shared/session";
const session = useSession();
defineEmits<{ guide: [key: string] }>();
const tracked = computed(
  () =>
    session.world?.quests.find((q) => q.id === session.trackedQuest) ??
    session.world?.quests.find((q) => q.status !== "complete"),
);
const node = computed(() =>
  tracked.value?.nodes.find((n) => n.status === "ready"),
);
</script>
