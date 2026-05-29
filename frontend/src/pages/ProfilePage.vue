<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import MarkdownView from '@/components/common/MarkdownView.vue';
import { useLearningStore } from '@/stores/learning';
import { useSessionStore } from '@/stores/session';
import { playUiClick } from '@/composables/useStardewFx';

const learning = useLearningStore();
const session = useSessionStore();
const router = useRouter();

function goBack(): void {
  playUiClick();
  void router.push('/world');
}

onMounted(async () => {
  await learning.loadProfile();
});

const p = computed(() => learning.profile);

const dimensionCards = computed(() => {
  if (!p.value) return [];
  return [
    {
      key: 'major',
      title: '专业背景',
      tag: 'BG',
      value: p.value.major ?? '未设定',
      detail: null,
    },
    {
      key: 'baseLevel',
      title: '知识基础',
      tag: 'KN',
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
      tag: 'GL',
      value: p.value.learningGoal?.kind ?? '未设定',
      detail: p.value.learningGoal?.deadlineIso
        ? `截止: ${p.value.learningGoal.deadlineIso.slice(0, 10)}`
        : null,
    },
    {
      key: 'cognitiveStyle',
      title: '认知风格',
      tag: 'CS',
      value: p.value.cognitiveStyle
        ? `场依存 ${p.value.cognitiveStyle.fieldDependent}/10 | ${p.value.cognitiveStyle.sequentialVsHolistic > 5 ? '整体型' : '序列型'}`
        : '待评估',
      detail: null,
    },
    {
      key: 'learningPreference',
      title: '学习偏好',
      tag: 'LP',
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
      tag: 'EP',
      value: p.value.errorPatterns?.commonCategories.length
        ? p.value.errorPatterns.commonCategories.slice(0, 3).join(', ')
        : '暂无数据',
      detail: p.value.errorPatterns?.frequentKcCodes.length
        ? `高频 KC: ${p.value.errorPatterns.frequentKcCodes.slice(0, 3).join(', ')}`
        : null,
    },
    {
      key: 'practiceCapability',
      title: '实践能力',
      tag: 'PR',
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
      tag: 'EM',
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
        <button type="button" class="back-btn" @click="goBack">
          &lt;- 返回世界
        </button>
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

      <div v-if="p" class="dimension-grid">
        <article
          v-for="dim in dimensionCards"
          :key="dim.key"
          class="dim-card"
          :data-testid="`dim-${dim.key}`"
        >
          <div class="dim-card__header">
            <span class="dim-card__tag">[{{ dim.tag }}]</span>
            <h3 class="dim-card__title">{{ dim.title }}</h3>
          </div>
          <p class="dim-card__value">{{ dim.value }}</p>
          <p v-if="dim.detail" class="dim-card__detail">{{ dim.detail }}</p>
        </article>
      </div>

      <div v-if="p?.narrativeSummary" class="profile-summary">
        <h2 class="summary-title">[SUMMARY] 综合总结</h2>
        <MarkdownView :source="p.narrativeSummary" />
      </div>

      <p v-if="!p" class="profile-empty">
        画像尚未生成。请先完成入学访谈或开始学习。
      </p>
    </section>
  </AppLayout>
</template>

<style scoped>
.profile-page {
  padding: 24px 16px;
  min-height: calc(100vh - 64px);
  background: linear-gradient(180deg, #fef7e3 0%, #f3e4be 100%);
  max-width: 960px;
  margin: 0 auto;
}

.profile-nav {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.back-btn {
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 13px;
  padding: 6px 14px;
  background: var(--pyt-color-primary, #6e4f1d);
  border: 2px solid var(--pyt-color-border-strong, #4d3613);
  border-radius: 2px;
  color: #fff7e6;
  cursor: pointer;
  box-shadow: 2px 2px 0 rgba(77, 54, 19, 0.3);
  transition: transform 80ms, box-shadow 80ms, background 120ms;
}

.back-btn:hover {
  background: #4d3613;
  transform: translateY(-1px);
  box-shadow: 2px 3px 0 rgba(77, 54, 19, 0.3);
}

.back-btn:active {
  transform: translateY(1px);
  box-shadow: 0 0 0 rgba(77, 54, 19, 0.3);
}

.nav-breadcrumb {
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 12px;
  color: var(--pyt-color-text-muted, #8a6c2c);
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
  color: #fff7e6;
  font-family: var(--pyt-font-pixel, monospace);
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
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 22px;
  color: var(--pyt-color-text-primary);
  letter-spacing: 1px;
}

.profile-subtitle {
  font-family: var(--pyt-font-pixel, monospace);
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
  box-shadow: 0 3px 0 rgba(77, 54, 19, 0.16);
}

.dim-card__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.dim-card__tag {
  font-family: var(--pyt-font-pixel-mono, monospace);
  font-size: 11px;
  color: var(--pyt-color-primary);
  background: rgba(110, 79, 29, 0.1);
  padding: 1px 6px;
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: 2px;
  white-space: nowrap;
}

.dim-card__title {
  margin: 0;
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 14px;
  color: var(--pyt-color-text-primary);
  font-weight: 700;
}

.dim-card__value {
  margin: 0 0 4px;
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 13px;
  color: var(--pyt-color-text-primary);
  line-height: 1.5;
}

.dim-card__detail {
  margin: 0;
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 11px;
  color: var(--pyt-color-text-muted);
  border-top: 1px dashed var(--pyt-color-border-soft);
  padding-top: 6px;
  margin-top: 6px;
}

.profile-summary {
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: 2px;
  padding: 16px;
  box-shadow: var(--pyt-shadow-card);
}

.summary-title {
  margin: 0 0 12px;
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 15px;
  color: var(--pyt-color-text-primary);
}

.profile-empty {
  text-align: center;
  padding: 48px 16px;
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 14px;
  color: var(--pyt-color-text-muted);
  background: var(--pyt-color-bg-elevated);
  border: 2px dashed var(--pyt-color-border);
  border-radius: 2px;
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
