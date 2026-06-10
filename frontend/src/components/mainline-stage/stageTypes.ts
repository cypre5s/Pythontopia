// A5 (fe-stage) 本地事件契约：三件套子组件 → 学习页的进度回流。
//
// stage 的「静态定义 + 进度」类型已统一到 A3 的 @/api/mainline `StageDetailDto`
// （A5↔A3 契约对账方案 A：新增 GET /api/mainline/stages/{stageId} 详情端点，overview 保持轻量）。
// 本文件只保留本端内部事件类型，不再镜像 overview/stage 形状（避免契约二处发散）。

/**
 * 三件套子组件 → 学习页的进度上报（本端内部事件契约，与后端返回 DTO 解耦）。
 * 子组件打点成功后用本地已知 id 乐观更新并 emit 自己负责的那一维度；学习页据此
 * 合并刷新总览与「全部过线」状态（completed 由学习页按三件套是否均过线客户端推导）。
 */
export interface StageTaskProgress {
  stageId: number;
  /** 「看」：当前累计已观看秒数。 */
  videoWatchedSec?: number;
  /** 「学」：当前已读 page_id 集合。 */
  readPageIds?: number[];
  /** 「练」：当前已完成 problem_id 集合。 */
  doneProblemIds?: number[];
}
