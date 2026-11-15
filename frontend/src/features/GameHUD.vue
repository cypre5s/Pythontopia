<template>
  <div class="world-ui" :inert="blocked" :aria-hidden="blocked || undefined">
    <header class="game-hud">
      <div class="resident-badge">

        <div>
          <strong>{{ session.user?.name }}</strong
          ><small :title="classroomName">{{ classroomName }}</small>
        </div>
      </div>
    </header>
    <aside
      v-if="tracked"
      class="quest-tracker"

      aria-label="当前委托"
    >
<h2 class="quest-heading"><GameIcon name="message" /><span class="quest-title">{{ tracked.title }}</span></h2>
      <div id="tracked-quest-details">

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
          {{ tracked.status === "complete" ? "委托已完成" : "当前目标" }}
        </p>
        <p class="quest-next">
          {{ node ? node.title : "全部目标已达成，去看看下一段故事吧。" }}
        </p>
        <p v-if="readyCount > 1" class="quest-parallel">
          还有 {{ readyCount - 1 }} 个目标可在日志中查看
        </p>
        <button v-if="node?.navigation_target" class="quest-go" @click="$emit('guide', node.navigation_target)">
          <span>立即前往</span><GameIcon name="compass" />
        </button>
        <button v-else-if="node?.activity_id && !tracked?.story_id" class="quest-go" @click="openActivity">
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
          <span>立即前往</span><GameIcon name="compass" />
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
      <button @click="session.open({ kind: 'map' })">
        <GameIcon name="compass" /><span>地图</span>
      </button>
      <button @click="session.open({ kind: 'classroom' })">
        <GameIcon name="people" /><span>我的班级</span>
      </button>
      <button aria-label="打开设置" @click="session.open({ kind: 'settings' })">
        <GameIcon name="settings" /><span>设置</span><kbd>Esc</kbd>
      </button>
      <button v-if="canUseDevTools(session.user)" @click="session.open({ kind: 'developer' })">
        <GameIcon name="settings" /><span>开发工具</span>
      </button>
    </nav>
  </div>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { useSession } from "../shared/session";
import GameIcon from "../shared/GameIcon.vue";
import { requiredQuestNodes } from "../shared/questProgress";
import { canUseDevTools } from "../shared/devTools";
defineProps<{ blocked?: boolean }>();
const session = useSession();

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
const required = computed(() => requiredQuestNodes(tracked.value));
const completed = computed(
  () => required.value.filter((n) => n.status === "complete").length,
);
const total = computed(() => required.value.length);

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

<style scoped src="./world/hud.css"></style>
