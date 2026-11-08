<template>
  <aside v-bind="attrs" v-show="!sidebarCollapsed" :hidden="sidebarCollapsed || attributeEnabled(attrs.hidden)" :inert="sidebarCollapsed || attributeEnabled(attrs.inert)" class="learning-support" aria-label="学习目标与助教">
    <div class="support-heading">
    <div class="support-tabs" :style="{ '--support-tab-count': tabs.length }" role="tablist" aria-label="学习支持" @keydown="moveTab">
      <button :id="`${panelId}-goals-tab`" ref="goalsTab" role="tab" :aria-selected="tab === 'goals'" :tabindex="tab === 'goals' ? 0 : -1" :aria-controls="`${panelId}-goals`" @click="tab = 'goals'">学习目标</button>
      <button v-if="outline?.length" :id="`${panelId}-outline-tab`" ref="outlineTab" role="tab" :aria-selected="tab === 'outline'" :tabindex="tab === 'outline' ? 0 : -1" :aria-controls="`${panelId}-outline`" @click="tab = 'outline'">文档目录</button>
      <button v-if="questionOutline?.length" :id="`${panelId}-questions-tab`" ref="questionsTab" role="tab" :aria-selected="tab === 'questions'" :tabindex="tab === 'questions' ? 0 : -1" :aria-controls="`${panelId}-questions`" @click="tab = 'questions'">题目目录</button>
      <button :id="`${panelId}-assistant-tab`" ref="assistantTab" role="tab" :aria-selected="tab === 'assistant'" :tabindex="tab === 'assistant' ? 0 : -1" :aria-controls="`${panelId}-assistant`" @click="tab = 'assistant'">{{ roleName }}</button>
    </div>
    <SidebarToggle />
    </div>
    <section :id="`${panelId}-goals`" class="support-goals" role="tabpanel" :aria-labelledby="`${panelId}-goals-tab`" tabindex="0" :hidden="tab !== 'goals'" :inert="tab !== 'goals'">
      <p v-if="!goals.length" class="support-goals-empty">按照当前资料或题目，完成本次学习。</p>
      <nav v-else :aria-label="goals.some(goal => goal.pageNo) ? '本章阅读目标' : '本次学习目标'">
      <ol class="support-goal-list">
        <li v-for="(goal, index) in goals" :key="`${index}-${goal.title}`" :class="{ 'is-complete': goal.completed }">
          <span class="support-goal-marker" aria-hidden="true"><GameIcon v-if="goal.completed" name="check" /><template v-else>{{ index + 1 }}</template></span>
          <div>
            <strong>{{ goal.title }}</strong>
            <p v-if="goal.detail">{{ goal.detail }}</p>
            <div v-if="goal.progress" class="support-goal-progress">
              <span>{{ goal.progress.label }}</span>
              <progress :value="goal.progress.value" :max="Math.max(1, goal.progress.max)" :aria-label="goal.progress.label" />
            </div>
            <span v-if="goal.completed" class="support-goal-state">已完成</span>
            <button v-if="goal.pageNo" class="support-goal-page" @click="$emit('selectPage', goal.pageNo)"><GameIcon v-if="goal.completed" name="check" />阅读第{{ goal.pageNo }}页 <GameIcon name="arrowRight" /></button>
            <nav v-if="goal.items?.length" :aria-label="goal.items.some(item => item.pageNo) ? '本章阅读目标' : '练习题目'">
              <ol class="support-goal-items">
                <li v-for="(item, itemIndex) in goal.items" :key="`${itemIndex}-${item.title}`" :class="{ 'is-complete': item.completed }">
                  <span class="support-goal-item-state" aria-hidden="true"><GameIcon v-if="item.completed" name="check" /><template v-else>{{ itemIndex + 1 }}</template></span>
                  <div>
                    <span>{{ item.title }}</span>
                    <button v-if="item.pageNo" class="support-goal-page" :aria-current="pageNo === item.pageNo ? 'page' : undefined" @click="$emit('selectPage', item.pageNo)"><GameIcon v-if="item.completed" name="check" />阅读第{{ item.pageNo }}页 <GameIcon name="arrowRight" /></button>
                    <button v-else-if="item.timeSeconds !== undefined" class="support-goal-page" @click="$emit('selectTime', item.timeSeconds)">查看片段 <GameIcon name="arrowRight" /></button>
                    <span v-else-if="item.completed" class="support-goal-item-complete">已完成</span>
                  </div>
                </li>
              </ol>
            </nav>
          </div>
        </li>
      </ol>
      </nav>
      <slot name="goals-footer" />
    </section>
    <section v-if="outline?.length" :id="`${panelId}-outline`" class="support-outline" role="tabpanel" :aria-labelledby="`${panelId}-outline-tab`" tabindex="0" :hidden="tab !== 'outline'" :inert="tab !== 'outline'">
      <nav aria-label="文档页面目录">
        <ol class="support-outline-list">
          <li v-for="item in outline" :key="item.pageNo">
            <button :aria-current="pageNo === item.pageNo ? 'page' : undefined" @click="$emit('selectPage', item.pageNo)">
              <span class="support-outline-page">{{ item.pageNo }}</span>
              <span class="support-outline-title">{{ item.title || `第 ${item.pageNo} 页` }}<span v-if="item.important" class="support-outline-important">{{ item.highlightLabel || "重点" }}</span></span>
            </button>
          </li>
        </ol>
      </nav>
    </section>
    <section v-if="questionOutline?.length" :id="`${panelId}-questions`" class="support-questions" role="tabpanel" :aria-labelledby="`${panelId}-questions-tab`" :hidden="tab !== 'questions'" :inert="tab !== 'questions'">
      <div v-if="hasQuestionActions()" class="support-question-actions"><slot name="question-actions" /></div>
      <nav class="support-question-navigation" aria-label="题目目录">
        <ol class="support-outline-list support-question-list">
          <li v-for="(question, index) in questionOutline" :key="question.id">
            <button :aria-current="question.current || currentQuestionId === question.id ? 'step' : undefined" @click="$emit('selectQuestion', question.id)">
              <span class="support-outline-page">{{ question.index ?? index + 1 }}</span>
              <span class="support-outline-title">{{ question.title }}</span>
              <span class="support-question-state" :data-state="question.state ?? 'unanswered'"><GameIcon v-if="question.state === 'accepted'" name="check" /><GameIcon v-else-if="question.state === 'wrong'" name="circleX" /><GameIcon v-else-if="question.state === 'pending'" name="clock" />{{ questionStateLabel[question.state ?? 'unanswered'] }}</span>
            </button>
          </li>
        </ol>
      </nav>
    </section>
    <section :id="`${panelId}-assistant`" class="support-chat" role="tabpanel" :aria-labelledby="`${panelId}-assistant-tab`" :hidden="tab !== 'assistant'" :inert="tab !== 'assistant'">
      <AssistantPanel v-if="activityId" :activity-id="activityId" :allowed="allowed" :role-name="roleName" :portrait-url="portraitUrl" :page-no="pageNo" :video-time="videoTime" :code="code" @cite="(materialId, page) => $emit('cite', materialId, page)" />
      <div v-else class="support-unlinked">
        <img v-if="portraitUrl" class="assistant-portrait" :src="portraitUrl" alt="" aria-hidden="true" />
        <p>当前资料未关联助教活动，可继续阅读。</p>
      </div>
    </section>
  </aside>
</template>
<script setup lang="ts">
import { Comment, Fragment, Text, computed, isVNode, nextTick, ref, useAttrs, useSlots, watch, type VNodeChild } from "vue";
import AssistantPanel from "./AssistantPanel.vue";
import GameIcon from "../shared/GameIcon.vue";
import SidebarToggle from "../shared/SidebarToggle.vue";
import { useWorkspaceSidebar } from "../shared/workspaceSidebar";
import "./assistantUi.css";
defineOptions({ inheritAttrs: false });
const attrs = useAttrs();
const slots = useSlots();
function attributeEnabled(value: unknown) { return value !== undefined && value !== null && value !== false; }
function containsContent(nodes: VNodeChild[]): boolean {
  return nodes.some(node => {
    if (Array.isArray(node)) return containsContent(node);
    if (!isVNode(node)) return typeof node === "number" || typeof node === "string" && node.trim().length > 0;
    if (node.type === Comment) return false;
    if (node.type === Text) return typeof node.children === "string" && node.children.trim().length > 0;
    if (node.type === Fragment) return Array.isArray(node.children) && containsContent(node.children as VNodeChild[]);
    return true;
  });
}
function hasQuestionActions() { return containsContent(slots["question-actions"]?.({}) ?? []); }

const props = defineProps<{
  activityId?: string;
  allowed: boolean;
  roleName: string;
  portraitUrl?: string;
  goals: {
    title: string;
    detail?: string;
    pageNo?: number;
    completed?: boolean;
    progress?: { value: number; max: number; label: string };
    items?: { title: string; pageNo?: number; timeSeconds?: number; completed?: boolean }[];
  }[];
  outline?: { pageNo: number; title: string; important?: boolean; highlightLabel?: string }[];
  questionOutline?: { id: string; index?: number; title: string; state?: "unanswered" | "answered" | "accepted" | "wrong" | "pending"; current?: boolean }[];
  currentQuestionId?: string;
  pageNo?: number;
  videoTime?: number;
  code?: string;
}>();
defineEmits<{ cite: [materialId: number, page: number]; selectPage: [page: number]; selectTime: [seconds: number]; selectQuestion: [questionId: string] }>();
const { collapsed: sidebarCollapsed } = useWorkspaceSidebar();
const tab = ref<"goals" | "outline" | "questions" | "assistant">("goals"),
  goalsTab = ref<HTMLButtonElement>(), outlineTab = ref<HTMLButtonElement>(), questionsTab = ref<HTMLButtonElement>(), assistantTab = ref<HTMLButtonElement>();
const questionStateLabel = { unanswered: "未作答", answered: "已作答", accepted: "正确", wrong: "错误", pending: "判定中" };
const panelId = computed(() => `learning-support-${props.activityId || 'material'}`);
const tabs = computed(() => ["goals", ...(props.outline?.length ? ["outline"] : []), ...(props.questionOutline?.length ? ["questions"] : []), "assistant"]);
watch(() => props.outline?.length, length => { if (!length && tab.value === "outline") tab.value = "goals"; });
watch(() => props.questionOutline?.length, length => { if (!length && tab.value === "questions") tab.value = "goals"; });
async function moveTab(event: KeyboardEvent) {
  if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
  event.preventDefault();
  const index = tabs.value.findIndex(item => item === tab.value),
    next = event.key === "Home" ? 0 : event.key === "End" ? tabs.value.length - 1 : (index + (event.key === "ArrowRight" ? 1 : -1) + tabs.value.length) % tabs.value.length;
  tab.value = tabs.value[next]! as typeof tab.value;
  await nextTick();
  (tab.value === "goals" ? goalsTab.value : tab.value === "outline" ? outlineTab.value : tab.value === "questions" ? questionsTab.value : assistantTab.value)?.focus();
}
</script>
