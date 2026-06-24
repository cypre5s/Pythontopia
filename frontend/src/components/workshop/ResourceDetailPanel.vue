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
import GlassBox from '@/components/companion/GlassBox.vue';
import { kcDisplayName } from '@/data/kcNames';
import type {
  CodeLabContent,
  HandoutContent,
  MindMapContent,
  PracticeContent,
  ReadingContent,
  StoryboardContent,
  WorkshopCheckResponse,
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
const consumingResource = ref<boolean>(false);
const checkAnswer = ref<string>('');
const submittingCheck = ref<boolean>(false);
const checkResult = ref<WorkshopCheckResponse | null>(null);

const resource = computed(() => learning.resources.current);
const consumption = computed(() => learning.resources.currentConsumption);

watch(
  () => props.resourceId,
  async (id) => {
    showPpt.value = false;
    checkAnswer.value = '';
    checkResult.value = null;
    if (id) {
      try {
        await learning.openResource(id);
        feedbackNote.value = learning.resources.current?.feedbackNote ?? '';
        void consumeCurrentResource(id);
      } catch (error) {
        const message = error instanceof Error ? error.message : '载入资源失败';
        toast.show(message, 'error');
      }
    }
  },
  { immediate: true },
);

async function consumeCurrentResource(id: number): Promise<void> {
  consumingResource.value = true;
  try {
    await learning.consumeResource(id);
  } catch (error) {
    const message = error instanceof Error ? error.message : '学习记录同步失败';
    toast.show(message, 'error');
  } finally {
    consumingResource.value = false;
  }
}

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

async function submitMicroCheck(): Promise<void> {
  if (!resource.value || resource.value.kcCodes.length === 0) return;
  const primaryKcCode = resource.value.kcCodes[0];
  if (!primaryKcCode) return;
  const answerText = checkAnswer.value.trim();
  if (answerText.length === 0) {
    toast.show('先写一句你的理解，再提交小检查。', 'info');
    return;
  }
  submittingCheck.value = true;
  try {
    checkResult.value = await learning.submitResourceCheck(resource.value.id, {
      kcCode: primaryKcCode,
      evidenceType: 'MICRO_QUIZ',
      answerText,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : '小检查提交失败';
    toast.show(message, 'error');
  } finally {
    submittingCheck.value = false;
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
          {{ resource.type }} · 知识点: {{ resource.kcCodes.map(kcDisplayName).join('、') }} ·
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
    <section
      class="micro-check"
      data-testid="resource-micro-check"
    >
      <div>
        <h4>读完小检查</h4>
        <p>
          {{ consumption?.prompt ?? (consumingResource ? '正在记录你的学习进度…' : '读完后做一道小检查，把资料转成真正会用的能力。') }}
        </p>
      </div>
      <router-link
        v-if="resource.kcCodes.length > 0"
        class="micro-check__cta"
        :to="{ name: 'workshop', query: { type: 'PRACTICE', kc: resource.kcCodes.join(',') } }"
      >
        生成迁移练习
      </router-link>
      <div class="micro-check__form">
        <textarea
          v-model="checkAnswer"
          data-testid="resource-check-answer"
          maxlength="2000"
          rows="2"
          placeholder="用自己的话写一个关键点，例如 input 读取用户输入"
        />
        <button
          type="button"
          data-testid="resource-check-submit"
          :disabled="submittingCheck || checkAnswer.trim().length === 0"
          @click="submitMicroCheck"
        >
          {{ submittingCheck ? '提交中…' : '提交小检查' }}
        </button>
      </div>
      <p
        v-if="checkResult"
        class="micro-check__result"
        data-testid="resource-check-result"
      >
        {{ checkResult.status }} · 掌握度 {{ checkResult.masteryDelta >= 0 ? '+' : '' }}{{ checkResult.masteryDelta.toFixed(2) }}
        <span v-if="checkResult.nextAction === 'remediation' && checkResult.remediationType">
          · 建议补救：{{ checkResult.remediationType }}
        </span>
        <span v-if="checkResult.feedback"> · {{ checkResult.feedback }}</span>
      </p>
    </section>
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
    <!-- ── 决策玻璃盒：任意决策点可展开「为什么这样生成」（GET /api/learner/decisions） ── -->
    <section
      class="decision-box"
      data-testid="resource-decision-box"
    >
      <h4>为什么这样生成</h4>
      <p class="decision-box__hint">
        展开查看各智能体在生成本资源时的关键决策与课件证据依据。
      </p>
      <GlassBox />
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
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated), var(--pyt-color-bg-soft));
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
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
  color: var(--pyt-color-text-hint);
}
.summary {
  margin: 0;
  line-height: 1.5;
  color: #2b1c0b;
}
.micro-check {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px;
  background: #f4f7dc;
  border: 2px solid #9cad57;
  border-radius: var(--pyt-radius-lg);
}
.micro-check h4 {
  margin: 0 0 4px;
  color: #364310;
}
.micro-check p {
  margin: 0;
  line-height: 1.5;
  color: #4f5c20;
}
.micro-check__cta {
  flex: 0 0 auto;
  padding: 8px 12px;
  border-radius: 999px;
  background: #56711f;
  color: #fffde8;
  font-weight: 700;
  text-decoration: none;
}
.micro-check__cta:hover {
  background: #6f8c2b;
}
.micro-check__form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  width: 100%;
}
.micro-check__form textarea {
  width: 100%;
  resize: vertical;
  border: 1px solid #9cad57;
  border-radius: 6px;
  padding: 8px;
  font: inherit;
  color: #2b1c0b;
  background: #fffef2;
}
.micro-check__form button {
  border: 0;
  border-radius: 6px;
  padding: 0 12px;
  background: #3f6421;
  color: #fffde8;
  font-weight: 700;
}
.micro-check__form button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.micro-check__result {
  width: 100%;
  font-weight: 700;
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
.status-badge.grounded { background: #4f8a3e; color: var(--pyt-color-bg-elevated); }
.status-badge.no-evidence { background: #b39554; color: var(--pyt-color-bg-elevated); }
.status-badge.failed { background: #c14a2f; color: var(--pyt-color-bg-elevated); }
.status-badge.safe { background: #3a7d6e; color: var(--pyt-color-bg-elevated); }
.status-badge.unsafe { background: #c14a2f; color: var(--pyt-color-bg-elevated); }
.status-badge.no-check { background: #9e9e9e; color: #fff; }
.evidence-detail {
  background: #fff2c9;
  border: 1px dashed var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  padding: 10px 12px;
}
.evidence-detail h4 {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--pyt-color-text-primary);
}
.page-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.page-chip {
  display: inline-block;
  padding: 2px 10px;
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
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
.decision-box {
  border-top: 1px dashed var(--pyt-color-border);
  padding-top: 10px;
}
.decision-box h4 {
  margin: 0 0 4px;
}
.decision-box__hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.feedback textarea {
  width: 100%;
  padding: 8px;
  border-radius: 6px;
  border: 1px solid var(--pyt-color-border);
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
  border: 2px solid var(--pyt-color-text-hint);
}
.like {
  background: #4f8a3e;
  color: var(--pyt-color-bg-elevated);
  border-color: #2b6021;
}
.like.selected {
  box-shadow: 0 0 0 3px rgba(79, 138, 62, 0.4);
  transform: scale(1.05);
}
.dislike {
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
}
.dislike.selected {
  background: #c14a2f;
  color: var(--pyt-color-bg-elevated);
  border-color: #8b2e1a;
  box-shadow: 0 0 0 3px rgba(193, 74, 47, 0.3);
  transform: scale(1.05);
}
.hint {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.placeholder {
  padding: 24px;
  text-align: center;
  color: var(--pyt-color-text-hint);
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
  color: var(--pyt-color-bg-elevated);
  cursor: pointer;
  font-size: 13px;
  font-family: var(--pyt-font-pixel);
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
  z-index: var(--pyt-z-modal);
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  flex-direction: column;
}
</style>
