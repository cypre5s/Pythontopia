<template>
  <section class="journal workspace-padding">
    <p class="eyebrow">任务日志</p>
    <h2>小镇里的未完故事</h2>
    <nav class="journal-filters" aria-label="任务类型">
      <button
        v-for="item in kinds"
        :key="item.key"
        :class="{ selected: kindFilter === item.key }"
        :aria-pressed="kindFilter === item.key"
        @click="kindFilter = item.key"
      >
        {{ item.label }}
      </button>
    </nav>
    <div class="journal-filters">
      <button
        v-for="item in filters"
        :key="item.key"
        :class="{ selected: filter === item.key }"
        @click="filter = item.key"
      >
        {{ item.label }}
      </button>
    </div>
    <article v-for="quest in quests" :key="quest.id" class="journal-entry">
      <header>
        <div>
          <span class="eyebrow"
            >{{ quest.kind === "side" ? "支线委托" : "主线委托" }} ·
            {{ quest.status === "complete" ? "已完成" : "进行中" }} · 版本
            {{ quest.version }}</span
          >
          <h3>{{ quest.title }}</h3>
        </div>
        <button
          @click="
            session.trackedQuest = quest.id;
            notify('已追踪此委托');
          "
        >
          {{ session.trackedQuest === quest.id ? "正在追踪" : "追踪" }}
        </button>
      </header>
      <ol>
        <li v-for="node in quest.nodes" :key="node.id" :class="node.status">
          <span class="node-icon">{{
            node.status === "complete"
              ? "✓"
              : node.status === "ready"
                ? "◇"
                : "·"
          }}</span>
          <div>
            <strong>{{ node.title }}</strong
            ><small
              >{{ stateName[node.status] }}
              {{ node.optional ? "· 可选" : "" }}</small
            >
          </div>
          <button
            v-if="node.status === 'ready' && node.activity_id"
            @click="
              session.open({
                kind: node.type === 'STUDY_LESSON' ? 'lesson' : 'practice',
                activity_id: node.activity_id,
              })
            "
          >
            {{ node.type === "STUDY_LESSON" ? "打开课程" : "打开练习" }}</button
          ><template v-if="node.status === 'ready' && node.choices?.length"
            ><button
              v-for="choice in node.choices"
              :key="choice.id"
              @click="choose(quest, node, choice.id)"
            >
              {{ choice.label ?? choice.text }}
            </button></template
          >
        </li>
      </ol>
    </article>
    <p v-if="!quests.length" class="notice">
      这里还没有对应的委托。未开放章节会在教师发布后出现。
    </p>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from "vue";
import { useSession } from "../shared/session";
import { notify } from "../shared/api";
import type { Quest, QuestNode } from "../shared/types";
const session = useSession(),
  filter = ref("active"),
  kindFilter = ref("all"),
  kinds = [
    { key: "all", label: "全部类型" },
    { key: "main", label: "主线" },
    { key: "side", label: "支线" },
  ],
  filters = [
    { key: "active", label: "进行中" },
    { key: "complete", label: "已完成" },
    { key: "all", label: "全部" },
  ],
  stateName = {
    ready: "当前可进行",
    complete: "已完成",
    locked: "等待前置",
    skipped: "未选择的分支",
  };
const quests = computed(
  () =>
    session.world?.quests.filter(
      (q) =>
        (kindFilter.value === "all" || q.kind === kindFilter.value) &&
        (filter.value === "all" ||
          (filter.value === "complete"
            ? q.status === "complete"
            : q.status !== "complete")),
    ) ?? [],
);
async function choose(quest: Quest, node: QuestNode, choice: string) {
  try {
    await session.event(quest, node, {
      choice,
      npc_key: node.target,
      dialogue_id: node.dialogue_id ?? node.target,
    });
  } catch (e) {
    notify(e instanceof Error ? e.message : "选择失败");
  }
}
</script>
