// Boss 副本前端状态机。
//
// 阶段流转：
//   idle -> loading -> playing
//   playing -> evaluating -> passed(下一阶段) | failed(允许重试)
//   通过 stage 4 后 -> victory
//
// 评测策略：先在浏览器跑 Pyodide 给玩家即时反馈，再调真后端 submitBossAttempt
// 把分数 / mastery 同步入库（后端 5xx 时仍以 Pyodide 判定为准）。

import { ref, type Ref } from 'vue';
import { requestBossProblem, submitBossAttempt } from '@/api/boss';
import type {
  BossAttemptResultDto,
  BossProblemDto,
  BossSessionStatus,
} from '@/api/dto/Boss';
import { getPyodideRunner } from '@/game/adapters/pyodide';
import { useMainQuestStore } from '@stores/mainQuest';

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
  /** 当前阶段招式预告，由 chapter+stage 查表得到。 */
  currentMove: Ref<BossMove | null>;
  /** 最近一次造成的伤害（用于飘字动画）；null = 未刚扣血。 */
  recentDamage: Ref<number | null>;
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

export function getBossMove(chapter: number, stage: number): BossMove | null {
  const moves = BOSS_MOVES[chapter];
  if (!moves || stage < 1 || stage > moves.length) return null;
  return moves[stage - 1] ?? null;
}

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
  const currentMove = ref<BossMove | null>(getBossMove(opts.chapter, 1));
  const recentDamage = ref<number | null>(null);

  const mainQuest = useMainQuestStore();

  async function startStage(): Promise<void> {
    phase.value = 'loading';
    lastResult.value = null;
    elapsedSec.value = 0;
    // 每个 stage 切换招式预告。
    currentMove.value = getBossMove(opts.chapter, stage.value);
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
    // 写主线 AI 出题历史日志（用于自适应难度）
    mainQuest.recordAiProblem({
      problemId: p.problemId,
      kc: p.kcCode,
      difficulty: p.difficulty,
      succeededInSec: passed ? elapsedSec.value : null,
      timestamp: new Date().toISOString(),
    });
    if (passed) {
      clearedStages.value += 1;
      // boss 每个 stage 通过都触发 onSubmissionPassed，
      // 让 evidenceRequired.type==='submission' 的主线任务能被 boss 副本通过解锁。
      mainQuest.onSubmissionPassed({
        problemId: p.problemId,
        kcCode: p.kcCode,
      });
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
        phase.value = 'victory';
        mainQuest.defeatBoss(opts.chapter);
      } else {
        phase.value = 'passed';
      }
    } else {
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
    currentMove.value = getBossMove(opts.chapter, 1);
    recentDamage.value = null;
  }

  function tickSecond(): void {
    if (phase.value === 'playing') {
      elapsedSec.value += 1;
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
    startStage,
    submit,
    reset,
    status,
    tickSecond,
    advanceStage,
  };
}
