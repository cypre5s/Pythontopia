<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import HandoutView from './HandoutView.vue';
import MindMapView from './MindMapView.vue';
import PracticeList from './PracticeList.vue';
import CodeLabView from './CodeLabView.vue';
import ReadingList from './ReadingList.vue';
import StoryboardView from './StoryboardView.vue';
import PptView from './PptView.vue';
import type {
  CodeLabContent,
  HandoutContent,
  MindMapContent,
  PracticeContent,
  ReadingContent,
  StoryboardContent,
  WorkshopFeedbackRequest,
} from '@/api/dto/Workshop';

const props = defineProps<{
  resourceId: number | null;
}>();

const learning = useLearningStore();
const toast = useToastStore();
const feedbackNote = ref<string>('');
const submittingFeedback = ref<boolean>(false);
const showPpt = ref<boolean>(false);

const resource = computed(() => learning.resources.current);

watch(
  () => props.resourceId,
  async (id) => {
    showPpt.value = false;
    if (id) {
      try {
        await learning.openResource(id);
        feedbackNote.value = learning.resources.current?.feedbackNote ?? '';
      } catch (error) {
        const message = error instanceof Error ? error.message : '载入资源失败';
        toast.show(message, 'error');
      }
    }
  },
  { immediate: true },
);

async function submitFeedback(status: 'LIKED' | 'DISLIKED'): Promise<void> {
  if (!resource.value) return;
  submittingFeedback.value = true;
  try {
    const trimmed = feedbackNote.value.trim();
    const request: WorkshopFeedbackRequest =
      trimmed.length > 0 ? { status, note: trimmed } : { status };
    await learning.giveResourceFeedback(resource.value.id, request);
    toast.show('反馈已提交。', 'success');
  } catch (error) {
    const message = error instanceof Error ? error.message : '反馈提交失败';
    toast.show(message, 'error');
  } finally {
    submittingFeedback.value = false;
  }
}
</script>

<template>
  <article
    v-if="resource"
    class="detail"
    data-testid="resource-detail"
  >
    <header>
      <div>
        <h2>{{ resource.title }}</h2>
        <p class="meta">
          {{ resource.type }} · KC: {{ resource.kcCodes.join(', ') }} ·
          引用 {{ resource.evidencePageIds.length }} 个页码
        </p>
      </div>
      <div class="badges">
        <span
          :class="['status-badge', resource.groundingStatus === 'PASSED' ? 'grounded' : resource.groundingStatus === 'NO_EVIDENCE' ? 'no-evidence' : 'failed']"
        >
          {{ resource.groundingStatus === 'PASSED' ? '✓ 课件证据充分' : resource.groundingStatus === 'NO_EVIDENCE' ? '基于通识知识' : '⚠ 证据不足' }}
        </span>
        <span
          :class="['status-badge', resource.safetyStatus === 'PASSED' ? 'safe' : resource.safetyStatus === 'FAILED' ? 'unsafe' : 'no-check']"
        >
          {{ resource.safetyStatus === 'PASSED' ? '✓ 安全审查通过' : resource.safetyStatus === 'FAILED' ? '⚠ 存在安全隐患' : '未审查' }}
        </span>
      </div>
    </header>

    <section
      v-if="resource.evidencePageIds.length > 0"
      class="evidence-detail"
    >
      <h4>课件引用页码</h4>
      <div class="page-chips">
        <router-link
          v-for="pid in resource.evidencePageIds"
          :key="pid"
          :to="{ name: 'library', query: { pageId: String(pid) } }"
          class="page-chip"
        >
          p.{{ pid }}
        </router-link>
      </div>
    </section>

    <p
      v-if="resource.summary"
      class="summary"
    >
      {{ resource.summary }}
    </p>
    <div
      v-if="resource.type === 'HANDOUT'"
      class="handout-section"
    >
      <div class="handout-actions">
        <button
          type="button"
          class="ppt-btn"
          @click="showPpt = true"
        >
          查看 PPT
        </button>
      </div>
      <HandoutView :content="(resource.content as HandoutContent)" />
    </div>
    <MindMapView
      v-else-if="resource.type === 'MINDMAP'"
      :content="(resource.content as MindMapContent)"
    />
    <PracticeList
      v-else-if="resource.type === 'PRACTICE'"
      :content="(resource.content as PracticeContent)"
    />
    <CodeLabView
      v-else-if="resource.type === 'CODE_LAB'"
      :content="(resource.content as CodeLabContent)"
    />
    <ReadingList
      v-else-if="resource.type === 'READING'"
      :content="(resource.content as ReadingContent)"
    />
    <StoryboardView
      v-else-if="resource.type === 'STORYBOARD'"
      :content="(resource.content as StoryboardContent)"
    />
    <section class="feedback">
      <h4>反馈</h4>
      <textarea
        v-model="feedbackNote"
        maxlength="512"
        rows="2"
        placeholder="你的看法，将记录到 learning_resource.feedback_note"
      />
      <div class="actions">
        <button
          type="button"
          :class="['like', { selected: resource.feedbackStatus === 'LIKED' }]"
          :disabled="submittingFeedback"
          @click="submitFeedback('LIKED')"
        >
          [+] 有帮助
        </button>
        <button
          type="button"
          :class="['dislike', { selected: resource.feedbackStatus === 'DISLIKED' }]"
          :disabled="submittingFeedback"
          @click="submitFeedback('DISLIKED')"
        >
          [-] 需要改进
        </button>
        <span
          v-if="resource.feedbackStatus !== 'NONE'"
          class="hint"
        >
          上次反馈：{{ resource.feedbackStatus }}
        </span>
      </div>
    </section>
    <Teleport to="body">
      <div
        v-if="showPpt"
        class="ppt-modal-overlay"
      >
        <PptView
          :title="resource.title"
          :kc-codes="resource.kcCodes"
          :content="(resource.content as HandoutContent)"
          :grounding-status="resource.groundingStatus"
          :evidence-page-ids="resource.evidencePageIds"
          @close="showPpt = false"
        />
      </div>
    </Teleport>
  </article>
  <p
    v-else
    class="placeholder"
  >
    请在左侧选择已生成的资源查看详情。
  </p>
</template>

<style scoped>
.detail {
  display: grid;
  gap: 12px;
  background: linear-gradient(180deg, #fff7e6, #fdf3d0);
  border: 2px solid #c9a86b;
  border-radius: 10px;
  padding: 16px;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
header h2 {
  margin: 0;
}
.meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6e4f1d;
}
.summary {
  margin: 0;
  line-height: 1.5;
  color: #2b1c0b;
}
.badges {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-end;
  flex-shrink: 0;
}
.status-badge {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  white-space: nowrap;
}
.status-badge.grounded { background: #4f8a3e; color: #fff7e6; }
.status-badge.no-evidence { background: #b39554; color: #fff7e6; }
.status-badge.failed { background: #c14a2f; color: #fff7e6; }
.status-badge.safe { background: #3a7d6e; color: #fff7e6; }
.status-badge.unsafe { background: #c14a2f; color: #fff7e6; }
.status-badge.no-check { background: #9e9e9e; color: #fff; }
.evidence-detail {
  background: #fff2c9;
  border: 1px dashed #6e4f1d;
  border-radius: 8px;
  padding: 10px 12px;
}
.evidence-detail h4 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #4d3613;
}
.page-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.page-chip {
  display: inline-block;
  padding: 2px 10px;
  background: #6e4f1d;
  color: #fff7e6;
  border-radius: 4px;
  font-size: 12px;
  text-decoration: none;
  transition: background 0.15s;
}
.page-chip:hover {
  background: #4f8a3e;
}
.feedback h4 {
  margin: 0 0 6px;
}
.feedback textarea {
  width: 100%;
  padding: 8px;
  border-radius: 6px;
  border: 1px solid #c9a86b;
  background: #fffbe9;
  resize: vertical;
}
.actions {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-top: 8px;
}
.actions button {
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  border: 2px solid #6e4f1d;
}
.like {
  background: #4f8a3e;
  color: #fff7e6;
  border-color: #2b6021;
}
.like.selected {
  box-shadow: 0 0 0 3px rgba(79, 138, 62, 0.4);
  transform: scale(1.05);
}
.dislike {
  background: #fff7e6;
  color: #6e4f1d;
}
.dislike.selected {
  background: #c14a2f;
  color: #fff7e6;
  border-color: #8b2e1a;
  box-shadow: 0 0 0 3px rgba(193, 74, 47, 0.3);
  transform: scale(1.05);
}
.hint {
  font-size: 12px;
  color: #6e4f1d;
}
.placeholder {
  padding: 24px;
  text-align: center;
  color: #6e4f1d;
}
.handout-section {
  display: grid;
  gap: 10px;
}
.handout-actions {
  display: flex;
  gap: 10px;
}
.ppt-btn {
  padding: 6px 14px;
  border-radius: 6px;
  border: 2px solid #2b6021;
  background: #4f8a3e;
  color: #fff7e6;
  cursor: pointer;
  font-size: 13px;
  font-family: var(--pyt-font-pixel, monospace);
  transition: background 0.15s;
}
.ppt-btn:hover {
  background: #3d7230;
}
.ppt-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  flex-direction: column;
}
</style>
