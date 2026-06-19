<script setup lang="ts">
/**
 * /expedition 无尽远征（Roguelike）。
 *
 * 取代「7 章 × 10 关」死网格：知识前沿（掌握度 + 画像）生成分支地图，玩家选路闯关，
 * 战斗节点真判题；对得分攒气势、错按韧性温和扣血，HP 归零结束，打通 boss 胜利。
 * 章节为可选「聚焦」过滤器，默认全局自适应。详见 docs/plans/2026-06-19-endless-roguelike-rebuild.md。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PyButton from '@/components/ui/PyButton.vue';
import BossSprite from '@/components/boss/BossSprite.vue';
import JudgeResultPanel from '@/components/challenge/JudgeResultPanel.vue';
import { KindRouter } from '@/components/arena';
import { useRogueRunStore } from '@stores/rogueRun';
import { kcDisplayName } from '@/data/kcNames';
import { NODE_KIND_ICON, NODE_KIND_LABEL } from '@/data/rogueConfig';
import { relicById } from '@/data/rogueRelics';
import { MAIN_QUEST_BOSSES } from '@/data/bossCatalog';
import type { ChallengeResult } from '@/api/dto/CodeChallenge';
import type { RogueNode } from '@/types/rogue';

const route = useRoute();
const router = useRouter();
const run = useRogueRunStore();

// ?focus=章 来自旧 /arena 重定向或聚焦练习。
const focusFromQuery = computed<number | null>(() => {
  const raw = Number(route.query.focus);
  return Number.isInteger(raw) && raw >= 1 && raw <= 7 ? raw : null;
});
const focusChoice = ref<number | null>(run.focusChapter ?? focusFromQuery.value);

let timer: number | null = null;
onMounted(() => {
  if (typeof window !== 'undefined') timer = window.setInterval(() => run.tickSecond(), 1000);
});
onBeforeUnmount(() => {
  if (timer !== null) window.clearInterval(timer);
});

function onStart(): void {
  run.startRun(focusChoice.value);
}
function onExit(): void {
  run.abort();
  void router.push('/world');
}
function onEnter(node: RogueNode): void {
  void run.enterNode(node.id);
}
function onSubmit(): void {
  void run.submitChallenge();
}

const bossChapter = computed<number>(() => {
  // 聚焦章 → 该章 boss 立绘；否则按当前题目章节，兜底第 1 章。
  return run.focusChapter ?? run.activeProblem?.chapter ?? 1;
});
const bossName = computed<string>(
  () => MAIN_QUEST_BOSSES.find((b) => b.chapter === bossChapter.value)?.name ?? '知识守关者',
);

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
function relicTitle(id: string): string {
  const r = relicById(id);
  return r ? `${r.name}：${r.desc}` : id;
}
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
          ← 返回小镇
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
          系统按你的<strong>掌握度 + 学生画像</strong>实时编排一条分支远征路线：选路、闯关、攒气势。
          答错按你的抗挫折度温和扣体力，体力归零本局结束——但<strong>掌握度收益永久保留</strong>。
        </p>
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
        <PyButton
          variant="primary"
          test-id="expedition-begin"
          @click="onStart"
        >
          开始远征
        </PyButton>
      </div>

      <!-- 地图：选节点 -->
      <div
        v-else-if="run.phase === 'map'"
        class="exp-map"
        data-testid="expedition-map"
      >
        <p class="exp-map__hint">
          点亮的节点可前往；选了一个，同层其它岔路就走不回头了。一路打到顶层 BOSS 即通关。
        </p>
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
              type="button"
              :class="nodeClass(node)"
              :disabled="node.state !== 'available'"
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

      <!-- 战斗：出题 + 判题 -->
      <div
        v-else-if="run.phase === 'challenge'"
        class="exp-challenge"
        data-testid="expedition-challenge"
      >
        <header class="exp-challenge__head">
          <span class="exp-chip">{{ NODE_KIND_ICON[run.activeNode?.kind ?? 'battle'] }} {{ NODE_KIND_LABEL[run.activeNode?.kind ?? 'battle'] }}</span>
          <span class="exp-chip">{{ kcDisplayName(run.activeNode?.kcCode ?? '') }}</span>
          <span class="exp-chip">难度 {{ run.activeNode?.difficulty }} / 5</span>
          <span class="exp-chip exp-chip--timer">⏱ {{ run.elapsedSec }}s</span>
        </header>

        <div
          v-if="run.loading && !run.activeProblem"
          class="exp-loading"
          role="status"
        >
          正在出题中…（5 重审查 + golden 兜底）
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
            @click="run.retryChallenge()"
          >
            重试
          </PyButton>
        </div>

        <template v-else-if="run.activeProblem">
          <KindRouter
            v-model:code="run.activeCode"
            :kind="run.activeNode?.challengeKind ?? null"
            :problem="run.activeProblem"
            @timeout="onSubmit"
          >
            <pre class="exp-statement">{{ run.activeProblem.statement }}</pre>
          </KindRouter>

          <p
            v-if="run.errorMessage"
            class="exp-inline-error"
          >
            {{ run.errorMessage }}
          </p>

          <div class="exp-actions">
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
                  @click="run.retryChallenge()"
                >
                  再试一次（换一题）
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
          </div>

          <div
            v-if="challengeResult"
            class="exp-result"
            data-testid="expedition-result"
          >
            <JudgeResultPanel
              :result="challengeResult"
              mode="judge"
            />
            <p
              v-if="!run.challengeResult?.passed"
              class="exp-result__hp"
            >
              答错，体力 −{{ (1) }}~2（按抗挫折度）；当前体力 {{ run.hp }}/{{ run.maxHp }}
            </p>
          </div>
        </template>
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
          做过的每一题都已真实入库，掌握度与画像不会回滚。
        </p>
        <div class="exp-actions">
          <PyButton
            variant="primary"
            test-id="expedition-again"
            @click="onStart"
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
.exp-map__hint {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
  text-align: center;
}
.exp-layer {
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
  gap: 6px;
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
  font-family: 'Fira Code', 'JetBrains Mono', monospace;
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
