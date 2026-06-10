import { defineStore } from 'pinia';

/**
 * 工坊「书房」UI 临时状态：当前在弹窗中打开的资源 id。
 *
 * R-14 域1④ URL 隐藏 id：资源详情不再把 id 写进 URL（旧 `/workshop?id=`），改由本 store 持有。
 * 目的：不在网址里暴露可枚举的资源 id，避免学生改网址猜测/访问他人资源（后端本就做
 * `WHERE id=? AND user_id=?` 归属校验，这里是纵深加固 + 不暴露 id）。代价是资源详情不可深链/分享，
 * 与「URL 隐藏 id」决策一致。仅会话内有效（刷新清空，回到工坊列表）。
 */
export const useWorkshopUiStore = defineStore('workshopUi', {
  state: () => ({
    openResourceId: null as number | null,
  }),
  actions: {
    openResource(id: number): void {
      this.openResourceId = id;
    },
    closeResource(): void {
      this.openResourceId = null;
    },
  },
});
