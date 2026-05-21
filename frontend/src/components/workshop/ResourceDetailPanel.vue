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

const resource = computed(() => learning.resources.current);

watch(
  () => props.resourceId,
  async (id) => {
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
      <span
        v-if="resource.groundingStatus === 'NO_EVIDENCE'"
        class="badge"
      >
        基于通识知识
      </span>
    </header>
    <p
      v-if="resource.summary"
      class="summary"
    >
      {{ resource.summary }}
    </p>
    <HandoutView
      v-if="resource.type === 'HANDOUT'"
      :content="(resource.content as HandoutContent)"
    />
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
          class="like"
          :disabled="submittingFeedback"
          @click="submitFeedback('LIKED')"
        >
          有帮助
        </button>
        <button
          type="button"
          class="dislike"
          :disabled="submittingFeedback"
          @click="submitFeedback('DISLIKED')"
        >
          需要改进
        </button>
        <span
          v-if="resource.feedbackStatus !== 'NONE'"
          class="hint"
        >
          上次反馈：{{ resource.feedbackStatus }}
        </span>
      </div>
    </section>
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
.badge {
  background: #b39554;
  color: #fff7e6;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
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
.dislike {
  background: #fff7e6;
  color: #6e4f1d;
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
</style>
