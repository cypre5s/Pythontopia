<script setup lang="ts">
/**
 * 主线全屏面板（按 Q 切换显示，ESC / 重按 Q 关闭）。
 * 内容：7 章树（每章 5 子任务 + 章末 Boss）+ 进度条 + 「去做」「挑战 BOSS」按钮。
 * 「去做」复用 fastTravel 路由跳到子任务对应地图；「挑战 BOSS」在 5 子任务全完成或本章达标后解锁，跳 /boss/{chapter}。
 */
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useMainQuestStore } from '@stores/mainQuest';
import { useLearningStore } from '@stores/learning';
import { useWorldStore } from '@stores/world';
import { useToastStore } from '@stores/toast';
import { FAST_TRAVEL_ROUTES, type FastTravelRoute } from '@/data/fastTravel';
import { humanizeKcText } from '@/data/kcNames';
import {
  mainSubQuestPhase,
  mainlineKcCodesForChapter,
  type MainQuestPhaseMeta,
} from '@/data/mainQuests';
import { CHAPTERS } from '@/data/chapters';
import type { MainSubQuestViewItem } from '@/types/mainQuest';

const mainQuest = useMainQuestStore();
const learning = useLearningStore();
const world = useWorldStore();
const toast = useToastStore();
const router = useRouter();

const visible = computed(() => mainQuest.panelOpen);

// 每个 PRACTICE_PASS 后端 +0.20 掌握度（0..1）== 0..100 标度的 +20。
const MASTERY_PER_PASS = 20;

function phaseOf(sub: MainSubQuestViewItem): MainQuestPhaseMeta {
  return mainSubQuestPhase(sub);
}

// 把 kc_mastery 子任务的隐藏阈值翻译成「当前→目标 + 约再做对 N 题」。
interface MasteryHint {
  current: number;
  target: number;
  remainingProblems: number;
  met: boolean;
}

const masteryHints = computed<Map<string, MasteryHint>>(() => {
  const map = new Map<string, MasteryHint>();
  for (const ch of mainQuest.chapterViews) {
    for (const sub of ch.subQuests) {
      if (sub.evidenceRequired.type !== 'kc_mastery') continue;
      const ev = sub.evidenceRequired;
      const snap = learning.field.masterySnapshot.find((s) => s.kcCode === ev.kcCode);
      const current = Math.round((snap?.masteryScore ?? 0) * 100);
      const target = ev.thresholdScore;
      const met = current >= target;
      map.set(sub.id, {
        current,
        target,
        met,
        remainingProblems: met ? 0 : Math.ceil((target - current) / MASTERY_PER_PASS),
      });
    }
  }
  return map;
});

// 章节达标双条件的显式拆解（必做真题全过 + 本章 KC 平均掌握度 ≥ 70%）。
interface ChapterGate {
  requiredDone: number;
  requiredTotal: number;
  masteryPct: number;
  masteryTargetPct: number;
  /** 本章是否有「测」锚点（第 1 章为纯理论导入章，无锚点）。 */
  hasAssessment: boolean;
  practiceMet: boolean;
  masteryMet: boolean;
  met: boolean;
  /** 软门槛：是否已看过本章图文 / 视频讲解（视频 / AI 动画 / 图解）。不硬阻塞推进。 */
  mediaConsumed: boolean;
}

const chapterGates = computed<Map<number, ChapterGate>>(() => {
  const map = new Map<number, ChapterGate>();
  for (const ch of mainQuest.chapterViews) {
    const required = ch.subQuests.filter((s) => s.evidenceRequired.type === 'submission');
    const requiredDone = required.filter((s) => s.status === 'completed').length;
    // 「测」改为锚点 KC 制：分母 = 本章锚点 KC、目标 = 锚点阈值（练完即测，可达）；第 1 章无锚点。
    const hasAssessment = mainQuest.chapterMasteryAnchors(ch.chapter).length > 0;
    const masteryRatio = mainQuest.chapterMasteryAvg(ch.chapter);
    const targetRatio = mainQuest.chapterMasteryTarget(ch.chapter);
    const practiceMet = requiredDone >= required.length;
    const masteryMet = masteryRatio >= targetRatio;
    map.set(ch.chapter, {
      requiredDone,
      requiredTotal: required.length,
      masteryPct: Math.round(masteryRatio * 100),
      masteryTargetPct: Math.round(targetRatio * 100),
      hasAssessment,
      practiceMet,
      masteryMet,
      met: mainQuest.chapterStandardMet(ch.chapter),
      mediaConsumed: mainQuest.chapterMediaConsumed(ch.chapter),
    });
  }
  return map;
});

// 每章动机帧（为什么先学）+ 主线核心 / 选修拓展 KC 计数。
interface ChapterIntro {
  motivation: string;
  mainlineCount: number;
  electiveCount: number;
}
const chapterIntros = computed<Map<number, ChapterIntro>>(() => {
  const map = new Map<number, ChapterIntro>();
  for (const ch of mainQuest.chapterViews) {
    const entry = CHAPTERS.find((c) => c.order === ch.chapter);
    const totalKc = mainQuest.chapterKcCodes(ch.chapter).length;
    const mainlineCount = mainlineKcCodesForChapter(ch.chapter).length;
    map.set(ch.chapter, {
      motivation: entry?.motivation ?? '',
      mainlineCount,
      electiveCount: Math.max(0, totalKc - mainlineCount),
    });
  }
  return map;
});

function close(): void {
  mainQuest.closePanel();
}

function findRoute(mapKey: string): FastTravelRoute | null {
  return FAST_TRAVEL_ROUTES.find((r) => r.toMap === mapKey) ?? null;
}

function teleportTo(sub: MainSubQuestViewItem): void {
  const route = findRoute(sub.mapKey);
  if (route && world.player) {
    world.player = {
      ...world.player,
      mapKey: route.toMap,
      tileX: route.tileX,
      tileY: route.tileY,
    };
  }
  mainQuest.closePanel();
  void router.push({ path: '/world' });
}

function fightBoss(chapter: number): void {
  mainQuest.queueBoss(chapter);
  mainQuest.closePanel();
  void router.push({ path: `/boss/${chapter}` });
}

function goEndless(): void {
  mainQuest.closePanel();
  void router.push({ path: '/endless' });
}

// Boss 解锁：5/5 子任务全完成「或」章节达标（双条件）均可。
function bossUnlocked(chapter: number, defeated: boolean): boolean {
  if (defeated || chapter !== mainQuest.progress.currentChapter) return false;
  const view = mainQuest.chapterViews[chapter - 1];
  const all5 = view ? view.subQuests.every((s) => s.status === 'completed') : false;
  return all5 || mainQuest.chapterStandardMet(chapter);
}

// 进阶 Hub（达标后出现）：进阶难题 / 工坊拓展 / 跳过进入下一章。
function goArena(chapter: number): void {
  mainQuest.closePanel();
  void router.push({ path: `/arena/${chapter}` });
}
function goWorkshop(): void {
  mainQuest.closePanel();
  void router.push({ path: '/workshop' });
}
function skipToNext(chapter: number): void {
  if (mainQuest.advanceChapter()) {
    toast.show(`第 ${chapter} 章已达标，进入第 ${chapter + 1} 章！`, 'success');
  }
}
</script>

<template>
  <section
    v-if="visible"
    class="mq-modal"
    data-testid="main-quest-panel"
    role="dialog"
    aria-modal="true"
    aria-labelledby="mq-modal-title"
    @click.self="close"
  >
    <div class="mq-card">
      <header class="mq-card__head">
        <h2
          id="mq-modal-title"
          class="mq-card__title"
        >
          主线任务 · 镇长 Py 实习计划
        </h2>
        <button
          type="button"
          class="mq-card__close"
          aria-label="关闭主线面板（ESC / Q）"
          @click="close"
        >
          ✕
        </button>
      </header>
      <div class="mq-card__progress">
        <span>整体进度</span>
        <div class="mq-progress-bar">
          <span
            class="mq-progress-bar__fill"
            :style="{ width: `${Math.round(mainQuest.overallProgress * 100)}%` }"
          />
        </div>
        <span class="mq-progress-pct">
          {{ Math.round(mainQuest.overallProgress * 100) }}%
        </span>
        <button
          type="button"
          class="mq-endless-btn"
          data-testid="main-quest-endless"
          title="无尽模式 · 连续 AI 出题 + 自适应难度"
          @click="goEndless"
        >
          无尽闯关
        </button>
      </div>
      <div
        class="mq-chapters"
        data-testid="main-quest-chapters"
      >
        <article
          v-for="ch in mainQuest.chapterViews"
          :key="ch.chapter"
          class="mq-chapter"
          :class="{
            'mq-chapter--active': ch.chapter === mainQuest.progress.currentChapter,
            'mq-chapter--done': ch.bossDefeated,
          }"
        >
          <header class="mq-chapter__head">
            <span class="mq-chapter__no">第 {{ ch.chapter }} 章</span>
            <span class="mq-chapter__name">{{ ch.title }}</span>
            <span class="mq-chapter__progress">
              <span class="mq-progress-bar mq-progress-bar--mini">
                <span
                  class="mq-progress-bar__fill"
                  :style="{ width: `${Math.round(ch.progress * 100)}%` }"
                />
              </span>
              <span>{{ ch.subQuests.filter((s) => s.status === 'completed').length }}/5</span>
            </span>
          </header>
          <!-- 动机帧（为什么先学）+ 选修/拓展标注 -->
          <p
            v-if="chapterIntros.get(ch.chapter)?.motivation"
            class="mq-motivation"
            :data-testid="`main-quest-motivation-${ch.chapter}`"
          >
            💡 {{ chapterIntros.get(ch.chapter)?.motivation }}
          </p>
          <p
            class="mq-elective"
            :data-testid="`main-quest-elective-${ch.chapter}`"
          >
            主线核心知识点 {{ chapterIntros.get(ch.chapter)?.mainlineCount ?? 0 }} 个<template
              v-if="chapterIntros.get(ch.chapter)?.electiveCount"
            >
              · 另有 {{ chapterIntros.get(ch.chapter)?.electiveCount }} 个为选修 / 拓展（不必全啃）
            </template>
          </p>
          <ul class="mq-quest-list">
            <li
              v-for="sub in ch.subQuests"
              :key="sub.id"
              class="mq-quest-item"
              :class="`mq-quest-item--${sub.status}`"
              :data-testid="`main-quest-sub-${sub.id}`"
            >
              <span class="mq-quest-status">
                <template v-if="sub.status === 'completed'">✔</template>
                <template v-else-if="sub.status === 'in_progress'">▶</template>
                <template v-else>•</template>
              </span>
              <div class="mq-quest-meta">
                <span class="mq-quest-titleline">
                  <span
                    class="mq-quest-phase"
                    :class="`mq-quest-phase--${phaseOf(sub).phase}`"
                    :title="phaseOf(sub).role"
                    :data-testid="`main-quest-phase-${sub.id}`"
                  >{{ phaseOf(sub).label }}</span>
                  <span class="mq-quest-title">{{ humanizeKcText(sub.title) }}</span>
                </span>
                <small class="mq-quest-hint">{{ humanizeKcText(sub.hint) }}</small>
                <small
                  v-if="masteryHints.get(sub.id)"
                  class="mq-quest-mastery"
                  :class="{ 'mq-quest-mastery--met': masteryHints.get(sub.id)!.met }"
                  :data-testid="`main-quest-mastery-${sub.id}`"
                >
                  <template v-if="masteryHints.get(sub.id)!.met">✓ 掌握进度已达标</template>
                  <template v-else>
                    掌握进度 {{ masteryHints.get(sub.id)!.current }} → 目标 {{ masteryHints.get(sub.id)!.target }}
                    （约再做对 {{ masteryHints.get(sub.id)!.remainingProblems }} 题）
                  </template>
                </small>
              </div>
              <button
                type="button"
                class="mq-quest-go"
                :data-testid="`main-quest-go-${sub.id}`"
                :disabled="sub.status === 'completed'"
                @click="teleportTo(sub)"
              >
                {{ sub.status === 'completed' ? '已完成' : '去做' }}
              </button>
            </li>
          </ul>
          <!-- 章节达标双条件硬验证显式拆解（练 + 测） -->
          <div
            class="mq-gate"
            :data-testid="`main-quest-gate-${ch.chapter}`"
          >
            <span class="mq-gate__label">达标条件</span>
            <span
              class="mq-gate__item"
              :class="{ 'mq-gate__item--ok': chapterGates.get(ch.chapter)?.practiceMet }"
            >
              练 · 必做真题 {{ chapterGates.get(ch.chapter)?.requiredDone ?? 0 }}/{{ chapterGates.get(ch.chapter)?.requiredTotal ?? 0 }}
            </span>
            <span
              class="mq-gate__item"
              :class="{ 'mq-gate__item--ok': chapterGates.get(ch.chapter)?.masteryMet }"
            >
              <template v-if="chapterGates.get(ch.chapter)?.hasAssessment">
                测 · 锚点知识点掌握 {{ chapterGates.get(ch.chapter)?.masteryPct ?? 0 }}%/{{ chapterGates.get(ch.chapter)?.masteryTargetPct ?? 0 }}%
              </template>
              <template v-else>
                测 · 理论导入章（无需达标）
              </template>
            </span>
            <span
              class="mq-gate__item mq-gate__item--soft"
              :class="{ 'mq-gate__item--ok': chapterGates.get(ch.chapter)?.mediaConsumed }"
              :data-testid="`main-quest-gate-media-${ch.chapter}`"
              title="软门槛：看过本章讲解视频 / AI 动画 / 知识点图解（不阻塞推进）"
            >
              看 · 图文·视频讲解 {{ chapterGates.get(ch.chapter)?.mediaConsumed ? '✓' : '建议' }}
            </span>
          </div>
          <footer class="mq-chapter__foot">
            <span class="mq-boss-label">章末 BOSS · {{ ch.boss.name }}</span>
            <button
              v-if="!ch.bossDefeated"
              type="button"
              class="mq-boss-btn"
              :data-testid="`main-quest-boss-${ch.chapter}`"
              :disabled="!bossUnlocked(ch.chapter, ch.bossDefeated)"
              @click="fightBoss(ch.chapter)"
            >
              {{ bossUnlocked(ch.chapter, ch.bossDefeated) ? '挑战 BOSS' : '达标 / 五任务全完成后解锁' }}
            </button>
            <span
              v-else
              class="mq-boss-cleared"
            >★ 已击败</span>
          </footer>

          <!-- 达标后进阶 Hub（当前章 + 已达标 + boss 未通关） -->
          <div
            v-if="ch.chapter === mainQuest.progress.currentChapter && mainQuest.chapterStandardMet(ch.chapter) && !ch.bossDefeated"
            class="mq-hub"
            :data-testid="`main-quest-hub-${ch.chapter}`"
          >
            <span class="mq-hub__badge">✓ 本章已达标（必做题全过 · 知识点掌握进度 ≥ 70%）</span>
            <p class="mq-hub__tip">
              可选进阶（不选也能直接进下一章）：
            </p>
            <div class="mq-hub__actions">
              <button
                type="button"
                class="mq-hub__btn"
                @click="goArena(ch.chapter)"
              >
                ⚔ 进阶难题
              </button>
              <button
                type="button"
                class="mq-hub__btn"
                @click="goWorkshop"
              >
                🛠 工坊拓展
              </button>
              <button
                type="button"
                class="mq-hub__btn mq-hub__btn--skip"
                :data-testid="`main-quest-skip-${ch.chapter}`"
                @click="skipToNext(ch.chapter)"
              >
                ⏭ 跳过，进入下一章
              </button>
            </div>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.mq-modal {
  position: fixed;
  inset: 0;
  background: rgba(8, 8, 12, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 220;
  pointer-events: auto;
}
.mq-card {
  width: min(960px, calc(100vw - 32px));
  max-height: calc(100vh - 64px);
  background: var(--pyt-panel-bg);
  border: 3px solid var(--pyt-panel-border);
  border-radius: 2px;
  box-shadow: 4px 4px 0 var(--pyt-panel-border-dark), 0 8px 24px var(--pyt-panel-shadow);
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  image-rendering: pixelated;
}
.mq-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
  border-bottom: 2px solid var(--pyt-panel-border);
  background: var(--pyt-panel-header-bg);
}
.mq-card__title {
  margin: 0;
  font-size: 16px;
  color: var(--pyt-text-accent);
  letter-spacing: 1px;
  text-shadow: 1px 1px 0 var(--pyt-color-shadow);
}
.mq-card__close {
  background: var(--pyt-btn-bg);
  border: 2px solid var(--pyt-btn-border);
  color: var(--pyt-text-primary);
  width: 28px;
  height: 28px;
  border-radius: 2px;
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
}
.mq-card__progress {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 18px;
  border-bottom: 2px dashed var(--pyt-panel-border-dark);
  font-size: 12px;
}
.mq-progress-bar {
  position: relative;
  display: inline-block;
  flex: 1 1 auto;
  height: 8px;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid var(--pyt-panel-border-dark);
  border-radius: 2px;
  overflow: hidden;
}
.mq-progress-bar--mini {
  width: 60px;
  flex: 0 0 60px;
  height: 6px;
}
.mq-progress-bar__fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--pyt-bar-energy), var(--pyt-bar-mastery));
}
.mq-progress-pct {
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-text-accent);
  min-width: 36px;
  text-align: right;
}
.mq-endless-btn {
  margin-left: 12px;
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  padding: 4px 12px;
  background: rgba(106, 161, 214, 0.2);
  color: var(--pyt-info);
  border: 2px solid var(--pyt-info);
  border-radius: 2px;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
  cursor: pointer;
}
.mq-endless-btn:hover {
  background: rgba(106, 161, 214, 0.35);
}
.mq-endless-btn:active {
  transform: translateY(2px);
  box-shadow: 0 0 0 var(--pyt-panel-border-dark);
}
.mq-chapters {
  flex: 1 1 auto;
  overflow: auto;
  padding: 12px 18px 18px;
  display: grid;
  gap: 12px;
}
.mq-chapter {
  background: rgba(0, 0, 0, 0.15);
  border: 2px solid var(--pyt-panel-border-dark);
  border-radius: 2px;
  padding: 10px 14px;
}
.mq-chapter--active {
  border-color: var(--pyt-panel-border);
  box-shadow: inset 0 0 0 1px var(--pyt-text-gold);
}
.mq-chapter--done {
  opacity: 0.78;
}
.mq-chapter__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.mq-chapter__no {
  font-weight: 700;
  font-size: 13px;
  color: var(--pyt-text-gold);
}
.mq-chapter__name {
  font-size: 13px;
  flex: 1 1 auto;
}
.mq-chapter__progress {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--pyt-text-secondary);
}
/* 动机帧 + 选修标注 */
.mq-motivation {
  margin: 2px 0 4px;
  padding: 6px 8px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--pyt-text-secondary);
  background: rgba(241, 196, 83, 0.1);
  border-left: 3px solid var(--pyt-text-accent);
  border-radius: 2px;
}
.mq-elective {
  margin: 0 0 4px;
  font-size: 10px;
  color: var(--pyt-text-secondary);
}
.mq-quest-list {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.mq-quest-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 4px 8px;
  border: 1px solid var(--pyt-panel-border-dark);
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.1);
}
.mq-quest-item--completed {
  background: rgba(107, 191, 89, 0.15);
  border-color: var(--pyt-bar-energy);
  color: var(--pyt-bar-energy);
}
.mq-quest-item--in_progress {
  background: rgba(241, 196, 83, 0.12);
  border-color: var(--pyt-panel-border);
}
.mq-quest-status {
  font-size: 14px;
  width: 18px;
  text-align: center;
  color: var(--pyt-text-gold);
}
.mq-quest-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.mq-quest-title {
  font-size: 12px;
  font-weight: 700;
}
.mq-quest-hint {
  font-size: 10px;
  color: var(--pyt-text-secondary);
}
.mq-quest-titleline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
/* 看/学/练/测 阶段徽章 */
.mq-quest-phase {
  flex: 0 0 auto;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  padding: 2px 5px;
  border-radius: 2px;
  border: 1px solid currentColor;
}
.mq-quest-phase--watch {
  color: #6aa1d6;
}
.mq-quest-phase--learn {
  color: var(--pyt-color-gold);
}
.mq-quest-phase--practice {
  color: #e07a3a;
}
.mq-quest-phase--assess {
  color: #6bbf59;
}
/* 掌握度透明化 */
.mq-quest-mastery {
  font-size: 10px;
  color: var(--pyt-text-gold);
}
.mq-quest-mastery--met {
  color: var(--pyt-bar-energy);
}
/* 章节达标双条件 */
.mq-gate {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  font-size: 11px;
}
.mq-gate__label {
  color: var(--pyt-text-secondary);
}
.mq-gate__item {
  padding: 2px 8px;
  border-radius: 2px;
  border: 1px solid var(--pyt-panel-border-dark);
  background: rgba(0, 0, 0, 0.12);
  color: var(--pyt-text-secondary);
}
.mq-gate__item--ok {
  border-color: var(--pyt-bar-energy);
  color: var(--pyt-bar-energy);
  background: rgba(107, 191, 89, 0.15);
}
/* 软门槛：多模态讲解为建议项，用虚线弱化以区别于硬性达标条件。 */
.mq-gate__item--soft {
  border-style: dashed;
}
.mq-quest-go {
  font-family: inherit;
  font-size: 11px;
  padding: 4px 10px;
  background: var(--pyt-btn-bg);
  color: var(--pyt-text-primary);
  border: 2px solid var(--pyt-btn-border);
  border-radius: 2px;
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
}
.mq-quest-go:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.mq-quest-go:active:not(:disabled) {
  transform: translateY(2px);
  box-shadow: 0 0 0 var(--pyt-panel-border-dark);
}
.mq-chapter__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed var(--pyt-panel-border-dark);
}
.mq-boss-label {
  font-size: 11px;
  color: var(--pyt-text-gold);
  font-weight: 700;
}
.mq-boss-btn {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 12px;
  background: rgba(230, 57, 70, 0.25);
  color: var(--pyt-text-primary);
  border: 2px solid var(--pyt-danger);
  border-radius: 2px;
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
}
.mq-boss-btn:disabled {
  background: rgba(0, 0, 0, 0.15);
  color: var(--pyt-text-secondary);
  border-color: var(--pyt-panel-border-dark);
  box-shadow: none;
  cursor: not-allowed;
}
.mq-boss-cleared {
  font-size: 12px;
  color: var(--pyt-bar-energy);
  font-weight: 700;
}
.mq-hub {
  margin-top: 8px;
  padding: 8px 10px;
  border: 2px solid var(--pyt-text-gold);
  border-radius: 2px;
  background: rgba(241, 196, 83, 0.12);
  display: grid;
  gap: 6px;
}
.mq-hub__badge {
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-bar-energy);
}
.mq-hub__tip {
  margin: 0;
  font-size: 11px;
  color: var(--pyt-text-secondary);
}
.mq-hub__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.mq-hub__btn {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 12px;
  background: var(--pyt-btn-bg);
  color: var(--pyt-text-primary);
  border: 2px solid var(--pyt-btn-border);
  border-radius: 2px;
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
}
.mq-hub__btn:hover {
  filter: brightness(1.08);
}
.mq-hub__btn:active {
  transform: translateY(2px);
  box-shadow: 0 0 0 var(--pyt-panel-border-dark);
}
.mq-hub__btn--skip {
  background: rgba(107, 191, 89, 0.25);
  border-color: var(--pyt-bar-energy);
}
@media (max-width: 760px) {
  .mq-card {
    width: calc(100vw - 16px);
    max-height: calc(100vh - 24px);
  }
  .mq-quest-item {
    grid-template-columns: auto 1fr;
  }
  .mq-quest-go {
    grid-column: 1 / -1;
    justify-self: stretch;
  }
}
</style>
