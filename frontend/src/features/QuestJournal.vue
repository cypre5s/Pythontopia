<template>
  <section class="journal story-journal">
    <div class="journal-book">
      <aside class="journal-index">
        <div class="journal-cover"><h3>委托目录</h3></div>
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
        <section class="journal-objectives"><div><h4><GameIcon name="chart"/>目标进度</h4><span>{{ completedNodes }} / {{ requiredNodes.length }}</span></div><progress :value="completedNodes" :max="Math.max(1, requiredNodes.length)" aria-label="目标进度"/><p>{{ selectedQuest.status === 'complete' ? '这段故事已完成，探索的足迹会留在这里。' : '循着线索，一步步完成这段小镇故事。' }}</p></section>
        <h4 class="checklist-heading"><GameIcon name="journal"/>任务清单</h4>
        <ol>
          <li v-for="(node,index) in selectedQuest.nodes" :key="node.id" :class="node.status">
            <span class="node-icon" aria-hidden="true"><GameIcon v-if="node.status === 'complete'" name="check" /><template v-else>{{ node.status === 'skipped' ? '−' : index+1 }}</template></span>
            <GameIcon class="step-icon" :name="node.type === 'STUDY_LESSON' ? 'book' : node.type === 'TALK_TO_NPC' || node.type === 'PLAY_DIALOGUE' ? 'message' : 'compass'"/>
            <div><strong>{{ displayQuestTitle(node) }}</strong><small>{{ stateName[node.status] }}{{ node.optional ? ' · 可选' : '' }}</small></div>
            <button v-if="node.status === 'ready' && node.navigation_target" @click="session.guideTo(node.navigation_target)">立即前往 <GameIcon name="compass"/></button>
            <button v-else-if="node.status === 'ready' && node.activity_id && !selectedQuest.story_id" @click="session.navigate({kind:node.type === 'STUDY_LESSON' ? 'lesson' : 'practice', activity_id:node.activity_id})">{{ node.type === 'STUDY_LESSON' ? '打开课程' : '打开练习' }} <GameIcon name="chevron"/></button>
            <template v-if="!selectedQuest.story_id && node.status === 'ready' && node.choices?.length"><button v-for="choice in node.choices" :key="choice.id" @click="choose(selectedQuest,node,choice.id)">{{ choice.label ?? choice.text }}</button></template>
            <span v-if="node.status === 'complete'" class="step-done">已完成</span>
          </li>
        </ol>
        <footer class="quest-detail-footer"><span>{{ selectedQuest.status === 'complete' ? '故事已收录' : session.trackedQuest === selectedQuest.id ? '此委托已显示在小镇任务栏' : '追踪委托，在小镇查看当前目标' }}</span><button v-if="selectedQuest.status !== 'complete'" class="track-quest" :aria-pressed="session.trackedQuest === selectedQuest.id" @click="session.trackedQuest=selectedQuest.id; notify('已追踪此委托')"><GameIcon name="compass"/>{{ session.trackedQuest === selectedQuest.id ? '正在追踪' : '追踪任务' }}</button></footer>
      </article>
      <div v-else class="journal-empty"><p>没有委托</p></div>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from "vue";
import { useSession } from "../shared/session";
import { notify } from "../shared/api";
import type { Quest, QuestNode } from "../shared/types";
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
<style scoped>
.story-journal { display:flex; flex:1; flex-direction:column; min-width:0; min-height:0; height:100%; padding:0; color:#513b28; overflow:hidden; }
.journal-book { display:grid; grid-template-columns:minmax(300px,30%) minmax(0,1fr); gap:22px; flex:1; min-width:0; min-height:0; border:0; border-radius:0; background:transparent; box-shadow:none; overflow:hidden; }
.journal-index,.quest-detail,.journal-empty { border:1px solid var(--game-line,#d7c7a9); border-radius:var(--game-radius,16px); }
.quest-detail,.journal-empty { background:var(--game-paper,#fbf5e9); box-shadow:inset 0 1px 0 #fffaf0; }
.journal-index { display:flex; flex-direction:column; min-width:0; min-height:0; overflow:hidden; background:transparent; }
.journal-cover { display:flex; flex-shrink:0; align-items:center; min-height:96px; padding:22px 24px; color:var(--game-forest,#284b43); background:linear-gradient(90deg,var(--game-paper,#fbf5e9) 42%,#fbf5e9e6 62%,#fbf5e940 84%,transparent); }
.journal-cover h3 { margin:0; font-size:1.45rem; font-weight:700; line-height:1.5; }
.journal-directory { display:flex; flex-direction:column; min-height:0; flex:1; background:var(--game-paper,#fbf5e9); }
.journal-filter-bar { display:flex; flex-shrink:0; flex-wrap:wrap; align-items:center; gap:12px; padding:16px 18px; border-bottom:1px solid var(--game-line,#d7c7a9); }
.status-tabs { flex:1 1 14rem; min-width:0; max-width:100%; }
.status-tabs > button { flex:1 1 auto; min-width:0; font-size:.9375rem; padding-inline:.65rem; white-space:nowrap; }
.journal-kind-select { display:block; flex:0 1 8rem; min-width:7rem; max-width:100%; margin:0; }
.journal-kind-select select { width:100%; font-size:.9375rem; }
.journal-sr-only { position:absolute; width:1px; height:1px; margin:-1px; padding:0; overflow:hidden; clip-path:inset(50%); white-space:nowrap; border:0; }
.quest-directory { flex:1; min-height:0; overflow:auto; overscroll-behavior:contain; scrollbar-gutter:stable; padding:18px; }
.quest-directory.is-empty { display:grid; place-items:center; }
.quest-card { position:relative; width:100%; min-height:88px; padding:18px 30px 18px 16px; margin-bottom:12px; text-align:left; border:1px solid var(--game-line,#d7c7a9); border-radius:11px; background:#fffaf0; color:#634b37; box-shadow:none; }
.quest-card:hover { background:#f7edda; border-color:#bca077; }
.story-journal .quest-card.is-current { background:linear-gradient(120deg,#fff3cc,#f5e1ad); border-color:#caa15c; box-shadow:none; }
.quest-card small { display:block; margin-bottom:8px; font-size:.875rem; color:#82613b; }
.quest-card strong { display:block; font-size:1rem; line-height:1.7; overflow-wrap:anywhere; }
.quest-card svg { position:absolute; right:10px; top:calc(50% - 9px); width:18px; height:18px; color:#9f733a; }
.quest-detail { display:flex; flex-direction:column; min-width:0; min-height:0; margin:0; padding:30px 34px 24px; overflow:auto; overscroll-behavior:contain; scrollbar-gutter:stable; background:radial-gradient(ellipse at top,#fffcf2,var(--game-paper,#fbf5e9)); }
.quest-detail > * { flex-shrink:0; }
.quest-detail header { display:block; margin:0; }
.quest-detail .eyebrow { display:inline-block; padding:6px 12px; border:1px solid #d9c6a4; border-radius:7px; background:#eee2cc; color:#735233; font-size:.875rem; letter-spacing:0; box-shadow:none; }
.quest-detail h3 { margin:18px 0 22px; color:var(--game-forest,#284b43); font-size:clamp(1.35rem,2vw,1.8rem); line-height:1.5; overflow-wrap:anywhere; }
.journal-objectives { padding:20px 0 16px; margin-bottom:22px; border-top:1px solid var(--game-line,#d7c7a9); border-bottom:1px solid var(--game-line,#d7c7a9); }
.journal-objectives > div { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-bottom:14px; }
.quest-detail h4 { display:flex; align-items:center; gap:9px; margin:0; color:#624b32; font-size:1rem; }
.quest-detail h4 svg { width:22px; height:22px; color:#967443; }
.journal-objectives span { color:#796249; font-size:.95rem; }
.journal-objectives progress { display:block; width:100%; height:16px; appearance:none; border:1px solid #b9a989; border-radius:12px; overflow:hidden; background:#e6dcc8; }
.journal-objectives progress::-webkit-progress-bar { background:#e6dcc8; box-shadow:none; }
.journal-objectives progress::-webkit-progress-value { background:linear-gradient(90deg,#719457,#a2bf7b); border-radius:10px; }
.journal-objectives progress::-moz-progress-bar { background:#87a961; }
.journal-objectives p { margin:12px 0 0; color:#7f6d53; font-size:.875rem; line-height:1.7; }
.quest-detail ol { display:flex; flex-direction:column; gap:10px; list-style:none; padding:0; margin:18px 0 26px; }
.quest-detail li { display:flex; align-items:center; flex-wrap:wrap; gap:12px; padding:14px; border:1px solid #d8cbb58c; border-radius:10px; background:#ede7da; opacity:1; }
.quest-detail li.complete { background:#e6ecd9; border-color:#ced7b6; }
.quest-detail li.ready { background:#fff0c6; border-color:#d8b56e; box-shadow:none; }
.quest-detail li.locked,.quest-detail li.skipped { color:#7f7566; background:#eee8dc; }
.quest-detail li .node-icon { display:grid; place-items:center; width:28px; height:28px; flex-shrink:0; border:1px solid #baa786; border-radius:7px; background:#f9f2e4; color:#7c6546; font-size:.875rem; }
.quest-detail li.complete .node-icon { color:#fff; background:#74924f; border-color:#668343; }
.quest-detail li.ready .node-icon { color:#87602d; background:#fffaf0; border-color:#c49a56; }
.quest-detail li .step-icon { width:26px; height:26px; flex-shrink:0; padding:2px; color:#8a7350; }
.quest-detail li > div { flex:1; min-width:130px; overflow-wrap:anywhere; }
.quest-detail li strong { display:block; color:inherit; font-size:1rem; line-height:1.6; }
.quest-detail li small { display:block; margin-top:4px; color:#7e6c54; font-size:.875rem; line-height:1.5; }
.quest-detail li > button { display:flex; align-items:center; justify-content:center; gap:6px; min-height:44px; margin:0; padding:10px 13px; border:1px solid #d2b17b; border-radius:8px; background:#f7dfaa; color:#755025; font-size:.875rem; }
.quest-detail li > button:hover { background:#eace91; border-color:#b18b4d; }
.quest-detail li > button svg { width:18px; height:18px; flex-shrink:0; }
.step-done { font-size:.875rem; color:#617c3d; }
.quest-detail-footer { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:16px; margin-top:auto; padding-top:22px; border-top:1px solid var(--game-line,#d7c7a9); }
.quest-detail-footer > span { color:#7d694f; font-size:.875rem; line-height:1.7; }
.track-quest { display:flex; align-items:center; justify-content:center; gap:9px; min-height:46px; padding:11px 22px; border:1px solid #bf9656; border-radius:24px; background:linear-gradient(#fff0b8,#e8bd68); color:#694521; font-size:1rem; white-space:nowrap; box-shadow:none; }
.track-quest:hover { background:#f2d28c; }
.track-quest svg { width:21px; height:21px; }
.journal-empty { display:flex; flex-direction:column; align-items:center; justify-content:center; gap:14px; min-width:0; min-height:0; padding:40px 28px; overflow:auto; text-align:center; color:#7d694f; }
.journal-empty p { margin:0; font-size:1.25rem; line-height:1.8; color:var(--game-muted,#7d694f); }
.empty-directory { margin:0; color:var(--game-muted,#7d694f); font-size:1.125rem; line-height:1.7; text-align:center; }
@media(max-width:1100px) { .journal-book { grid-template-columns:minmax(260px,32%) minmax(0,1fr); }.quest-detail { padding:26px 22px; }.journal-cover { padding:20px 18px; }.journal-filter-bar { padding:14px 12px; gap:10px; }.quest-directory { padding:18px 14px; } }
@media(max-width:760px) { .journal-book { grid-template-columns:minmax(0,1fr); grid-template-rows:minmax(220px,38%) minmax(0,1fr); gap:16px; }.journal-index { min-height:0; }.journal-cover { min-height:0; padding:12px 18px; }.journal-cover h3 { font-size:1.25rem; }.journal-filter-bar { padding:10px 12px; gap:8px; }.status-tabs { flex-basis:12rem; }.journal-kind-select { flex-basis:7rem; min-width:6rem; }.quest-directory { padding:12px; }.quest-card { min-height:72px; padding:12px 28px 12px 14px; margin-bottom:8px; }.quest-card small { margin-bottom:4px; }.quest-detail { padding:22px 18px; }.quest-detail li { gap:9px; padding:12px; }.quest-detail li .step-icon { display:none; }.quest-detail li > div { min-width:110px; }.quest-detail li > button { width:100%; }.step-done { margin-left:auto; }.quest-detail-footer { gap:12px; }.journal-empty { padding:24px 18px; } }
</style>
