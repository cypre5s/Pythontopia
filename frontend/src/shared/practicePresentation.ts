import type { SubmissionStatus } from "./types";

export const pendingSubmissionStates: SubmissionStatus[] = ["prepared", "dispatching", "queued", "running"];
export const submissionStatusName: Record<SubmissionStatus, string> = {
  prepared: "已保存，等待判定",
  dispatching: "正在送交判题",
  queued: "已排队",
  running: "正在运行",
  accepted: "回答正确",
  wrong_answer: "回答错误",
  compile_error: "编译错误",
  runtime_error: "运行错误",
  time_limit: "超出时间限制",
  memory_limit: "超出内存限制",
  infrastructure_error: "判题服务暂不可用",
  submission_unknown: "判定状态待确认，请等待教师处理",
};
