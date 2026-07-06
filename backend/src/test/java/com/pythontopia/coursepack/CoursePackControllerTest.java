package com.pythontopia.coursepack;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.coursepack.controller.CoursePackController;
import com.pythontopia.coursepack.domain.CoursePack;
import com.pythontopia.coursepack.domain.CourseProblem;
import com.pythontopia.coursepack.service.CoursePackNotFoundException;
import com.pythontopia.coursepack.service.CoursePackService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CoursePackController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CoursePackControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean CoursePackService service;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

  @Test
  void getPackReturnsActivePythonPack() throws Exception {
    when(service.getActivePack())
        .thenReturn(
            new CoursePack(
                43L, "python-language-basic", 1, "Python语言基础", "Python", "desc", "published", 7,
                561, 7, 65, 46, 39));

    mockMvc
        .perform(get("/api/course/pack"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.packId").value(43))
        .andExpect(jsonPath("$.name").value("Python语言基础"))
        .andExpect(jsonPath("$.kcCount").value(65));
  }

  @Test
  void getPackReturns404WhenSeedMissing() throws Exception {
    when(service.getActivePack()).thenThrow(new CoursePackNotFoundException(43L));

    mockMvc
        .perform(get("/api/course/pack"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COURSE_PACK_NOT_FOUND"));
  }

  @Test
  void listProblemsSupportsKcFilterForAndroidWorkshop() throws Exception {
    when(service.listProblems("PY43-KC-001"))
        .thenReturn(
            List.of(
                new CourseProblem(
                    11L,
                    43L,
                    "PY43-KC-001",
                    "输出 Hello",
                    "请输出 Hello",
                    "无",
                    "Hello",
                    "EASY",
                    "seed")));

    mockMvc
        .perform(get("/api/course/problems").param("kcCode", "PY43-KC-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].problemId").value(11))
        .andExpect(jsonPath("$[0].kcCode").value("PY43-KC-001"))
        .andExpect(jsonPath("$[0].title").value("输出 Hello"));
  }

  @Test
  void listProblemsRejectsInvalidKcCode() throws Exception {
    mockMvc
        .perform(get("/api/course/problems").param("kcCode", "KC-1"))
        .andExpect(status().isBadRequest());
  }
}
