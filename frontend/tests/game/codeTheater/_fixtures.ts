// 代码剧场单测共享 fixture（非 .spec，不会被 vitest 当测试文件收集）。
// RenderCommand 真实来源是 C-mappers、Trace 来自 C-trace-engine；此刻它们可能未就绪，
// 故此处用契约类型（services/codeTheater/types.ts）自造贴近其产出的样例。
import type { RenderAction, RenderCommand, Trace } from '@/services/codeTheater/types';

export function cmd(
  step: number,
  action: RenderAction,
  payload: RenderCommand['payload'] = {},
  durationMs = 100,
): RenderCommand {
  return { step, action, payload, durationMs };
}

export function makeTrace(
  source: string,
  opts: { truncated?: boolean; stdout?: string } = {},
): Trace {
  return {
    version: 1,
    source,
    events: [],
    stdout: opts.stdout ?? '',
    truncated: opts.truncated ?? false,
    stepCount: 0,
  };
}

/** 代表性程序：累加 + for 循环 + print（贴近 C-mappers 的事件→指令映射）。 */
export const SUM_LOOP_SOURCE = [
  'total = 0',
  'for i in range(3):',
  '    total = total + i',
  'print(total)',
].join('\n');

export function sumLoopCommands(): RenderCommand[] {
  // 使用 C-mappers 实际产出的主键（set_var → varName）。
  return [
    cmd(0, 'set_var', { varName: 'total', value: '0', scope: 'global', line: 1 }, 100),
    cmd(1, 'walk_loop', { loopId: 'for@2', loopType: 'for', iter: 0, varName: 'i', value: '0', line: 2 }, 120),
    cmd(2, 'set_var', { varName: 'total', value: '0', line: 3 }, 100),
    cmd(3, 'walk_loop', { loopId: 'for@2', loopType: 'for', iter: 1, varName: 'i', value: '1', line: 2 }, 120),
    cmd(4, 'set_var', { varName: 'total', value: '1', line: 3 }, 100),
    cmd(5, 'walk_loop', { loopId: 'for@2', loopType: 'for', iter: 2, varName: 'i', value: '2', line: 2 }, 120),
    cmd(6, 'set_var', { varName: 'total', value: '3', line: 3 }, 100),
    cmd(7, 'move_to_line', { line: 4 }, 80),
    cmd(8, 'speak', { text: '3', channel: 'output', line: 4 }, 150),
  ];
}

/**
 * 一组「点亮路灯」world_effect 指令（对齐冻结契约 data/codeTheater/quests/worldEffects.ts）：
 * 共 total 盏，前 doneCount 盏 done、其余 pending。供舞台/状态机消费测试。
 */
export function lampWorldCommands(total = 4, doneCount = total, startStep = 0): RenderCommand[] {
  const out: RenderCommand[] = [];
  for (let i = 0; i < total; i += 1) {
    out.push(
      cmd(
        startStep + i,
        'world_effect',
        {
          goal: 'coverage',
          targetNoun: '路灯',
          target: i + 1,
          state: i < doneCount ? 'done' : 'pending',
          actionVerb: '点亮',
          unit: '盏',
        },
        120,
      ),
    );
  }
  return out;
}
