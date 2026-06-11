package com.pythontopia.mainline.practice;

/**
 * 小节「练」客观题题型。
 *
 * <p>OJ 编程题<b>不在此枚举</b>——它仍走 {@code course_problem} + {@code mainline_stage.example_problem_ids}
 * + 训练场真实判题（{@code postExampleDone}）。本枚举只覆盖站内即时判分的客观题（选择 / 判断 / 填空）。
 */
public enum PracticeQuestionType {
  CHOICE,
  JUDGE,
  FILL
}
