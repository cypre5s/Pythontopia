<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import ProfileInterviewPanel from '@/components/profile/ProfileInterviewPanel.vue';
import AgentNotebook from '@/components/agents/AgentNotebook.vue';
import KnowledgeConstellation from '@/components/expedition/KnowledgeConstellation.vue';
import TwinWeekly from '@/components/companion/TwinWeekly.vue';
import { fetchLearnerTwinState, type LearnerTwinNextStep, type LearnerTwinState } from '@/api/twin';
import { useLearningStore } from '@/stores/learning';
import { useSessionStore } from '@/stores/session';
import { useWorkshopUiStore } from '@/stores/workshopUi';

const learning = useLearningStore();
const session = useSessionStore();
const workshopUi = useWorkshopUiStore();
const router = useRouter();
const state = ref<LearnerTwinState | null>(null);
const busy = ref(true);
const error = ref('');
const showInterview = ref(false);
const showWeekly = ref(false);
const showNotebook = ref(false);

const assessedKcs = computed(() => state.value?.kcs.filter((kc) => kc.assessed).map((kc) => kc.kcCode) ?? []);
const masteryMap = computed<Record<string, number>>(() => {
  const values: Record<string, number> = {};
  for (const kc of state.value?.kcs ?? []) {
    if (kc.assessed && kc.masteryScore != null) values[kc.kcCode] = kc.masteryScore;
  }
  return values;
});
const focusKcs = computed(() => (state.value?.kcs ?? [])
  .filter((kc) => kc.assessed)
  .sort((left, right) => Number(right.reviewDue) - Number(left.reviewDue)
    || (left.masteryScore ?? 1) - (right.masteryScore ?? 1))
  .slice(0, 4));
const nextStep = computed(() => state.value?.nextSteps[0] ?? null);
const alternativeStep = computed(() => state.value?.nextSteps[1] ?? null);

async function loadState(): Promise<void> {
  busy.value = true;
  error.value = '';
  try {
    state.value = await fetchLearnerTwinState();
  } catch {
    error.value = '学习状态暂时无法加载';
  } finally {
    busy.value = false;
  }
}

onMounted(() => void loadState());

function destination(step: LearnerTwinNextStep) {
  const progress = state.value?.kcs.find((kc) => kc.kcCode === step.kcCode);
  if (progress?.reviewDue) {
    return { path: '/bug-mine', query: { review: step.kcCode } };
  }
  return { path: '/expedition', query: { focus: String(step.chapter), kcCode: step.kcCode } };
}

function goToNextStep(): void {
  if (!nextStep.value) return;
  void router.push(destination(nextStep.value));
}

function goToAlternative(): void {
  if (!alternativeStep.value) return;
  void router.push(destination(alternativeStep.value));
}

function actionLabel(step: LearnerTwinNextStep): string {
  const progress = state.value?.kcs.find((kc) => kc.kcCode === step.kcCode);
  if (progress?.reviewDue) return '进入到期复习';
  if (step.action === 'REMEDIATION') return '前往补强练习';
  if (step.action === 'REVIEW') return '巩固先修知识';
  return '前往无尽远征';
}

async function toggleInterview(): Promise<void> {
  showInterview.value = !showInterview.value;
  if (showInterview.value && !learning.profileInterview.sessionId) await learning.openInterview();
}

function onTwinPrompt(prompt: string): void {
  workshopUi.setPendingPrompt(prompt);
  void router.push('/workshop');
}

function percent(value: number | null): string {
  return value == null ? '掌握度暂无可比数据' : `掌握 ${Math.round(value * 100)}%`;
}

function lastPractice(value: string | null): string {
  if (!value) return '暂无正式练习记录';
  return `最近练习 ${new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(new Date(value))}`;
}
</script>

<template>
  <AppLayout>
    <main class="twin-page" data-testid="learner-twin-page">
      <header class="twin-hero">
        <img
          class="twin-hero__portrait"
          src="/assets/pixel/portraits/portrait_student_diligent.png"
          alt="学习分身像素头像"
        >
        <div class="twin-hero__copy">
          <p class="twin-eyebrow">PYTHONTOPIA · LEARNER TWIN</p>
          <h1>我的学习分身</h1>
          <p class="twin-hero__summary">
            {{ session.displayName }} · {{ state?.growthStage ?? '正在读取学习状态' }}
          </p>
          <p class="twin-hero__evidence">
            分身依据服务端判题与复习记录成长；访谈偏好只用于推荐，不会代替能力测评。
          </p>
        </div>
        <button class="twin-link" type="button" @click="router.push('/profile')">
          详细画像 ↗
        </button>
      </header>

      <div v-if="busy" class="twin-message" role="status">
        正在整理你的学习记录…
      </div>
      <div v-else-if="error" class="twin-message twin-message--error" role="alert">
        {{ error }}
        <button type="button" class="twin-button twin-button--small" @click="loadState">重试</button>
      </div>

      <template v-else-if="state">
        <section class="twin-overview" aria-label="学习进展">
          <div class="twin-stat">
            <strong>{{ state.assessedKcCount }} / {{ state.totalKcCount }}</strong>
            <span>已有练习证据</span>
          </div>
          <div class="twin-stat">
            <strong>{{ state.masteredKcCount }}</strong>
            <span>掌握度达到 80% 的知识点</span>
          </div>
          <div class="twin-stat">
            <strong>{{ focusKcs.filter((kc) => kc.reviewDue).length }}</strong>
            <span>重点知识点中复习已到期</span>
          </div>
        </section>

        <section class="twin-grid">
          <article class="twin-card twin-card--knowledge">
            <div class="twin-card__heading">
              <div>
                <p class="twin-eyebrow">KNOWLEDGE MAP</p>
                <h2>知识星图</h2>
              </div>
              <span class="twin-count">已评估 {{ state.assessedKcCount }}/{{ state.totalKcCount }}</span>
            </div>
            <KnowledgeConstellation :mastery="masteryMap" :assessed="assessedKcs" />
            <p class="twin-note">
              只有经过练习判题的知识点才显示掌握度。灰色星点表示尚未评估。
            </p>
            <div v-if="focusKcs.length" class="twin-kc-list" data-testid="twin-evidence-list">
              <div v-for="kc in focusKcs" :key="kc.kcCode" class="twin-kc-row">
                <div>
                  <strong>{{ kc.kcName }}</strong>
                  <span>{{ kc.chapterName }} · {{ lastPractice(kc.lastPracticedAt) }}</span>
                </div>
                <div class="twin-kc-row__status">
                  <strong>{{ percent(kc.masteryScore) }}</strong>
                  <span v-if="kc.reviewDue" class="twin-due">复习已到期</span>
                  <span v-else-if="kc.consecutiveFailures >= 2" class="twin-due">连续练习未通过</span>
                </div>
              </div>
            </div>
            <p v-else class="twin-note" data-testid="twin-unassessed-hint">
              还没有经判题确认的学习证据。先完成一道基础练习，分身就会开始记录你的进步。
            </p>
          </article>

          <article class="twin-card twin-card--next">
            <p class="twin-eyebrow">YOUR NEXT STEP</p>
            <h2>建议的下一步</h2>
            <template v-if="nextStep">
              <div class="twin-recommendation" data-testid="twin-recommendation">
                <span class="twin-recommendation__chapter">{{ nextStep.chapterName }} · 难度 {{ nextStep.difficulty }}/5</span>
                <h3>{{ nextStep.kcName }}</h3>
                <p>{{ nextStep.reason }}</p>
                <small v-if="nextStep.remediationRequired">近期连续未通过，先巩固再升级。</small>
                <button class="twin-button" type="button" data-testid="twin-next-primary" @click="goToNextStep">
                  {{ actionLabel(nextStep) }}
                </button>
                <button
                  v-if="alternativeStep"
                  class="twin-button twin-button--secondary"
                  type="button"
                  data-testid="twin-next-alternative"
                  @click="goToAlternative"
                >
                  {{ alternativeStep.kcName }} · {{ actionLabel(alternativeStep) }}
                </button>
              </div>
            </template>
            <p v-else class="twin-empty" data-testid="twin-no-recommendation">
              当前没有满足先修条件的推荐知识点。完成基础练习后再来查看下一步。
            </p>
          </article>
        </section>

        <section class="twin-details" aria-label="学习分身详情">
          <details data-testid="twin-weekly-details">
            <summary data-testid="twin-weekly-summary">近期变化 · 本周学习记录</summary>
            <TwinWeekly class="twin-details__panel" @prompt="onTwinPrompt" />
          </details>
          <details>
            <summary>学习目标与偏好 · 画像访谈</summary>
            <div class="twin-details__panel">
              <p>访谈回答用于设定目标和偏好，不作为实际能力的测评证据。</p>
              <button class="twin-button twin-button--small" type="button" data-testid="twin-interview-toggle" @click="toggleInterview">
                {{ showInterview ? '收起画像访谈' : '编辑学习目标与偏好' }}
              </button>
              <ProfileInterviewPanel v-if="showInterview" />
            </div>
          </details>
          <details>
            <summary>学习团队工作记录</summary>
            <AgentNotebook class="twin-details__panel" />
          </details>
        </section>
      </template>
    </main>
  </AppLayout>
</template>

<style scoped>
.twin-page {
  width: min(1160px, 100%);
  min-height: calc(100dvh - 64px);
  margin: 0 auto;
  padding: clamp(16px, 3vw, 32px);
  color: #1d2c27;
}
.twin-hero {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: clamp(18px, 3vw, 30px);
  color: #eff6ef;
  background: linear-gradient(115deg, #203b32, #345648 62%, #607e58);
  border: 3px solid #182d26;
  box-shadow: 5px 5px 0 #182d26;
}
.twin-hero__portrait {
  width: 92px;
  height: 112px;
  object-fit: contain;
  image-rendering: pixelated;
  filter: drop-shadow(3px 4px 0 rgba(0, 0, 0, .26));
}
.twin-hero__copy { flex: 1; min-width: 0; }
.twin-eyebrow {
  margin: 0 0 7px;
  color: #a7c79b;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .12em;
}
.twin-hero h1, .twin-card h2 { margin: 0; }
.twin-hero h1 { font-size: clamp(24px, 4vw, 34px); }
.twin-hero__summary { margin: 8px 0 4px; font-size: 15px; }
.twin-hero__evidence { margin: 0; color: #cfddcf; font-size: 12px; line-height: 1.6; }
.twin-link {
  flex: 0 0 auto;
  padding: 9px 12px;
  color: #f1f5e9;
  font: inherit;
  background: rgba(9, 25, 19, .28);
  border: 1px solid rgba(255,255,255,.45);
  cursor: pointer;
}
.twin-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 22px 0;
}
.twin-stat, .twin-card {
  background: #fff9e8;
  border: 2px solid #29382d;
  box-shadow: 3px 3px 0 #29382d;
}
.twin-stat { display: grid; gap: 4px; padding: 14px 18px; }
.twin-stat strong { color: #28533f; font: 700 21px 'Zpix', monospace; }
.twin-stat span { color: #586459; font-size: 12px; }
.twin-grid { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(280px, .75fr); gap: 18px; }
.twin-card { min-width: 0; padding: clamp(16px, 2vw, 24px); }
.twin-card__heading { display: flex; justify-content: space-between; align-items: end; gap: 12px; margin-bottom: 14px; }
.twin-card__heading .twin-eyebrow, .twin-card--next .twin-eyebrow { color: #66856a; }
.twin-card h2 { font-size: 21px; }
.twin-count { color: #657267; font-size: 12px; }
.twin-note, .twin-empty { color: #657267; font-size: 12px; line-height: 1.7; }
.twin-kc-list { margin-top: 14px; border-top: 1px solid #dedcc8; }
.twin-kc-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #e7e3d2; }
.twin-kc-row > div:first-child { display: grid; gap: 3px; }
.twin-kc-row span { color: #687469; font-size: 11px; }
.twin-kc-row__status { display: grid; justify-items: end; gap: 3px; white-space: nowrap; }
.twin-kc-row__status strong { font-size: 13px; }
.twin-due { color: #9a4b32 !important; }
.twin-recommendation { display: grid; gap: 12px; margin-top: 18px; padding: 18px; background: #e6eedc; border-left: 4px solid #62834f; }
.twin-recommendation__chapter { color: #56714c; font-size: 11px; font-weight: 700; }
.twin-recommendation h3 { margin: 0; font-size: 20px; }
.twin-recommendation p, .twin-recommendation small { margin: 0; color: #46564a; line-height: 1.6; }
.twin-button { min-height: 42px; padding: 9px 14px; color: #fff; font: 700 13px 'Zpix', monospace; background: #315b43; border: 2px solid #203b2c; box-shadow: 2px 2px 0 #203b2c; cursor: pointer; }
.twin-button--secondary { color: #31533c; background: transparent; }
.twin-button--small { min-height: 34px; padding: 6px 10px; font-size: 11px; }
.twin-details { display: grid; gap: 10px; margin-top: 22px; }
.twin-details details { background: #f4f0df; border: 1px solid #c7c3ac; }
.twin-details summary { padding: 12px 16px; color: #34463b; font-size: 13px; font-weight: 700; cursor: pointer; }
.twin-details__panel { margin: 0 16px 16px; }
.twin-details__panel p { color: #657267; font-size: 12px; line-height: 1.6; }
.twin-message { display: flex; align-items: center; gap: 12px; margin: 22px 0; padding: 16px; background: #fff9e8; border: 1px solid #c7c3ac; }
.twin-message--error { color: #863e31; }
@media (max-width: 760px) {
  .twin-hero { align-items: flex-start; flex-wrap: wrap; gap: 14px; }
  .twin-hero__portrait { width: 64px; height: 80px; }
  .twin-link { margin-left: 78px; }
  .twin-overview { grid-template-columns: 1fr; gap: 8px; }
  .twin-stat { grid-template-columns: 110px 1fr; align-items: center; }
  .twin-grid { grid-template-columns: 1fr; }
}
</style>
