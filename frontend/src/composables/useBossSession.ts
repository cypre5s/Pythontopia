// Boss 副本前端状态机。
//
// 阶段流转：
//   idle -> loading -> playing
//   playing -> evaluating -> passed(下一阶段) | failed(允许重试)
//   通过 stage 4 后 -> victory
//
// 评测策略：先在浏览器跑 Pyodide 给玩家即时反馈，再调真后端 submitBossAttempt
// 把分数 / mastery 同步入库（后端 5xx 时仍以 Pyodide 判定为准）。

import { computed, ref, type ComputedRef, type Ref } from 'vue';
import { requestBossProblem, submitBossAttempt } from '@/api/boss';
import type {
  BossAttemptResultDto,
  BossProblemDto,
  BossSessionStatus,
} from '@/api/dto/Boss';
import { getPyodideRunner } from '@/game/adapters/pyodide';
import { BOSS_MOVES_ENRAGE } from '@/data/bossMovesEnrage';

export type BossPhase =
  | 'idle'
  | 'loading'
  | 'playing'
  | 'evaluating'
  | 'passed'
  | 'failed'
  | 'victory'
  | 'error';

export interface UseBossSessionOptions {
  chapter: number;
  totalStages?: number;
}

/**
 * 每章 boss 4 个阶段对应 4 个招式名（按 chapter 主题）。
 * 招式名只是文本预告，不实际改变题目，目的是让 boss 战在「玩家提交代码」之外有"对战"叙事。
 */
export interface BossMove {
  /** 招式名（中文，带 boss 个性） */
  name: string;
  /** 招式介绍（玩家读到题目前看到的预告） */
  preview: string;
  /** 招式造成的伤害（设计上 = 玩家通关该 stage 后造成的对应反伤；总 100） */
  damageOnDefeat: number;
}

export interface UseBossSessionReturn {
  phase: Ref<BossPhase>;
  problem: Ref<BossProblemDto | null>;
  code: Ref<string>;
  stage: Ref<number>;
  clearedStages: Ref<number>;
  lastResult: Ref<BossAttemptResultDto | null>;
  elapsedSec: Ref<number>;
  errorMessage: Ref<string>;
  /** boss 当前 HP（满 100，每通过一 stage 扣 25）。 */
  bossHp: Ref<number>;
  /** 当前阶段招式预告；bossPhase 变化时自动切换狂暴文案。 */
  currentMove: ComputedRef<BossMove | null>;
  /** 最近一次造成的伤害（用于飘字动画）；null = 未刚扣血。 */
  recentDamage: Ref<number | null>;
  /** 玩家 HP（满 100）：答错 / 超时被 boss 反击扣血，归零则「被击倒」重整（低风险，不结束副本）。 */
  playerHp: Ref<number>;
  /** boss 阶段：bossHp≤50 进入第 2 阶段（狂暴）——反击更狠、限时更短。 */
  bossPhase: Ref<number>;
  /** 最近一次被 boss 反击的伤害（玩家侧飘字）；null = 未刚被击。 */
  recentPlayerDamage: Ref<number | null>;
  /** 最近一次作答是否暴击（限时内快答）。 */
  lastHitCrit: Ref<boolean>;
  /** 本阶段剩余作答时间（秒，倒计时）：既是 telegraph 压力，也决定暴击。 */
  timeLeftSec: Ref<number>;
  /** 本阶段限时（秒），随 boss 阶段缩短。 */
  stageTimeLimit: Ref<number>;
  /** 玩家 HP 归零后重整（击倒动画触发）。 */
  playerKnockedDown: Ref<boolean>;
  /** 本阶段倒计时已耗尽（仍可作答但无法暴击）。 */
  stageTimedOut: Ref<boolean>;
  startStage: () => Promise<void>;
  submit: () => Promise<void>;
  reset: () => void;
  status: () => BossSessionStatus;
  tickSecond: () => void;
  advanceStage: () => Promise<void>;
}

/** 7 章 × 4 阶段招式表（与 boss 主题一致）。 */
const BOSS_MOVES: Record<number, readonly BossMove[]> = {
  1: [
    { name: '算珠初震', preview: '算盘灵抛出一连串横向算珠，需要你识别"什么是计算"。', damageOnDefeat: 25 },
    { name: '光阴回响', preview: '算盘指向算筹→ENIAC→现代芯片，问你计算思维 5 步走。', damageOnDefeat: 25 },
    { name: '木珠迷阵', preview: '5 行算珠混乱排列，让你区分计算科学与计算机科学边界。', damageOnDefeat: 25 },
    { name: '终幕之珠', preview: '所有算珠汇聚成一个超大珠子，综合考查本章。', damageOnDefeat: 25 },
  ],
  2: [
    { name: 'IDLE 启动咒', preview: 'Python 学徒展开终端，问你解释器启动符是哪个。', damageOnDefeat: 25 },
    { name: '变量魔法术', preview: '巫师举起 a, b = 3, 4，让你算 a+b。', damageOnDefeat: 25 },
    { name: 'input 之印', preview: '学徒念出 input/print 双轨咒，让你回响一段文字。', damageOnDefeat: 25 },
    { name: '解释执行终', preview: 'Python 蛇盘绕终端，考综合编译/解释差别。', damageOnDefeat: 25 },
  ],
  3: [
    { name: '0/1 视察', preview: '独眼睁开，0/1 字符在你周围旋转，问十进制 7 的二进制。', damageOnDefeat: 25 },
    { name: '字符切片', preview: '黑白条纹收缩为字符串，让你切出 Pythontopia 中的 "thon"。', damageOnDefeat: 25 },
    { name: '编码漩涡', preview: 'UTF-8 / ASCII 风暴袭来，问你两者本质差别。', damageOnDefeat: 25 },
    { name: '进制终焉', preview: '独眼瞳孔变形为 2/8/10/16 四面体，综合考进制转换。', damageOnDefeat: 25 },
  ],
  4: [
    { name: '钟楼鸣初', preview: '钟楼敲第一声，让你写出顺序结构 hello world。', damageOnDefeat: 25 },
    { name: '分支铭', preview: '钟摆左右摇摆，问你 if/elif 边界。', damageOnDefeat: 25 },
    { name: 'for 之环', preview: '齿轮自转 N 圈，让你写 for i in range。', damageOnDefeat: 25 },
    { name: '循环终断', preview: '钟塔轰鸣 4 阶段，考查 while + break/continue。', damageOnDefeat: 25 },
  ],
  5: [
    { name: '红光折射', preview: '棱镜射出红光，list[] 在你面前排成一列让你访问。', damageOnDefeat: 25 },
    { name: '绿光折射', preview: '绿光折射成 dict{}，让你 CRUD 一个 dict。', damageOnDefeat: 25 },
    { name: '蓝光折射', preview: '蓝光折射成 set，让你去重一组数据。', damageOnDefeat: 25 },
    { name: '三光合流', preview: '红绿蓝三光合流，list+dict+set 综合。', damageOnDefeat: 25 },
  ],
  6: [
    { name: '卷轴开篇', preview: '文档守护神翻开第一页，问 with open 的好处。', damageOnDefeat: 25 },
    { name: '猫头鹰之眼', preview: '猫头鹰盯着你，让你用 readlines 数行。', damageOnDefeat: 25 },
    { name: '编码钥匙', preview: '金钥匙旋转，让你用 utf-8 读一段中文。', damageOnDefeat: 25 },
    { name: '词频终章', preview: '卷轴合上，综合考词频统计 + 文件 IO。', damageOnDefeat: 25 },
  ],
  7: [
    { name: '类的诞生', preview: '镜面浮现一只蓝色蛋，问 class 与 object 的关系。', damageOnDefeat: 25 },
    { name: '继承之裂', preview: '镜面裂出多个小镜，让你写 class Dog(Animal)。', damageOnDefeat: 25 },
    { name: '抽象金字', preview: '类层级金字塔上升，让你写 abstract 方法。', damageOnDefeat: 25 },
    { name: '归纳终极', preview: '镜面汇聚成元，综合考类 + lambda + 递归。', damageOnDefeat: 25 },
  ],
};

export function getBossMove(chapter: number, stage: number, bossPhase = 1): BossMove | null {
  const moves = BOSS_MOVES[chapter];
  if (!moves || stage < 1 || stage > moves.length) return null;
  const base = moves[stage - 1];
  if (!base) return null;
  if (bossPhase !== 2) return base;
  const enrage = BOSS_MOVES_ENRAGE[chapter]?.[stage - 1];
  if (!enrage) {
    return {
      ...base,
      name: `狂暴·${base.name}`,
      preview: `【狂暴】${base.preview}`,
    };
  }
  return { ...base, name: enrage.name, preview: enrage.preview };
}

// ── 回合制对攻参数（A 方案）──
const PLAYER_MAX_HP = 100;
/** bossHp ≤ 此值进入第 2 阶段（狂暴）。 */
const BOSS_PHASE2_HP = 50;
/** 每阶段作答限时：狂暴阶段更短，制造压力。 */
const STAGE_TIME_PHASE1 = 110;
const STAGE_TIME_PHASE2 = 75;
/** boss 反击伤害（答错 / 超时）：狂暴阶段更狠。 */
const BOSS_ATTACK_PHASE1 = 18;
const BOSS_ATTACK_PHASE2 = 30;
/** 暴击（限时过半快答）回血量。 */
const CRIT_HEAL = 12;

export function useBossSession(opts: UseBossSessionOptions): UseBossSessionReturn {
  const totalStages = opts.totalStages ?? 4;
  const phase = ref<BossPhase>('idle');
  const problem = ref<BossProblemDto | null>(null);
  const code = ref<string>('');
  const stage = ref<number>(1);
  const clearedStages = ref<number>(0);
  const lastResult = ref<BossAttemptResultDto | null>(null);
  const elapsedSec = ref<number>(0);
  const errorMessage = ref<string>('');
  const bossHp = ref<number>(100);
  const recentDamage = ref<number | null>(null);
  const playerHp = ref<number>(PLAYER_MAX_HP);
  const recentPlayerDamage = ref<number | null>(null);
  const lastHitCrit = ref<boolean>(false);
  // bossPhase 由 bossHp 派生：≤50 进入第 2 阶段（狂暴）。
  const bossPhase = computed<number>(() => (bossHp.value <= BOSS_PHASE2_HP ? 2 : 1));
  const currentMove = computed(() => getBossMove(opts.chapter, stage.value, bossPhase.value));
  const stageTimeLimit = computed<number>(() =>
    bossPhase.value === 2 ? STAGE_TIME_PHASE2 : STAGE_TIME_PHASE1,
  );
  const timeLeftSec = ref<number>(STAGE_TIME_PHASE1);
  const playerKnockedDown = ref<boolean>(false);
  const stageTimedOut = ref<boolean>(false);

  function clearPlayerDamageSoon(): void {
    if (typeof window !== 'undefined') {
      window.setTimeout(() => {
        recentPlayerDamage.value = null;
      }, 1200);
    }
  }

  async function startStage(): Promise<void> {
    phase.value = 'loading';
    lastResult.value = null;
    elapsedSec.value = 0;
    // 重置本阶段倒计时（telegraph：限时内作答才暴击），并清掉上轮暴击 / 反击痕迹。
    timeLeftSec.value = stageTimeLimit.value;
    lastHitCrit.value = false;
    recentPlayerDamage.value = null;
    stageTimedOut.value = false;
    playerKnockedDown.value = false;
    try {
      const fetched = await requestBossProblem(opts.chapter, stage.value);
      problem.value = fetched;
      code.value = '';
      phase.value = 'playing';
    } catch (error) {
      phase.value = 'error';
      errorMessage.value = error instanceof Error ? error.message : String(error);
    }
  }

  async function runPyodideEvaluation(
    p: BossProblemDto,
    source: string,
  ): Promise<{ ok: boolean; actual: string; stderr: string }> {
    try {
      const runner = getPyodideRunner();
      // 直接走 Pyodide adapter 的 stdin 通道，避免在 user code 前注入 stub
      // 干扰玩家自己定义的 input 函数 / 全局变量。
      const result = await runner.run(source, p.sampleInput);
      const actual = (result.stdout ?? '').trimEnd();
      const expected = (p.sampleOutput ?? '').trimEnd();
      return { ok: actual === expected, actual, stderr: result.stderr ?? '' };
    } catch (error) {
      const stderr = error instanceof Error ? error.message : String(error);
      return { ok: false, actual: '', stderr };
    }
  }

  async function submit(): Promise<void> {
    if (!problem.value) return;
    phase.value = 'evaluating';
    const p = problem.value;
    const localEval = await runPyodideEvaluation(p, code.value);
    let backendResult: BossAttemptResultDto | null = null;
    try {
      backendResult = await submitBossAttempt({
        problemId: p.problemId,
        chapter: p.chapter,
        stage: p.stage,
        code: code.value,
        elapsedSec: elapsedSec.value,
      });
    } catch (error) {
      // 后端失败不影响本地 Pyodide 判定
      const msg = error instanceof Error ? error.message : String(error);
      backendResult = {
        problemId: p.problemId,
        passed: localEval.ok,
        passedTestCases: localEval.ok ? p.totalTestCases : 0,
        totalTestCases: p.totalTestCases,
        score: localEval.ok ? 80 : 0,
        feedback: `后端联通失败，按本地 Pyodide 判定：${msg}`,
      };
    }
    // 后端有隐藏用例与正式判题结果；本地 Pyodide 只跑样例，不能把后端 WA 抬成通过。
    const passed = backendResult.passed;
    const finalResult: BossAttemptResultDto = {
      ...backendResult,
      passed,
      passedTestCases: passed
        ? backendResult.passedTestCases
        : backendResult.passedTestCases,
      feedback: passed
        ? backendResult.feedback || '通过！沙箱与服务端均确认无误。'
        : backendResult.feedback || localEval.stderr || '未通过，请检查输出',
      ...(localEval.actual && !passed
        ? {
            failureFirstHint:
              backendResult.failureFirstHint ??
              `期望 ${JSON.stringify(p.sampleOutput.trimEnd())}，实际 ${JSON.stringify(localEval.actual)}`,
          }
        : {}),
    };
    lastResult.value = finalResult;
    // R-15：boss 自适应难度由后端 BossService 据掌握度推断；不再写客户端 mainQuest 出题日志。
    if (passed) {
      clearedStages.value += 1;
      // 限时内快答（剩余时间过半）= 暴击：回血 + 标记，给「抢时间」正反馈。
      // 刻意不改 boss 扣血量，保证 4 阶段恰好把 boss 打空（与既有契约一致）。
      const crit = timeLeftSec.value >= Math.ceil(stageTimeLimit.value / 2);
      lastHitCrit.value = crit;
      if (crit) {
        playerHp.value = Math.min(PLAYER_MAX_HP, playerHp.value + CRIT_HEAL);
      }
      // boss HP 真扣血 + 飘字效果
      const move = currentMove.value ?? getBossMove(opts.chapter, stage.value);
      const dmg = move?.damageOnDefeat ?? Math.floor(100 / totalStages);
      bossHp.value = Math.max(0, bossHp.value - dmg);
      recentDamage.value = dmg;
      // 飘字 1.2s 后清空（UI 通过 watch 处理 fade-out）
      if (typeof window !== 'undefined') {
        window.setTimeout(() => {
          recentDamage.value = null;
        }, 1200);
      }
      if (stage.value >= totalStages) {
        // R-15：boss 通关不再推进旧主线（mainQuest 已退役）；毕业以 mainline 章末测试星级为准。
        phase.value = 'victory';
      } else {
        phase.value = 'passed';
      }
    } else {
      // 答错 → boss 反击：玩家扣血（狂暴阶段更狠）。归零则「被击倒重整」回满，
      // 不结束副本（低风险重试，符合教育场景）。
      lastHitCrit.value = false;
      const atk = bossPhase.value === 2 ? BOSS_ATTACK_PHASE2 : BOSS_ATTACK_PHASE1;
      const nextHp = playerHp.value - atk;
      recentPlayerDamage.value = atk;
      if (nextHp <= 0) {
        playerHp.value = PLAYER_MAX_HP;
        playerKnockedDown.value = true;
        if (typeof window !== 'undefined') {
          window.setTimeout(() => {
            playerKnockedDown.value = false;
          }, 1600);
        }
      } else {
        playerHp.value = nextHp;
      }
      clearPlayerDamageSoon();
      phase.value = 'failed';
    }
  }

  function reset(): void {
    phase.value = 'idle';
    problem.value = null;
    code.value = '';
    stage.value = 1;
    clearedStages.value = 0;
    lastResult.value = null;
    elapsedSec.value = 0;
    errorMessage.value = '';
    bossHp.value = 100;
    recentDamage.value = null;
    playerHp.value = PLAYER_MAX_HP;
    recentPlayerDamage.value = null;
    lastHitCrit.value = false;
    timeLeftSec.value = STAGE_TIME_PHASE1;
    playerKnockedDown.value = false;
    stageTimedOut.value = false;
  }

  function tickSecond(): void {
    if (phase.value === 'playing') {
      elapsedSec.value += 1;
      if (timeLeftSec.value > 0) {
        timeLeftSec.value -= 1;
        // 时间耗尽的一刻：boss 趁机出招打一下（一次性，封底保留 1 滴血），
        // 之后停在 0——仍可作答但不再暴击，制造「快答」压力而非硬性失败。
        if (timeLeftSec.value === 0) {
          stageTimedOut.value = true;
          const atk = bossPhase.value === 2 ? BOSS_ATTACK_PHASE2 : BOSS_ATTACK_PHASE1;
          playerHp.value = Math.max(1, playerHp.value - atk);
          recentPlayerDamage.value = atk;
          clearPlayerDamageSoon();
        }
      }
    }
  }

  function status(): BossSessionStatus {
    return {
      chapter: opts.chapter,
      stage: stage.value,
      clearedStages: clearedStages.value,
      problem: problem.value,
      lastAttempt: lastResult.value,
      victory: phase.value === 'victory',
    };
  }

  // passed 状态下推进到下一 stage：stage++ 后重新 startStage。
  const advanceStage = async (): Promise<void> => {
    if (phase.value !== 'passed') return;
    if (stage.value >= totalStages) return;
    stage.value += 1;
    await startStage();
  };

  return {
    phase,
    problem,
    code,
    stage,
    clearedStages,
    lastResult,
    elapsedSec,
    errorMessage,
    bossHp,
    currentMove,
    recentDamage,
    playerHp,
    bossPhase,
    recentPlayerDamage,
    lastHitCrit,
    timeLeftSec,
    stageTimeLimit,
    playerKnockedDown,
    stageTimedOut,
    startStage,
    submit,
    reset,
    status,
    tickSecond,
    advanceStage,
  };
}
