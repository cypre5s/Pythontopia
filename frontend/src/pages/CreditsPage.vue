<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import {
  CREDITS,
  LICENSE_GROUPS,
  type CreditEntry,
  type CreditLicense,
  type LicenseGroupMeta,
} from '@/data/credits';

const router = useRouter();

function back(): void {
  if (window.history.length > 1) {
    router.back();
  } else {
    void router.push('/');
  }
}

const CATEGORY_LABELS: Readonly<Record<CreditEntry['category'], string>> = {
  tileset: '地图 / Tileset',
  character: '角色 / Sprite',
  ui: 'UI 面板',
  bgm: '背景音乐',
  sfx: '音效',
  font: '字体',
  icon: '图标',
  effect: '特效',
};

interface CreditGroup extends LicenseGroupMeta {
  readonly entries: ReadonlyArray<CreditEntry>;
}

const groupedCredits = computed<ReadonlyArray<CreditGroup>>(() => {
  return LICENSE_GROUPS.map((meta) => ({
    ...meta,
    entries: CREDITS.filter((entry) => entry.license === meta.license),
  })).filter((group) => group.entries.length > 0);
});

const totalCount = computed<number>(() => CREDITS.length);

function categoryLabel(category: CreditEntry['category']): string {
  return CATEGORY_LABELS[category];
}

function licenseBadgeClass(license: CreditLicense): string {
  switch (license) {
    case 'CC0':
      return 'badge badge--cc0';
    case 'CC-BY 3.0':
    case 'CC-BY 4.0':
      return 'badge badge--by';
    case 'CC-BY-SA 3.0':
      return 'badge badge--sa';
    case 'OFL-1.1':
      return 'badge badge--ofl';
    default:
      return 'badge';
  }
}
</script>

<template>
  <section class="credits-page">
    <header class="hero">
      <button
        type="button"
        class="back"
        @click="back"
      >
        ← 返回
      </button>
      <h1 class="pyt-pixel-text">
        Pythontopia 致谢
      </h1>
      <p class="lead">
        本项目代码以 <strong>MIT License</strong> 发布；像素美术、音效与字体资产则按分层授权，
        覆盖 <code>CC0 / CC-BY 3.0 / CC-BY 4.0 / CC-BY-SA 3.0 / OFL-1.1</code> 多个协议族。
        共收录 <strong>{{ totalCount }}</strong> 项素材署名。
      </p>
      <p class="lead-secondary">
        完整 license 见仓库根 <code>LICENSE-ART.md</code>，前端运行时简版见
        <code>public/assets/pixel/LICENSE.md</code>。
      </p>
    </header>

    <section
      v-for="group in groupedCredits"
      :key="group.license"
      class="license-group"
    >
      <header class="license-header">
        <h2>
          <span :class="licenseBadgeClass(group.license)">{{ group.license }}</span>
          {{ group.title }}
        </h2>
        <ul class="license-meta">
          <li>
            署名：
            <strong v-if="group.requiresAttribution">强制</strong>
            <span v-else>推荐</span>
          </li>
          <li>
            传染（Share-Alike）：
            <strong v-if="group.shareAlike">是</strong>
            <span v-else>否</span>
          </li>
        </ul>
        <p class="license-note">
          {{ group.note }}
        </p>
      </header>

      <ul class="card-grid">
        <li
          v-for="entry in group.entries"
          :key="`${group.license}::${entry.name}`"
          class="card"
        >
          <h3 class="card-title">
            {{ entry.name }}
          </h3>
          <p class="card-row">
            <span class="card-label">类别</span>
            <span>{{ categoryLabel(entry.category) }}</span>
          </p>
          <p class="card-row">
            <span class="card-label">作者</span>
            <span>{{ entry.authors.join(' / ') }}</span>
          </p>
          <p class="card-row">
            <span class="card-label">来源</span>
            <span v-if="entry.sourceUrl">
              <a
                :href="entry.sourceUrl"
                target="_blank"
                rel="noreferrer noopener"
              >{{ entry.source }}</a>
            </span>
            <span v-else>{{ entry.source }}</span>
          </p>
          <p class="card-row">
            <span class="card-label">用途</span>
            <span>{{ entry.usedFor }}</span>
          </p>
        </li>
      </ul>
    </section>

    <footer class="meta">
      <p>
        本声明页面是软件运行时合规的一部分。如发现条目缺漏或需补充作者，请在
        <code>frontend/src/data/credits.ts</code> 提交补丁。
      </p>
    </footer>
  </section>
</template>

<style scoped>
.credits-page {
  max-width: 960px;
  margin: 32px auto;
  padding: 24px 32px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  color: var(--pyt-color-text-primary);
  box-shadow: var(--pyt-shadow-card);
}

.hero {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 24px;
}

.back {
  align-self: flex-start;
  background: transparent;
  border: 2px solid var(--pyt-color-border);
  padding: 6px 12px;
  border-radius: var(--pyt-radius-sm);
  font-family: var(--pyt-font-sans);
  cursor: pointer;
  color: var(--pyt-color-text-primary);
  min-height: 32px;
  box-shadow: none;
}

.back:hover {
  background: var(--pyt-color-bg-soft);
}

h1 {
  font-size: 24px;
  margin: 0;
}

.lead,
.lead-secondary {
  color: var(--pyt-color-text-muted);
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
}

.lead-secondary {
  color: var(--pyt-color-text-hint);
  font-size: 13px;
}

.license-group {
  margin-top: 28px;
  padding-top: 16px;
  border-top: 2px dashed var(--pyt-color-border-soft);
}

.license-group:first-of-type {
  border-top: none;
  margin-top: 8px;
  padding-top: 0;
}

.license-header h2 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  margin: 0 0 8px;
  color: var(--pyt-color-primary-dark);
}

.badge {
  display: inline-block;
  font-family: var(--pyt-font-mono);
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  letter-spacing: 0.5px;
}

.badge--cc0 {
  background: var(--pyt-color-success-bg);
  border-color: var(--pyt-color-success);
  color: var(--pyt-color-success);
}

.badge--by {
  background: var(--pyt-color-info-bg);
  border-color: var(--pyt-color-info);
  color: var(--pyt-color-info);
}

.badge--sa {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-warning);
  color: var(--pyt-color-warning);
}

.badge--ofl {
  background: var(--pyt-color-danger-bg);
  border-color: var(--pyt-color-danger);
  color: var(--pyt-color-danger);
}

.license-meta {
  list-style: none;
  padding: 0;
  margin: 0 0 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: var(--pyt-color-text-hint);
}

.license-meta strong {
  color: var(--pyt-color-primary-dark);
}

.license-note {
  font-size: 13px;
  color: var(--pyt-color-text-muted);
  margin: 0 0 14px;
}

.card-grid {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.card {
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-primary);
  border-radius: var(--pyt-radius-md);
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.card-title {
  font-size: 15px;
  margin: 0 0 4px;
  color: var(--pyt-color-primary-dark);
  word-break: break-word;
}

.card-row {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 8px;
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--pyt-color-text-primary);
}

.card-label {
  color: var(--pyt-color-text-hint);
  font-size: 12px;
}

.card a {
  color: var(--pyt-color-info);
  word-break: break-all;
}

.meta {
  margin-top: 32px;
  padding-top: 16px;
  border-top: 1px solid var(--pyt-color-border-soft);
  color: var(--pyt-color-text-hint);
  font-size: 12px;
}

code {
  font-family: var(--pyt-font-mono);
  background: var(--pyt-color-bg-soft);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
</style>
