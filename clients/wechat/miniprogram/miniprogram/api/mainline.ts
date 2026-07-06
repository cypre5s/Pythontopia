// R-15 主线域（mainline）小程序访问层（镜像 frontend/src/api/mainline.ts）。
//
// 仅「typed DTO + thin fetch」：overview / stage 三件套打点 / 无尽补星 / 错题复盘补星。
// 章末大测试相关端点见 api/chapterTest.ts。打点类高频写入失败由调用方静默兜底，不打断学习。

import { request } from '../utils/request';
import {
  EndlessSummaryInput,
  MainlineOverviewDto,
  PracticeAnswerResultDto,
  StageDetailDto,
  StageProgressDto,
  StarAwardResultDto,
} from './dto/mainline';

/** 拉取主线地图总览（地图 / 节点 / 星级 / 三件套计数一次取齐）。 */
export function getMainlineOverview(): Promise<MainlineOverviewDto> {
  return request<MainlineOverviewDto>('/mainline/overview');
}

/** 拉取单个 stage 的学习页详情（静态定义 + 当前用户三件套进度 id 集合）。 */
export function getStageDetail(stageId: number): Promise<StageDetailDto> {
  return request<StageDetailDto>(`/mainline/stages/${stageId}`);
}

/**
 * 上报视频观看时长增量（「看」打点）。
 * 后端硬校验：单次 deltaSec ≤120、每分钟 ≤2 次（防挂机刷）。
 */
export function postVideoProgress(stageId: number, deltaSec: number): Promise<StageProgressDto> {
  return request<StageProgressDto, { deltaSec: number }>(
    `/mainline/stages/${stageId}/video-progress`,
    { method: 'POST', data: { deltaSec } },
  );
}

/** 上报课件页阅读完成（「学」打点；幂等，重复同页只记一次）。 */
export function postPageRead(stageId: number, pageId: number): Promise<StageProgressDto> {
  return request<StageProgressDto, { pageId: number }>(
    `/mainline/stages/${stageId}/page-read`,
    { method: 'POST', data: { pageId } },
  );
}

/** 批量上报课件页阅读（「学」打点，并集去重幂等）。 */
export function postPagesRead(stageId: number, pageIds: number[]): Promise<StageProgressDto> {
  return request<StageProgressDto, { pageIds: number[] }>(
    `/mainline/stages/${stageId}/pages-read`,
    { method: 'POST', data: { pageIds } },
  );
}

/**
 * 上报例题完成（「练」打点）。
 * 若带 submissionId：后端校验该提交 PASSED 且属本人，否则拒收。
 */
export function postExampleDone(
  stageId: number,
  problemId: number,
  submissionId?: number,
): Promise<StageProgressDto> {
  const body: { problemId: number; submissionId?: number } = { problemId };
  if (submissionId !== undefined) {
    body.submissionId = submissionId;
  }
  return request<StageProgressDto, { problemId: number; submissionId?: number }>(
    `/mainline/stages/${stageId}/example-done`,
    { method: 'POST', data: body },
  );
}

/**
 * 提交一道小节客观题作答，返回判分结果。
 * answers 长度按题型：CHOICE/JUDGE 为 1；FILL 为 blankCount（按空位顺序）。
 */
export function answerPracticeQuestion(
  stageId: number,
  questionId: number,
  answers: string[],
): Promise<PracticeAnswerResultDto> {
  return request<PracticeAnswerResultDto, { answers: string[] }>(
    `/mainline/stages/${stageId}/practice/${questionId}/answer`,
    { method: 'POST', data: { answers } },
  );
}

/** 上报无尽模式一轮小结 → 触发 ENDLESS_RECOVERY 补星判定（后端防刷）。返回是否发放补星。 */
export function postEndlessSummary(input: EndlessSummaryInput): Promise<StarAwardResultDto> {
  return request<StarAwardResultDto, EndlessSummaryInput>('/mainline/endless/summary', {
    method: 'POST',
    data: input,
  });
}

/** 申领本章错题复盘补星（后端校验本章 bug-mine RESOLVED ≥ 3 后发放，幂等）。返回是否发放补星。 */
export function postRecoveryErrorbook(chapter: number): Promise<StarAwardResultDto> {
  return request<StarAwardResultDto, Record<string, never>>(
    `/mainline/chapters/${chapter}/recovery/errorbook`,
    { method: 'POST', data: {} },
  );
}
