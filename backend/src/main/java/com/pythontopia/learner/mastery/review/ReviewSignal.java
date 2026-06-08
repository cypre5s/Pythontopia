package com.pythontopia.learner.mastery.review;

import java.time.OffsetDateTime;

/**
 * 某个 KC 的"当下"复习信号：由引擎根据 FSRS-lite 状态（或缺省时的掌握度播种）计算，
 * 供遗忘回声排程、错题排序与 Phase 2 百晓工坊推送共用。
 */
public record ReviewSignal(
    String kcCode,
    double stability,
    double retrievability,
    boolean due,
    OffsetDateTime lastReviewAt,
    OffsetDateTime dueAt,
    int reps,
    int lapses) {}
