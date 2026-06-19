<script setup lang="ts">
import type { BossProblemDto } from '@/api/dto/Boss';
import type { ArenaLevelKind } from '@/types/arena';
import FillBlankCard from './FillBlankCard.vue';
import DebugCard from './DebugCard.vue';
import ShortAnswerCard from './ShortAnswerCard.vue';
import ClosedLoopCard from './ClosedLoopCard.vue';
import DualTrackCard from './DualTrackCard.vue';
import MultiRoundCard from './MultiRoundCard.vue';
import TeachBackCard from './TeachBackCard.vue';
import BugMineCard from './BugMineCard.vue';
import PdfQuestionCard from './PdfQuestionCard.vue';

defineProps<{
  kind: ArenaLevelKind | null;
  problem: BossProblemDto;
}>();

const code = defineModel<string>('code', { required: true });
const emit = defineEmits<{ (e: 'timeout'): void }>();
</script>

<template>
  <FillBlankCard
    v-if="kind === 'fill_blank'"
    v-model:code="code"
    :problem="problem"
  />
  <DebugCard
    v-else-if="kind === 'debug'"
    v-model:code="code"
    :problem="problem"
  />
  <ShortAnswerCard
    v-else-if="kind === 'short_answer'"
    v-model:code="code"
    :problem="problem"
  />
  <ClosedLoopCard
    v-else-if="kind === 'closed_loop'"
    v-model:code="code"
    :problem="problem"
  />
  <DualTrackCard
    v-else-if="kind === 'dual_track'"
    v-model:code="code"
    :problem="problem"
  />
  <MultiRoundCard
    v-else-if="kind === 'multi_round'"
    v-model:code="code"
    :problem="problem"
  />
  <TeachBackCard
    v-else-if="kind === 'teach_back'"
    v-model:code="code"
    :problem="problem"
  />
  <BugMineCard
    v-else-if="kind === 'bug_mine'"
    v-model:code="code"
    :problem="problem"
    @timeout="emit('timeout')"
  />
  <PdfQuestionCard
    v-else-if="kind === 'pdf_question'"
    v-model:code="code"
    :problem="problem"
  />
  <slot v-else />
</template>
