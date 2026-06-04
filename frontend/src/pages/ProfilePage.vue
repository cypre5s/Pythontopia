<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
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
import { kcDisplayName } from '@/data/kcNames';

const learning = useLearningStore();
const session = useSessionStore();
const toast = useToastStore();
const router = useRouter();

const dimensions = ref<ProfileDimensionDto[]>([]);
const savingKey = ref<string | null>(null);

function goBack(): void {
  playUiClick();
  void router.push('/world');
}

async function loadDimensions(): Promise<void> {
  try {
    dimensions.value = await getProfileDimensions();
  } catch {
    dimensions.value = [];
  }
}

const selfEditable = computed(() => dimensions.value.filter((d) => d.selfEditable));

/** 学生自评：滑块给出目标分（0..100），换算成相对当前分的增量提交。 */
async function onSelfRate(dim: ProfileDimensionDto, event: Event): Promise<void> {
  const target = Number((event.target as HTMLInputElement).value) / 100;
  const delta = Math.round((target - dim.score) * 100) / 100;
  if (delta === 0) return;
  savingKey.value = dim.key;
  try {
    await selfUpdateDimension(dim.key, delta);
    await loadDimensions();
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
      key: 'cognitiveStyle',
      title: '认知风格',
      value: p.value.cognitiveStyle
        ? `场依存 ${p.value.cognitiveStyle.fieldDependent}/10 | ${p.value.cognitiveStyle.sequentialVsHolistic > 5 ? '整体型' : '序列型'}`
        : '待评估',
      detail: null,
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
    {
      key: 'errorPatterns',
      title: '易错模式',
      value: p.value.errorPatterns?.commonCategories.length
        ? p.value.errorPatterns.commonCategories.slice(0, 3).join(', ')
        : '暂无数据',
      detail: p.value.errorPatterns?.frequentKcCodes.length
        ? `高频知识点: ${p.value.errorPatterns.frequentKcCodes
            .slice(0, 3)
            .map(kcDisplayName)
            .join(', ')}`
        : null,
    },
    {
      key: 'practiceCapability',
      title: '实践能力',
      value: p.value.practiceCapability
        ? `成功率 ${Math.round(p.value.practiceCapability.successRate * 100)}%`
        : '暂无数据',
      detail: p.value.practiceCapability
        ? `平均尝试 ${p.value.practiceCapability.avgAttemptsPerProblem.toFixed(1)} 次/题`
        : null,
    },
    {
      key: 'emotionPersistence',
      title: '情绪与坚持',
      value: p.value.emotionPersistence
        ? `韧性 ${p.value.emotionPersistence.resilience}/10 | ${p.value.emotionPersistence.recentMood}`
        : '待评估',
      detail: null,
    },
  ];
});
</script>

<template>
  <AppLayout>
    <section class="profile-page" data-testid="profile-page">
      <nav class="profile-nav">
        <PyButton
          test-id="profile-back"
          variant="secondary"
          size="sm"
          @click="goBack"
        >
          ← 返回小镇
        </PyButton>
        <span class="nav-breadcrumb">我的画像</span>
      </nav>
      <header class="profile-header">
        <div class="profile-avatar">
          <span class="avatar-tag">Lv</span>
        </div>
        <div class="profile-info">
          <h1 class="profile-name">{{ session.displayName }}</h1>
          <span class="profile-subtitle">Pythontopia 学习者画像</span>
        </div>
      </header>

      <PyPanel
        v-if="dimensions.length > 0"
        class="radar-section"
      >
        <h2 class="summary-title">8 维画像雷达</h2>
        <ProfileRadar :dimensions="dimensions" />
        <div
          v-if="selfEditable.length > 0"
          class="self-rate"
        >
          <h3 class="self-rate__title">自评（仅主观维度，随时调整）</h3>
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
              :value="Math.round(dim.score * 100)"
              :disabled="savingKey === dim.key"
              class="self-rate__slider"
              @change="onSelfRate(dim, $event)"
            >
            <span class="self-rate__value">{{ Math.round(dim.score * 100) }}%</span>
          </div>
        </div>
      </PyPanel>

      <div v-if="p" class="dimension-grid">
        <article
          v-for="dim in dimensionCards"
          :key="dim.key"
          class="dim-card"
          :data-testid="`dim-${dim.key}`"
        >
          <div class="dim-card__header">
            <h3 class="dim-card__title">{{ dim.title }}</h3>
          </div>
          <p class="dim-card__value">{{ dim.value }}</p>
          <p v-if="dim.detail" class="dim-card__detail">{{ dim.detail }}</p>
        </article>
      </div>

      <PyPanel v-if="p?.narrativeSummary">
        <h2 class="summary-title">综合总结</h2>
        <MarkdownView :source="p.narrativeSummary" />
      </PyPanel>

      <PyEmptyState
        v-if="!p"
        title="画像尚未生成"
        description="先完成入学访谈，或在小镇开始学习，系统会据此生成你的画像。"
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
    </section>
  </AppLayout>
</template>

<style scoped>
.profile-page {
  padding: 24px 16px;
  min-height: calc(100vh - 64px);
  background: linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%);
  max-width: 960px;
  margin: 0 auto;
}

.profile-nav {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.nav-breadcrumb {
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding: 16px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: 2px;
  box-shadow: var(--pyt-shadow-card);
}

.profile-avatar {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--pyt-color-primary);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: 2px;
  color: var(--pyt-color-bg-elevated);
  font-family: var(--pyt-font-pixel);
  font-size: 18px;
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
  font-size: 22px;
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
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
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

.radar-section {
  margin-bottom: var(--pyt-space-5);
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

@media (max-width: 640px) {
  .dimension-grid {
    grid-template-columns: 1fr;
  }
  .profile-header {
    flex-direction: column;
    text-align: center;
  }
}
</style>
