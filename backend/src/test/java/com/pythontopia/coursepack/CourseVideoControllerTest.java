package com.pythontopia.coursepack;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.coursepack.controller.CourseVideoController;
import com.pythontopia.coursepack.service.CourseVideoService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Drives {@link CourseVideoController} against a real {@link CourseVideoService} pointed at a
 * temp manifest + a tiny fake mp4, covering chapter filtering, the available flag, 404s, and HTTP
 * Range (206 / Content-Range / 416).
 */
@WebMvcTest(CourseVideoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CourseVideoService.class, GlobalExceptionHandler.class})
@TestPropertySource(
    properties = {
      "pythontopia.coursepack.videoDir=${java.io.tmpdir}/pyt-test-videos",
      "pythontopia.coursepack.videoManifest=${java.io.tmpdir}/pyt-test-videos/video_manifest.json"
    })
class CourseVideoControllerTest {

  /** 16 deterministic bytes (0..15) so a Range slice has predictable content. */
  private static final byte[] FAKE_MP4 = new byte[16];

  static {
    for (int i = 0; i < FAKE_MP4.length; i++) {
      FAKE_MP4[i] = (byte) i;
    }
  }

  private static final String MANIFEST_JSON =
      """
      [
        {"id":1,"chapter":3,"title":"第三章 变量与表达式","objectid":"obj-1",
         "file":"ch3_001.mp4","durationSec":123,"kcCodes":["PY43-KC-017"]},
        {"id":2,"chapter":3,"title":"第三章 输入输出","objectid":"obj-2",
         "file":"ch3_002_missing.mp4","durationSec":null,"kcCodes":[]},
        {"id":3,"chapter":5,"title":"第五章 循环","objectid":"obj-3",
         "file":"ch5_001_missing.mp4","durationSec":300,"kcCodes":["PY43-KC-030"]}
      ]
      """;

  @Autowired MockMvc mockMvc;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

  private static Path dir() {
    return Path.of(System.getProperty("java.io.tmpdir"), "pyt-test-videos");
  }

  @BeforeEach
  void seed() throws IOException {
    Path dir = dir();
    Files.createDirectories(dir);
    Files.write(dir.resolve("ch3_001.mp4"), FAKE_MP4);
    Files.writeString(dir.resolve("video_manifest.json"), MANIFEST_JSON, StandardCharsets.UTF_8);
  }

  @AfterEach
  void cleanup() throws IOException {
    Path dir = dir();
    Files.deleteIfExists(dir.resolve("ch3_001.mp4"));
    Files.deleteIfExists(dir.resolve("video_manifest.json"));
    Files.deleteIfExists(dir);
  }

  @Test
  void listsEveryEntryWhenNoChapterFilter() throws Exception {
    mockMvc
        .perform(get("/api/course/videos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].chapter").value(3))
        .andExpect(jsonPath("$[0].title").value("第三章 变量与表达式"))
        .andExpect(jsonPath("$[0].durationSec").value(123))
        .andExpect(jsonPath("$[0].kcCodes[0]").value("PY43-KC-017"))
        .andExpect(jsonPath("$[0].available").value(true));
  }

  @Test
  void filtersByChapterAndFlagsAvailability() throws Exception {
    mockMvc
        .perform(get("/api/course/videos").param("chapterId", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].available").value(true))
        .andExpect(jsonPath("$[1].id").value(2))
        .andExpect(jsonPath("$[1].available").value(false))
        .andExpect(jsonPath("$[1].durationSec").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void filterMatchingNoVideosReturnsEmptyArray() throws Exception {
    mockMvc
        .perform(get("/api/course/videos").param("chapterId", "9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void unknownIdReturns404() throws Exception {
    mockMvc.perform(get("/api/course/videos/999")).andExpect(status().isNotFound());
  }

  @Test
  void manifestEntryWithMissingFileReturns404() throws Exception {
    mockMvc.perform(get("/api/course/videos/2")).andExpect(status().isNotFound());
  }

  @Test
  void fullRequestReturns200WithAcceptRanges() throws Exception {
    mockMvc
        .perform(get("/api/course/videos/1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("video/mp4"))
        .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
        .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 16));
  }

  @Test
  void rangeRequestReturns206WithContentRange() throws Exception {
    mockMvc
        .perform(get("/api/course/videos/1").header(HttpHeaders.RANGE, "bytes=0-3"))
        .andExpect(status().isPartialContent())
        .andExpect(content().contentType("video/mp4"))
        .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
        .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-3/16"))
        .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 4))
        .andExpect(content().bytes(new byte[] {0, 1, 2, 3}));
  }

  @Test
  void openEndedRangeReturns206ToEnd() throws Exception {
    mockMvc
        .perform(get("/api/course/videos/1").header(HttpHeaders.RANGE, "bytes=8-"))
        .andExpect(status().isPartialContent())
        .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 8-15/16"))
        .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 8));
  }

  @Test
  void unsatisfiableRangeReturns416() throws Exception {
    mockMvc
        .perform(get("/api/course/videos/1").header(HttpHeaders.RANGE, "bytes=100-200"))
        .andExpect(status().isRequestedRangeNotSatisfiable())
        .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */16"));
  }
}
