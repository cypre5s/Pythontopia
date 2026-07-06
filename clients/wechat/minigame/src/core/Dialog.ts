// 对话/信息框的运行时状态机：记住当前打开的视图与来源对象（用于走远自动关闭）。
// 只管「开/关 + 当前内容」，不做渲染，也不解析触摸——交互编排在 Game 层。
import type { DialogDeepLink, DialogView } from './dialogContent';
import type { Interactable } from './TileMap';

export class Dialog {
  private view: DialogView | null = null;
  private target: Interactable | null = null;

  open(view: DialogView, target: Interactable): void {
    this.view = view;
    this.target = target;
  }

  close(): void {
    this.view = null;
    this.target = null;
  }

  isOpen(): boolean {
    return this.view !== null;
  }

  currentView(): DialogView | null {
    return this.view;
  }

  /** 对话来源对象（用于判断玩家是否走出交互范围而自动关闭）。 */
  currentTarget(): Interactable | null {
    return this.target;
  }

  /**
   * 异步回填 NPC 个性化寒暄：仅当当前对话仍是同一个 NPC 时生效（玩家可能已走开/换对象）。
   * 非阻塞链路的收尾，绝不强行打开/覆盖其它对话。
   */
  applyNpcGreeting(npcKey: string, greeting: string): void {
    if (
      this.view &&
      this.view.kind === 'npc' &&
      this.target?.npcKey === npcKey &&
      greeting.trim().length > 0
    ) {
      this.view = { ...this.view, greeting };
    }
  }

  hasDeepLink(): boolean {
    return this.view?.deepLink != null;
  }

  deepLink(): DialogDeepLink | null {
    return this.view?.deepLink ?? null;
  }
}
