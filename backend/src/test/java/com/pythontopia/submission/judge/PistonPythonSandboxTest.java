package com.pythontopia.submission.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PistonPythonSandboxTest {

  private RestTemplate restTemplate;
  private MockRestServiceServer server;
  private PistonPythonSandbox sandbox;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    sandbox =
        new PistonPythonSandbox(restTemplate, "https://judge.internal", "3.11.9", "release-token");
  }

  @Test
  void executesThroughPistonWithResourceLimitsAndBearerToken() {
    server
        .expect(requestTo("https://judge.internal/api/v2/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer release-token"))
        .andExpect(jsonPath("$.language").value("python"))
        .andExpect(jsonPath("$.version").value("3.11.9"))
        .andExpect(jsonPath("$.files[0].name").value("submission.py"))
        .andExpect(jsonPath("$.files[0].content").value("print(input())"))
        .andExpect(jsonPath("$.stdin").value("hello\n"))
        .andExpect(jsonPath("$.run_timeout").value(1500))
        .andExpect(jsonPath("$.run_cpu_time").value(1500))
        .andExpect(jsonPath("$.run_memory_limit").value(268435456))
        .andRespond(
            withSuccess(
                """
                {"language":"python","version":"3.11.9","run":{"stdout":"hello\\n","stderr":"","code":0,"signal":null,"message":null,"status":null,"wall_time":18}}
                """,
                MediaType.APPLICATION_JSON));

    SandboxRunResult result =
        sandbox.run("print(input())", new JudgeTestCase("hello\n", "hello\n", 1500));

    assertThat(result.timedOut()).isFalse();
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).isEqualTo("hello\n");
    assertThat(result.stderr()).isEmpty();
    assertThat(result.durationMs()).isEqualTo(18);
    assertThat(result.errorKind()).isNull();
    server.verify();
  }

  @Test
  void mapsPistonTimeoutWithoutFallingBackLocally() {
    server
        .expect(requestTo("https://judge.internal/api/v2/execute"))
        .andRespond(
            withSuccess(
                """
                {"run":{"stdout":"","stderr":"","code":null,"signal":"SIGKILL","message":"Time limit exceeded","status":"TO","wall_time":1000}}
                """,
                MediaType.APPLICATION_JSON));

    SandboxRunResult result = sandbox.run("while True: pass", new JudgeTestCase("", "", 1000));

    assertThat(result.timedOut()).isTrue();
    assertThat(result.errorKind()).isEqualTo("TLE");
    assertThat(result.stderr()).isEqualTo("Time limit exceeded");
    server.verify();
  }

  @Test
  void mapsLegacyPistonSigkillWithoutStatusToSignalExitCode() {
    server
        .expect(requestTo("https://judge.internal/api/v2/execute"))
        .andRespond(
            withSuccess(
                """
                {"run":{"stdout":"","stderr":"","code":null,"signal":"SIGKILL","output":""}}
                """,
                MediaType.APPLICATION_JSON));

    SandboxRunResult result = sandbox.run("while True: pass", new JudgeTestCase("", "", 1000));

    assertThat(result.timedOut()).isFalse();
    assertThat(result.exitCode()).isEqualTo(137);
    assertThat(result.errorKind()).isNull();
    server.verify();
  }

  @Test
  void transportFailureFailsClosedAsSystemError() {
    server
        .expect(requestTo("https://judge.internal/api/v2/execute"))
        .andRespond(withException(new IOException("connection refused")));

    SandboxRunResult result = sandbox.run("print(1)", new JudgeTestCase("", "1", 1000));

    assertThat(result.errorKind()).isEqualTo("SYSTEM_ERROR");
    assertThat(result.stderr()).isEqualTo("Sandbox unavailable");
    assertThat(result.stdout()).isEmpty();
    server.verify();
  }

  @Test
  void readinessRequiresInstalledPythonRuntime() {
    server
        .expect(requestTo("https://judge.internal/api/v2/runtimes"))
        .andExpect(header("Authorization", "Bearer release-token"))
        .andRespond(
            withSuccess(
                """
                [{"language":"python","version":"3.11.9","aliases":["py","python3"]}]
                """,
                MediaType.APPLICATION_JSON));

    PythonSandbox.Readiness readiness = sandbox.readiness();

    assertThat(readiness.ready()).isTrue();
    assertThat(readiness.detail()).contains("python 3.11.9");
    server.verify();
  }

  @Test
  void missingPistonConfigurationDisablesExecutionWithoutBreakingStartup() {
    assertThatCode(
            () -> {
              PistonPythonSandbox unconfigured =
                  new PistonPythonSandbox(restTemplate, "", "3.11.9", "");
              assertThat(unconfigured.readiness().ready()).isFalse();
              assertThat(
                      unconfigured
                          .run("print('unsafe')", new JudgeTestCase("", "", 1000))
                          .errorKind())
                  .isEqualTo("SYSTEM_ERROR");
            })
        .doesNotThrowAnyException();
    server.verify();
  }

  @Test
  void unknownPistonStatusFailsClosed() {
    server
        .expect(requestTo("https://judge.internal/api/v2/execute"))
        .andRespond(
            withSuccess(
                """
                {"run":{"stdout":"expected","stderr":"","code":0,"signal":null,"status":"AC","wall_time":1}}
                """,
                MediaType.APPLICATION_JSON));

    SandboxRunResult result =
        sandbox.run("print('expected')", new JudgeTestCase("", "expected", 1000));

    assertThat(result.errorKind()).isEqualTo("SYSTEM_ERROR");
    assertThat(result.exitCode()).isNotZero();
    server.verify();
  }

  @Test
  void outputLimitStatusCannotBeAcceptedAsSuccessfulExecution() {
    server
        .expect(requestTo("https://judge.internal/api/v2/execute"))
        .andRespond(
            withSuccess(
                """
                {"run":{"stdout":"expected","stderr":"","code":0,"signal":null,"status":"OL","wall_time":1}}
                """,
                MediaType.APPLICATION_JSON));

    SandboxRunResult result =
        sandbox.run("print('expected')", new JudgeTestCase("", "expected", 1000));

    assertThat(result.errorKind()).isNull();
    assertThat(result.exitCode()).isNotZero();
    server.verify();
  }
}
