package com.pythontopia.common.web;

import com.pythontopia.coursepack.service.CourseVideoTranscriptService;
import com.pythontopia.submission.judge.StaticCodeChecker;
import com.pythontopia.submission.judge.PythonSandbox;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final PythonSandbox sandbox;
  private final StaticCodeChecker staticCodeChecker;
  private final CourseVideoTranscriptService videoTranscripts;

  public HealthController(
      PythonSandbox sandbox,
      StaticCodeChecker staticCodeChecker,
      CourseVideoTranscriptService videoTranscripts) {
    this.sandbox = sandbox;
    this.staticCodeChecker = staticCodeChecker;
    this.videoTranscripts = videoTranscripts;
  }

  @GetMapping("/healthz")
  Map<String, String> healthz() {
    return Map.of("status", "ok");
  }

  @GetMapping("/readyz")
  ResponseEntity<Map<String, String>> readyz() {
    PythonSandbox.Readiness judge = sandbox.readiness();
    PythonSandbox.Readiness staticCheck = staticCodeChecker.readiness();
    CourseVideoTranscriptService.Readiness courseVideoTranscripts = videoTranscripts.readiness();
    boolean ready = judge.ready() && staticCheck.ready() && courseVideoTranscripts.ready();
    HttpStatus status = ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "status",
                ready ? "ready" : "not_ready",
                "judge",
                judge.detail(),
                "python_static_checker",
                staticCheck.detail(),
                "course_video_transcripts",
                courseVideoTranscripts.detail()));
  }
}
