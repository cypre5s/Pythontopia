package com.pythontopia.stage12;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.PythontopiaApplication;
import com.pythontopia.classroom.quest.repository.ClassroomQuestRepository;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.learner.quest.repository.QuestRepository.QuestInsert;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Stage12TeacherDashboardAcceptanceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add(
        "pythontopia.security.jwt.secret",
        () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
    registry.add("pythontopia.services.graphUrl", () -> "http://graph.test");
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper objectMapper;
  @Autowired ClassroomQuestRepository classroomQuestRepository;

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void publishClassroomQuestAndAggregateDashboard() {
    String teacherToken = registerTeacher("stage12-t01");
    String stuA = registerStudent("stage12-stu-a");
    String stuB = registerStudent("stage12-stu-b");
    String stuC = registerStudent("stage12-stu-c");

    Long teacherId = userId("stage12-t01");
    Long aId = userId("stage12-stu-a");
    Long bId = userId("stage12-stu-b");
    Long cId = userId("stage12-stu-c");

    Map<String, Object> classroomBody =
        teacherCreateClassroom(teacherToken, "Stage12 演示班级");
    String joinCode = (String) classroomBody.get("joinCode");
    Long classroomId = ((Number) classroomBody.get("classroomId")).longValue();
    studentJoin(stuA, joinCode);
    studentJoin(stuB, joinCode);
    studentJoin(stuC, joinCode);

    Long problemId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM course_problem WHERE pack_id = 43 ORDER BY id LIMIT 1",
            Long.class);
    String firstKc =
        jdbcTemplate.queryForObject(
            "SELECT kc_code FROM course_problem WHERE id = ?", String.class, problemId);

    Map<String, Object> publishRequest =
        Map.of(
            "title", "完成 for 循环练习",
            "description", "对应 KC for-loop",
            "kcCodes", List.of(firstKc),
            "targetProblemId", problemId,
            "dueDate", LocalDate.now(ZoneOffset.UTC).plusDays(2).toString());
    ResponseEntity<Map> publishResponse =
        restTemplate.exchange(
            "/api/teacher/classrooms/" + classroomId + "/quests",
            HttpMethod.POST,
            bearer(teacherToken, publishRequest),
            Map.class);
    assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Number created = (Number) publishResponse.getBody().get("createdQuests");
    Number total = (Number) publishResponse.getBody().get("totalStudents");
    assertThat(total.intValue()).isEqualTo(3);
    assertThat(created.intValue()).isEqualTo(3);

    Integer questCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quest WHERE source = 'CLASSROOM'", Integer.class);
    assertThat(questCount).isEqualTo(3);

    Long classroomQuestId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM classroom_quest WHERE classroom_id = ? ORDER BY id DESC LIMIT 1",
            Long.class,
            classroomId);
    registerStudent("stage12-stu-extra");
    Long extraStudentId = userId("stage12-stu-extra");
    List<QuestInsert> retryCopies =
        List.of(
            new QuestInsert(
                aId,
                QuestSource.CLASSROOM,
                classroomQuestId,
                "should keep existing copy",
                "duplicate",
                firstKc,
                QuestNodeType.SOLVE_PROBLEM,
                objectMapper.createObjectNode(),
                LocalDate.now(ZoneOffset.UTC).plusDays(2),
                9,
                "submission",
                null,
                List.of(problemId),
                null,
                "/training",
                objectMapper.createObjectNode().put("problemId", problemId)),
            new QuestInsert(
                extraStudentId,
                QuestSource.CLASSROOM,
                classroomQuestId,
                "班级任务 · 完成 for 循环练习",
                "对应 KC for-loop",
                firstKc,
                QuestNodeType.SOLVE_PROBLEM,
                objectMapper.createObjectNode().put("classroomQuestSource", true),
                LocalDate.now(ZoneOffset.UTC).plusDays(2),
                9,
                "submission",
                null,
                List.of(problemId),
                null,
                "/training",
                objectMapper.createObjectNode().put("problemId", problemId)));
    assertThat(classroomQuestRepository.insertStudentQuestsIfAbsent(retryCopies)).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT title FROM quest WHERE user_id = ? AND source = 'CLASSROOM' AND source_ref_id = ?",
                String.class,
                aId,
                classroomQuestId))
        .isEqualTo("班级任务 · 完成 for 循环练习");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT target_problem_ids[1] FROM quest WHERE user_id = ? AND source = 'CLASSROOM' AND source_ref_id = ?",
                Long.class,
                extraStudentId,
                classroomQuestId))
        .isEqualTo(problemId);

    // 两位学生完成任务，一位保持 OPEN，覆盖率约 0.67。
    completeFirstClassroomQuest(stuA, aId);
    completeFirstClassroomQuest(stuB, bId);

    ResponseEntity<List> summary =
        restTemplate.exchange(
            "/api/teacher/classrooms/" + classroomId + "/dashboard/quest-summary",
            HttpMethod.GET,
            bearer(teacherToken, null),
            List.class);
    assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> first = (Map<String, Object>) summary.getBody().get(0);
    Number completionRate = (Number) first.get("completionRate");
    assertThat(new BigDecimal(completionRate.toString())).isGreaterThan(new BigDecimal("0.5"));

    // KC 热力图：覆盖 65 个 KC，且对应学生数 = 3
    ResponseEntity<List> heatmap =
        restTemplate.exchange(
            "/api/teacher/classrooms/" + classroomId + "/dashboard/kc-heatmap",
            HttpMethod.GET,
            bearer(teacherToken, null),
            List.class);
    assertThat(heatmap.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(heatmap.getBody()).hasSizeGreaterThan(60);

    Integer snapshot =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM classroom_kc_snapshot WHERE classroom_id = ?",
            Integer.class,
            classroomId);
    assertThat(snapshot).isGreaterThan(60);

    // 学生跨班级 / 跨教师禁止访问看板。
    ResponseEntity<Map> denied =
        restTemplate.exchange(
            "/api/teacher/classrooms/" + classroomId + "/dashboard/kc-heatmap",
            HttpMethod.GET,
            bearer(stuA, null),
            Map.class);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 学生公告板可以看到班级任务
    ResponseEntity<List> studentBoard =
        restTemplate.exchange(
            "/api/classroom/quests/mine",
            HttpMethod.GET,
            bearer(stuC, null),
            List.class);
    assertThat(studentBoard.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(studentBoard.getBody()).hasSize(1);

    // 重置 joinCode
    ResponseEntity<Map> rotate =
        restTemplate.exchange(
            "/api/teacher/classrooms/" + classroomId + "/regenerate-code",
            HttpMethod.POST,
            bearer(teacherToken, null),
            Map.class);
    assertThat(rotate.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(rotate.getBody().get("joinCode")).isNotEqualTo(joinCode);

    // 教师归档任务，但学生已生成 quest 不应被破坏（OPEN/COMPLETED 都不会丢失）。
    classroomQuestId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM classroom_quest WHERE classroom_id = ? ORDER BY id ASC LIMIT 1",
            Long.class,
            classroomId);
    ResponseEntity<Map> archived =
        restTemplate.exchange(
            "/api/teacher/classrooms/" + classroomId + "/quests/" + classroomQuestId + "/archive",
            HttpMethod.POST,
            bearer(teacherToken, null),
            Map.class);
    assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(archived.getBody().get("status")).isEqualTo("ARCHIVED");
    Integer remainingQuests =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quest WHERE source = 'CLASSROOM'", Integer.class);
    assertThat(remainingQuests).isEqualTo(4);
  }

  @SuppressWarnings("unchecked")
  private void completeFirstClassroomQuest(String studentToken, Long userId) {
    Long questId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM quest WHERE user_id = ? AND source = 'CLASSROOM' ORDER BY id LIMIT 1",
            Long.class,
            userId);
    ResponseEntity<Map> started =
        restTemplate.exchange(
        "/api/quests/" + questId + "/start", HttpMethod.POST, bearer(studentToken, null), Map.class);
    assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);

    Long problemId =
        jdbcTemplate.queryForObject(
            "SELECT (target_problem_ids[1]) FROM quest WHERE id = ?",
            Long.class,
            questId);
    Long submissionId = createPassedSubmission(userId, problemId, questId);
    ResponseEntity<Map> completed =
        restTemplate.exchange(
        "/api/quests/" + questId + "/complete",
        HttpMethod.POST,
        bearer(studentToken, Map.of("note", "done", "submissionId", submissionId)),
        Map.class);
    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(completed.getBody().get("status")).isEqualTo("COMPLETED");
  }

  private Long createPassedSubmission(Long userId, Long problemId, Long questId) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO practice_submission (
          user_id, problem_id, quest_id, source, language, code, status, runtime_ms, memory_kb
        ) VALUES (?, ?, ?, 'CLASSROOM', 'PYTHON_3_11', 'print("ok")', 'PASSED', 12, 1024)
        RETURNING id
        """,
        Long.class,
        userId,
        problemId,
        questId);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> teacherCreateClassroom(String token, String name) {
    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/classrooms",
            HttpMethod.POST,
            bearer(token, Map.of("name", name, "description", "stage12 测试班级")),
            Map.class);
    return response.getBody();
  }

  private void studentJoin(String studentToken, String joinCode) {
    restTemplate.exchange(
        "/api/classrooms/join",
        HttpMethod.POST,
        bearer(studentToken, Map.of("joinCode", joinCode)),
        Map.class);
  }

  @SuppressWarnings("unchecked")
  private String registerTeacher(String username) {
    restTemplate.postForEntity(
        "/api/auth/register",
        Map.of(
            "username", username,
            "password", "P@ssw0rd1",
            "displayName", username,
            "role", "TEACHER",
            "teacherInviteCode", "test-teacher-code"),
        Map.class);
    ResponseEntity<Map> response =
        restTemplate.postForEntity(
            "/api/auth/login",
            Map.of("username", username, "password", "P@ssw0rd1"),
            Map.class);
    return (String) response.getBody().get("token");
  }

  @SuppressWarnings("unchecked")
  private String registerStudent(String username) {
    restTemplate.postForEntity(
        "/api/auth/register",
        Map.of(
            "username", username,
            "password", "P@ssw0rd1",
            "displayName", username,
            "role", "STUDENT"),
        Map.class);
    ResponseEntity<Map> response =
        restTemplate.postForEntity(
            "/api/auth/login",
            Map.of("username", username, "password", "P@ssw0rd1"),
            Map.class);
    return (String) response.getBody().get("token");
  }

  private Long userId(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username = ?", Long.class, username);
  }

  private HttpEntity<?> bearer(String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(body, headers);
  }
}
