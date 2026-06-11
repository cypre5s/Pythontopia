package com.pythontopia.mainline.dto;

/**
 * 小节「练」OJ 编程题展示条目（统一编号 / 命名 / KC 覆盖层）。
 *
 * <p>R-14「safe」决策：不改 {@code course_problem.id}，由 {@code mainline_oj_catalog}（Phase 1 P1 seed）
 * 提供统一展示编号（如 {@code OJ-01}）、规范题名与 KC。{@code id} 仍是 {@code course_problem.id}，
 * 学生在训练场用它真实判题，完成后经 {@code example-done} 打点。
 */
public record StageExampleDto(long id, String displayNo, String displayName, String kcCode) {}
