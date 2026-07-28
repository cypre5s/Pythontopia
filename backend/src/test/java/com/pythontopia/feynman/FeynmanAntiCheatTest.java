package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeynmanAntiCheatTest {

  @Test
  void legitimateNaturalLanguageTeachingIsAllowed() {
    assertThat(
            FeynmanAntiCheat.looksLikeAnswerDump(
                "range 的右端点不包含，range(1, 5) 只到 4；要包含 5 就写 range(1, 6)。"))
        .isFalse();
    assertThat(FeynmanAntiCheat.looksLikeAnswerDump("用 int() 把 input 转成整数再相加")).isFalse();
  }

  @Test
  void answerKeyKeywordIsBlocked() {
    assertThat(FeynmanAntiCheat.looksLikeAnswerDump("这是 answer_key 直接抄")).isTrue();
    assertThat(FeynmanAntiCheat.looksLikeAnswerDump("ANSWER_KEY=...")).isTrue();
  }

  @Test
  void fencedCodeBlockIsBlocked() {
    assertThat(FeynmanAntiCheat.looksLikeAnswerDump("照这个抄：```python\nprint(1)\n```")).isTrue();
  }

  @Test
  void fullProgramPasteIsBlocked() {
    assertThat(FeynmanAntiCheat.looksLikeAnswerDump("def solve():\n    return 42")).isTrue();
    assertThat(FeynmanAntiCheat.looksLikeAnswerDump("import sys\nprint(sys.argv)")).isTrue();
    assertThat(FeynmanAntiCheat.looksLikeAnswerDump("from math import sqrt\nprint(sqrt(4))"))
        .isTrue();
  }

  @Test
  void blankIsNotAnswerDump() {
    assertThat(FeynmanAntiCheat.looksLikeAnswerDump(null)).isFalse();
    assertThat(FeynmanAntiCheat.looksLikeAnswerDump("   ")).isFalse();
  }

  @Test
  void producedProgramStructureIsNotMistakenForLeakedAnswer() {
    assertThat(FeynmanAntiCheat.looksLikeLeakedAnswer("def solve():\n    return 1")).isFalse();
    assertThat(FeynmanAntiCheat.looksLikeLeakedAnswer("import sys\nprint(sys.stdin.read())"))
        .isFalse();
    assertThat(FeynmanAntiCheat.looksLikeLeakedAnswer("ANSWER_KEY = 'secret'")).isTrue();
  }

  @Test
  void stripsMarkdownFenceBeforeSendingProducedCodeToSandbox() {
    assertThat(FeynmanAntiCheat.stripCodeFence("```python\nprint(1)\n```"))
        .isEqualTo("print(1)\n");
    assertThat(FeynmanAntiCheat.stripCodeFence("print(1)")).isEqualTo("print(1)");
  }
}
