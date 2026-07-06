package com.pythontopia.bugmine.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 根据 KC 掌握度与学生画像易错命中，估算错题矿洞建议下探深度：
 * 掌握强者浅出（目标 L2），弱者需更深（目标 L4/L5）。
 */
@Component
public class BugMineProfileAdvisor {

  public int recommendedDepthTarget(BigDecimal masteryScore, boolean patternMatched) {
    double mastery = masteryScore == null ? 0.0 : masteryScore.doubleValue();
    if (mastery >= 0.75) {
      return 2;
    }
    if (mastery >= 0.45) {
      return patternMatched ? 4 : 3;
    }
    return patternMatched ? 5 : 4;
  }

  public String guidanceLabel(int depthTarget, boolean patternMatched) {
    if (depthTarget <= 2) {
      return "画像显示掌握较好，优先 L1 裸重试，通过即可出矿。";
    }
    if (depthTarget >= 5) {
      return patternMatched
          ? "画像命中你的易错模式，建议下探到 L5 变式确认迁移。"
          : "该 KC 掌握偏弱，建议至少下探到 L4 分层提示。";
    }
    return patternMatched
        ? "易错模式已命中，卡住时按阶梯逐层解锁，不要跳层抄答案。"
        : "掌握度中等，先 L1 独立重试，卡住再逐层下探。";
  }

  public String echoGuidanceLabel(int depthTarget) {
    int target = Math.max(4, depthTarget);
    return "遗忘回声：久未复习，建议下探到 L" + target + " 再确认。";
  }
}
