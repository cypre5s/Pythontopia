package com.pythontopia.common.web;

import com.pythontopia.coursepack.service.CourseVideoTranscriptService;
import com.pythontopia.submission.judge.PythonSandbox;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final PythonSandbox sandbox;
  private final CourseVideoTranscriptService videoTranscripts;

  public HealthController(PythonSandbox sandbox, CourseVideoTranscriptService videoTranscripts) {
    this.sandbox = sandbox;
    this.videoTranscripts = videoTranscripts;
  }

  @GetMapping("/healthz")
  Map<String, String> healthz() {
    return Map.of("status", "ok");
  }

  @GetMapping("/readyz")
  ResponseEntity<Map<String, String>> readyz() {
    PythonSandbox.Readiness judge = sandbox.readiness();
    CourseVideoTranscriptService.Readiness courseVideoTranscripts = videoTranscripts.readiness();
    boolean ready = judge.ready() && courseVideoTranscripts.ready();
    HttpStatus status = ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "status",
                ready ? "ready" : "not_ready",
                "judge",
                judge.detail(),
                "course_video_transcripts",
                courseVideoTranscripts.detail()));
  }
}
