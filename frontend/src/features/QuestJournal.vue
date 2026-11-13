<template>
  <section class="journal story-journal">
    <div class="journal-book">
      <aside class="journal-index">
        <div class="journal-cover"><h3 class="game-card-title"><GameIcon name="journal"/>委托目录</h3></div>
        <div class="journal-directory">
          <div class="journal-filter-bar">
            <div class="journal-filters status-tabs game-segmented" role="group" aria-label="任务状态">
              <button v-for="item in filters" :key="item.key" :class="{'is-current': filter === item.key}" :aria-pressed="filter === item.key" @click="filter = item.key">{{ item.label }}</button>
            </div>
            <label class="journal-kind-select"><span class="journal-sr-only">任务类型</span><select v-model="kindFilter"><option v-for="item in kinds" :key="item.key" :value="item.key">{{ item.label }}</option></select></label>
          </div>
          <div class="quest-directory" :class="{ 'is-empty': !quests.length }">
            <button v-for="quest in quests" :key="quest.id" class="quest-card" :class="{'is-current': selectedQuest?.id === quest.id}" :aria-pressed="selectedQuest?.id === quest.id" @click="selectedId = quest.id">
              <small>{{ quest.kind === 'side' ? '支线委托' : '主线委托' }} <span v-if="quest.status === 'complete'">· 已完成</span></small><strong>{{ quest.title }}</strong><GameIcon name="chevron"/>
            </button>
            <p v-if="!quests.length" class="empty-directory">没有委托</p>
          </div>
        </div>
      </aside>
      <article v-if="selectedQuest" :key="selectedQuest.id" class="journal-entry quest-detail">
        <header><div><span class="eyebrow">{{ selectedQuest.kind === 'side' ? '支线委托' : '主线委托' }} · {{ selectedQuest.status === 'complete' ? '已完成' : '进行中' }}</span><h3>{{ selectedQuest.title }}</h3></div></header>
        <section class="journal-objectives"><div><h4 class="game-card-title"><GameIcon name="chart"/>目标进度</h4><span>{{ completedNodes }} / {{ requiredNodes.length }}</span></div><progress :value="completedNodes" :max="Math.max(1, requiredNodes.length)" aria-label="目标进度"/><p>{{ selectedQuest.status === 'complete' ? '这段故事已完成，探索的足迹会留在这里。' : '循着线索，一步步完成这段小镇故事。' }}</p></section>
        <h4 class="checklist-heading game-card-title"><GameIcon name="journal"/>任务清单</h4>
        <ol>
          <li v-for="(node,index) in selectedQuest.nodes" :key="node.id" :class="node.status">
            <span class="node-icon" aria-hidden="true"><GameIcon v-if="node.status === 'complete'" name="check" /><template v-else>{{ node.status === 'skipped' ? '−' : index+1 }}</template></span>
            <GameIcon class="step-icon" :name="node.type === 'STUDY_LESSON' ? 'book' : node.type === 'TALK_TO_NPC' || node.type === 'PLAY_DIALOGUE' ? 'message' : 'compass'"/>
            <div><strong>{{ displayQuestTitle(node) }}</strong><small>{{ stateName[node.status] }}{{ node.optional ? ' · 可选' : '' }}</small></div>
            <button v-if="node.status === 'ready' && node.navigation_target" @click="session.guideTo(node.navigation_target)">立即前往 <GameIcon name="compass"/></button>
            <button v-else-if="node.status === 'ready' && node.activity_id && !selectedQuest.story_id" @click="session.navigate({kind:node.type === 'STUDY_LESSON' ? 'lesson' : 'practice', activity_id:node.activity_id})">{{ node.type === 'STUDY_LESSON' ? '打开课程' : '打开练习' }} <GameIcon name="chevron"/></button>
            <span v-if="node.status === 'complete'" class="step-done">已完成</span>
          </li>
        </ol>
        <footer class="quest-detail-footer"><span>{{ selectedQuest.status === 'complete' ? '故事已收录' : session.trackedQuest === selectedQuest.id ? '此委托已显示在小镇任务栏' : '追踪委托，在小镇查看当前目标' }}</span><button v-if="selectedQuest.status !== 'complete'" class="track-quest primary" :aria-pressed="session.trackedQuest === selectedQuest.id" @click="session.trackedQuest=selectedQuest.id; notify('已追踪此委托')"><GameIcon name="compass"/>{{ session.trackedQuest === selectedQuest.id ? '正在追踪' : '追踪任务' }}</button></footer>
      </article>
      <div v-else class="journal-empty"><p>没有委托</p></div>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from "vue";
import { useSession } from "../shared/session";
import { notify } from "../shared/api";
import GameIcon from "../shared/GameIcon.vue";
import { displayQuestTitle } from "../game/config/worldConfig";
import { requiredQuestNodes } from "../shared/questProgress";
const selectedId = ref("");
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
const selectedQuest = computed(() => quests.value.find(q => q.id === selectedId.value) ?? quests.value.find(q => q.id === session.trackedQuest) ?? quests.value[0]);
const requiredNodes = computed(() => requiredQuestNodes(selectedQuest.value));
const completedNodes = computed(() => requiredNodes.value.filter(n => n.status === "complete").length);
</script>
<style scoped>
.story-journal { display:flex; flex:1; min-width:0; min-height:0; color:var(--game-ink); overflow:hidden; }
.journal-book { display:grid; grid-template-columns:minmax(260px,28%) minmax(0,1fr); gap:var(--game-gap); flex:1; min-width:0; min-height:0; overflow:hidden; }
.journal-index,.quest-detail,.journal-empty { border:1px solid var(--game-line); border-radius:var(--game-radius); background:var(--game-paper); }
.journal-index { display:flex; flex-direction:column; min-width:0; min-height:0; overflow:hidden; }
.journal-cover { display:flex; flex-shrink:0; align-items:center; padding:8px 12px; border-bottom:1px solid var(--game-line); background:#e9e5da; }
.journal-directory { display:flex; flex-direction:column; min-height:0; flex:1; }
.journal-filter-bar { display:flex; flex-shrink:0; flex-wrap:wrap; align-items:center; gap:8px; padding:10px; border-bottom:1px solid var(--game-line); }
.status-tabs { flex:1 1 14rem; min-width:0; max-width:100%; }
.status-tabs > button { flex:1 1 auto; min-width:0; padding-inline:8px; white-space:nowrap; }
.journal-kind-select { display:block; flex:1; min-width:6rem; max-width:100%; margin:0; }
.journal-kind-select select { width:100%; }
.journal-sr-only { position:absolute; width:1px; height:1px; margin:-1px; padding:0; overflow:hidden; clip-path:inset(50%); white-space:nowrap; border:0; }
.quest-directory { flex:1; min-height:0; overflow:auto; overscroll-behavior:contain; padding:10px; }
.quest-directory.is-empty { display:grid; place-items:center; }
.quest-card { position:relative; width:100%; padding:10px 28px 10px 12px; margin-bottom:8px; text-align:left; border:1px solid var(--game-line); border-radius:var(--game-control-radius); background:var(--game-paper-raised); color:var(--game-ink); }
.quest-card:hover { background:#eee9dd; }.quest-card.is-current { background:#e8eddf; border-color:#b8c7ab; }
.quest-card small { display:block; margin-bottom:4px; font-size:var(--game-caption-size); color:var(--game-muted); }
.quest-card strong { display:block; font-size:var(--game-label-size); line-height:1.5; overflow-wrap:anywhere; }
.quest-card svg { position:absolute; right:8px; top:calc(50% - 8px); width:16px; height:16px; }
.quest-detail { display:flex; flex-direction:column; min-width:0; min-height:0; margin:0; padding:var(--game-panel-padding); overflow:auto; overscroll-behavior:contain; }
.quest-detail > * { flex-shrink:0; }.quest-detail header { display:block; margin:0; }
.quest-detail .eyebrow { display:inline-block; color:var(--game-muted); font-size:var(--game-caption-size); letter-spacing:0; }
.quest-detail h3 { margin:8px 0 16px; color:var(--game-forest); font-size:1.25rem; line-height:1.5; overflow-wrap:anywhere; }
.journal-objectives { padding:12px 0; margin-bottom:16px; border-top:1px solid var(--game-line); border-bottom:1px solid var(--game-line); }
.journal-objectives > div { display:flex; justify-content:space-between; align-items:center; gap:10px; margin-bottom:8px; }
.journal-objectives span { color:var(--game-muted); font-size:var(--game-caption-size); }
.journal-objectives progress { display:block; width:100%; height:8px; appearance:none; border:0; border-radius:4px; overflow:hidden; background:var(--game-line); }
.journal-objectives progress::-webkit-progress-bar { background:var(--game-line); }.journal-objectives progress::-webkit-progress-value { background:var(--game-forest); }.journal-objectives progress::-moz-progress-bar { background:var(--game-forest); }
.journal-objectives p { margin:8px 0 0; color:var(--game-muted); font-size:var(--game-caption-size); line-height:1.6; }
.quest-detail ol { display:flex; flex-direction:column; gap:8px; list-style:none; padding:0; margin:12px 0 16px; }
.quest-detail li { display:flex; align-items:center; flex-wrap:wrap; gap:8px; padding:10px; border:1px solid var(--game-line); border-radius:var(--game-control-radius); background:#eee9dd; }
.quest-detail li.complete { background:#e8eddf; border-color:#c5d0bb; }.quest-detail li.ready { background:#f9efd6; border-color:#cbb47d; }
.quest-detail li.locked,.quest-detail li.skipped { color:var(--game-muted); }
.node-icon { display:grid; place-items:center; width:24px; height:24px; flex-shrink:0; border:1px solid var(--game-line); border-radius:6px; background:var(--game-paper-raised); font-size:var(--game-caption-size); }
.node-icon .game-icon { width:16px; height:16px; }.complete .node-icon { color:white; background:var(--game-forest); }
.step-icon { width:18px; height:18px; flex-shrink:0; color:var(--game-muted); }
.quest-detail li > div { flex:1; min-width:100px; overflow-wrap:anywhere; }
.quest-detail li strong { display:block; font-size:var(--game-body-size); line-height:1.5; }
.quest-detail li small { display:block; margin-top:3px; color:var(--game-muted); font-size:var(--game-caption-size); line-height:1.5; }
.quest-detail li > button,.track-quest { display:flex; align-items:center; justify-content:center; gap:6px; margin:0; }
.step-done { font-size:var(--game-caption-size); color:var(--game-forest); }
.quest-detail-footer { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:10px; margin-top:auto; padding-top:12px; border-top:1px solid var(--game-line); }
.quest-detail-footer > span { color:var(--game-muted); font-size:var(--game-caption-size); line-height:1.6; }
.journal-empty { display:grid; place-items:center; min-width:0; min-height:0; padding:24px; overflow:auto; }
.journal-empty p,.empty-directory { margin:0; color:var(--game-muted); font-size:var(--game-body-size); line-height:1.7; text-align:center; }
@media(max-width:760px) { .journal-book { grid-template-columns:minmax(0,1fr); grid-template-rows:minmax(190px,35%) minmax(0,1fr); }.quest-detail { padding:12px; }.quest-detail li .step-icon { display:none; }.quest-detail li > button { width:100%; }.step-done { margin-left:auto; } }
</style>
