package com.pythontopia.bugmine.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.judge.SandboxRunResult;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CounterExampleSelectorTest {

  private static final long USER_ID = 73L;
  private static final long PROBLEM_ID = 1_000_000_001L;

  @Mock private PublicProblemRepository problemRepository;
  @Mock private PythonSandbox sandbox;
  @Mock private JudgeWorkerPool judgeWorkerPool;

  @Test
  void usesOwnerScopedCasesAndDoesNotReturnHiddenCaseData() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    var cases =
        new PublicProblemRepository.JudgeTestCases(
            objectMapper.readTree("[]"),
            objectMapper.readTree(
                "[{\"input\":\"PRIVATE_INPUT_123\",\"output\":\"PRIVATE_EXPECTED_456\"}]"));
    lenient()
        .when(problemRepository.findTestCases(PROBLEM_ID, USER_ID))
        .thenReturn(Optional.of(cases));
    when(judgeWorkerPool.submitAndWait(any()))
        .thenAnswer(
            invocation -> {
              Callable<?> task = invocation.getArgument(0);
              return task.call();
            });
    when(sandbox.run(eq("student code"), any()))
        .thenReturn(new SandboxRunResult(false, 0, "student output", "", 1, null));

    CounterExampleSelector selector =
        new CounterExampleSelector(problemRepository, sandbox, judgeWorkerPool);

    var result = selector.findFirstFailing(PROBLEM_ID, USER_ID, "student code");

    assertThat(result).isPresent();
    assertThat(result.orElseThrow())
        .satisfies(
            counterExample -> {
              assertThat(counterExample.stdin()).isEqualTo("隐藏测试用例（输入已隐藏）");
              assertThat(counterExample.expected()).isEqualTo("期望输出已隐藏");
              assertThat(counterExample.actual()).isEqualTo("实际输出已隐藏");
              assertThat(counterExample.redacted()).isTrue();
            });
    verify(problemRepository).findTestCases(PROBLEM_ID, USER_ID);
    verify(problemRepository, never()).findTestCases(PROBLEM_ID);
    verify(sandbox)
        .run(
            eq("student code"),
            argThat(
                testCase ->
                    testCase.hidden()
                        && testCase.input().equals("PRIVATE_INPUT_123")
                        && testCase.expectedOutput().equals("PRIVATE_EXPECTED_456")));
  }
}
