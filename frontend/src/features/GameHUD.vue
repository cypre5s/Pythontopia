<template>
  <div class="world-ui" :inert="blocked" :aria-hidden="blocked || undefined">
    <header class="game-hud">
      <div class="brand town-brand">
        <span class="brand-mark"><GameIcon name="leaf" /></span>
        <div>
          PYTHONTOPIA<small
            >{{ place }}<span aria-hidden="true"> · </span>学习小镇</small
          >
        </div>
      </div>
      <div class="resident-badge">
        <span class="resident-avatar" aria-hidden="true">{{
          session.user?.name.slice(0, 1)
        }}</span>
        <div>
          <strong>{{ session.user?.name }}</strong
          ><small :title="classroomName">{{ classroomName }}</small>
        </div>
      </div>
    </header>
    <aside
      v-if="tracked"
      class="quest-tracker"
      :class="{ 'is-collapsed': collapsed }"
      aria-label="当前委托"
    >
      <button
        class="quest-toggle"
        :aria-expanded="!collapsed"
        aria-controls="tracked-quest-details"
        @click="collapsed = !collapsed"
      >
        <GameIcon name="compass" /><span>{{
          tracked.kind === "main" ? "主线委托" : "支线委托"
        }}</span>
        <span class="quest-count">{{ completed }} / {{ total }}</span
        ><span class="collapse-mark" aria-hidden="true">{{
          collapsed ? "+" : "−"
        }}</span>
      </button>
      <div v-show="!collapsed" id="tracked-quest-details">
        <h2>{{ tracked.title }}</h2>
        <div
          class="quest-progress"
          role="progressbar"
          aria-label="委托步骤进度"
          :aria-valuenow="completed"
          :aria-valuemax="total"
          aria-valuemin="0"
          :aria-valuetext="`已完成 ${completed} / ${total} 步`"
        >
          <span
            :style="{ width: `${total ? (completed / total) * 100 : 0}%` }"
          />
        </div>
        <p class="quest-next-label">
          {{ tracked.status === "complete" ? "委托已完成" : "接下来" }}
        </p>
        <p class="quest-next">
          {{ node?.title ?? "全部目标已达成，去看看下一段故事吧。" }}
        </p>
        <p v-if="readyCount > 1" class="quest-parallel">
          还有 {{ readyCount - 1 }} 个目标可在日志中查看
        </p>
        <button v-if="node?.activity_id" class="quest-go" @click="openActivity">
          <span>{{
            node.type === "STUDY_LESSON" ? "继续学习" : "开始练习"
          }}</span
          ><GameIcon name="chevron" />
        </button>
        <button
          v-else-if="node?.target"
          class="quest-go"
          @click="$emit('guide', node.target)"
        >
          <span>寻找目标</span><GameIcon name="compass" />
        </button>
        <button
          v-else
          class="quest-go"
          @click="session.open({ kind: 'journal' })"
        >
          查看委托详情<GameIcon name="chevron" />
        </button>
      </div>
    </aside>
    <nav class="hud-dock" aria-label="小镇菜单">
      <button data-world-focus @click="session.open({ kind: 'journal' })">
        <GameIcon name="journal" /><span>任务日志</span><kbd>J</kbd>
      </button>
      <button @click="session.open({ kind: 'library' })">
        <GameIcon name="book" /><span>学习资料</span>
      </button>
      <button @click="session.open({ kind: 'classroom' })">
        <GameIcon name="people" /><span>我的班级</span>
      </button>
      <button aria-label="打开设置" @click="session.open({ kind: 'settings' })">
        <GameIcon name="settings" /><span>设置</span><kbd>Esc</kbd>
      </button>
    </nav>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from "vue";
import { useSession } from "../shared/session";
import GameIcon from "../shared/GameIcon.vue";
defineProps<{ blocked?: boolean }>();
const session = useSession();
const collapsed = ref(false);
defineEmits<{ guide: [key: string] }>();
const tracked = computed(
  () =>
    session.world?.quests.find(
      (q) => q.id === session.trackedQuest && q.status !== "complete",
    ) ??
    session.world?.quests.find(
      (q) => q.kind === "main" && q.status !== "complete",
    ) ??
    session.world?.quests.find((q) => q.status !== "complete") ??
    session.world?.quests.at(-1),
);
const node = computed(() =>
  tracked.value?.nodes.find((n) => n.status === "ready"),
);
const readyCount = computed(
  () => tracked.value?.nodes.filter((n) => n.status === "ready").length ?? 0,
);
const required = computed(
  () =>
    tracked.value?.nodes.filter((n) => !n.optional && n.status !== "skipped") ??
    [],
);
const completed = computed(
  () => required.value.filter((n) => n.status === "complete").length,
);
const total = computed(() => required.value.length);
const place = computed(() =>
  session.world?.player.map_key === "library" ? "图书馆" : "中央小镇",
);
const classroomName = computed(
  () =>
    session.classes.find((c) => c.id === session.classroomId)?.name ??
    "我的班级",
);
function openActivity() {
  if (node.value?.activity_id)
    session.open({
      kind: node.value.type === "STUDY_LESSON" ? "lesson" : "practice",
      activity_id: node.value.activity_id,
    });
}
</script>
