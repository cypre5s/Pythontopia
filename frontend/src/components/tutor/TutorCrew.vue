<script setup lang="ts">
// 「助教小队」工作流拟人化（R-14，借鉴腾讯 Marvis 的「虚拟办公室」多 Agent 可视化）：
// 把导师答疑的后台流水线（课件检索 → 看图理解 → 导师讲解 → 引用校对）拟人成 4 名像素助教，
// 由后端 SSE trace 实时驱动状态（待命/摸鱼 → 干活 → 完成），让答疑不再是黑盒聊天框。
import { computed } from 'vue';
import type { SseTraceEvent } from '@/composables/useSseStream';

const props = withDefaults(
  defineProps<{
    traces: SseTraceEvent[];
    status: string;
    imageCount?: number;
    grounding?: string | null;
    cognitiveStyle?: string;
    errorTags?: string[];
  }>(),
  { imageCount: 0, grounding: null, cognitiveStyle: '', errorTags: () => [] },
);

const emit = defineEmits<{ (e: 'pick-style', style: 'visual' | 'logical' | 'trial'): void }>();

// withDefaults + exactOptionalPropertyTypes 下模板里 errorTags 仍被视作可能 undefined，统一收敛成非空数组。
const errorTagList = computed<string[]>(() => props.errorTags ?? []);

const STYLE_LABEL: Record<string, string> = {
  visual: '图解优先',
  logical: '推理优先',
  trial: '示例优先',
};
function styleLabel(s?: string): string {
  return s ? STYLE_LABEL[s] ?? s : '';
}
const STYLE_OPTIONS: Array<{ id: 'visual' | 'logical' | 'trial'; label: string }> = [
  { id: 'visual', label: '图解' },
  { id: 'logical', label: '推理' },
  { id: 'trial', label: '示例' },
];

type MemberState = 'idle' | 'working' | 'done';
interface CrewMember {
  key: string;
  avatar: string;
  name: string;
  role: string;
  state: MemberState;
  line: string;
}

const active = computed(() => props.status === 'connecting' || props.status === 'streaming');
const finished = computed(() => props.status === 'done');

function tracesByNode(node: string): SseTraceEvent[] {
  return props.traces.filter((t) => t.node === node);
}

const evidenceMember = computed<CrewMember>(() => {
  const base = { key: 'evidence', avatar: '📚', name: '小书', role: '资料员' };
  const ev = tracesByNode('evidence_fetcher');
  const lastEv = ev[ev.length - 1];
  if (lastEv) {
    const chunks = lastEv.chunks ?? 0;
    return { ...base, state: 'done', line: chunks > 0 ? `翻好课件 · ${chunks} 段` : '走通识知识' };
  }
  if (active.value) return { ...base, state: 'working', line: '翻找课件中…' };
  return { ...base, state: 'idle', line: '💤 待命' };
});

const imageMember = computed<CrewMember | null>(() => {
  const base = { key: 'image', avatar: '🖼', name: '小睛', role: '识图员' };
  const imgs = tracesByNode('image_understanding');
  if ((props.imageCount ?? 0) <= 0 && imgs.length === 0) return null;
  const last = imgs[imgs.length - 1];
  if (!last) {
    return { ...base, state: active.value ? 'working' : 'idle', line: active.value ? '准备看图…' : '💤 待命' };
  }
  const total = last.total ?? props.imageCount ?? imgs.length;
  const okCount = imgs.filter((t) => t.status === 'ok').length;
  if (last.status === 'start') {
    return { ...base, state: 'working', line: `看第 ${last.index ?? '?'}/${total} 张图…` };
  }
  return { ...base, state: 'done', line: okCount > 0 ? `看懂 ${okCount}/${total} 张` : '没看清图' };
});

const tutorMember = computed<CrewMember>(() => {
  const base = { key: 'tutor', avatar: '🧑‍🏫', name: '阿派', role: '导师' };
  if (finished.value) return { ...base, state: 'done', line: '讲完啦' };
  if (active.value) {
    const started = tracesByNode('TutorAgent').length > 0;
    return { ...base, state: 'working', line: started ? '讲解中…' : '组织思路…' };
  }
  return { ...base, state: 'idle', line: '💤 待命' };
});

const GROUNDING_TEXT: Record<string, string> = {
  PASSED: '引用充分',
  NO_EVIDENCE: '通识知识',
  FAILED: '复核未过',
};
const groundingMember = computed<CrewMember>(() => {
  const base = { key: 'grounding', avatar: '✅', name: '小核', role: '校对员' };
  if (props.grounding) {
    return { ...base, state: 'done', line: GROUNDING_TEXT[props.grounding] ?? props.grounding };
  }
  if (active.value) return { ...base, state: 'working', line: '等待复核…' };
  return { ...base, state: 'idle', line: '💤 待命' };
});

const members = computed<CrewMember[]>(() => {
  const list: CrewMember[] = [evidenceMember.value];
  if (imageMember.value) list.push(imageMember.value);
  list.push(tutorMember.value, groundingMember.value);
  return list;
});

const anyWorking = computed(() => members.value.some((m) => m.state === 'working'));
</script>

<template>
  <div
    class="crew"
    data-testid="tutor-crew"
  >
    <div class="crew__head">
      <span class="crew__title">🐎 助教小队</span>
      <span class="crew__hint">{{ anyWorking ? '正在协作…' : finished ? '协作完成' : '空闲摸鱼中，有问题就开工' }}</span>
    </div>
    <div
      v-if="cognitiveStyle || errorTagList.length > 0"
      class="crew__tailor"
      data-testid="crew-tailor"
    >
      <span v-if="cognitiveStyle">🎯 按「{{ styleLabel(cognitiveStyle) }}」讲法</span>
      <span
        v-if="errorTagList.length > 0"
        class="crew__tailor-warn"
      >⚠ 老坑：{{ errorTagList.join('、') }}</span>
    </div>
    <div class="crew__styles">
      <span class="crew__styles-label">换讲法：</span>
      <button
        v-for="opt in STYLE_OPTIONS"
        :key="opt.id"
        type="button"
        class="crew__style-btn"
        :class="{ 'is-active': cognitiveStyle === opt.id }"
        @click="emit('pick-style', opt.id)"
      >
        {{ opt.label }}
      </button>
    </div>
    <ul class="crew__list">
      <li
        v-for="m in members"
        :key="m.key"
        class="crew-member"
        :class="`crew-member--${m.state}`"
        :data-testid="`crew-${m.key}`"
      >
        <span class="crew-member__avatar">{{ m.avatar }}</span>
        <span class="crew-member__meta">
          <span class="crew-member__name">{{ m.name }}<small>·{{ m.role }}</small></span>
          <span class="crew-member__line">{{ m.line }}</span>
        </span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.crew {
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-bg-soft);
  padding: 8px 10px;
  display: grid;
  gap: 6px;
}
.crew__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.crew__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.crew__hint {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
}
.crew__tailor {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 10px;
  color: var(--pyt-color-text-hint);
}
.crew__tailor-warn {
  color: var(--pyt-color-warning);
}
.crew__styles {
  display: flex;
  align-items: center;
  gap: 4px;
}
.crew__styles-label {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
}
.crew__style-btn {
  font-family: inherit;
  font-size: 10px;
  padding: 2px 8px;
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
}
.crew__style-btn.is-active {
  background: var(--pyt-color-gold-bright);
  border-color: var(--pyt-color-text-hint);
}
.crew__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.crew-member {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1 1 120px;
  min-width: 120px;
  padding: 5px 8px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-elevated);
  transition: border-color var(--pyt-motion-base), opacity var(--pyt-motion-base);
}
.crew-member--idle {
  opacity: 0.6;
}
.crew-member--working {
  border-color: var(--pyt-color-gold);
  box-shadow: inset 0 0 0 1px var(--pyt-color-gold);
}
.crew-member--done {
  border-color: var(--pyt-color-success);
}
.crew-member__avatar {
  font-size: 18px;
  line-height: 1;
}
.crew-member--working .crew-member__avatar {
  animation: crew-bob 0.7s ease-in-out infinite;
}
.crew-member__meta {
  display: grid;
  gap: 1px;
  min-width: 0;
}
.crew-member__name {
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
  white-space: nowrap;
}
.crew-member__name small {
  font-weight: 400;
  color: var(--pyt-color-text-muted);
}
.crew-member__line {
  font-size: 10px;
  color: var(--pyt-color-text-hint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.crew-member--done .crew-member__line {
  color: var(--pyt-color-success);
}

@keyframes crew-bob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}
@media (prefers-reduced-motion: reduce) {
  .crew-member--working .crew-member__avatar {
    animation: none;
  }
}
</style>
