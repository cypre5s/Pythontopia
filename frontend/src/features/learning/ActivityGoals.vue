<template>
  <div class="activity-reading-goals">
    <header class="activity-reading-summary">
      <strong>{{ stageTitle || activity.title }}</strong>
      <span v-if="stageTitle && stageTitle !== activity.title" class="muted">{{ activity.title }}</span>
      <div class="activity-reading-summary-line">
        <span>必读 {{ progress?.completed_count ?? 0 }} / {{ progress?.required_count ?? requiredCount }} 份</span>
        <button class="primary" :disabled="busy || !progress?.completed" @click="$emit('complete')">{{ busy ? '保存中…' : '完成学习' }}</button>
      </div>
      <progress :value="progress?.fraction ?? 0" :max="1" aria-label="活动总进度" />
      <slot name="status" />
    </header>
    <section v-for="row in activity.resources" :key="row.material_id" class="activity-reading-resource" :class="{ 'is-optional': !row.required }">
      <button class="activity-resource-heading" :aria-current="row.material_id === materialId ? 'page' : undefined" :disabled="busy" @click="$emit('select', row.material_id)">
        <GameIcon :name="state(row.material_id)?.completed ? 'check' : row.material.kind === 'video' ? 'play' : 'description'" />
        <span class="activity-resource-title" :title="row.material.title">{{ row.material.title }}</span>
        <small>{{ row.required ? '必读' : '可选' }}</small>
        <GameIcon name="arrowRight" />
      </button>
      <div class="activity-resource-progress">
        <span v-if="row.material.status !== 'ready'">资料暂不可用</span>
        <span v-else-if="row.material.kind !== 'video'">已阅读 {{ state(row.material_id)?.read_count ?? 0 }} / {{ state(row.material_id)?.read_total ?? row.material.pages.length }} 页</span>
        <span v-else>有效观看 {{ stamp(state(row.material_id)?.watched_seconds ?? 0) }}<template v-if="row.learning.video?.minimum_seconds"> / {{ stamp(row.learning.video.minimum_seconds) }}</template><template v-if="row.learning.video?.segments?.length"> · 关键片段 {{ state(row.material_id)?.completed_segments.length ?? 0 }} / {{ row.learning.video.segments.length }}</template></span>
        <progress :value="state(row.material_id)?.fraction ?? 0" :max="1" :aria-label="`${row.material.title}学习进度`" />
      </div>
      <details v-if="row.learning.reading?.scope && row.learning.reading.scope !== 'all' || row.learning.video?.segments?.length" class="activity-resource-requirements">
        <summary>完成要求 <GameIcon name="chevronDown" /></summary>
        <button v-for="page in requiredPages(row)" :key="page.id" :disabled="busy" @click="$emit('locate', row.material_id, page.page_no)">
          <GameIcon :name="state(row.material_id)?.visited_pages.includes(Number(page.id)) ? 'check' : 'circle'" /><span>{{ page.page_no }}. {{ page.title || `第${page.page_no}页` }}</span>
        </button>
        <button v-for="(segment, index) in row.learning.video?.segments ?? []" :key="index" :disabled="busy" @click="$emit('locate', row.material_id, undefined, segment.start_seconds)">
          <GameIcon :name="state(row.material_id)?.completed_segments.includes(index) ? 'check' : 'circle'" /><span>{{ stamp(segment.start_seconds) }}–{{ stamp(segment.end_seconds) }} {{ segment.label }}</span>
        </button>
      </details>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import GameIcon from '../../shared/GameIcon.vue';
import type { Activity, ActivityProgress, ActivityResource } from '../../shared/types';
const props = defineProps<{ activity: Activity; progress?: ActivityProgress; materialId?: number; stageTitle?: string; busy: boolean }>();
defineEmits<{ complete: []; select: [id: number]; locate: [id: number, page?: number, seconds?: number] }>();
const requiredCount = computed(() => props.activity.resources.filter(row => row.required).length);
const state = (id: number) => props.progress?.resources?.find(row => row.material_id === id);
function requiredPages(row: ActivityResource) {
  const ids = state(row.material_id)?.required_page_ids ?? row.learning.reading?.page_ids;
  return row.material.pages.filter(page => ids ? ids.includes(Number(page.id)) : page.page_no >= (row.learning.reading?.start_page ?? 1) && page.page_no <= (row.learning.reading?.end_page ?? Infinity));
}
function stamp(value: number) { return `${Math.floor(value / 60)}:${String(Math.floor(value % 60)).padStart(2, '0')}`; }
</script>
