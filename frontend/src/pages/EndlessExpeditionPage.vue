<script setup lang="ts">
/**
 * /expedition 无尽远征（Roguelike）。
 *
 * 取代「7 章 × 10 关」死网格：知识前沿（掌握度 + 画像）生成分支地图，玩家选路闯关，
 * 战斗节点真判题；对得分攒气势、错按韧性温和扣血，HP 归零结束，打通 boss 胜利。
 * 章节为可选「聚焦」过滤器，默认全局自适应。详见 docs/plans/2026-06-19-endless-roguelike-rebuild.md。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PyButton from '@/components/ui/PyButton.vue';
import BossSprite from '@/components/boss/BossSprite.vue';
import ChallengeWorkspace from '@/components/challenge/ChallengeWorkspace.vue';
import KnowledgeConstellation from '@/components/expedition/KnowledgeConstellation.vue';
import ExpeditionCanvas from '@/components/expedition/ExpeditionCanvas.vue';
import ExpeditionLeaderboard from '@/components/expedition/ExpeditionLeaderboard.vue';
import { useRogueRunStore } from '@stores/rogueRun';
import { useLearningStore } from '@stores/learning';
import { kcDisplayName } from '@/data/kcNames';
import { NODE_KIND_ICON, NODE_KIND_LABEL } from '@/data/rogueConfig';
import { relicById } from '@/data/rogueRelics';
import { ROGUE_EVENT_CHOICES } from '@/data/rogueEvents';
import { MAIN_QUEST_BOSSES } from '@/data/bossCatalog';
import type { ChallengeResult } from '@/api/dto/CodeChallenge';
import type { RogueNode, RogueNodeKind } from '@/types/rogue';

// 地图图例固定顺序（与节点类型一一对应，供玩家快速看懂各房间含义）。
const LEGEND_KINDS: readonly RogueNodeKind[] = [
  'battle', 'elite', 'event', 'bug', 'chest', 'rest', 'boss',
];
const EXPEDITION_BUG_MINE_SECONDS = 75;

const route = useRoute();
const router = useRouter();
const run = useRogueRunStore();
const learning = useLearningStore();

// 知识星座掌握度映射（跨局 meta 进度）。
const masteryMap = computed<Record<string, number>>(() => {
  const m: Record<string, number> = {};
  for (const s of learning.field.masterySnapshot) m[s.kcCode] = s.masteryScore;
  return m;
});
const showConstellation = ref(false);
const showLeaderboard = ref(false);

// ?focus=章 来自旧 /arena 重定向或聚焦练习。
const focusFromQuery = computed<number | null>(() => {
  const raw = Number(route.query.focus);
  return Number.isInteger(raw) && raw >= 1 && raw <= 7 ? raw : null;
});
const focusChoice = ref<number | null>(run.focusChapter ?? focusFromQuery.value);

// 地图视图：像素远征塔（Phaser 渐进增强）/ 列表（移动端 + 降级真源）。窄屏默认列表。
const mapView = ref<'pixel' | 'list'>(
  typeof window !== 'undefined' && window.innerWidth >= 900 ? 'pixel' : 'list',
);

let timer: number | null = null;
onMounted(() => {
  if (typeof window !== 'undefined') timer = window.setInterval(() => run.tickSecond(), 1000);
});
onMounted(() => {
  // 跨设备/会话存档：只刷新可继续入口，不静默进入，让玩家自己选择继续或新开。
  if (run.phase === 'idle') void run.refreshSavedRun();
  // 班级排行榜：进入即拉一次（best-effort，失败静默降级）。
  void run.loadLeaderboard();
});
onBeforeUnmount(() => {
  if (timer !== null) window.clearInterval(timer);
});

function onStart(): void {
  void run.startRun(focusChoice.value);
}
/** 再来一局 / 弃局重开：显式新开，不续旧局（startRun 总是全量重建地图）。 */
function onRestart(): void {
  void run.startRun(focusChoice.value);
}
function onResume(): void {
  void run.resumeSavedRun();
}
/** 弃局：清后端活跃 run + 本地缓存，回到起始页。 */
function onAbandon(): void {
  void run.abort();
}
/** 保存并返回小镇：保留当前 run，下次回来可从存档继续。 */
function onExit(): void {
  if (run.phase !== 'idle' && run.phase !== 'cleared' && run.phase !== 'over') {
    run.saveCheckpoint();
  }
  void router.push('/world');
}
function onEnter(node: RogueNode): void {
  void run.enterNode(node.id);
}
function onEnterId(id: string): void {
  void run.enterNode(id);
}
function onSubmit(): void {
  void run.submitChallenge();
}
// 答题中「主动结束本局」→ 进结算（掌握度已入库不回滚）；「查看地图」只读弹窗。
const showMapModal = ref(false);
function onFinishRun(): void {
  showMapModal.value = false;
  run.finishRun();
}

const bossChapter = computed<number>(() => {
  // 聚焦章 → 该章 boss 立绘；否则按当前题目章节，兜底第 1 章。
  return run.focusChapter ?? run.activeProblem?.chapter ?? 1;
});
const bossName = computed<string>(
  () => MAIN_QUEST_BOSSES.find((b) => b.chapter === bossChapter.value)?.name ?? '知识守关者',
);

// 局内自适应难度展示：实际出题难度 + 相对基准的「↑气势 / ↓回稳」标签。
const shownDifficulty = computed<number>(() =>
  run.activeDifficulty > 0 ? run.activeDifficulty : run.activeNode?.difficulty ?? 0,
);
const difficultyTag = computed<string>(() => {
  const node = run.activeNode;
  if (!node || run.activeDifficulty <= 0) return '';
  if (run.activeDifficulty > node.difficulty) return '↑气势';
  if (run.activeDifficulty < node.difficulty) return '↓回稳';
  return '';
});

const challengeResult = computed<ChallengeResult | null>(() => {
  const r = run.challengeResult;
  if (!r) return null;
  return {
    passed: r.passed,
    passedCases: r.passedTestCases,
    totalCases: r.totalTestCases,
    verdict: r.passed ? 'AC' : 'WA',
    feedback: r.feedback,
    ...(r.failureFirstHint ? { firstHint: r.failureFirstHint } : {}),
  };
});

function star(n: number): string {
  return '★'.repeat(n) + '☆'.repeat(Math.max(0, 5 - n));
}
function relicIcon(id: string): string {
  return relicById(id)?.icon ?? '◆';
}
function relicName(id: string): string {
  return relicById(id)?.name ?? id;
}
function relicDesc(id: string): string {
  return relicById(id)?.desc ?? '';
}
function relicTitle(id: string): string {
  const r = relicById(id);
  return r ? `${r.name}：${r.desc}` : id;
}

// ── 分支连线可视化：测量节点中心，画 SVG 连边（类杀戮尖塔地图） ──
const mapInner = ref<HTMLElement | null>(null);
const nodeEls = new Map<string, HTMLElement>();
function setNodeEl(id: string, el: unknown): void {
  if (el instanceof HTMLElement) nodeEls.set(id, el);
  else nodeEls.delete(id);
}
interface EdgeSeg {
  id: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  cls: string;
}
const edges = ref<EdgeSeg[]>([]);
function computeEdges(): void {
  const inner = mapInner.value;
  if (!inner) {
    edges.value = [];
    return;
  }
  const ir = inner.getBoundingClientRect();
  const segs: EdgeSeg[] = [];
  for (const node of run.nodes) {
    const fromEl = nodeEls.get(node.id);
    if (!fromEl) continue;
    const fr = fromEl.getBoundingClientRect();
    const x1 = fr.left - ir.left + fr.width / 2;
    const y1 = fr.top - ir.top + fr.height;
    for (const nid of node.next) {
      const toEl = nodeEls.get(nid);
      if (!toEl) continue;
      const tr = toEl.getBoundingClientRect();
      const x2 = tr.left - ir.left + tr.width / 2;
      const y2 = tr.top - ir.top;
      const target = run.nodes.find((n) => n.id === nid);
      const cls = node.state === 'cleared'
        ? (target && (target.state === 'cleared' || target.state === 'available') ? 'is-open' : 'is-dim')
        : 'is-dim';
      segs.push({ id: `${node.id}->${nid}`, x1, y1, x2, y2, cls });
    }
  }
  edges.value = segs;
}
function scheduleCompute(): void {
  void nextTick(() => computeEdges());
}
onMounted(() => {
  scheduleCompute();
  if (typeof window !== 'undefined') window.addEventListener('resize', computeEdges);
});
onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('resize', computeEdges);
});
// 进入地图 / 节点状态变化（通关解锁）后重算连线。
watch(
  () => [run.phase, run.nodes.map((n) => n.state).join(',')],
  () => scheduleCompute(),
);
function nodeTooltip(node: RogueNode): string {
  const parts: string[] = [NODE_KIND_LABEL[node.kind]];
  if (node.kcCode) parts.push(kcDisplayName(node.kcCode));
  if (node.difficulty > 0) parts.push(`难度 ${node.difficulty}/5`);
  if (node.kind === 'rest') parts.push('恢复体力');
  if (node.kind === 'chest') parts.push('三选一学习增益');
  if (node.state === 'cleared') parts.push('已通关');
  else if (node.state === 'available') parts.push('可前往');
  else if (node.state === 'bypassed') parts.push('已绕过');
  else if (node.state === 'locked') parts.push('未解锁');
  return parts.join(' · ');
}

// 结算「本局练习小结」：按 KC 聚合 history 的对错（自包含、与判题真源一致，不依赖异步掌握度刷新）。
const kcSummary = computed<{ kcCode: string; passed: number; total: number }[]>(() => {
  const agg = new Map<string, { passed: number; total: number }>();
  for (const h of run.history) {
    if (!h.kcCode) continue;
    const cur = agg.get(h.kcCode) ?? { passed: 0, total: 0 };
    cur.total += 1;
    if (h.passed) cur.passed += 1;
    agg.set(h.kcCode, cur);
  }
  return [...agg.entries()]
    .map(([kcCode, v]) => ({ kcCode, passed: v.passed, total: v.total }))
    .sort((a, b) => b.total - a.total);
});

function nodeClass(node: RogueNode): Record<string, boolean> {
  return {
    'exp-node': true,
    [`exp-node--${node.kind}`]: true,
    'is-available': node.state === 'available',
    'is-cleared': node.state === 'cleared',
    'is-locked': node.state === 'locked',
    'is-bypassed': node.state === 'bypassed',
  };
}
</script>

<template>
  <AppLayout>
    <section
      class="exp-page"
      data-testid="expedition-page"
    >
      <header class="exp-head">
        <PyButton
          variant="secondary"
          size="sm"
          test-id="expedition-exit"
          @click="onExit"
        >
          {{ run.phase === 'idle' || run.phase === 'cleared' || run.phase === 'over' ? '← 返回小镇' : '保存并返回小镇' }}
        </PyButton>
        <h1 class="exp-title">
          无尽远征 · 知识前沿
        </h1>
        <div
          v-if="run.phase !== 'idle'"
          class="exp-stats"
        >
          <span
            class="exp-stat exp-stat--hp"
            data-testid="expedition-hp"
            :title="`体力 ${run.hp}/${run.maxHp}`"
          >
            <span class="exp-stat__label">体力</span>
            <span class="exp-stat__value">{{ '♥'.repeat(run.hp) }}{{ '♡'.repeat(Math.max(0, run.maxHp - run.hp)) }}</span>
          </span>
          <span class="exp-stat">
            <span class="exp-stat__label">气势</span>
            <span class="exp-stat__value">×{{ run.momentum }}</span>
          </span>
          <span
            class="exp-stat"
            data-testid="expedition-score"
          >
            <span class="exp-stat__label">得分</span>
            <span class="exp-stat__value">{{ run.score }}</span>
          </span>
          <span class="exp-stat">
            <span class="exp-stat__label">深度</span>
            <span class="exp-stat__value">{{ run.depth }}</span>
          </span>
        </div>
      </header>

      <div
        v-if="run.phase !== 'idle' && run.relics.length > 0"
        class="exp-relics"
        data-testid="expedition-relics"
      >
        <span class="exp-relics__label">学习增益</span>
        <span
          v-for="id in run.relics"
          :key="id"
          class="exp-relic"
          :title="relicTitle(id)"
        >{{ relicIcon(id) }}</span>
      </div>

      <!-- 起始：选聚焦章节 + 开始 -->
      <div
        v-if="run.phase === 'idle'"
        class="exp-start"
        data-testid="expedition-start"
      >
        <BossSprite
          :chapter="focusChoice ?? 1"
          phase="idle"
          :size="150"
        />
        <p class="exp-start__lead">
          按你的<strong>掌握度 + 画像</strong>生成分支远征：选路闯关、攒气势；答错温和扣体力，<strong>掌握度永久保留</strong>。
        </p>
        <div
          v-if="run.hasSavedRun && run.savedRunSummary"
          class="exp-save-card"
          data-testid="expedition-save-card"
        >
          <span class="exp-save-card__eyebrow">已有远征存档</span>
          <strong>{{ run.savedRunSummary.focusChapter ? `第 ${run.savedRunSummary.focusChapter} 章聚焦` : '全局自适应远征' }}</strong>
          <span>体力 {{ run.savedRunSummary.hp }}/{{ run.savedRunSummary.maxHp }} · 深度 {{ run.savedRunSummary.depth }} · 得分 {{ run.savedRunSummary.score }}</span>
          <span v-if="run.savedRunSummary.relicCount > 0">学习增益 {{ run.savedRunSummary.relicCount }} 件</span>
          <PyButton
            variant="primary"
            size="sm"
            test-id="expedition-resume"
            @click="onResume"
          >
            继续上次远征
          </PyButton>
        </div>
        <label class="exp-focus">
          <span>聚焦章节</span>
          <select
            v-model.number="focusChoice"
            data-testid="expedition-focus"
          >
            <option :value="null">
              全局自适应（按画像）
            </option>
            <option
              v-for="b in MAIN_QUEST_BOSSES"
              :key="b.chapter"
              :value="b.chapter"
            >
              第 {{ b.chapter }} 章 · {{ b.name }}
            </option>
          </select>
        </label>
        <div class="exp-actions">
          <PyButton
            variant="primary"
            test-id="expedition-begin"
            @click="onStart"
          >
            开始远征
          </PyButton>
          <PyButton
            variant="secondary"
            test-id="expedition-daily"
            :disabled="!run.dailyAvailable"
            @click="run.startDailyRun()"
          >
            {{ run.dailyAvailable ? '☀ 今日挑战 · 弱项' : '今日挑战已完成' }}
          </PyButton>
        </div>
        <button
          type="button"
          class="exp-constellation-toggle"
          data-testid="expedition-constellation-toggle"
          @click="showConstellation = !showConstellation"
        >
          {{ showConstellation ? '收起知识星座 ▴' : '✦ 查看知识星座（跨局进度）' }}
        </button>
        <KnowledgeConstellation
          v-if="showConstellation"
          :mastery="masteryMap"
        />
        <button
          type="button"
          class="exp-constellation-toggle"
          data-testid="expedition-leaderboard-toggle"
          @click="showLeaderboard = !showLeaderboard"
        >
          {{ showLeaderboard ? '收起班级排行榜 ▴' : '🏆 查看班级排行榜' }}
        </button>
        <ExpeditionLeaderboard
          v-if="showLeaderboard"
          :board="run.leaderboard"
        />
      </div>

      <!-- 地图：选节点 -->
      <div
        v-else-if="run.phase === 'map'"
        class="exp-map"
        data-testid="expedition-map"
      >
        <div class="exp-map__bar">
          <p class="exp-map__hint">
            点亮的房间可前往，选了同层就不回头——一路打到顶层 BOSS。
          </p>
          <div class="exp-map__tools">
            <div
              class="exp-view-toggle"
              role="group"
              aria-label="地图视图切换"
            >
              <button
                type="button"
                class="exp-view-btn"
                :class="{ 'is-active': mapView === 'pixel' }"
                :aria-pressed="mapView === 'pixel'"
                data-testid="expedition-view-pixel"
                @click="mapView = 'pixel'"
              >
                🗼 像素塔
              </button>
              <button
                type="button"
                class="exp-view-btn"
                :class="{ 'is-active': mapView === 'list' }"
                :aria-pressed="mapView === 'list'"
                data-testid="expedition-view-list"
                @click="mapView = 'list'"
              >
                ☰ 列表
              </button>
            </div>
            <button
              type="button"
              class="exp-abandon"
              data-testid="expedition-save-exit"
              @click="onExit"
            >
              保存退出
            </button>
            <button
              type="button"
              class="exp-abandon exp-abandon--finish"
              data-testid="expedition-finish-map"
              @click="onFinishRun"
            >
              🏁 结束本局
            </button>
            <button
              type="button"
              class="exp-abandon"
              data-testid="expedition-abandon"
              @click="onAbandon"
            >
              🏳 弃局
            </button>
          </div>
        </div>

        <!-- 图例：让玩家快速看懂各房间含义 -->
        <div
          class="exp-legend"
          data-testid="expedition-legend"
        >
          <span
            v-for="k in LEGEND_KINDS"
            :key="k"
            class="exp-legend__item"
          >
            <span class="exp-legend__icon">{{ NODE_KIND_ICON[k] }}</span>{{ NODE_KIND_LABEL[k] }}
          </span>
        </div>

        <!-- 像素远征塔（Phaser 单例渐进增强）：点像素节点 = 同 DOM 的 run.enterNode -->
        <div
          v-if="mapView === 'pixel'"
          class="exp-tower"
          data-testid="expedition-tower"
        >
          <ExpeditionCanvas
            :nodes="run.nodes"
            :layers="run.layers"
            :current-node-id="run.currentNodeId"
            :phase="run.phase"
            :hp="run.hp"
            :max-hp="run.maxHp"
            :score="run.score"
            @select="onEnterId"
          />
        </div>

        <!-- 列表视图（移动端 / 降级交互真源） -->
        <div
          v-else
          ref="mapInner"
          class="exp-map__inner"
        >
          <svg
            class="exp-edges"
            aria-hidden="true"
          >
            <line
              v-for="e in edges"
              :key="e.id"
              :x1="e.x1"
              :y1="e.y1"
              :x2="e.x2"
              :y2="e.y2"
              :class="['exp-edge', e.cls]"
            />
          </svg>
          <div
            v-for="(layer, li) in run.nodesByLayer"
            :key="li"
            class="exp-layer"
          >
            <span class="exp-layer__tag">{{ li === run.layers - 1 ? '顶层' : `第 ${li + 1} 层` }}</span>
            <div class="exp-layer__nodes">
              <button
                v-for="node in layer"
                :key="node.id"
                :ref="(el) => setNodeEl(node.id, el)"
                type="button"
                :class="nodeClass(node)"
                :disabled="node.state !== 'available'"
                :title="nodeTooltip(node)"
                :data-testid="`expedition-node-${node.id}`"
                @click="onEnter(node)"
              >
                <span class="exp-node__icon">{{ NODE_KIND_ICON[node.kind] }}</span>
                <span class="exp-node__kind">{{ NODE_KIND_LABEL[node.kind] }}</span>
                <span
                  v-if="node.kcCode"
                  class="exp-node__kc"
                >{{ kcDisplayName(node.kcCode) }}</span>
                <span
                  v-if="node.difficulty > 0"
                  class="exp-node__diff"
                >{{ star(node.difficulty) }}</span>
                <span
                  v-if="node.state === 'cleared'"
                  class="exp-node__done"
                >✓</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 概念事件：三选一应对 -->
      <div
        v-else-if="run.phase === 'event'"
        class="exp-event"
        data-testid="expedition-event"
      >
        <div class="exp-event__card">
          <span class="exp-event__icon">{{ NODE_KIND_ICON.event }}</span>
          <h2 class="exp-event__title">
            概念事件 · {{ kcDisplayName(run.activeNode?.kcCode ?? '') }}
          </h2>
          <p class="exp-event__text">
            {{ run.activeEventText }}
          </p>
          <div class="exp-event__choices">
            <button
              v-for="c in ROGUE_EVENT_CHOICES"
              :key="c.key"
              type="button"
              class="exp-event__choice"
              :data-testid="`expedition-event-${c.key}`"
              @click="run.chooseEvent(c.key)"
            >
              <span class="exp-event__choice-icon">{{ c.icon }}</span>
              <strong>{{ c.label }}</strong>
              <small>{{ c.desc }}</small>
            </button>
          </div>
        </div>
      </div>

      <!-- 领取学习增益：三选一 -->
      <div
        v-else-if="run.phase === 'reward'"
        class="exp-reward"
        data-testid="expedition-reward"
      >
        <div class="exp-reward__card">
          <span class="exp-reward__icon">{{ NODE_KIND_ICON.chest }}</span>
          <h2 class="exp-reward__title">
            选择一件学习增益
          </h2>
          <p class="exp-reward__hint">
            每件都正向且与学习相关，挑最契合你打法的一件，未选中的本次放弃。
          </p>
          <div class="exp-reward__choices">
            <button
              v-for="id in run.rewardChoices"
              :key="id"
              type="button"
              class="exp-reward__choice"
              :data-testid="`expedition-reward-${id}`"
              @click="run.chooseReward(id)"
            >
              <span class="exp-reward__choice-icon">{{ relicIcon(id) }}</span>
              <strong>{{ relicName(id) }}</strong>
              <small>{{ relicDesc(id) }}</small>
            </button>
          </div>
        </div>
      </div>

      <!-- 战斗：出题 + 判题 -->
      <div
        v-else-if="run.phase === 'challenge'"
        class="exp-challenge"
        data-testid="expedition-challenge"
      >
        <header class="exp-challenge__head">
          <span class="exp-chip">{{ NODE_KIND_ICON[run.activeNode?.kind ?? 'battle'] }} {{ NODE_KIND_LABEL[run.activeNode?.kind ?? 'battle'] }}</span>
          <span class="exp-chip exp-chip--timer">⏱ {{ run.elapsedSec }}s</span>
          <div class="exp-challenge__tools">
            <button
              type="button"
              class="exp-mini-btn"
              data-testid="expedition-view-map"
              @click="showMapModal = true"
            >
              🗺 查看地图
            </button>
            <button
              type="button"
              class="exp-mini-btn exp-mini-btn--danger"
              data-testid="expedition-finish"
              @click="onFinishRun"
            >
              🏁 结束本局
            </button>
          </div>
        </header>

        <div
          v-if="run.loading && !run.activeProblem"
          class="exp-loading"
          role="status"
        >
          正在出题…
        </div>

        <div
          v-else-if="run.errorMessage && !run.activeProblem"
          class="exp-error"
          role="alert"
        >
          <p>{{ run.errorMessage }}</p>
          <PyButton
            variant="primary"
            size="sm"
            test-id="expedition-error-retry"
            :disabled="run.loading"
            @click="run.retryChallenge()"
          >
            {{ run.loading ? '出题中…' : '重试' }}
          </PyButton>
        </div>

        <ChallengeWorkspace
          v-else-if="run.activeProblem"
          v-model:code="run.activeCode"
          :kind="run.activeNode?.challengeKind ?? null"
          :problem="run.activeProblem"
          :title="NODE_KIND_LABEL[run.activeNode?.kind ?? 'battle']"
          :kc-code="run.activeNode?.kcCode ?? null"
          :difficulty="shownDifficulty"
          :elapsed-sec="run.elapsedSec"
          :ai-generated="run.activeProblem.aiGenerated"
          :bug-mine-time-limit-seconds="EXPEDITION_BUG_MINE_SECONDS"
          :result="challengeResult"
          @timeout="onSubmit"
        >
          <template #chips>
            <span
              v-if="difficultyTag"
              class="exp-chip"
            >{{ difficultyTag }}</span>
            <span
              v-if="run.activeNode?.kind === 'boss'"
              class="exp-chip exp-chip--boss"
            >阶段 {{ run.bossPhase }} / {{ run.bossPhasesTotal }}</span>
          </template>
          <template #actions>
            <p
              v-if="run.errorMessage"
              class="exp-inline-error"
            >
              {{ run.errorMessage }}
            </p>
            <PyButton
              v-if="!run.challengeResult"
              variant="primary"
              test-id="expedition-submit"
              :disabled="run.loading"
              @click="onSubmit"
            >
              {{ run.loading ? '评测中…' : '提交答案' }}
            </PyButton>
            <template v-else>
              <PyButton
                v-if="run.challengeResult.passed"
                variant="primary"
                test-id="expedition-continue"
                @click="run.continueAfterChallenge()"
              >
                {{ run.activeNode?.kind === 'boss' ? '通关！' : '继续闯关' }}
              </PyButton>
              <template v-else>
                <PyButton
                  v-if="run.hp > 0"
                  variant="primary"
                  test-id="expedition-retry"
                  :disabled="run.loading"
                  @click="run.retryChallenge()"
                >
                  {{ run.loading ? '出题中…' : '再试一次（换一题）' }}
                </PyButton>
                <PyButton
                  v-else
                  variant="secondary"
                  test-id="expedition-over"
                  @click="run.continueAfterChallenge()"
                >
                  体力耗尽 · 查看结算
                </PyButton>
              </template>
            </template>
          </template>
          <template #result-extra>
            <p
              v-if="!run.challengeResult?.passed"
              class="exp-result__hp"
            >
              答错 · 体力 {{ run.hp }}/{{ run.maxHp }}
            </p>
          </template>
        </ChallengeWorkspace>

        <!-- 只读地图弹窗：答题中查看进度，不可换节点 -->
        <div
          v-if="showMapModal"
          class="exp-modal"
          data-testid="expedition-map-modal"
          @click.self="showMapModal = false"
        >
          <div
            class="exp-modal__panel"
            role="dialog"
            aria-label="远征地图"
          >
            <header class="exp-modal__head">
              <h3>远征地图 · 只读</h3>
              <button
                type="button"
                class="exp-modal__close"
                aria-label="关闭地图"
                data-testid="expedition-map-modal-close"
                @click="showMapModal = false"
              >
                ×
              </button>
            </header>
            <p class="exp-modal__legend">
              <span
                v-for="k in LEGEND_KINDS"
                :key="k"
                class="exp-legend__item"
              ><span class="exp-legend__icon">{{ NODE_KIND_ICON[k] }}</span>{{ NODE_KIND_LABEL[k] }}</span>
            </p>
            <div class="exp-modal__body">
              <div
                v-for="(layer, li) in run.nodesByLayer"
                :key="li"
                class="exp-mini-layer"
              >
                <span class="exp-mini-layer__tag">{{ li === run.layers - 1 ? '顶层' : `第 ${li + 1} 层` }}</span>
                <div class="exp-mini-layer__nodes">
                  <span
                    v-for="node in layer"
                    :key="node.id"
                    class="exp-mini-node"
                    :class="{
                      'is-cleared': node.state === 'cleared',
                      'is-available': node.state === 'available',
                      'is-locked': node.state === 'locked',
                      'is-bypassed': node.state === 'bypassed',
                      'is-current': node.id === run.currentNodeId,
                    }"
                    :title="nodeTooltip(node)"
                  >
                    {{ NODE_KIND_ICON[node.kind] }}
                    <span
                      v-if="node.id === run.currentNodeId"
                      class="exp-mini-node__here"
                    >●</span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 结算：胜利 / 失败 -->
      <div
        v-else
        class="exp-end"
        :class="run.phase === 'cleared' ? 'exp-end--win' : 'exp-end--over'"
        data-testid="expedition-end"
      >
        <BossSprite
          :chapter="bossChapter"
          :phase="run.phase === 'cleared' ? 'hurt' : 'idle'"
          :size="150"
        />
        <h2>{{ run.phase === 'cleared' ? `击穿知识闸 · 战胜 ${bossName}！` : '本局远征结束' }}</h2>
        <ul class="exp-end__stats">
          <li>深度 {{ run.depth }} 层</li>
          <li>得分 {{ run.score }}</li>
          <li>最高气势 ×{{ run.bestMomentum }}</li>
          <li>答对 {{ run.passedCount }} / {{ run.attempts }}</li>
        </ul>
        <p class="exp-end__note">
          做过的每题都已入库，掌握度永久保留。
        </p>
        <div
          v-if="kcSummary.length > 0"
          class="exp-end__kc"
          data-testid="expedition-kc-summary"
        >
          <h3 class="exp-end__kc-title">
            本局练习小结
          </h3>
          <ul class="exp-end__kc-list">
            <li
              v-for="row in kcSummary"
              :key="row.kcCode"
              class="exp-end__kc-row"
              :class="{ 'is-weak': row.passed < row.total }"
            >
              <span class="exp-end__kc-name">{{ kcDisplayName(row.kcCode) }}</span>
              <span class="exp-end__kc-score">答对 {{ row.passed }}/{{ row.total }}</span>
            </li>
          </ul>
        </div>
        <KnowledgeConstellation :mastery="masteryMap" />
        <ExpeditionLeaderboard
          :board="run.leaderboard"
          data-testid="expedition-end-leaderboard"
        />
        <div class="exp-actions">
          <PyButton
            variant="primary"
            test-id="expedition-again"
            @click="onRestart"
          >
            再来一局
          </PyButton>
          <PyButton
            variant="secondary"
            @click="onExit"
          >
            返回小镇
          </PyButton>
        </div>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.exp-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  min-height: calc(100dvh - 56px);
  background:
    linear-gradient(180deg, rgba(243, 220, 157, 0.26), rgba(122, 93, 48, 0.1)),
    var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-pixel);
}
.exp-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  background: rgba(255, 249, 234, 0.94);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: 3px 3px 0 rgba(74, 54, 29, 0.35);
}
.exp-title {
  margin: 0;
  font-size: 18px;
}
.exp-stats {
  display: flex;
  gap: 12px;
}
.exp-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.exp-stat__label {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
}
.exp-stat__value {
  font-size: 15px;
  font-weight: 700;
  color: var(--pyt-color-warning);
}
.exp-stat--hp .exp-stat__value {
  color: var(--pyt-color-danger);
  letter-spacing: 1px;
}

/* 学习增益条 */
.exp-relics {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: rgba(237, 233, 254, 0.6);
  border: 1px solid rgba(124, 92, 196, 0.4);
  border-radius: var(--pyt-radius-md);
}
.exp-relics__label {
  font-size: 11px;
  color: #4b3b86;
}
.exp-relic {
  font-size: 18px;
  cursor: help;
}
.exp-constellation-toggle {
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  padding: 6px 12px;
  cursor: pointer;
  color: var(--pyt-color-text-hint);
  background: transparent;
  border: 1px dashed var(--pyt-color-border);
  border-radius: 999px;
}
.exp-constellation-toggle:hover {
  color: var(--pyt-color-text-primary);
  border-style: solid;
}

/* 概念事件卡 */
.exp-event {
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.exp-event__card {
  max-width: 560px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
  padding: 24px;
  background: rgba(255, 249, 234, 0.94);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: 3px 3px 0 rgba(74, 54, 29, 0.25);
}
.exp-event__icon {
  font-size: 34px;
}
.exp-event__title {
  margin: 0;
  font-size: 18px;
}
.exp-event__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--pyt-color-text-hint);
}
.exp-event__choices {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.exp-event__choice {
  display: grid;
  gap: 2px;
  text-align: left;
  padding: 10px 14px;
  cursor: pointer;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  box-shadow: 2px 2px 0 rgba(74, 54, 29, 0.25);
  color: var(--pyt-color-text-primary);
  font-family: inherit;
}
.exp-event__choice:hover {
  border-color: var(--pyt-color-warning);
  background: var(--pyt-color-warning-bg);
}
.exp-event__choice-icon {
  font-size: 16px;
}
.exp-event__choice strong {
  font-size: 14px;
}
.exp-event__choice small {
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}

/* 领取学习增益（三选一） */
.exp-reward {
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.exp-reward__card {
  max-width: 640px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
  padding: 24px;
  background: rgba(237, 233, 254, 0.6);
  border: 2px solid rgba(124, 92, 196, 0.5);
  border-radius: var(--pyt-radius-md);
  box-shadow: 3px 3px 0 rgba(74, 54, 29, 0.2);
}
.exp-reward__icon {
  font-size: 34px;
}
.exp-reward__title {
  margin: 0;
  font-size: 18px;
}
.exp-reward__hint {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.exp-reward__choices {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  width: 100%;
}
.exp-reward__choice {
  flex: 1 1 170px;
  max-width: 200px;
  display: grid;
  gap: 4px;
  text-align: center;
  padding: 14px 12px;
  cursor: pointer;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  box-shadow: 2px 2px 0 rgba(74, 54, 29, 0.25);
  color: var(--pyt-color-text-primary);
  font-family: inherit;
  transition: transform 0.1s, border-color 0.1s;
}
.exp-reward__choice:hover {
  transform: translateY(-2px);
  border-color: #7c5cc4;
  background: rgba(237, 233, 254, 0.9);
}
.exp-reward__choice-icon {
  font-size: 24px;
}
.exp-reward__choice strong {
  font-size: 13px;
}
.exp-reward__choice small {
  font-size: 11px;
  line-height: 1.6;
  color: var(--pyt-color-text-muted);
}

.exp-start,
.exp-end,
.exp-loading,
.exp-error {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  text-align: center;
  padding: 24px;
}
.exp-start__lead,
.exp-end__note {
  max-width: 620px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--pyt-color-text-hint);
}
.exp-save-card {
  display: grid;
  gap: 6px;
  min-width: min(520px, 100%);
  padding: 12px 14px;
  text-align: left;
  background: rgba(255, 249, 234, 0.96);
  border: 2px solid var(--pyt-color-warning);
  border-radius: var(--pyt-radius-md);
  box-shadow: 3px 3px 0 rgba(217, 159, 58, 0.28);
  color: var(--pyt-color-text-primary);
}
.exp-save-card__eyebrow {
  font-size: 11px;
  color: var(--pyt-color-warning);
}
.exp-save-card strong {
  font-size: 15px;
}
.exp-save-card span {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.exp-save-card :deep(.py-button) {
  justify-self: start;
}
.exp-focus {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--pyt-color-text-hint);
}
.exp-focus select {
  font-family: inherit;
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-primary);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  padding: 4px 8px;
}

/* ── 分支地图 ── */
.exp-map {
  flex: 1 1 auto;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 6px 4px 24px;
}
.exp-map__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.exp-map__hint {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
  text-align: left;
  flex: 1 1 240px;
}
.exp-map__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}
.exp-view-toggle {
  display: inline-flex;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  overflow: hidden;
}
.exp-view-btn {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 10px;
  cursor: pointer;
  border: none;
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
}
.exp-view-btn.is-active {
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
}
.exp-abandon {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 10px;
  cursor: pointer;
  color: var(--pyt-color-text-hint);
  background: transparent;
  border: 1px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
.exp-abandon:hover {
  color: var(--pyt-color-danger);
  border-color: var(--pyt-color-danger);
  border-style: solid;
}
/* 「结束本局」走结算（正向收束），与「弃局」的危险红区分开。 */
.exp-abandon--finish:hover {
  color: var(--pyt-color-warning);
  border-color: var(--pyt-color-warning);
  border-style: solid;
}
/* 像素远征塔画布容器：浅色地图底，和中央小镇/学习面板的纸面风格保持一致。
   高度收敛到视口内，让整座塔（起点层→顶层 BOSS）一屏可见，不必滚动页面。 */
.exp-tower {
  flex: 1 1 auto;
  height: clamp(380px, calc(100dvh - 220px), 900px);
  border: 2px solid rgba(129, 112, 75, 0.38);
  border-radius: var(--pyt-radius-md);
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(232, 247, 255, 0.96), rgba(248, 244, 225, 0.98));
  box-shadow: 3px 3px 0 rgba(129, 112, 75, 0.18), 0 10px 30px rgba(70, 92, 72, 0.12);
}
@media (max-width: 900px) {
  .exp-tower {
    height: clamp(320px, calc(100dvh - 200px), 720px);
  }
}
/* 图例条 */
.exp-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  padding: 6px 10px;
  background: rgba(255, 249, 234, 0.7);
  border: 1px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  font-size: 11px;
  color: var(--pyt-color-text-hint);
}
.exp-legend__item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.exp-legend__icon {
  font-size: 13px;
}
.exp-map__inner {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
/* 分支连线层：垫在节点下方，纯展示不挡点击。 */
.exp-edges {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: visible;
  z-index: 0;
}
.exp-edge {
  stroke-width: 2;
  fill: none;
}
.exp-edge.is-open {
  stroke: var(--pyt-color-warning, #d99f3a);
  stroke-opacity: 0.85;
}
.exp-edge.is-dim {
  stroke: rgba(74, 54, 29, 0.22);
  stroke-dasharray: 4 5;
}
.exp-layer {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}
.exp-layer__tag {
  flex: 0 0 56px;
  font-size: 11px;
  color: var(--pyt-color-text-muted);
  text-align: right;
}
.exp-layer__nodes {
  flex: 1 1 auto;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.exp-node {
  position: relative;
  display: grid;
  gap: 2px;
  min-width: 150px;
  padding: 8px 12px;
  text-align: left;
  cursor: pointer;
  background: rgba(255, 249, 234, 0.9);
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  box-shadow: 2px 2px 0 rgba(74, 54, 29, 0.25);
  color: var(--pyt-color-text-primary);
  font-family: inherit;
  transition: transform 0.1s, box-shadow 0.1s;
}
.exp-node.is-available {
  border-color: var(--pyt-color-warning);
  box-shadow: 2px 2px 0 var(--pyt-color-warning);
}
.exp-node.is-available:hover {
  transform: translateY(-2px);
}
.exp-node.is-cleared {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}
.exp-node.is-locked {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}
.exp-node.is-bypassed {
  opacity: 0.3;
  cursor: not-allowed;
  text-decoration: line-through;
  box-shadow: none;
}
.exp-node--boss {
  background: linear-gradient(135deg, var(--pyt-color-gold-bright) 0%, rgba(255, 249, 234, 0.9) 100%);
  border-width: 3px;
}
.exp-node__icon {
  font-size: 16px;
}
.exp-node__kind {
  font-size: 12px;
  font-weight: 700;
}
.exp-node__kc {
  font-size: 11px;
  color: var(--pyt-color-text-hint);
}
.exp-node__diff {
  font-size: 11px;
  color: var(--pyt-color-danger);
  letter-spacing: 1px;
}
.exp-node__done {
  position: absolute;
  top: 6px;
  right: 8px;
  color: var(--pyt-color-success);
  font-weight: 700;
}

/* ── 战斗 ── */
.exp-challenge {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  background: rgba(255, 249, 234, 0.94);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: 3px 3px 0 rgba(74, 54, 29, 0.25);
  padding: 12px;
  overflow: auto;
}
.exp-challenge__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.exp-challenge__tools {
  margin-left: auto;
  display: flex;
  gap: 6px;
}
.exp-mini-btn {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 10px;
  cursor: pointer;
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
.exp-mini-btn:hover {
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-text-primary);
}
.exp-mini-btn--danger:hover {
  color: var(--pyt-color-danger);
  border-color: var(--pyt-color-danger);
}

/* 只读地图弹窗 */
.exp-modal {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(20, 13, 34, 0.6);
}
.exp-modal__panel {
  width: min(680px, 100%);
  max-height: 86dvh;
  display: flex;
  flex-direction: column;
  background: rgba(255, 249, 234, 0.98);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: 4px 4px 0 rgba(74, 54, 29, 0.4);
  overflow: hidden;
}
.exp-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--pyt-color-border);
}
.exp-modal__head h3 {
  margin: 0;
  font-size: 15px;
}
.exp-modal__close {
  width: 28px;
  height: 28px;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
}
.exp-modal__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 0;
  padding: 8px 14px;
  font-size: 11px;
  color: var(--pyt-color-text-hint);
  border-bottom: 1px dashed var(--pyt-color-border);
}
.exp-modal__body {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column-reverse;
  gap: 8px;
}
.exp-mini-layer {
  display: flex;
  align-items: center;
  gap: 8px;
}
.exp-mini-layer__tag {
  flex: 0 0 56px;
  font-size: 11px;
  color: var(--pyt-color-text-muted);
  text-align: right;
}
.exp-mini-layer__nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.exp-mini-node {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  font-size: 16px;
  border: 2px solid var(--pyt-color-border);
  border-radius: 6px;
  background: rgba(255, 252, 242, 0.9);
}
.exp-mini-node.is-cleared {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}
.exp-mini-node.is-available {
  border-color: var(--pyt-color-warning);
}
.exp-mini-node.is-locked {
  opacity: 0.45;
}
.exp-mini-node.is-bypassed {
  opacity: 0.3;
}
.exp-mini-node.is-current {
  box-shadow: 0 0 0 2px var(--pyt-color-warning);
}
.exp-mini-node__here {
  position: absolute;
  bottom: -2px;
  right: -2px;
  font-size: 9px;
  color: var(--pyt-color-warning);
}
.exp-chip {
  display: inline-block;
  padding: 2px 8px;
  font-size: 11px;
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-info);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-info);
}
.exp-chip__tag {
  margin-left: 4px;
  font-size: 10px;
  color: var(--pyt-color-warning);
}
.exp-chip--boss {
  background: var(--pyt-color-danger-bg);
  border-color: var(--pyt-color-danger);
  color: var(--pyt-color-danger);
}
.exp-chip--timer {
  margin-left: auto;
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-warning);
  color: var(--pyt-color-warning);
}
.exp-statement {
  margin: 0;
  padding: 10px 12px;
  background: var(--pyt-color-terminal-bg);
  border: 1px solid var(--pyt-color-border-strong);
  border-radius: 6px;
  white-space: pre-wrap;
  font-family: 'Consolas', 'JetBrains Mono', 'Cascadia Code', Menlo, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  color: var(--pyt-color-terminal-text);
}
.exp-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.exp-inline-error,
.exp-result__hp {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-danger);
}
.exp-result {
  display: grid;
  gap: 6px;
}

/* ── 结算 ── */
.exp-end__stats {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 14px;
  color: var(--pyt-color-text-primary);
}
.exp-end--win h2 {
  color: var(--pyt-color-success);
}
.exp-end__kc {
  width: min(520px, 100%);
  text-align: left;
}
.exp-end__kc-title {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--pyt-color-text-hint);
}
.exp-end__kc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 4px;
}
.exp-end__kc-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 10px;
  font-size: 12px;
  background: var(--pyt-color-success-bg);
  border: 1px solid var(--pyt-color-success);
  border-radius: var(--pyt-radius-sm);
}
.exp-end__kc-row.is-weak {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-warning);
}
.exp-end__kc-name {
  color: var(--pyt-color-text-primary);
}
.exp-end__kc-score {
  color: var(--pyt-color-text-hint);
  white-space: nowrap;
}

@media (max-width: 720px) {
  .exp-head {
    grid-template-columns: auto 1fr;
  }
  .exp-stats {
    grid-column: 1 / span 2;
    justify-content: space-around;
    width: 100%;
  }
  .exp-layer {
    flex-direction: column;
    align-items: stretch;
  }
  .exp-layer__tag {
    text-align: left;
  }
}
</style>
