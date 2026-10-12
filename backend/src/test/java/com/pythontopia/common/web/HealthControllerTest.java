package com.pythontopia.common.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.judge.StaticCodeChecker;
import com.pythontopia.coursepack.service.CourseVideoTranscriptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

  private final PythonSandbox sandbox = mock(PythonSandbox.class);
  private final StaticCodeChecker staticCodeChecker = mock(StaticCodeChecker.class);
  private final CourseVideoTranscriptService videoTranscripts =
      mock(CourseVideoTranscriptService.class);
  private final MockMvc mvc =
      MockMvcBuilders
          .standaloneSetup(new HealthController(sandbox, staticCodeChecker, videoTranscripts))
          .build();

  @BeforeEach
  void courseDataReadyByDefault() {
    when(staticCodeChecker.readiness())
        .thenReturn(new PythonSandbox.Readiness(true, "AST whitelist available"));
    when(videoTranscripts.readiness())
        .thenReturn(new CourseVideoTranscriptService.Readiness(true, "34/34 transcripts"));
  }

  @Test
  void livenessDoesNotDependOnRemoteJudge() throws Exception {
    when(sandbox.readiness()).thenReturn(new PythonSandbox.Readiness(false, "offline"));

    mvc.perform(get("/healthz"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void readinessIsUnavailableWhenJudgeIsNotReady() throws Exception {
    when(sandbox.readiness()).thenReturn(new PythonSandbox.Readiness(false, "piston unavailable"));

    mvc.perform(get("/readyz"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("not_ready"))
        .andExpect(jsonPath("$.judge").value("piston unavailable"));
  }

  @Test
  void readinessIsUnavailableWhenStaticCheckerIsNotReady() throws Exception {
    when(sandbox.readiness()).thenReturn(new PythonSandbox.Readiness(true, "piston python 3.11.9"));
    when(staticCodeChecker.readiness())
        .thenReturn(new PythonSandbox.Readiness(false, "AST whitelist missing"));

    mvc.perform(get("/readyz"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("not_ready"))
        .andExpect(jsonPath("$.python_static_checker").value("AST whitelist missing"));
  }

  @Test
  void readinessReportsConfiguredJudge() throws Exception {
    when(sandbox.readiness()).thenReturn(new PythonSandbox.Readiness(true, "piston python 3.11.9"));

    mvc.perform(get("/readyz"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ready"))
        .andExpect(jsonPath("$.judge").value("piston python 3.11.9"))
        .andExpect(jsonPath("$.python_static_checker").value("AST whitelist available"));
  }

  @Test
  void readinessIsUnavailableWhenCourseVideoTranscriptsAreMissing() throws Exception {
    when(sandbox.readiness()).thenReturn(new PythonSandbox.Readiness(true, "piston python 3.11.9"));
    when(videoTranscripts.readiness())
        .thenReturn(new CourseVideoTranscriptService.Readiness(false, "33/34 transcripts"));

    mvc.perform(get("/readyz"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("not_ready"))
        .andExpect(jsonPath("$.course_video_transcripts").value("33/34 transcripts"));
  }
}
