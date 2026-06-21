<script setup lang="ts">
/**
 * 操作指南页（/guide）：分页式图文手册。
 * 每个大主题一页（?topic=<id> 驱动），桌面三栏 = 左主题导航 + 中正文 + 右本页大纲，
 * 底部「上一篇 / 下一篇」翻页；移动端导航可折叠。
 * 内容由 content/guideSections.ts 结构化驱动；像素风仅用于边框、徽标、装饰角。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import GuideSidebar from '@/components/guide/GuideSidebar.vue';
import GuideSection from '@/components/guide/GuideSection.vue';
import {
  GUIDE_NAV_GROUPS,
  GUIDE_SECTIONS,
} from '@/content/guideSections';

const route = useRoute();
const router = useRouter();

const sections = GUIDE_SECTIONS;
const navGroups = GUIDE_NAV_GROUPS;
const navOpen = ref(false);
// 正文列内部滚动（整页自适应窗口高度）：切主题时滚回该容器顶部，而非滚 window。
const contentEl = ref<HTMLElement | null>(null);

/** 小节锚点归属的主题 id（点其他主题的小节锚点时先切页再滚动）。 */
function ownerSectionId(anchorId: string): string {
  for (const section of sections) {
    if (section.id === anchorId) return section.id;
    if (section.subsections.some((sub) => sub.id === anchorId)) return section.id;
  }
  return anchorId;
}

/** 当前主题（?topic= 驱动；无效/缺省回退第一个主题）。 */
const activeId = computed<string>(() => {
  const topic = typeof route.query.topic === 'string' ? route.query.topic : '';
  return sections.some((s) => s.id === topic) ? topic : (sections[0]?.id ?? '');
});

const activeIndex = computed(() => Math.max(0, sections.findIndex((s) => s.id === activeId.value)));
// GUIDE_SECTIONS 静态非空，此处断言安全；空数据是内容文件被清空的编程错误，构建期即暴露。
const activeSection = computed(() => (sections[activeIndex.value] ?? sections[0])!);
const prevSection = computed(() => (activeIndex.value > 0 ? sections[activeIndex.value - 1] : null));
const nextSection = computed(() =>
  (activeIndex.value < sections.length - 1 ? sections[activeIndex.value + 1] : null));

// ── 右侧大纲 scroll-spy：下滑时自动高亮当前正在阅读的小节锚点 ──
const activeAnchor = ref<string>('');
let outlineSpy: IntersectionObserver | null = null;

/** 当前主题正文里所有可定位锚点（主题标题 + 各小节标题）。 */
function activeAnchorIds(): string[] {
  const section = activeSection.value;
  return [section.id, ...section.subsections.map((sub) => sub.id)];
}

function setupScrollSpy(): void {
  outlineSpy?.disconnect();
  if (typeof IntersectionObserver !== 'function') return;
  const tops = new Map<string, number>();
  outlineSpy = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          tops.set(entry.target.id, entry.boundingClientRect.top);
        } else {
          tops.delete(entry.target.id);
        }
      }
      // 取视口内最靠上的标题为「正在读」的锚点。
      let best = '';
      let bestTop = Number.POSITIVE_INFINITY;
      tops.forEach((top, id) => {
        if (top < bestTop) {
          bestTop = top;
          best = id;
        }
      });
      if (best) activeAnchor.value = best;
    },
    { rootMargin: '-84px 0px -68% 0px', threshold: 0 },
  );
  for (const id of activeAnchorIds()) {
    const el = document.getElementById(id);
    if (el) outlineSpy.observe(el);
  }
}

onMounted(() => {
  void nextTick(setupScrollSpy);
});

// 切主题后小节集合变化：重置高亮并对新页重新观察。
watch(activeId, () => {
  activeAnchor.value = '';
  void nextTick(setupScrollSpy);
});

onBeforeUnmount(() => {
  outlineSpy?.disconnect();
  outlineSpy = null;
});

function prefersReducedMotion(): boolean {
  return (
    typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

function scrollToAnchor(anchorId: string): void {
  const el = document.getElementById(anchorId);
  if (el && typeof el.scrollIntoView === 'function') {
    el.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'start' });
  }
}

function scrollToTop(): void {
  const behavior = prefersReducedMotion() ? 'auto' : 'smooth';
  const el = contentEl.value;
  if (el && typeof el.scrollTo === 'function') {
    el.scrollTo({ top: 0, behavior });
  } else if (typeof window.scrollTo === 'function') {
    window.scrollTo({ top: 0, behavior });
  }
}

/** 锚点导航：必要时先切换主题页，再保留 hash 并滚到目标。 */
async function goTo(anchorId: string): Promise<void> {
  const owner = ownerSectionId(anchorId);
  navOpen.value = false;
  const isTopicSwitch = owner !== activeId.value;
  const hash = `#${anchorId}`;
  if (isTopicSwitch || route.hash !== hash) {
    await router.push({ query: { ...route.query, topic: owner }, hash });
  }
  requestAnimationFrame(() => scrollToAnchor(anchorId));
}

async function goToPage(sectionId: string): Promise<void> {
  navOpen.value = false;
  if (sectionId !== activeId.value || route.hash) {
    await router.push({ query: { ...route.query, topic: sectionId }, hash: '' });
  }
  scrollToTop();
}

// 直链 #小节 进入时（含切页后），滚到对应锚点。
watch(
  () => route.hash,
  (hash) => {
    const anchorId = hash.slice(1);
    if (anchorId && ownerSectionId(anchorId) === activeId.value) {
      requestAnimationFrame(() => scrollToAnchor(anchorId));
    }
  },
  { immediate: true },
);
</script>

<template>
  <AppLayout>
    <div class="guide-page">
      <div
        class="guide-layout"
        :class="{ 'guide-layout--nav-open': navOpen }"
      >
        <div class="guide-mobile-bar">
          <button
            type="button"
            class="guide-nav-toggle"
            data-testid="guide-nav-toggle"
            :aria-expanded="navOpen"
            aria-controls="guide-nav"
            @click="navOpen = !navOpen"
          >
            <span
              class="guide-nav-toggle__icon"
              aria-hidden="true"
            />
            章节导航
          </button>
        </div>

        <GuideSidebar
          class="guide-nav-area"
          :sections="sections"
          :groups="navGroups"
          :active-id="activeId"
          @select="goTo"
        />

        <article
          ref="contentEl"
          class="guide-content"
          data-testid="guide-content"
          aria-label="操作指南正文"
        >
          <GuideSection
            :key="activeSection.id"
            :section="activeSection"
            :index="activeIndex"
            @navigate="goTo"
          />

          <nav
            class="guide-pager"
            aria-label="指南翻页"
            data-testid="guide-pager"
          >
            <button
              v-if="prevSection"
              type="button"
              class="guide-pager__btn guide-pager__btn--prev"
              data-testid="guide-pager-prev"
              @click="goToPage(prevSection.id)"
            >
              <span class="guide-pager__dir pyt-pixel-text">← 上一篇</span>
              <span class="guide-pager__title">{{ prevSection.navTitle }}</span>
            </button>
            <span
              v-else
              class="guide-pager__spacer"
              aria-hidden="true"
            />
            <button
              v-if="nextSection"
              type="button"
              class="guide-pager__btn guide-pager__btn--next"
              data-testid="guide-pager-next"
              @click="goToPage(nextSection.id)"
            >
              <span class="guide-pager__dir pyt-pixel-text">下一篇 →</span>
              <span class="guide-pager__title">{{ nextSection.navTitle }}</span>
            </button>
          </nav>

          <footer class="guide-footnote">
            <p>
              截图由集成阶段统一生成后落盘；某张截图尚未就绪时，页面会以文字占位说明替代，不影响指南阅读。
            </p>
            <p>
              在游戏中迷路时，随时按 <kbd class="guide-footnote__kbd">ESC</kbd>
              打开暂停菜单，选「系统 → 操作帮助」，或点小镇底部工具条的「指南」，都能回到本页。
            </p>
          </footer>
        </article>

        <nav
          class="guide-outline"
          aria-label="指南二级目录"
          data-testid="guide-outline"
        >
          <p class="guide-outline__label pyt-pixel-text">
            二级目录
          </p>
          <ol class="guide-outline__list">
            <li class="guide-outline__group">
              <a
                class="guide-outline__link guide-outline__link--active"
                :href="`#${activeSection.id}`"
                aria-current="location"
                @click.prevent="goTo(activeSection.id)"
              >
                {{ activeSection.navTitle }}
              </a>
              <ol class="guide-outline__subs">
                <li
                  v-for="sub in activeSection.subsections"
                  :key="sub.id"
                >
                  <a
                    class="guide-outline__sublink"
                    :class="{ 'guide-outline__sublink--active': sub.id === activeAnchor }"
                    :href="`#${sub.id}`"
                    :aria-current="sub.id === activeAnchor ? 'location' : undefined"
                    @click.prevent="goTo(sub.id)"
                  >
                    {{ sub.title }}
                  </a>
                </li>
              </ol>
            </li>
          </ol>
        </nav>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
/* 整页自适应窗口高度：页面高度锁到视口（减页头），内部分区各自滚动，不让整页长滚。 */
.guide-page {
  height: calc(100dvh - 64px);
  overflow: hidden;
  background: var(--pyt-color-bg-primary);
}

.guide-layout {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  grid-template-areas: 'nav main';
  gap: var(--pyt-space-4) var(--pyt-space-5);
  max-width: 1320px;
  height: 100%;
  margin: 0 auto;
  padding: var(--pyt-space-5) var(--pyt-space-6) 0;
}

/* ── 移动端导航开关（桌面隐藏） ─────────────────────────────── */
.guide-mobile-bar {
  display: none;
}

.guide-nav-toggle {
  display: none;
  align-items: center;
  gap: var(--pyt-space-2);
  align-self: flex-start;
  min-height: 44px;
  padding: 8px 16px;
  font-family: var(--pyt-font-read);
  font-size: 15px;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  box-shadow: 2px 2px 0 var(--pyt-color-border-soft);
  cursor: pointer;
}

.guide-nav-toggle__icon {
  display: inline-block;
  width: 14px;
  height: 12px;
  background: repeating-linear-gradient(
    180deg,
    var(--pyt-color-primary-dark) 0 2px,
    transparent 2px 5px
  );
}

/* ── 左侧章节导航 ───────────────────────────────────────────── */
.guide-nav-area {
  grid-area: nav;
  align-self: stretch;
  min-height: 0;
  overflow-y: auto;
  padding-right: var(--pyt-space-2);
  padding-bottom: var(--pyt-space-6);
}

/* ── 中间正文列（内部滚动） ─────────────────────────────────── */
.guide-content {
  grid-area: main;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  max-width: 820px;
  padding-bottom: var(--pyt-space-6);
  font-family: var(--pyt-font-read);
  font-size: 17px;
  line-height: 1.8;
  color: var(--pyt-color-text-primary);
  /* 学习内容必须可选中复制 */
  user-select: text;
}

/* ── 底部翻页 ───────────────────────────────────────────────── */
.guide-pager {
  display: flex;
  justify-content: space-between;
  gap: var(--pyt-space-3);
  margin-top: var(--pyt-space-6);
}

.guide-pager__spacer {
  flex: 1 1 0;
}

.guide-pager__btn {
  flex: 1 1 0;
  max-width: 46%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--pyt-space-3) var(--pyt-space-4);
  font-family: var(--pyt-font-read);
  text-align: left;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  box-shadow: 3px 3px 0 var(--pyt-color-border-soft);
  cursor: pointer;
  transition: background var(--pyt-motion-base) ease;
}

.guide-pager__btn--next {
  text-align: right;
  align-items: flex-end;
}

.guide-pager__btn:hover {
  background: var(--pyt-color-bg-soft);
}

.guide-pager__dir {
  font-size: 12px;
  color: var(--pyt-color-warning);
}

.guide-pager__title {
  font-size: 16px;
  font-weight: 700;
  color: var(--pyt-color-primary-dark);
}

.guide-footnote {
  margin-top: var(--pyt-space-6);
  padding: var(--pyt-space-4);
  border: 1px solid var(--pyt-color-border-soft);
  background: var(--pyt-color-bg-elevated);
  font-size: 14px;
  line-height: 1.7;
  color: var(--pyt-color-text-muted);
}

.guide-footnote p {
  margin: 0 0 var(--pyt-space-2);
}

.guide-footnote p:last-child {
  margin-bottom: 0;
}

.guide-footnote__kbd {
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  padding: 1px 6px;
  color: var(--pyt-color-primary-dark);
  background: rgba(241, 196, 83, 0.18);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
}

/* ── 页内二级目录：已并入左侧一级目录（GuideSidebar 常显当前主题子节）。
   此块仍保留在 DOM 中（直链锚点 / 测试契约），但视觉隐藏，不再单独占列或顶部横排。 ── */
.guide-outline {
  display: none;
}

.guide-outline__label {
  margin: 0 0 var(--pyt-space-2);
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--pyt-color-text-muted);
}

.guide-outline__list,
.guide-outline__subs {
  list-style: none;
  margin: 0;
  padding: 0;
}

.guide-outline__group {
  margin-bottom: var(--pyt-space-2);
}

.guide-outline__link {
  display: block;
  padding: 3px 0;
  color: var(--pyt-color-text-hint);
  text-decoration: none;
  line-height: 1.5;
  transition: color var(--pyt-motion-base) ease;
}

.guide-outline__link--active {
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
}

.guide-outline__subs {
  padding-left: var(--pyt-space-3);
  border-left: 1px solid var(--pyt-color-border-soft);
}

.guide-outline__sublink {
  display: block;
  padding: 2px 0;
  font-size: 13px;
  color: var(--pyt-color-text-muted);
  text-decoration: none;
  line-height: 1.6;
  transition: color var(--pyt-motion-base) ease;
}

.guide-outline__link:hover,
.guide-outline__sublink:hover {
  color: var(--pyt-color-warning);
}

/* scroll-spy：下滑时高亮当前正在阅读的小节 */
.guide-outline__sublink--active {
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
  border-left: 2px solid var(--pyt-color-gold);
  margin-left: -2px;
  padding-left: 6px;
}

/* ── 响应式 ─────────────────────────────────────────────────── */
/* 中屏：保持两列（左一级+二级目录 / 右正文），仅收窄目录列。 */
@media (max-width: 1279px) {
  .guide-layout {
    grid-template-columns: 220px minmax(0, 1fr);
  }
}

/* 窄屏：单列 + 可折叠导航；回归整页自然滚动（移动端不做内部双滚动） */
@media (max-width: 1023px) {
  .guide-page {
    height: auto;
    min-height: calc(100dvh - 56px);
    overflow: visible;
  }

  .guide-layout {
    height: auto;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto auto auto;
    grid-template-areas:
      'toolbar'
      'nav'
      'main';
    padding: var(--pyt-space-4) var(--pyt-space-4) 72px;
  }

  .guide-mobile-bar {
    display: block;
    grid-area: toolbar;
  }

  .guide-nav-toggle {
    display: inline-flex;
  }

  .guide-nav-area {
    display: none;
    overflow: visible;
    padding: var(--pyt-space-3);
    border: 2px solid var(--pyt-color-border);
    background: var(--pyt-color-bg-elevated);
  }

  .guide-layout--nav-open .guide-nav-area {
    display: flex;
  }

  .guide-content {
    overflow: visible;
    font-size: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .guide-outline__link,
  .guide-outline__sublink {
    transition: none;
  }
}
</style>
