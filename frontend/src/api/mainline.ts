// R-15 主线域（mainline）前端访问层。
//
// 后端权威（旧前端 mainQuest 存档作废重开）：地图/星级/stage 三件套全部以后端为准，
// 本模块只做「typed DTO + thin fetch」，不做业务判定（星级公式、解锁规则、补星校验
// 全在 backend，见 docs/plans/r15-mainline-rebuild-plan.md §5/§9）。
//
// 契约来源：r15-mainline-rebuild-plan.md §9（STUDENT，全部 JWT）。本文件覆盖学生端
// 「overview / stage 三件套打点 / 无尽补星 / 错题复盘补星」；章末大测试相关端点
// 由 api/chapterTest.ts（A4）负责，本文件不实现。
//
// 失败语义：沿用 http.ts 全局拦截（ApiClientError + 401 / 网络 toast）。打点类高频写入
// 失败时由调用方（store）静默兜底，不打断学习；overview 拉取失败由页面显示错误态。

import { http } from './http';

/** 章末大测试状态（与后端 `chapter_test.status` 对齐；`NONE` = 尚未创建测试记录）。 */
export type ChapterTestStatus =
  | 'NONE'
  | 'PENDING'
  | 'GENERATING'
  | 'READY'
  | 'IN_PROGRESS'
  | 'SUBMITTED'
  | 'EXPIRED'
  | 'FAILED';

/**
 * 星级构成明细（后端 `chapter_star.breakdown` JSONB 的前端镜像）。
 * test/stage/mastery 为各路径贡献的星数（0/1），recovered 为已生效的补星来源标签
 * （如 `ENDLESS_RECOVERY` / `ERRORBOOK_RECOVERY`，JSONB 自由集合，故用宽松 string[]）。
 */
export interface StarBreakdownDto {
  test: number;
  stage: number;
  mastery: number;
  recovered: string[];
}

/** 本章补星完成情况（§9 overview.recovery）。 */
export interface RecoveryStatusDto {
  /** 错题复盘补星是否已发放（本章 bug-mine RESOLVED ≥ 3）。 */
  errorbookDone: boolean;
  /** 无尽挑战补星是否已发放（本章范围单轮答对 ≥ 8 且正确率 ≥ 80%）。 */
  endlessDone: boolean;
}

/**
 * 小章节（stage）在 overview 内的进度视图（§9）。
 * 注意：`pagesRead` / `examplesDone` 在 overview 内是**计数**（用于「X / required」进度条），
 * 而非 id 数组；id 数组（哪些页/例题已完成）只在 stage 学习页按需取（A5）。
 */
export interface MainlineStageDto {
  id: number;
  index: number;
  title: string;
  videoWatchedSec: number;
  requiredVideoSec: number;
  pagesRead: number;
  requiredPages: number;
  examplesDone: number;
  requiredExamples: number;
  completed: boolean;
}

/** 大章节（chapter）在 overview 内的视图（§9）。 */
export interface MainlineChapterDto {
  chapter: number;
  title: string;
  /** 是否已解锁（后端权威：通常前一章达标后解锁，第 1 章常解锁）。 */
  unlocked: boolean;
  /** 0–3★（chapter_star 权威）。 */
  stars: number;
  /** 章末大测试历史最高分；从未参试为 null。 */
  bestTestScore: number | null;
  stages: MainlineStageDto[];
  testStatus: ChapterTestStatus;
  starBreakdown: StarBreakdownDto;
  recovery: RecoveryStatusDto;
}

/** `GET /api/mainline/overview` 响应（§9，地图一次拉取）。 */
export interface MainlineOverviewDto {
  totalStars: number;
  chapters: MainlineChapterDto[];
}

/**
 * stage 学习页详情（`GET /api/mainline/stages/{stageId}`，§9 集成补充）。
 * overview 故意轻量不带静态定义（§11.4），学习页按需取本 DTO 渲染三件套（看/学/练）：
 * 静态定义（kcCodes / videoKeyword / coursewarePageIds / exampleProblemIds）+ 当前用户已完成 id 集合。
 * `pagesReadIds` / `examplesDoneIds` 与 `StageProgressDto` 命名一致（id 集合，非计数）。
 */
export interface StageDetailDto {
  id: number;
  chapter: number;
  chapterTitle: string;
  index: number;
  title: string;
  kcCodes: string[];
  videoKeyword: string;
  coursewarePageIds: number[];
  exampleProblemIds: number[];
  requiredVideoSec: number;
  requiredPages: number;
  requiredExamples: number;
  videoWatchedSec: number;
  pagesReadIds: number[];
  examplesDoneIds: number[];
  completed: boolean;
}

/**
 * stage 三件套打点端点的统一返回（video-progress / page-read / example-done 共用）。
 * 字段为后端重算后的该 stage 最新进度；`stageId` === overview 中 `MainlineStageDto.id`。
 * `pagesReadIds` / `examplesDoneIds` 为可选的「已完成 id 集合」，供 stage 学习页（A5）标记，
 * overview 聚合视图只用 `pagesRead` / `examplesDone` 计数。
 */
export interface StageProgressDto {
  stageId: number;
  chapter: number;
  index: number;
  title: string;
  videoWatchedSec: number;
  requiredVideoSec: number;
  pagesRead: number;
  requiredPages: number;
  examplesDone: number;
  requiredExamples: number;
  completed: boolean;
  pagesReadIds?: number[];
  examplesDoneIds?: number[];
}

/** 无尽模式轮次小结上报体（§7 / §9）。 */
export interface EndlessSummaryInput {
  chapter: number;
  correct: number;
  total: number;
  streak: number;
}

/** 补星类端点返回（无尽小结 / 错题复盘）：是否本次发放了一颗补星（幂等，后端权威）。 */
export interface StarAwardResultDto {
  starAwarded: boolean;
}

/** 拉取主线地图总览（地图/节点/星级/三件套一次取齐）。 */
export async function getMainlineOverview(): Promise<MainlineOverviewDto> {
  const response = await http.get<MainlineOverviewDto>('/api/mainline/overview');
  return response.data;
}

/** 拉取单个 stage 的学习页详情（静态定义 + 当前用户三件套进度 id 集合）。 */
export async function getStageDetail(stageId: number): Promise<StageDetailDto> {
  const response = await http.get<StageDetailDto>(`/api/mainline/stages/${stageId}`);
  return response.data;
}

/**
 * 上报视频观看时长增量（「看」打点）。
 * 后端硬校验：deltaSec ≤ 真实流逝时间，单次 ≤120s、每分钟 ≤2 次（防挂机刷）。
 */
export async function postVideoProgress(
  stageId: number,
  deltaSec: number,
): Promise<StageProgressDto> {
  const response = await http.post<StageProgressDto>(
    `/api/mainline/stages/${stageId}/video-progress`,
    { deltaSec },
  );
  return response.data;
}

/** 上报课件页阅读完成（「学」打点；幂等，重复同页只记一次）。 */
export async function postPageRead(stageId: number, pageId: number): Promise<StageProgressDto> {
  const response = await http.post<StageProgressDto>(
    `/api/mainline/stages/${stageId}/page-read`,
    { pageId },
  );
  return response.data;
}

/**
 * 上报例题完成（「练」打点）。
 * 若带 submissionId：后端校验该提交 PASSED 且属本人，否则拒收。
 */
export async function postExampleDone(
  stageId: number,
  problemId: number,
  submissionId?: number,
): Promise<StageProgressDto> {
  const body: { problemId: number; submissionId?: number } = { problemId };
  if (submissionId !== undefined) {
    body.submissionId = submissionId;
  }
  const response = await http.post<StageProgressDto>(
    `/api/mainline/stages/${stageId}/example-done`,
    body,
  );
  return response.data;
}

/**
 * 上报无尽模式一轮小结 → 触发 `ENDLESS_RECOVERY` 补星判定（后端校验 + 防刷：
 * 每日每章至多记 1 次有效补星）。返回是否发放补星。
 */
export async function postEndlessSummary(
  input: EndlessSummaryInput,
): Promise<StarAwardResultDto> {
  const response = await http.post<StarAwardResultDto>('/api/mainline/endless/summary', input);
  return response.data;
}

/**
 * 申领本章错题复盘补星（后端校验本章 bug-mine RESOLVED ≥ 3 后发放，幂等）。返回是否发放补星。
 */
export async function postRecoveryErrorbook(chapter: number): Promise<StarAwardResultDto> {
  const response = await http.post<StarAwardResultDto>(
    `/api/mainline/chapters/${chapter}/recovery/errorbook`,
    {},
  );
  return response.data;
}
