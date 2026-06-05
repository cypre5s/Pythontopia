package com.pythontopia.submission.dto;

public enum Verdict {
  AC, // 通过：全部测试用例正确
  WA, // 答案错误：输出与期望不一致
  RE, // 运行时错误：进程非零退出
  TLE, // 超时：超过用例时间限制
  CE, // 未通过静态检查：禁用 token / 代码过长 / 为空
  MLE, // 超内存
  SYSTEM_ERROR // 判题系统异常：如无可用测试用例
}
