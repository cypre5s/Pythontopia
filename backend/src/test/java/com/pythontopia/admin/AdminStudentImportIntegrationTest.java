package com.pythontopia.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtService;
import com.pythontopia.auth.security.Role;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminStudentImportIntegrationTest {

  private static final String ADMIN_USERNAME = "student-import-admin";
  private static final String ADMIN_SECRET = "student-import-admin-test-secret-long-enough";

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
    registry.add("pythontopia.security.jwt.secret", () -> ADMIN_SECRET);
    registry.add("pythontopia.security.teacherInviteCode", () -> "student-import-test-invite");
    registry.add("pythontopia.services.graphUrl", () -> "http://graph.test");
    registry.add("pythontopia.judge.python", () -> "python3");
    registry.add("pythontopia.auth.registration-enabled", () -> "false");
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired JwtService jwtService;
  @Autowired PasswordEncoder passwordEncoder;

  private String adminToken;
  private String studentToken;

  @BeforeEach
  void seedAccounts() {
    jdbcTemplate.update(
        "INSERT INTO users (username, display_name, password_hash, email, role)"
            + " VALUES (?, ?, ?, NULL, 'ADMIN') ON CONFLICT (username) DO NOTHING",
        ADMIN_USERNAME,
        "名单管理员",
        passwordEncoder.encode("not-used-password"));
    long adminId = userId(ADMIN_USERNAME);
    adminToken = jwtService.issue(new CurrentUserPrincipal(adminId, ADMIN_USERNAME, Role.ADMIN)).token();

    String studentUsername = "student-import-member";
    jdbcTemplate.update(
        "INSERT INTO users (username, display_name, password_hash, email, role)"
            + " VALUES (?, ?, ?, NULL, 'STUDENT') ON CONFLICT (username) DO NOTHING",
        studentUsername,
        "名单学生",
        passwordEncoder.encode("not-used-password"));
    studentToken =
        jwtService
            .issue(
                new CurrentUserPrincipal(
                    userId(studentUsername), studentUsername, Role.STUDENT))
            .token();
  }

  @Test
  void importsStudentsWithReversedIdPasswordsAndSkipsExistingAccounts() throws Exception {
    jdbcTemplate.update(
        "INSERT INTO users (username, display_name, password_hash, email, role)"
            + " VALUES (?, ?, ?, ?, 'STUDENT')",
        "20260009",
        "旧姓名",
        passwordEncoder.encode("original-password"),
        "old@example.test");
    String existingHash = passwordHash("20260009");

    ResponseEntity<Map> response = importRoster(adminToken, roster(
        new String[][] {
          {"学号", "姓名"},
          {"20260001", "张小明"},
          {"20260009", "新姓名"},
          {"20260002", "李小华"}
        }));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("createdCount")).isEqualTo(2);
    assertThat(response.getBody().get("skippedCount")).isEqualTo(1);
    List<Map<String, Object>> credentials = (List<Map<String, Object>>) response.getBody().get("credentials");
    assertThat(credentials)
        .containsExactly(
            Map.of("username", "20260001", "displayName", "张小明", "password", "10006202"),
            Map.of("username", "20260002", "displayName", "李小华", "password", "20006202"));
    assertThat(passwordEncoder.matches("10006202", passwordHash("20260001"))).isTrue();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT display_name FROM users WHERE username = '20260001'", String.class))
        .isEqualTo("张小明");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT email FROM users WHERE username = '20260001'", String.class))
        .isNull();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT role FROM users WHERE username = '20260001'", String.class))
        .isEqualTo("STUDENT");
    assertThat(passwordHash("20260009")).isEqualTo(existingHash);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT display_name FROM users WHERE username = '20260009'", String.class))
        .isEqualTo("旧姓名");

    ResponseEntity<Map> login =
        restTemplate.postForEntity(
            "/api/auth/login",
            Map.of("username", "20260001", "password", "10006202"),
            Map.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void identifiesExactColumnsAmongOtherColumnsRegardlessOfOrder() throws Exception {
    ResponseEntity<Map> response = importRoster(adminToken, roster(
        new String[][] {
          {"年级", "学号备注", "姓名拼音", "姓名", "班级", "学生学号", "备注"},
          {"2026", "勿用此列", "Zhang Xiaoming", "张小明", "一班", "20260012", "备注值"}
        }));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("createdCount")).isEqualTo(1);
    List<Map<String, Object>> credentials = (List<Map<String, Object>>) response.getBody().get("credentials");
    assertThat(credentials)
        .containsExactly(
            Map.of("username", "20260012", "displayName", "张小明", "password", "21006202"));
  }

  @Test
  void rejectsAmbiguousColumnsInsteadOfChoosingOneSilently() throws Exception {
    ResponseEntity<Map> response = importRoster(adminToken, roster(
        new String[][] {
          {"学号", "姓名", "学生学号"},
          {"20260013", "张小明", "20260014"}
        }));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT count(*) FROM users WHERE username IN ('20260013', '20260014')", Integer.class))
        .isZero();
  }

  @Test
  void rejectsDuplicateOrInvalidRowsBeforeWritingAnyRows() throws Exception {
    ResponseEntity<Map> response = importRoster(adminToken, roster(
        new String[][] {
          {"学号", "姓名"},
          {"20260003", "张小明"},
          {"20260003", "另一个名字"}
        }));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT count(*) FROM users WHERE username = '20260003'", Integer.class))
        .isZero();

    response = importRoster(adminToken, roster(
        new String[][] {
          {"学号", "姓名"},
          {"20260005", "张小明"},
          {"bad!", "无效学号"}
        }));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT count(*) FROM users WHERE username = '20260005'", Integer.class))
        .isZero();
  }

  @Test
  void requiresAnAdminAndLeavesLoginAvailable() throws Exception {
    byte[] workbook = roster(new String[][] {{"学号", "姓名"}, {"20260004", "王小明"}});

    assertThat(importRoster(null, workbook).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(importRoster(studentToken, workbook).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(restTemplate.postForEntity(
        "/api/auth/register",
        Map.of(
            "username", "public-register-disabled",
            "password", "P@ssw0rd1",
            "displayName", "不能注册",
            "role", "STUDENT"),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(restTemplate.postForEntity(
        "/api/auth/login",
        Map.of("username", "student-import-member", "password", "not-used-password"),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private ResponseEntity<Map> importRoster(String token, byte[] workbook) {
    HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(
        MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    ByteArrayResource file = new ByteArrayResource(workbook) {
      @Override
      public String getFilename() {
        return "students.xlsx";
      }
    };
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", new HttpEntity<>(file, fileHeaders));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    if (token != null) headers.setBearerAuth(token);
    return restTemplate.exchange(
        "/api/admin/student-imports",
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        Map.class);
  }

  private static byte[] roster(String[][] values) throws Exception {
    try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("学生名单");
      for (int rowIndex = 0; rowIndex < values.length; rowIndex++) {
        var row = sheet.createRow(rowIndex);
        for (int columnIndex = 0; columnIndex < values[rowIndex].length; columnIndex++) {
          row.createCell(columnIndex).setCellValue(values[rowIndex][columnIndex]);
        }
      }
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private long userId(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username = ?", Long.class, username);
  }

  private String passwordHash(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT password_hash FROM users WHERE username = ?", String.class, username);
  }
}
