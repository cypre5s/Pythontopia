package com.pythontopia.coursepack;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.coursepack.controller.CourseDocumentPdfController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verify {@link CourseDocumentPdfController} streams the right PDF and rejects out-of-range / missing
 * documents without leaking the underlying filesystem path.
 */
@WebMvcTest(CourseDocumentPdfController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "pythontopia.coursepack.pdfDir=${java.io.tmpdir}/pyt-test-pdfs")
class CourseDocumentPdfControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

  @TempDir static Path sharedTempDir;

  @BeforeEach
  void seedPdfDir() throws IOException {
    Path target = Path.of(System.getProperty("java.io.tmpdir"), "pyt-test-pdfs");
    Files.createDirectories(target);
    Files.writeString(target.resolve("186.pdf"), "%PDF-1.4 stub", StandardCharsets.UTF_8);
  }

  @AfterEach
  void cleanupPdfDir() throws IOException {
    Path target = Path.of(System.getProperty("java.io.tmpdir"), "pyt-test-pdfs");
    Files.deleteIfExists(target.resolve("186.pdf"));
    Files.deleteIfExists(target.resolve("187.pdf"));
    Files.deleteIfExists(target);
  }

  @Test
  void streamsExistingPdfWithCorrectContentType() throws Exception {
    mockMvc
        .perform(get("/api/course/documents/186/pdf"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"))
        .andExpect(header().string("Content-Length", "13"))
        .andExpect(header().exists("Content-Disposition"));
  }

  @Test
  void returns404WhenPdfFileMissing() throws Exception {
    mockMvc.perform(get("/api/course/documents/187/pdf")).andExpect(status().isNotFound());
  }

  @Test
  void rejectsDocumentIdBelowRange() throws Exception {
    mockMvc.perform(get("/api/course/documents/100/pdf")).andExpect(status().isNotFound());
  }

  @Test
  void rejectsDocumentIdAboveRange() throws Exception {
    mockMvc.perform(get("/api/course/documents/999/pdf")).andExpect(status().isNotFound());
  }

  @Test
  void rejectsNonPositiveDocumentId() throws Exception {
    mockMvc.perform(get("/api/course/documents/0/pdf")).andExpect(status().is4xxClientError());
  }
}
