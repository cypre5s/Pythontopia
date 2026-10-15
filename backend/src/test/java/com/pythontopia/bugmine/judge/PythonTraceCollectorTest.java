package com.pythontopia.bugmine.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.judge.SandboxRunResult;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PythonTraceCollectorTest {

  private static final String STUDENT_OUTPUT = "student_secret_value";

  @Test
  @SuppressWarnings("unchecked")
  void malformedStudentTraceDoesNotLogStudentOutput() {
    PythonSandbox sandbox = mock(PythonSandbox.class);
    JudgeWorkerPool workerPool = mock(JudgeWorkerPool.class);
    when(sandbox.name()).thenReturn("test-sandbox");
    when(sandbox.run(any(), any()))
        .thenReturn(
            new SandboxRunResult(
                false,
                0,
                "##PYT_TRACE_START##" + STUDENT_OUTPUT + "##PYT_TRACE_END##",
                "",
                5L,
                null));
    when(workerPool.<SandboxRunResult>submitAndWait(any(Callable.class)))
        .thenAnswer(invocation -> ((Callable<SandboxRunResult>) invocation.getArgument(0)).call());
    PythonTraceCollector collector =
        new PythonTraceCollector(sandbox, new ObjectMapper(), workerPool);

    Logger logger = (Logger) LoggerFactory.getLogger(PythonTraceCollector.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThat(collector.collect("print('ok')", "").stepCount()).isZero();

      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain(STUDENT_OUTPUT);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
