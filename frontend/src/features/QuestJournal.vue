<template>
  <section class="journal story-journal">
    <header class="journal-heading">
      <div class="journal-title"><GameIcon name="journal"/><div><h2>任务日志</h2><small>QUEST JOURNAL</small></div></div>
      <div class="journal-filters status-tabs" role="group" aria-label="任务状态">
        <button v-for="item in filters" :key="item.key" :class="{'is-current': filter === item.key}" :aria-pressed="filter === item.key" @click="filter = item.key">{{ item.label }}</button>
      </div>
      <span class="journal-count">{{ activeCount }} 进行中 · {{ completeCount }} 已完成</span>
    </header>
    <div class="journal-book">
      <aside class="journal-index">
        <div class="journal-cover"><div><p>探索 · 学习 · 成长</p><h2>小镇里的<br>未完故事</h2></div><img :src="journalHouse" alt="" /></div>
        <div class="journal-directory">
          <nav class="journal-filters kind-tabs" aria-label="任务类型">
            <button v-for="item in kinds" :key="item.key" :class="{'is-current': kindFilter === item.key}" :aria-pressed="kindFilter === item.key" @click="kindFilter = item.key"><GameIcon :name="item.key === 'all' ? 'grid' : item.key === 'main' ? 'book' : 'message'"/>{{ item.label }}</button>
          </nav>
          <div class="quest-directory">
            <p class="directory-label">{{ filters.find(item => item.key === filter)?.label }} · {{ quests.length }} 段故事</p>
            <button v-for="quest in quests" :key="quest.id" class="quest-card" :class="{'is-current': selectedQuest?.id === quest.id}" :aria-pressed="selectedQuest?.id === quest.id" @click="selectedId = quest.id">
              <small>{{ quest.kind === 'side' ? '支线委托' : '主线委托' }} <span v-if="quest.status === 'complete'">· 已完成</span></small><strong>{{ quest.title }}</strong><GameIcon name="chevron"/>
            </button>
            <p v-if="!quests.length" class="empty-directory">暂无此类委托</p>
          </div>
        </div>
      </aside>
      <article v-if="selectedQuest" :key="selectedQuest.id" class="journal-entry quest-detail">
        <header><div><span class="eyebrow">{{ selectedQuest.kind === 'side' ? '支线委托' : '主线委托' }} · {{ selectedQuest.status === 'complete' ? '已完成' : '进行中' }}</span><h3>{{ selectedQuest.title }}</h3></div></header>
        <section class="journal-objectives"><div><h4><GameIcon name="chart"/>目标进度</h4><span>{{ completedNodes }} / {{ requiredNodes.length }}</span></div><progress :value="completedNodes" :max="Math.max(1, requiredNodes.length)" aria-label="目标进度"/><p>{{ selectedQuest.status === 'complete' ? '这段故事已完成，探索的足迹会留在这里。' : '循着线索，一步步完成这段小镇故事。' }}</p></section>
        <h4 class="checklist-heading"><GameIcon name="journal"/>任务清单</h4>
        <ol>
          <li v-for="(node,index) in selectedQuest.nodes" :key="node.id" :class="node.status">
            <span class="node-icon" aria-hidden="true">{{ node.status === 'complete' ? '✓' : node.status === 'skipped' ? '−' : index+1 }}</span>
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
      <div v-else class="journal-empty"><GameIcon name="book"/><h3>这一页，等待新的故事</h3><p>这里还没有对应的委托。未开放章节会在教师发布后出现。</p></div>
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
const journalHouse = "/assets/pixel/buildings/super-retro-world/house_03.png";
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
const activeCount = computed(
  () =>
    session.world?.quests.filter((q) => q.status !== "complete").length ?? 0,
);
const completeCount = computed(
  () =>
    session.world?.quests.filter((q) => q.status === "complete").length ?? 0,
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
<style scoped>
.story-journal { padding: 8px 8px 12px; color: #513b28; display: flex; flex-direction: column; min-height: 0; height: 100%; }
.journal-heading { margin: 0; padding: 8px 14px 20px; gap: 20px; color: #fff0cc; flex-wrap: wrap; }
.journal-title { display:flex; align-items:center; gap:14px; }
.journal-title > svg { width:42px; height:42px; color:#e6be76; }
.journal-title h2 { margin:0; font-size:1.7rem; color:#fff2d6; }
.journal-title small { font-size:.65rem; letter-spacing:.2em; color:#cfbe98; }
.journal-count { margin-left:auto; font-size:.8rem; color:#dfd0b4; }
.status-tabs { background:transparent; padding:0; border:0; gap:8px; }
.status-tabs button { background:#152e29b0; border:1px solid #b9b48b38; color:#eee2c8; border-radius:24px; padding:10px 24px; min-height:44px; }
.story-journal .status-tabs button.is-current { background:linear-gradient(#fff0b6,#eec66f); color:#62421f; border-color:#ffe9af; box-shadow:0 0 16px #ffc66035,inset 0 0 0 2px #b8792638; }
.journal-book { display:grid; grid-template-columns:minmax(290px,35%) minmax(0,1fr); flex:1; min-height:0; border:7px solid #7a563d; border-radius:20px 26px 24px 16px; background:#f7efdf; box-shadow:0 5px 0 #bc9766,0 10px 0 #503e30,0 22px 45px #051b2580,inset 0 0 0 2px #d7b481; overflow:hidden; }
.journal-index { display:flex; flex-direction:column; min-height:0; background:linear-gradient(90deg,#ecddc3,#f6ecdb 90%,#d7c4a7); border-right:1px solid #bca483; }
.journal-cover { min-height:180px; display:flex; overflow:hidden; align-items:center; justify-content:space-between; padding:24px; background:radial-gradient(ellipse at 85% 20%,#73948c,#284944 72%); color:#fff0ce; border-bottom:5px solid #d6b786; }
.journal-cover h2 { font-family:Georgia,'Noto Serif SC',serif; font-size:clamp(1.6rem,2.5vw,2.25rem); line-height:1.3; color:#fff0ce; margin:0; text-shadow:2px 3px #1c3333; }
.journal-cover p { font-size:.7rem; letter-spacing:.15em; margin:0 0 10px; color:#e0c99b; }
.journal-cover img { width:110px; max-height:150px; object-fit:contain; image-rendering:pixelated; filter:drop-shadow(3px 6px 0 #122d3655); align-self:flex-end; }
.journal-directory { display:grid; grid-template-columns:85px minmax(0,1fr); min-height:0; flex:1; }
.kind-tabs { display:flex; flex-direction:column; justify-content:flex-start; border:0; border-radius:0; background:#6a584c; padding:18px 6px; gap:10px; }
.kind-tabs button { display:flex; flex-direction:column; gap:8px; align-items:center; justify-content:center; min-height:72px; padding:10px 4px; background:#ffffff0b; color:#f3e6cd; border:1px solid #c5ac8850; border-radius:8px; font-size:.8rem; }
.story-journal .kind-tabs button.is-current { background:linear-gradient(#ffefbf,#e8c682); color:#654222; border-color:#ffedbe; box-shadow:0 2px 8px #402d3040; }
.kind-tabs svg { width:22px; height:22px; }
.quest-directory { overflow:auto; padding:20px 14px; }
.directory-label { font-size:.75rem; color:#826b52; margin:0 0 14px; }
.quest-card { position:relative; width:100%; padding:16px 26px 16px 14px; text-align:left; border:1px solid #d8c5a5; border-radius:9px; background:#fffaf080; color:#634b37; margin-bottom:12px; box-shadow:0 3px 0 #b5986e12; }
.story-journal .quest-card.is-current { background:linear-gradient(120deg,#fff3c4,#f9e2a0); border-color:#d4a153; box-shadow:inset 0 0 0 2px #fff8dd,0 3px 10px #a97b3230; }
.quest-card small { display:block; font-size:.65rem; color:#967245; margin-bottom:8px; }
.quest-card strong { font-size:.9rem; line-height:1.7; }
.quest-card svg { position:absolute; right:8px; top:50%; width:16px; color:#b98947; }
.quest-detail { margin:0; padding:28px 30px 20px; border:0; border-radius:0; overflow:auto; min-height:0; display:flex; flex-direction:column; background:linear-gradient(90deg,#bba38230,transparent 4%),radial-gradient(ellipse at top,#fffcf2,#f5ead7); }
.quest-detail header { display:block; margin:0; }
.quest-detail .eyebrow { display:inline-block; background:#94613c; color:#fff2d5; font-size:.7rem; padding:5px 12px; letter-spacing:.05em; border-radius:3px; box-shadow:0 2px 0 #65472e; }
.quest-detail h3 { font-family:Georgia,'Noto Serif SC',serif; font-size:clamp(1.25rem,2vw,1.8rem); line-height:1.5; margin:16px 0 18px; color:#493120; }
.journal-objectives { flex-shrink:0; border-top:1px solid #ceb79470; border-bottom:1px solid #ceb79470; padding:16px 0 12px; margin-bottom:18px; }
.journal-objectives > div { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; }
.quest-detail h4 { display:flex; align-items:center; gap:9px; font-size:.95rem; margin:0; color:#62462e; }
.quest-detail h4 svg { width:20px; height:20px; color:#a17e4d; }
.journal-objectives span { color:#8a7357; font-size:.85rem; }
.journal-objectives progress { display:block; width:100%; height:17px; appearance:none; border:1px solid #b3a387; border-radius:12px; overflow:hidden; background:#e6dcc8; }
.journal-objectives progress::-webkit-progress-bar { background:#e6dcc8; box-shadow:inset 0 2px 3px #6f573326; }
.journal-objectives progress::-webkit-progress-value { background:linear-gradient(#a6c57b,#729956); border-radius:10px; }
.journal-objectives progress::-moz-progress-bar { background:#87a961; }
.journal-objectives p { margin:9px 0 0; font-size:.75rem; color:#998165; }
.quest-detail ol { list-style:none; padding:0; margin:14px 0 24px; display:flex; flex-direction:column; gap:7px; }
.quest-detail li { display:flex; align-items:center; flex-wrap:wrap; gap:10px; padding:11px 12px; border:1px solid #d8cbb54d; border-radius:8px; background:#e9e2d36b; opacity:1; }
.quest-detail li.complete { background:#e5ead4; }
.quest-detail li.ready { background:#fff1c8; border-color:#e9c482; box-shadow:0 2px 5px #caac6520; }
.quest-detail li.locked,.quest-detail li.skipped { color:#8f8372; background:#e7dfd04d; }
.quest-detail li .node-icon { width:24px; height:24px; border-radius:6px; background:#f7f0df; border:1px solid #b8a17b; color:#8e7451; font-size:.75rem; flex-shrink:0; }
.quest-detail li.complete .node-icon { color:#fff; background:#7fa357; border-color:#6c904a; }
.quest-detail li.ready .node-icon { background:#fffaf0; color:#98652f; border-color:#c99b59; }
.quest-detail li .step-icon { width:26px; height:26px; padding:3px; color:#8c7956; flex-shrink:0; }
.quest-detail li > div { flex:1; min-width:100px; }
.quest-detail li strong { color:inherit; font-size:1rem; line-height:1.5; }
.quest-detail li small { color:#8a795f; font-size:.72rem; margin-top:3px; }
.quest-detail li > button { display:flex; align-items:center; gap:4px; margin:0; border:0; background:#e8c88b6b; color:#82572b; padding:8px; font-size:.75rem; min-height:36px; }
.quest-detail li > button svg { width:14px; }
.step-done { font-size:.7rem; color:#779154; }
.quest-detail-footer { display:flex; gap:14px; align-items:center; justify-content:space-between; margin-top:auto; padding-top:18px; border-top:1px solid #d2bda080; }
.quest-detail-footer > span { font-size:.7rem; color:#9c8567; }
.track-quest { display:flex; align-items:center; gap:8px; background:linear-gradient(#fff0b8,#e8bd68); color:#694521; border:1px solid #c69a51; border-radius:24px; padding:12px 22px; min-height:44px; white-space:nowrap; box-shadow:inset 0 0 0 3px #fff4c3,0 3px 0 #ac7d3c40; }
.track-quest svg { width:20px; height:20px; }
.journal-empty { padding:60px 30px; text-align:center; align-self:center; color:#927c60; }
.journal-empty > svg { width:48px; height:48px; }
.empty-directory { color:#9c886b; font-size:.8rem; }
:global(.workspace-shell.workspace-journal) { background:transparent; border:0; box-shadow:none; width:min(1400px,100%); }
:global(.workspace-journal .workspace-topbar) { background:transparent; border:0; color:#f9ecd0; padding-top:0; min-height:48px; }
:global(.workspace-journal .workspace-heading > span), :global(.workspace-journal .workspace-heading small) { display:none; }
:global(.workspace-journal .workspace-topbar > button) { color:#f5e7ce; background:#203b32d9; border-color:#a9987155; border-radius:22px; }
:global(.workspace-journal .workspace-topbar > button:hover) { color:#fff2d6; background:#355646; }
@media(max-width:1000px) { .journal-book { grid-template-columns:minmax(250px,34%) minmax(0,1fr); } .journal-cover { padding:18px; }.journal-cover img { width:70px; }.quest-detail { padding:22px 18px; }.journal-directory { grid-template-columns:64px minmax(0,1fr); }.quest-directory { padding:18px 10px; }.journal-count { display:none; } }
@media(max-width:700px) { .story-journal { height:auto; min-height:100%; padding:0 3px 12px; }.journal-heading { padding:8px 4px 16px; gap:14px; }.journal-title h2 { font-size:1.4rem; }.status-tabs { width:100%; }.status-tabs button { flex:1; padding:8px 12px; }.journal-book { display:block; overflow:visible; border-width:4px; }.journal-index { border:0; }.journal-cover { min-height:125px; }.journal-cover h2 { font-size:1.5rem; }.journal-cover img { width:85px; max-height:100px; }.journal-directory { display:block; }.kind-tabs { flex-direction:row; padding:8px; }.kind-tabs button { flex:1; flex-direction:row; min-height:44px; }.kind-tabs svg { width:16px; }.quest-directory { max-height:220px; padding:12px; }.quest-card { padding:10px 24px 10px 12px; margin-bottom:8px; }.quest-card small { margin-bottom:3px; }.index-footer { display:none; }.quest-detail { border-top:3px double #c6ad85; padding:22px 16px; overflow:visible; }.quest-detail li { gap:8px; }.step-icon,.step-done { display:none; }.quest-detail li > button { width:auto; }.quest-detail-footer { flex-wrap:wrap; }.journal-title > svg { width:32px; height:32px; } }
</style>
