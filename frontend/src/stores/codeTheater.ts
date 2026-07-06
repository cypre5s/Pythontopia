// 代码剧场跨页交接（handoff）store。
//
// 用途：训练场判题 WA/RE 后，把「学生这次提交的代码 + 调试输入」交给代码剧场，
// 让其进入「自由可视化模式」把这段真实代码的执行过程演出来（学习预览，不判分）。
// 仅持有一次性待消费载荷；代码剧场页 onMounted 时 consume（读取并清空），刷新即安全降级。

import { defineStore } from 'pinia';

/** 交给代码剧场自由可视化的一次性载荷。 */
export interface CodeTheaterFreeRun {
  /** 要可视化执行的学生代码。 */
  readonly code: string;
  /** 喂给 input() 的标准输入（按行切分，每行一次）；无则空串。 */
  readonly stdin: string;
  /** 来源标签（如题目标题），仅用于横幅展示。 */
  readonly label: string;
}

interface CodeTheaterState {
  pendingFreeRun: CodeTheaterFreeRun | null;
}

export const useCodeTheaterStore = defineStore('codeTheater', {
  state: (): CodeTheaterState => ({ pendingFreeRun: null }),
  actions: {
    /** 设置一次性待可视化载荷（训练场跳转前调用）。 */
    setFreeRun(payload: CodeTheaterFreeRun): void {
      this.pendingFreeRun = { ...payload };
    },
    /** 读取并清空待可视化载荷；无则返回 null。 */
    consumeFreeRun(): CodeTheaterFreeRun | null {
      const pending = this.pendingFreeRun;
      this.pendingFreeRun = null;
      return pending;
    },
  },
});
