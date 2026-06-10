<script setup lang="ts">
// 按题型分派到对应渲染器（类似 arena/KindRouter，但走 §3 的判别联合）。
// 受控：作答状态由父级（useChapterTestExam）持有，子组件只渲染 + 抛事件。
import { computed } from 'vue';
import ChoiceQuestion from './ChoiceQuestion.vue';
import JudgeQuestion from './JudgeQuestion.vue';
import FillQuestion from './FillQuestion.vue';
import CodingQuestion from './CodingQuestion.vue';
import type { LocalAnswerState } from './useChapterTestExam';
import type {
  ChapterTestQuestionDto,
  ChapterTestResultItemDto,
} from '@/api/chapterTest';

const props = defineProps<{
  item: ChapterTestQuestionDto;
  index: number;
  answer: LocalAnswerState | undefined;
  reviewItem?: ChapterTestResultItemDto | null;
}>();

const emit = defineEmits<{
  (e: 'choice', qid: string, letter: string): void;
  (e: 'judge', qid: string, value: boolean): void;
  (e: 'blank', qid: string, index: number, value: string): void;
  (e: 'code', qid: string, value: string): void;
  (e: 'paste-blocked', qid: string): void;
}>();

// 判别联合收窄：v-if 引用这些 computed，模板内即获得精确类型。
const choice = computed(() => (props.item.type === 'CHOICE' ? props.item : null));
const judge = computed(() => (props.item.type === 'JUDGE' ? props.item : null));
const fill = computed(() => (props.item.type === 'FILL' ? props.item : null));
const coding = computed(() => (props.item.type === 'CODING' ? props.item : null));
</script>

<template>
  <ChoiceQuestion
    v-if="choice"
    :question="choice"
    :index="index"
    :selected="answer?.choice ?? null"
    :review-item="reviewItem"
    @select="(letter) => emit('choice', item.qid, letter)"
  />
  <JudgeQuestion
    v-else-if="judge"
    :question="judge"
    :index="index"
    :selected="answer?.judgement ?? null"
    :review-item="reviewItem"
    @select="(value) => emit('judge', item.qid, value)"
  />
  <FillQuestion
    v-else-if="fill"
    :question="fill"
    :index="index"
    :blanks="answer?.blanks ?? []"
    :review-item="reviewItem"
    @change="(i, value) => emit('blank', item.qid, i, value)"
  />
  <CodingQuestion
    v-else-if="coding"
    :question="coding"
    :index="index"
    :code="answer?.code ?? ''"
    :review-item="reviewItem"
    @update:code="(value) => emit('code', item.qid, value)"
    @paste-blocked="emit('paste-blocked', item.qid)"
  />
</template>
