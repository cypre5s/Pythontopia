<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import MarkdownView from '@/components/common/MarkdownView.vue';
import ProfileRadar from '@/components/profile/ProfileRadar.vue';
import PyButton from '@/components/ui/PyButton.vue';
import PyPanel from '@/components/ui/PyPanel.vue';
import PyEmptyState from '@/components/ui/PyEmptyState.vue';
import { useLearningStore } from '@/stores/learning';
import { useSessionStore } from '@/stores/session';
import { useToastStore } from '@/stores/toast';
import { playUiClick } from '@/composables/useStardewFx';
import {
  getProfileDimensions,
  selfUpdateDimension,
  type ProfileDimensionDto,
} from '@/api/learner';

const learning = useLearningStore();
const session = useSessionStore();
const toast = useToastStore();
const router = useRouter();

const dimensions = ref<ProfileDimensionDto[]>([]);
const dimensionsError = ref('');
const savingKey = ref<string | null>(null);

function goBack(): void {
  playUiClick();
  void router.push('/world');
}

async function loadDimensions(): Promise<void> {
  dimensionsError.value = '';
  try {
    dimensions.value = await getProfileDimensions();
  } catch {
    dimensions.value = [];
    dimensionsError.value = '画像维度暂时无法加载，请重试。';
  }
}

const selfEditable = computed(() =>
  dimensions.value.filter((d) => d.selfEditable),
);

// 自评滑块的「本地草稿值」(0..100)：拖动即时跟手、提交后保留学生选定位置，
// 不被后端 effective(decay) 分数拉回——否则松手就回弹，像「拉不动」。
const draftScores = ref<Record<string, number>>({});
watch(
  selfEditable,
  (dims) => {
    for (const d of dims) {
      if (draftScores.value[d.key] === undefined) {
        draftScores.value[d.key] = Math.round(d.score * 100);
      }
    }
  },
  { immediate: true },
);
function draftValue(dim: ProfileDimensionDto): number {
  return draftScores.value[dim.key] ?? Math.round(dim.score * 100);
}
function onDraftInput(dim: ProfileDimensionDto, event: Event): void {
  draftScores.value[dim.key] = Number((event.target as HTMLInputElement).value);
}

/** 学生自评：松手时把草稿目标分（0..100）换算成相对当前分的增量提交，并保留所选位置。 */
async function onSelfRate(dim: ProfileDimensionDto): Promise<void> {
  const targetPct = draftValue(dim);
  const target = targetPct / 100;
  const delta = Math.round((target - dim.score) * 100) / 100;
  if (delta === 0) return;
  savingKey.value = dim.key;
  try {
    await selfUpdateDimension(dim.key, delta);
    await loadDimensions();
    // 乐观保留学生选定位置（自评代表「我的主观目标」），不让 effective decay 把滑块拉回。
    draftScores.value[dim.key] = targetPct;
    toast.show(`已更新「${dim.label}」自评`, 'success');
  } catch (error) {
    const message = error instanceof Error ? error.message : '自评提交失败';
    toast.show(message, 'error');
  } finally {
    savingKey.value = null;
  }
}

onMounted(async () => {
  await Promise.all([learning.loadProfile(), loadDimensions()]);
});

const p = computed(() => learning.profile);

const dimensionCards = computed(() => {
  if (!p.value) return [];
  return [
    {
      key: 'major',
      title: '专业背景',
      value: p.value.major ?? '未设定',
      detail: null,
    },
    {
      key: 'baseLevel',
      title: '知识基础',
      value: p.value.baseLevel
        ? `编程 ${p.value.baseLevel.programmingYears} 年 / Python Lv.${p.value.baseLevel.pythonSelfRatedLevel}`
        : '待评估',
      detail: p.value.baseLevel?.languagesKnown.length
        ? `已学语言: ${p.value.baseLevel.languagesKnown.join(', ')}`
        : null,
    },
    {
      key: 'learningGoal',
      title: '学习目标',
      value: p.value.learningGoal?.kind ?? '未设定',
      detail: p.value.learningGoal?.deadlineIso
        ? `截止: ${p.value.learningGoal.deadlineIso.slice(0, 10)}`
        : null,
    },
    {
      key: 'learningPreference',
      title: '学习偏好',
      value: p.value.learningPreference
        ? `每日 ${p.value.learningPreference.sessionMinutesPerDay} 分钟`
        : '待评估',
      detail: p.value.learningPreference?.mediaPriority.length
        ? `偏好: ${p.value.learningPreference.mediaPriority.join(' > ')}`
        : null,
    },
  ];
});
</script>

<template>
  <AppLayout>
    <section
      class="profile-page"
      data-testid="profile-page"
    >
      <header class="profile-topbar">
        <PyButton
          test-id="profile-back"
          variant="secondary"
          size="sm"
          @click="goBack"
        >
          ← 返回小镇
        </PyButton>
        <div class="profile-identity">
          <div class="profile-avatar">
            <span class="avatar-tag">Lv</span>
          </div>
          <div class="profile-info">
            <h1 class="profile-name">
              {{ session.displayName }}
            </h1>
            <span class="profile-subtitle">学习者画像</span>
          </div>
        </div>
        <span class="nav-breadcrumb">我的画像</span>
      </header>

      <div
        v-if="dimensionsError"
        class="profile-load-error"
        role="alert"
        data-testid="profile-dimensions-error"
      >
        <span>{{ dimensionsError }}</span>
        <PyButton
          variant="secondary"
          size="sm"
          test-id="profile-dimensions-retry"
          @click="loadDimensions"
        >
          重试
        </PyButton>
      </div>

      <div class="profile-body">
        <aside
          v-if="dimensions.length > 0"
          class="profile-col profile-col--left"
        >
          <PyPanel class="radar-section">
            <h2 class="summary-title">
              详细画像雷达
            </h2>
            <ProfileRadar :dimensions="dimensions" />
            <p class="profile-note">证据不足的维度不绘制分值；情绪和固定学习风格不作推断，知识掌握以正式练习记录为准。</p>
            <div
              v-if="selfEditable.length > 0"
              class="self-rate"
            >
              <h3 class="self-rate__title">
                自评（仅主观维度）
              </h3>
              <div
                v-for="dim in selfEditable"
                :key="dim.key"
                class="self-rate__row"
                :data-testid="`self-rate-${dim.key}`"
              >
                <span class="self-rate__label">{{ dim.label }}</span>
                <input
                  type="range"
                  min="0"
                  max="100"
                  step="5"
                  :value="draftValue(dim)"
                  :disabled="savingKey === dim.key"
                  class="self-rate__slider"
                  @input="onDraftInput(dim, $event)"
                  @change="onSelfRate(dim)"
                >
                <span class="self-rate__value">{{ draftValue(dim) }}%</span>
              </div>
            </div>
          </PyPanel>
        </aside>

        <div
          v-if="p"
          class="profile-col profile-col--right"
        >
          <div class="dimension-grid">
            <article
              v-for="dim in dimensionCards"
              :key="dim.key"
              class="dim-card"
              :data-testid="`dim-${dim.key}`"
            >
              <div class="dim-card__header">
                <h3 class="dim-card__title">
                  {{ dim.title }}
                </h3>
              </div>
              <p class="dim-card__value">
                {{ dim.value }}
              </p>
              <p
                v-if="dim.detail"
                class="dim-card__detail"
              >
                {{ dim.detail }}
              </p>
            </article>
          </div>

          <PyPanel v-if="p?.narrativeSummary">
            <h2 class="summary-title">
              综合总结
            </h2>
            <MarkdownView :source="p.narrativeSummary" />
          </PyPanel>
        </div>

        <PyEmptyState
          v-if="!p"
          class="profile-empty"
          title="画像尚未生成"
          description="完成入学访谈或开始学习后自动生成。"
          icon="🧭"
        >
          <PyButton
            variant="primary"
            size="sm"
            @click="goBack"
          >
            去小镇学习
          </PyButton>
        </PyEmptyState>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 16px;
  background: linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%);
  overflow: hidden;
}

/* 顶栏：返回 + 身份 + 面包屑，单行常驻不滚动 */
.profile-topbar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 0 0 auto;
  padding: 10px 14px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: 2px;
  box-shadow: var(--pyt-shadow-card);
}

.profile-identity {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.nav-breadcrumb {
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
  flex: 0 0 auto;
}

/* 主体两栏：左雷达 / 右卡片，各自内部滚动，整页不上下滑动 */
.profile-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
}

.profile-load-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  color: var(--pyt-color-danger, #9b2d20);
  border: 1px solid var(--pyt-color-danger, #9b2d20);
}

.profile-col {
  min-height: 0;
  overflow-y: auto;
}

.profile-col--left {
  flex: 0 0 clamp(320px, 32%, 420px);
}

.profile-col--right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.profile-empty {
  margin: auto;
}

.profile-avatar {
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--pyt-color-primary);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: 2px;
  color: var(--pyt-color-bg-elevated);
  font-family: var(--pyt-font-pixel);
  font-size: 16px;
  font-weight: 700;
  image-rendering: pixelated;
}

.avatar-tag {
  letter-spacing: 1px;
}

.profile-info {
  flex: 1;
}

.profile-name {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  font-size: 18px;
  color: var(--pyt-color-text-primary);
  letter-spacing: 1px;
}

.profile-subtitle {
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  color: var(--pyt-color-text-muted);
}

.dimension-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.dim-card {
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: 2px;
  padding: 12px 14px;
  box-shadow: var(--pyt-shadow-card);
  transition: border-color 160ms, box-shadow 160ms;
}

.dim-card:hover {
  border-color: var(--pyt-color-border-strong);
  box-shadow: var(--pyt-shadow-card);
}

.dim-card__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.dim-card__title {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  font-size: 14px;
  color: var(--pyt-color-text-primary);
  font-weight: 700;
}

.dim-card__value {
  margin: 0 0 4px;
  font-family: var(--pyt-font-pixel);
  font-size: 13px;
  color: var(--pyt-color-text-primary);
  line-height: 1.5;
}

.dim-card__detail {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
  color: var(--pyt-color-text-muted);
  border-top: 1px dashed var(--pyt-color-border-soft);
  padding-top: 6px;
  margin-top: 6px;
}

.cog-correct {
  margin-top: 10px;
  border-top: 1px dashed var(--pyt-color-border-soft);
  padding-top: 8px;
}
.cog-correct__hint {
  display: block;
  margin-bottom: 6px;
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
.cog-correct__options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.radar-section {
  margin-bottom: 0;
}
.self-rate {
  margin-top: 12px;
  border-top: 1px dashed var(--pyt-color-border-soft, var(--pyt-color-border));
  padding-top: 12px;
}
.self-rate__title {
  margin: 0 0 10px;
  font-family: var(--pyt-font-pixel);
  font-size: 13px;
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
}
.self-rate__row {
  display: grid;
  grid-template-columns: 88px 1fr 44px;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.self-rate__label {
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
}
.self-rate__slider {
  width: 100%;
  accent-color: var(--pyt-color-success);
}
.self-rate__value {
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
  text-align: right;
}

.summary-title {
  margin: 0 0 12px;
  font-family: var(--pyt-font-pixel);
  font-size: 15px;
  color: var(--pyt-color-text-primary);
}

/* 窄屏（含手机）：两栏降级为单列纵向流，放开整页滚动 */
@media (max-width: 900px) {
  .profile-page {
    height: auto;
    overflow: visible;
  }
  .profile-body {
    flex-direction: column;
    overflow: visible;
  }
  .profile-col {
    overflow-y: visible;
  }
  .profile-col--left {
    flex: 0 0 auto;
  }
}

@media (max-width: 640px) {
  .dimension-grid {
    grid-template-columns: 1fr;
  }
  .profile-topbar {
    flex-wrap: wrap;
  }
}
</style>

<!-- 画像页占满 app-main 剩余视口，桌面端两栏各自滚动、整页不上下滑动（与图书馆页同约定） -->
<style>
.app-main:has(.profile-page) {
  display: flex;
  flex-direction: column;
  min-height: calc(100dvh - 56px);
  max-height: calc(100dvh - 56px);
  overflow: hidden;
}
@supports not (height: 100dvh) {
  .app-main:has(.profile-page) {
    min-height: calc(100vh - 56px);
    max-height: calc(100vh - 56px);
  }
}
/* 窄屏放开高度锁，让内容自然纵向滚动 */
@media (max-width: 900px) {
  .app-main:has(.profile-page) {
    min-height: calc(100dvh - 56px);
    max-height: none;
    overflow: visible;
  }
}
</style>
