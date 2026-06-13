<script setup lang="ts">
/** 指南单个主题：标题锚点 + 概述 + 主截图 + 闭环说明 + 小节正文，由结构化数据驱动。 */
import GuideScreenshot from './GuideScreenshot.vue';
import type { GuideSectionContent } from '@/content/guideSections';

defineProps<{
  section: GuideSectionContent;
  index: number;
}>();

const emit = defineEmits<{
  (e: 'navigate', anchorId: string): void;
}>();

function pad(value: number): string {
  return String(value).padStart(2, '0');
}
</script>

<template>
  <section
    :id="section.id"
    class="guide-section"
    :data-testid="`guide-section-${section.id}`"
    :aria-labelledby="`${section.id}-title`"
  >
    <header class="guide-section__head">
      <span
        class="guide-section__index pyt-pixel-text"
        aria-hidden="true"
      >{{ pad(index + 1) }}</span>
      <h2
        :id="`${section.id}-title`"
        class="guide-section__title"
      >
        {{ section.title }}
        <a
          class="guide-anchor"
          :href="`#${section.id}`"
          :aria-label="`「${section.navTitle}」主题的锚点链接`"
          @click.prevent="emit('navigate', section.id)"
        >#</a>
      </h2>
      <p class="guide-section__summary">
        {{ section.summary }}
      </p>
    </header>

    <GuideScreenshot
      :src="section.heroShot.src"
      :alt="section.heroShot.alt"
      :caption="section.heroShot.caption"
    />

    <aside
      class="guide-loop"
      data-testid="guide-loop-relation"
    >
      <span class="guide-loop__badge pyt-pixel-text">看学练测</span>
      <p class="guide-loop__text">
        {{ section.loop }}
      </p>
    </aside>

    <div
      v-for="sub in section.subsections"
      :key="sub.id"
      class="guide-subsection"
    >
      <h3
        :id="sub.id"
        class="guide-subsection__title"
      >
        {{ sub.title }}
        <a
          class="guide-anchor"
          :href="`#${sub.id}`"
          :aria-label="`「${sub.title}」小节的锚点链接`"
          @click.prevent="emit('navigate', sub.id)"
        >#</a>
      </h3>

      <template
        v-for="(block, blockIndex) in sub.blocks"
        :key="`${sub.id}-${blockIndex}`"
      >
        <p
          v-if="block.kind === 'paragraph'"
          class="guide-p"
        >
          {{ block.text }}
        </p>

        <ul
          v-else-if="block.kind === 'list'"
          class="guide-list"
        >
          <li
            v-for="item in block.items"
            :key="item"
          >
            {{ item }}
          </li>
        </ul>

        <ol
          v-else-if="block.kind === 'steps'"
          class="guide-steps"
        >
          <li
            v-for="item in block.items"
            :key="item"
          >
            {{ item }}
          </li>
        </ol>

        <table
          v-else-if="block.kind === 'keys'"
          class="guide-keys"
        >
          <thead>
            <tr>
              <th scope="col">按键</th>
              <th scope="col">作用</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in block.rows"
              :key="row.keys"
            >
              <td><kbd class="guide-kbd pyt-pixel-text">{{ row.keys }}</kbd></td>
              <td>{{ row.desc }}</td>
            </tr>
          </tbody>
        </table>

        <GuideScreenshot
          v-else-if="block.kind === 'screenshot'"
          :src="block.shot.src"
          :alt="block.shot.alt"
          :caption="block.shot.caption"
        />

        <figure
          v-else-if="block.kind === 'diagram'"
          class="guide-diagram"
          data-testid="guide-diagram"
        >
          <figcaption
            v-if="block.title"
            class="guide-diagram__title pyt-pixel-text"
          >
            {{ block.title }}
          </figcaption>
          <ol
            class="guide-diagram__flow"
            :class="`guide-diagram__flow--${block.flow}`"
          >
            <li
              v-for="(node, i) in block.nodes"
              :key="i"
              class="guide-diagram__node"
            >
              <span
                v-if="node.icon"
                class="guide-diagram__icon"
                aria-hidden="true"
              >{{ node.icon }}</span>
              <span class="guide-diagram__label">{{ node.label }}</span>
              <span
                v-if="node.desc"
                class="guide-diagram__desc"
              >{{ node.desc }}</span>
            </li>
          </ol>
          <figcaption
            v-if="block.caption"
            class="guide-diagram__caption"
          >
            {{ block.caption }}
          </figcaption>
        </figure>

        <div
          v-else-if="block.kind === 'download'"
          class="guide-downloads"
          data-testid="guide-downloads"
        >
          <a
            v-for="dl in block.items"
            :key="dl.href"
            class="guide-download"
            :href="dl.href"
            download
            :data-testid="`guide-download-${dl.platform}`"
          >
            <span
              v-if="dl.icon"
              class="guide-download__icon"
              aria-hidden="true"
            >{{ dl.icon }}</span>
            <span class="guide-download__body">
              <span class="guide-download__label">{{ dl.label }}</span>
              <span
                v-if="dl.note"
                class="guide-download__note"
              >{{ dl.note }}</span>
            </span>
            <span
              class="guide-download__arrow"
              aria-hidden="true"
            >↓</span>
          </a>
        </div>

        <aside
          v-else
          class="guide-tip"
        >
          <span class="guide-tip__badge pyt-pixel-text">提示</span>
          <p class="guide-tip__text">
            {{ block.text }}
          </p>
        </aside>
      </template>
    </div>
  </section>
</template>

<style scoped>
.guide-section {
  padding: var(--pyt-space-6) 0;
  border-bottom: 2px dashed var(--pyt-color-border-soft);
  /* 锚点定位时给吸顶元素留出空间 */
  scroll-margin-top: 88px;
}

.guide-section:last-of-type {
  border-bottom: none;
}

.guide-section__head {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}

.guide-section__index {
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--pyt-color-warning);
}

.guide-section__title {
  margin: 0;
  font-size: 24px;
  line-height: 1.4;
  color: var(--pyt-color-primary-dark);
}

.guide-section__summary {
  margin: 0;
  font-size: 17px;
  line-height: 1.75;
  color: var(--pyt-color-text-hint);
}

/* 标题锚点：常驻淡显，悬停/聚焦增强，方便复制小节直链 */
.guide-anchor {
  margin-left: var(--pyt-space-2);
  color: var(--pyt-color-border);
  text-decoration: none;
  font-weight: 400;
  transition: color var(--pyt-motion-base) ease;
}

.guide-anchor:hover,
.guide-anchor:focus-visible {
  color: var(--pyt-color-warning);
}

/* 学习闭环说明：左侧金色像素条 + 角标 */
.guide-loop {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--pyt-space-3);
  margin: var(--pyt-space-4) 0 var(--pyt-space-2);
  padding: var(--pyt-space-3) var(--pyt-space-4);
  border: 2px solid var(--pyt-color-border-strong);
  border-left: 6px solid var(--pyt-color-gold);
  background: var(--pyt-color-bg-soft);
  box-shadow: 3px 3px 0 var(--pyt-color-border-soft);
}

.guide-loop__badge {
  flex-shrink: 0;
  font-size: 12px;
  padding: 2px 8px;
  color: var(--pyt-color-text-on-dark);
  background: var(--pyt-color-primary-dark);
  border: 1px solid var(--pyt-color-border-strong);
}

.guide-loop__text {
  margin: 0;
  font-size: 16px;
  line-height: 1.75;
  color: var(--pyt-color-text-primary);
}

.guide-subsection {
  margin-top: var(--pyt-space-5);
}

.guide-subsection__title {
  margin: 0 0 var(--pyt-space-3);
  font-size: 19px;
  line-height: 1.5;
  color: var(--pyt-color-primary-dark);
  scroll-margin-top: 88px;
}

.guide-p {
  margin: 0 0 var(--pyt-space-3);
  font-size: 17px;
  line-height: 1.8;
}

.guide-list,
.guide-steps {
  margin: 0 0 var(--pyt-space-3);
  padding-left: 1.5em;
  font-size: 17px;
  line-height: 1.8;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-1);
}

.guide-list li::marker {
  color: var(--pyt-color-warning);
}

.guide-steps li::marker {
  color: var(--pyt-color-warning);
  font-weight: 700;
}

.guide-keys {
  width: 100%;
  margin: 0 0 var(--pyt-space-3);
  border-collapse: collapse;
  font-size: 16px;
  line-height: 1.6;
}

.guide-keys th,
.guide-keys td {
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border: 1px solid var(--pyt-color-border);
  text-align: left;
  vertical-align: top;
}

.guide-keys th {
  background: var(--pyt-color-bg-soft);
  font-size: 14px;
  color: var(--pyt-color-text-hint);
}

.guide-keys tbody tr:nth-child(even) {
  background: var(--pyt-color-bg-elevated);
}

.guide-kbd {
  display: inline-block;
  font-size: 13px;
  padding: 1px 8px;
  white-space: nowrap;
  color: var(--pyt-color-primary-dark);
  background: rgba(241, 196, 83, 0.18);
  border: 1px solid var(--pyt-color-border);
  border-bottom-width: 2px;
  border-radius: var(--pyt-radius-sm);
}

.guide-tip {
  display: flex;
  align-items: flex-start;
  gap: var(--pyt-space-3);
  margin: 0 0 var(--pyt-space-3);
  padding: var(--pyt-space-3) var(--pyt-space-4);
  border: 2px dashed var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
}

.guide-tip__badge {
  flex-shrink: 0;
  font-size: 12px;
  padding: 2px 8px;
  color: var(--pyt-color-primary-dark);
  background: var(--pyt-color-gold-bright);
  border: 1px solid var(--pyt-color-border-strong);
}

.guide-tip__text {
  margin: 0;
  font-size: 16px;
  line-height: 1.75;
  color: var(--pyt-color-text-primary);
}

/* ── 原生客户端下载按钮（APK / EXE） ── */
.guide-downloads {
  display: flex;
  flex-wrap: wrap;
  gap: var(--pyt-space-3);
  margin: 0 0 var(--pyt-space-4);
}
.guide-download {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-3);
  min-width: 240px;
  flex: 1 1 240px;
  padding: var(--pyt-space-3) var(--pyt-space-4);
  text-decoration: none;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  box-shadow: 3px 3px 0 var(--pyt-color-border-soft);
  transition:
    background var(--pyt-motion-base) ease,
    transform var(--pyt-motion-base) ease;
}
.guide-download:hover {
  background: var(--pyt-color-bg-soft);
}
.guide-download:active {
  transform: translate(2px, 2px);
  box-shadow: 1px 1px 0 var(--pyt-color-border-soft);
}
.guide-download__icon {
  font-size: 26px;
  line-height: 1;
}
.guide-download__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}
.guide-download__label {
  font-size: 16px;
  font-weight: 700;
  color: var(--pyt-color-primary-dark);
}
.guide-download__note {
  font-size: 13px;
  color: var(--pyt-color-text-muted);
}
.guide-download__arrow {
  font-size: 18px;
  font-weight: 700;
  color: var(--pyt-color-warning);
}

/* ── 可视化流程图（纯 CSS，无依赖；row 横向 / col 纵向，节点用箭头串联） ── */
.guide-diagram {
  margin: 0 0 var(--pyt-space-4);
  padding: var(--pyt-space-4);
  border: 2px solid var(--pyt-color-border-strong);
  background: var(--pyt-color-bg-soft);
  box-shadow: 3px 3px 0 var(--pyt-color-border-soft);
}
.guide-diagram__title {
  margin: 0 0 var(--pyt-space-3);
  font-size: 13px;
  letter-spacing: 1px;
  color: var(--pyt-color-warning);
}
.guide-diagram__flow {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  gap: var(--pyt-space-4);
  align-items: stretch;
}
.guide-diagram__flow--row {
  flex-direction: row;
  flex-wrap: wrap;
}
.guide-diagram__flow--col {
  flex-direction: column;
}
.guide-diagram__node {
  position: relative;
  flex: 1 1 0;
  min-width: 96px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--pyt-space-3);
  text-align: center;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  box-shadow: 2px 2px 0 var(--pyt-color-border-soft);
}
.guide-diagram__flow--row .guide-diagram__node:not(:last-child)::after {
  content: '→';
  position: absolute;
  right: calc(-1 * var(--pyt-space-4));
  top: 50%;
  transform: translate(-2px, -50%);
  color: var(--pyt-color-warning);
  font-weight: 700;
}
.guide-diagram__flow--col .guide-diagram__node:not(:last-child)::after {
  content: '↓';
  position: absolute;
  bottom: calc(-1 * var(--pyt-space-4));
  left: 50%;
  transform: translate(-50%, -2px);
  color: var(--pyt-color-warning);
  font-weight: 700;
}
.guide-diagram__icon {
  font-size: 22px;
  line-height: 1;
}
.guide-diagram__label {
  font-size: 15px;
  font-weight: 700;
  color: var(--pyt-color-primary-dark);
}
.guide-diagram__desc {
  font-size: 13px;
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
}
.guide-diagram__caption {
  margin-top: var(--pyt-space-3);
  font-size: 13px;
  color: var(--pyt-color-text-muted);
}
@media (max-width: 640px) {
  .guide-diagram__flow--row {
    flex-direction: column;
  }
  .guide-diagram__flow--row .guide-diagram__node:not(:last-child)::after {
    content: '↓';
    right: auto;
    top: auto;
    bottom: calc(-1 * var(--pyt-space-4));
    left: 50%;
    transform: translate(-50%, -2px);
  }
}
</style>
