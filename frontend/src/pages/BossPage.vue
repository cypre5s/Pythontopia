<script setup lang="ts">
/**
 * 章末 BOSS 副本页。
 *
 * 与 useBossSession 对接：
 *   - mounted 时调 startStage（stage 1 自动开始）；
 *   - 编辑器 v-model code，点击「提交答案」触发 submit；
 *   - 通过后 stage++ 进入下一阶段，4 阶段全胜 → victory；
 *   - 失败时显示「再试一次」+ 按 difficulty 给的提示。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import gsap from 'gsap';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { kcDisplayName } from '@/data/kcNames';
import BossSprite from '@/components/boss/BossSprite.vue';
import BossMoveEffect from '@/components/boss/BossMoveEffect.vue';
import { useBossSession } from '@/composables/useBossSession';
import { findBossByChapter, type MainSubQuestReward } from '@/data/bossCatalog';

const props = defineProps<{ chapter: number }>();

const router = useRouter();
const session = useBossSession({ chapter: props.chapter });
const boss = findBossByChapter(props.chapter);

let secondTimer: number | null = null;
const exitConfirm = ref<boolean>(false);

onMounted(async () => {
  if (!boss) {
    session.errorMessage.value = `未找到第 ${props.chapter} 章 boss 配置`;
    session.phase.value = 'error';
    return;
  }
  await session.startStage();
  if (typeof window !== 'undefined') {
    secondTimer = window.setInterval(() => session.tickSecond(), 1000);
  }
});

onBeforeUnmount(() => {
  if (secondTimer !== null) {
    window.clearInterval(secondTimer);
    secondTimer = null;
  }
});

function isSubmitDisabled(): boolean {
  const p = session.phase.value;
  return p === 'evaluating' || p === 'loading' || p === 'victory' || p === 'error';
}

async function onSubmit(): Promise<void> {
  const current = session.phase.value;
  if (current !== 'playing' && current !== 'failed') return;
  if (current === 'failed') {
    session.phase.value = 'playing';
  }
  await session.submit();
}

async function onAdvance(): Promise<void> {
  await session.advanceStage();
}

async function onRetry(): Promise<void> {
  session.phase.value = 'playing';
  session.lastResult.value = null;
}

function onExit(): void {
  if (exitConfirm.value) {
    void router.push('/world');
    return;
  }
  exitConfirm.value = true;
  window.setTimeout(() => {
    exitConfirm.value = false;
  }, 3000);
}

function pickHint(): string {
  const p = session.problem.value;
  if (!p) return '';
  if (p.hints.length === 0) {
    return '题目自带提示已用完，去工坊为该 KC 生成 HANDOUT。';
  }
  const idx = Math.min(p.hints.length - 1, session.clearedStages.value);
  return p.hints[idx] ?? '';
}

watch(
  () => session.phase.value,
  (next) => {
    if (next === 'victory') {
      // 闯关塔已退役（并入无尽远征）：章末 BOSS 通关不再回写静态关卡进度，掌握度由判题入库权威驱动。
      if (typeof window !== 'undefined') {
        window.setTimeout(() => {
          // 第 7 章终极 BOSS 通关 → 毕业典礼（主线终局演出）；其余章节回小镇。
          void router.push(props.chapter === 7 ? '/graduation' : '/world');
        }, 4000);
      }
    }
  },
);

const effectTrigger = ref(0);
const playerHpBarRef = ref<HTMLElement | null>(null);
const bossSpriteWrapRef = ref<HTMLElement | null>(null);

watch(
  () => session.stage.value,
  () => { effectTrigger.value += 1; },
);
watch(
  () => session.bossPhase.value,
  (phase, prev) => {
    if (phase === 2 && prev === 1) {
      effectTrigger.value += 1;
    }
  },
);
watch(
  () => session.playerKnockedDown.value,
  async (knocked) => {
    if (!knocked || typeof window === 'undefined') return;
    await Promise.resolve();
    const hpEl = playerHpBarRef.value;
    const wrapEl = bossSpriteWrapRef.value;
    if (hpEl) {
      gsap.fromTo(hpEl, { x: 0 }, { x: -8, duration: 0.06, repeat: 5, yoyo: true, ease: 'power1.inOut' });
      gsap.fromTo(hpEl, { filter: 'brightness(1)' }, { filter: 'brightness(2.2)', duration: 0.12, yoyo: true, repeat: 3 });
    }
    if (wrapEl) {
      gsap.fromTo(wrapEl, { scale: 1 }, { scale: 0.92, duration: 0.18, yoyo: true, repeat: 1, ease: 'power2.out' });
    }
  },
);
watch(
  () => session.lastResult.value,
  (r) => { if (r) effectTrigger.value += 1; },
);

const bossSpritePhase = computed<'idle' | 'hurt' | 'angry' | 'victory' | 'enrage'>(() => {
  const phase = session.phase.value;
  if (phase === 'passed' || phase === 'victory') return 'hurt';
  if (phase === 'failed' || phase === 'evaluating') return 'angry';
  if (session.bossPhase.value === 2) return 'enrage';
  return 'idle';
});

// 剩余时间过半 = 暴击窗口（telegraph：抢时间快答）。
const inCritWindow = computed<boolean>(
  () => session.timeLeftSec.value >= Math.ceil(session.stageTimeLimit.value / 2),
);

const REWARD_ITEM_LABELS: Record<string, string> = {
  'outfit:apprentice': '学徒装',
  'title:python_speaker': '称号·Python 代言人',
  'storyboard:binary_dawn': '故事板·二进制黎明',
  'item:loop_bookmark': '循环书签',
  'outfit:captain': '船长装',
  'title:file_keeper': '称号·档案守护者',
  'outfit:graduate': '毕业袍',
  'title:pythontopia_alumnus': '称号·Pythontopia 校友',
};

const visibleRewards = computed<MainSubQuestReward[]>(() => [...(boss?.rewards ?? [])]);

function rewardLabel(r: MainSubQuestReward): string {
  if (r.kind === 'kc_mastery') {
    return `掌握进度 +${r.amount}`;
  }
  if (r.kind === 'item') {
    return (r.ref ? REWARD_ITEM_LABELS[r.ref] : undefined) ?? '奖励道具';
  }
  return '';
}
</script>

<template>
  <AppLayout>
    <section
      class="boss-page"
      data-testid="boss-page"
    >
      <header class="boss-head">
        <button
          type="button"
          class="boss-exit"
          data-testid="boss-exit"
          @click="onExit"
        >
          {{ exitConfirm ? '再点确认退出' : '退出副本' }}
        </button>
        <div class="boss-title">
          <span class="boss-chapter">第 {{ chapter }} 章 BOSS</span>
          <span class="boss-name">
            {{ boss?.name ?? '???' }}
            <span
              v-if="session.bossPhase.value === 2"
              class="boss-enrage-badge"
              data-testid="boss-enrage"
            >狂暴</span>
          </span>
        </div>
        <div
          class="boss-stage-bar"
          data-testid="boss-stage-bar"
        >
          <span
            v-for="s in 4"
            :key="s"
            class="boss-stage-step"
            :class="{
              'boss-stage-step--current': s === session.stage.value,
              'boss-stage-step--cleared': s <= session.clearedStages.value,
            }"
            :data-testid="`boss-stage-${s}`"
          >Stage {{ s }}</span>
        </div>
        <div
          class="boss-timer"
          :class="{
            'boss-timer--urgent': session.timeLeftSec.value <= 15,
            'boss-timer--crit': inCritWindow && !session.stageTimedOut.value,
            'boss-timer--expired': session.stageTimedOut.value,
          }"
          data-testid="boss-timer"
        >
          ⏱ {{ session.timeLeftSec.value }}s
          <span class="boss-timer__hint">
            {{
              session.stageTimedOut.value
                ? '超时！仍可作答但无法暴击'
                : inCritWindow
                  ? '快答暴击'
                  : '限时作答'
            }}
          </span>
        </div>
      </header>

      <div
        v-if="session.phase.value === 'loading'"
        class="boss-state"
        data-testid="boss-loading"
      >
        正在生成第 {{ session.stage.value }} 阶段题目…
      </div>
      <div
        v-else-if="session.phase.value === 'error'"
        class="boss-state boss-state--error"
        data-testid="boss-error"
      >
        副本出错：{{ session.errorMessage.value }}
        <button
          type="button"
          class="boss-btn"
          @click="session.startStage"
        >
          重试
        </button>
      </div>
      <div
        v-else-if="session.phase.value === 'victory'"
        class="boss-state boss-state--victory"
        data-testid="boss-victory"
      >
        <h2>★ 全 4 阶段通关！</h2>
        <p v-if="visibleRewards.length">
          解锁奖励：
          <span
            v-for="r in visibleRewards"
            :key="`${r.kind}-${r.ref ?? r.amount}`"
            class="boss-reward"
          >
            {{ rewardLabel(r) }}
          </span>
        </p>
        <p class="boss-storyboard">
          {{ boss?.storyboardCue }}
        </p>
        <p class="boss-tip">
          {{ props.chapter === 7 ? '4 秒后进入毕业典礼…' : '4 秒后自动返回小镇…' }}
        </p>
      </div>
      <div
        v-else-if="session.problem.value"
        class="boss-body"
      >
        <article
          class="boss-problem"
          data-testid="boss-problem"
        >
          <h2 class="boss-problem__title">
            难度 {{ session.problem.value.difficulty }} · {{ kcDisplayName(session.problem.value.kcCode) }}
            <span
              v-if="session.problem.value.aiGenerated"
              class="badge badge--ai"
            >AI · 五重审查</span>
            <span
              v-else
              class="badge badge--golden"
            >Golden 兜底</span>
          </h2>
          <pre class="boss-problem__statement">{{ session.problem.value.statement }}</pre>
          <details class="boss-sample">
            <summary>样例输入 / 输出</summary>
            <div class="boss-sample__pair">
              <pre>{{ session.problem.value.sampleInput }}</pre>
              <pre>{{ session.problem.value.sampleOutput }}</pre>
            </div>
          </details>
          <details
            v-if="pickHint()"
            class="boss-sample"
          >
            <summary>提示</summary>
            <p>{{ pickHint() }}</p>
          </details>
        </article>
        <article class="boss-editor">
          <label class="boss-editor__label">
            <span>你的代码（Python 3）</span>
            <textarea
              v-model="session.code.value"
              data-testid="boss-code-input"
              placeholder="# 在这里写 Python 代码，stdout 与样例期望一致即可通过本阶段"
              rows="14"
            />
          </label>
          <div class="boss-editor__bar">
            <button
              type="button"
              class="boss-btn boss-btn--primary"
              data-testid="boss-submit"
              :disabled="isSubmitDisabled()"
              @click="onSubmit"
            >
              {{ session.phase.value === 'evaluating' ? '评测中…' : '提交答案' }}
            </button>
            <button
              v-if="session.phase.value === 'passed'"
              type="button"
              class="boss-btn boss-btn--next"
              data-testid="boss-next"
              @click="onAdvance"
            >
              下一阶段
            </button>
            <button
              v-if="session.phase.value === 'failed'"
              type="button"
              class="boss-btn"
              data-testid="boss-retry"
              @click="onRetry"
            >
              再来一次
            </button>
          </div>
          <div
            v-if="session.lastResult.value"
            class="boss-result"
            :class="{
              'boss-result--passed': session.lastResult.value.passed,
              'boss-result--failed': !session.lastResult.value.passed,
            }"
            data-testid="boss-result"
          >
            <p>
              <strong>
                {{ session.lastResult.value.passed ? '通过' : '未通过' }}
              </strong>
              · 通过用例 {{ session.lastResult.value.passedTestCases }}/{{ session.lastResult.value.totalTestCases }}
              · 分数 {{ session.lastResult.value.score }}
            </p>
            <p>{{ session.lastResult.value.feedback }}</p>
            <p
              v-if="session.lastResult.value.failureFirstHint"
              class="boss-hint"
            >
              提示：{{ session.lastResult.value.failureFirstHint }}
            </p>
          </div>
        </article>
        <article class="boss-sprite">
          <div
            ref="bossSpriteWrapRef"
            class="boss-sprite-wrap"
            data-testid="boss-sprite-wrap"
          >
            <BossMoveEffect
              :stage="session.stage.value"
              :trigger="effectTrigger"
              :enraged="session.bossPhase.value === 2"
            />
            <BossSprite
              :chapter="chapter"
              :phase="bossSpritePhase"
              :size="160"
            />
            <transition name="dmg-float">
              <span
                v-if="session.recentDamage.value !== null"
                class="boss-dmg-float"
                data-testid="boss-dmg-float"
              >
                -{{ session.recentDamage.value }}
              </span>
            </transition>
            <transition name="knockdown-fade">
              <div
                v-if="session.playerKnockedDown.value"
                class="boss-knockdown"
                data-testid="boss-knockdown"
              >
                被击倒！重整旗鼓…
              </div>
            </transition>
          </div>
          <p class="boss-sprite__stage">
            Stage {{ session.stage.value }} / 4
          </p>

          <div
            class="boss-hpbar"
            data-testid="boss-hpbar"
            :aria-valuenow="session.bossHp.value"
            aria-valuemin="0"
            aria-valuemax="100"
            role="progressbar"
          >
            <div
              class="boss-hpbar__fill"
              :style="{ width: `${session.bossHp.value}%` }"
            />
            <span class="boss-hpbar__label">HP {{ session.bossHp.value }} / 100</span>
          </div>

          <div
            ref="playerHpBarRef"
            class="boss-hpbar boss-hpbar--player"
            :class="{ 'boss-hpbar--knocked': session.playerKnockedDown.value }"
            data-testid="player-hpbar"
            role="progressbar"
            :aria-valuenow="session.playerHp.value"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <div
              class="boss-hpbar__fill boss-hpbar__fill--player"
              :style="{ width: `${session.playerHp.value}%` }"
            />
            <span class="boss-hpbar__label">你 HP {{ session.playerHp.value }} / 100</span>
          </div>

          <p
            v-if="session.lastHitCrit.value && session.phase.value === 'passed'"
            class="boss-crit"
            data-testid="boss-crit"
          >
            ⚡ 暴击！限时内快答，回血 +12
          </p>
          <p
            v-if="session.recentPlayerDamage.value !== null"
            class="boss-counter"
            data-testid="player-dmg-float"
          >
            ⚔ boss 反击！你 -{{ session.recentPlayerDamage.value }}
          </p>
          <p
            v-if="session.stageTimedOut.value"
            class="boss-timeout"
            data-testid="boss-timeout-banner"
          >
            ⏳ 作答超时：仍可提交，但本阶段无法暴击回血
          </p>

          <div
            v-if="session.currentMove.value"
            class="boss-move-card"
            data-testid="boss-move-card"
          >
            <header class="boss-move-card__head">
              <span class="boss-move-card__label">
                {{ session.bossPhase.value === 2 ? '狂暴招式' : '招式预告' }} · Stage {{ session.stage.value }}
              </span>
            </header>
            <h3 class="boss-move-card__name">
              ★ {{ session.currentMove.value.name }}
            </h3>
            <p class="boss-move-card__preview">
              {{ session.currentMove.value.preview }}
            </p>
            <p class="boss-move-card__dmg">
              通关此招可对 boss 造成 {{ session.currentMove.value.damageOnDefeat }} 伤害
            </p>
          </div>
        </article>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.boss-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  min-height: calc(100dvh - 56px);
  background: radial-gradient(ellipse at center, #2c1408 0%, #0a0500 80%);
  color: var(--pyt-color-bg-elevated);
  font-family: var(--pyt-font-pixel), 'PingFang SC', monospace;
}
.boss-head {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  background: rgba(28, 14, 4, 0.75);
  border: 3px solid #b3402a;
  box-shadow: 0 0 12px rgba(255, 80, 40, 0.4);
}
.boss-exit {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 10px;
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-text-primary);
  cursor: pointer;
}
.boss-title {
  display: flex;
  flex-direction: column;
}
.boss-chapter {
  font-size: 11px;
  color: var(--pyt-color-gold);
}
.boss-name {
  font-size: 16px;
  font-weight: 700;
}
.boss-stage-bar {
  display: flex;
  gap: 4px;
}
.boss-stage-step {
  padding: 2px 8px;
  font-size: 11px;
  border: 2px solid var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
  background: rgba(110, 79, 29, 0.4);
  border-radius: 4px;
}
.boss-stage-step--current {
  border-color: var(--pyt-color-gold);
  background: rgba(241, 196, 83, 0.35);
}
.boss-stage-step--cleared {
  background: rgba(120, 200, 100, 0.45);
  border-color: #6bbf59;
  text-decoration: line-through;
}

/* 血条 / 招式预告 / 受击飘字 */
.boss-sprite-wrap {
  position: relative;
  display: inline-block;
}
.boss-dmg-float {
  position: absolute;
  top: 10%;
  left: 50%;
  transform: translateX(-50%);
  font-size: 28px;
  font-weight: 800;
  color: var(--pyt-color-bg-elevated);
  text-shadow:
    0 0 6px #ef4444,
    2px 2px 0 #4d1b14,
    -2px -2px 0 #4d1b14;
  pointer-events: none;
}
.dmg-float-enter-active {
  transition: transform 1.1s cubic-bezier(0.16, 0.84, 0.44, 1), opacity 1.1s ease-out;
}
.dmg-float-enter-from {
  transform: translateX(-50%) translateY(0) scale(1.4);
  opacity: 1;
}
.dmg-float-enter-to {
  transform: translateX(-50%) translateY(-60px) scale(1);
  opacity: 0;
}

.boss-hpbar {
  position: relative;
  width: 200px;
  height: 22px;
  margin: 8px auto 0;
  background: linear-gradient(180deg, var(--pyt-color-shadow) 0%, #1c0e02 100%);
  border: 3px solid var(--pyt-color-text-hint);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.4);
  overflow: hidden;
}
.boss-hpbar__fill {
  height: 100%;
  background: linear-gradient(180deg, #ef4444 0%, var(--pyt-color-danger) 60%, #7c1d1d 100%);
  transition: width 0.6s cubic-bezier(0.4, 0.2, 0.2, 1);
  box-shadow: inset 0 -2px 0 rgba(0, 0, 0, 0.25);
}
.boss-hpbar__label {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 12px;
  color: var(--pyt-color-bg-elevated);
  text-shadow: 1px 1px 0 #000;
  letter-spacing: 0.5px;
}

.boss-move-card {
  margin: 12px auto 0;
  padding: 10px 14px;
  background: rgba(28, 14, 4, 0.85);
  border: 2px solid #b3402a;
  border-radius: 6px;
  box-shadow: 0 0 10px rgba(255, 80, 40, 0.35);
  max-width: 280px;
  text-align: left;
}
.boss-move-card__head {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: var(--pyt-color-gold);
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}
.boss-move-card__name {
  margin: 0 0 6px;
  font-size: 16px;
  color: var(--pyt-color-gold-bright);
  text-shadow: 1px 1px 0 #4d1b14;
}
.boss-move-card__preview {
  margin: 0 0 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pyt-color-bg-elevated);
}
.boss-move-card__dmg {
  margin: 0;
  font-size: 11px;
  color: #ef4444;
  font-weight: 600;
}
.boss-timer {
  font-size: 13px;
  color: var(--pyt-color-gold);
}
.boss-state {
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  font-size: 18px;
  color: var(--pyt-color-bg-elevated);
}
.boss-state--error {
  color: #ff7466;
}
.boss-state--victory h2 {
  font-size: 28px;
  color: var(--pyt-color-gold);
}
.boss-reward {
  display: inline-block;
  padding: 2px 8px;
  margin: 0 4px;
  background: rgba(241, 196, 83, 0.25);
  border: 1px solid var(--pyt-color-gold);
  border-radius: 4px;
}
.boss-storyboard {
  font-style: italic;
  color: #b8d99a;
}
.boss-tip {
  color: rgba(255, 247, 230, 0.6);
  font-size: 12px;
}
.boss-body {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: 1.2fr 1.2fr 0.6fr;
  gap: 12px;
  min-height: 0;
}
.boss-problem,
.boss-editor,
.boss-sprite {
  background: rgba(15, 8, 2, 0.75);
  border: 2px solid var(--pyt-color-text-primary);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
}
.boss-problem__title {
  margin: 0;
  font-size: 13px;
  color: var(--pyt-color-gold);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.boss-problem__statement {
  margin: 0;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  color: var(--pyt-color-bg-elevated);
}
.badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
}
.badge--ai {
  background: rgba(120, 220, 120, 0.25);
  border: 1px solid #6bbf59;
  color: #b8d99a;
}
.badge--golden {
  background: rgba(241, 196, 83, 0.2);
  border: 1px solid var(--pyt-color-gold);
  color: var(--pyt-color-gold);
}
.boss-sample {
  background: rgba(0, 0, 0, 0.35);
  padding: 6px 10px;
  border: 1px solid var(--pyt-color-text-hint);
}
.boss-sample summary {
  cursor: pointer;
  font-size: 12px;
  color: var(--pyt-color-gold);
}
.boss-sample__pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.boss-sample__pair pre {
  margin: 0;
  font-size: 12px;
  background: #1a0e04;
  padding: 6px;
  border: 1px solid var(--pyt-color-text-primary);
  color: #fff;
}
.boss-editor__label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--pyt-color-gold);
}
.boss-editor__label textarea {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  background: #110a04;
  color: #f6f1e6;
  border: 2px solid var(--pyt-color-text-primary);
  padding: 8px;
  resize: vertical;
  min-height: 220px;
}
.boss-editor__bar {
  display: flex;
  gap: 8px;
}
.boss-btn {
  font-family: inherit;
  font-size: 12px;
  padding: 6px 14px;
  background: #c95e3a;
  color: var(--pyt-color-bg-elevated);
  border: 2px solid #5a1a08;
  cursor: pointer;
  box-shadow: 0 2px 0 #5a1a08;
}
.boss-btn--primary {
  background: var(--pyt-color-gold);
  color: #2a1a08;
  border-color: var(--pyt-color-text-hint);
  box-shadow: 0 2px 0 var(--pyt-color-text-hint);
}
.boss-btn--next {
  background: #6bbf59;
  border-color: #2d5b1b;
  box-shadow: 0 2px 0 #2d5b1b;
}
.boss-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.boss-btn:active:not(:disabled) {
  transform: translateY(1px);
  box-shadow: none;
}
.boss-result {
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid var(--pyt-color-text-primary);
}
.boss-result--passed {
  border-color: #6bbf59;
  background: rgba(60, 140, 50, 0.25);
}
.boss-result--failed {
  border-color: #b3402a;
  background: rgba(140, 60, 30, 0.25);
}
.boss-result p {
  margin: 2px 0;
}
.boss-hint {
  font-style: italic;
  color: var(--pyt-color-gold);
}
.boss-sprite {
  align-items: center;
  text-align: center;
  gap: 8px;
}
.boss-sprite__stage {
  margin: 0;
  color: var(--pyt-color-gold);
}
.boss-sprite__hp {
  margin: 0;
  color: #ff7466;
}
/* 回合制对攻（A 方案）：玩家血条 / 狂暴 / 暴击 / 反击 / 倒计时 */
.boss-hpbar--player {
  margin-top: 6px;
}
.boss-hpbar--knocked {
  box-shadow: 0 0 14px rgba(255, 80, 40, 0.85);
}
.boss-timeout {
  margin: 8px auto 0;
  max-width: 280px;
  padding: 4px 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(90, 30, 8, 0.65);
  border: 1px solid #ff7466;
  border-radius: 4px;
  text-align: center;
}
.boss-hpbar__fill--player {
  background: linear-gradient(180deg, #6bd06b 0%, #3ca85a 60%, #1d7c3a 100%);
}
.boss-enrage-badge {
  margin-left: 6px;
  padding: 1px 6px;
  font-size: 10px;
  color: #fff;
  background: #b3402a;
  border: 1px solid #ff7466;
  border-radius: 4px;
  animation: enrage-pulse 0.8s ease-in-out infinite;
}
@keyframes enrage-pulse {
  0%, 100% { box-shadow: 0 0 4px rgba(255, 80, 40, 0.5); }
  50% { box-shadow: 0 0 12px rgba(255, 80, 40, 0.95); }
}
.boss-timer__hint {
  display: block;
  font-size: 9px;
  color: var(--pyt-color-gold);
}
.boss-timer--crit .boss-timer__hint {
  color: #6bd06b;
}
.boss-timer--urgent {
  color: #ff7466;
  animation: enrage-pulse 0.6s ease-in-out infinite;
}
.boss-crit {
  margin: 8px auto 0;
  max-width: 280px;
  padding: 4px 10px;
  font-size: 12px;
  color: #2a1a08;
  background: var(--pyt-color-gold-bright);
  border: 1px solid var(--pyt-color-gold);
  border-radius: 4px;
  text-align: center;
}
.boss-counter {
  margin: 8px auto 0;
  max-width: 280px;
  padding: 4px 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(140, 60, 30, 0.5);
  border: 1px solid #b3402a;
  border-radius: 4px;
  text-align: center;
}
.boss-knockdown {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  background: rgba(20, 4, 0, 0.72);
  border: 2px solid #b3402a;
  border-radius: 8px;
  pointer-events: none;
}
.knockdown-fade-enter-active,
.knockdown-fade-leave-active {
  transition: opacity 0.35s ease;
}
.knockdown-fade-enter-from,
.knockdown-fade-leave-to {
  opacity: 0;
}
.boss-timer--expired .boss-timer__hint {
  color: #ff7466;
}
@media (prefers-reduced-motion: reduce) {
  .boss-enrage-badge,
  .boss-timer--urgent {
    animation: none;
  }
}

@media (max-width: 1024px) {
  .boss-body {
    grid-template-columns: 1fr;
  }
}
</style>
