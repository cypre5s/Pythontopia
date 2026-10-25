package com.pythontopia.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.repository.UserRepository.StudentImport;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminStudentImportServiceTest {

  @Mock UserRepository userRepository;
  @Mock PasswordEncoder passwordEncoder;
  @InjectMocks AdminStudentImportService service;

  @Test
  void checksExistingUsernamesOnceAndInsertsOnlyNewStudentsAsABatch() throws Exception {
    List<String> usernames = List.of("20260001", "20260002", "20260003");
    when(userRepository.findExistingUsernames(usernames)).thenReturn(Set.of("20260002"));
    when(passwordEncoder.encode("10006202")).thenReturn("hash-01");
    when(passwordEncoder.encode("30006202")).thenReturn("hash-03");

    AdminStudentImportResultDto result =
        service.importRoster(
            new CurrentUserPrincipal(1L, "admin", Role.ADMIN),
            new MockMultipartFile("file", "roster.xlsx", "application/octet-stream", roster()));

    assertThat(result.createdCount()).isEqualTo(2);
    assertThat(result.skippedCount()).isEqualTo(1);
    assertThat(result.credentials())
        .containsExactly(
            new AdminStudentCredentialDto("20260001", "张小明", "10006202"),
            new AdminStudentCredentialDto("20260003", "王小明", "30006202"));

    ArgumentCaptor<List<StudentImport>> batch = ArgumentCaptor.forClass(List.class);
    verify(userRepository).findExistingUsernames(usernames);
    verify(userRepository).insertStudents(batch.capture());
    assertThat(batch.getValue())
        .containsExactly(
            new StudentImport("20260001", "张小明", "hash-01"),
            new StudentImport("20260003", "王小明", "hash-03"));
  }

  @Test
  void skipsDatabaseInsertWhenEveryStudentAlreadyExists() throws Exception {
    List<String> usernames = List.of("20260002");
    when(userRepository.findExistingUsernames(usernames)).thenReturn(Set.copyOf(usernames));

    AdminStudentImportResultDto result =
        service.importRoster(
            new CurrentUserPrincipal(1L, "admin", Role.ADMIN),
            new MockMultipartFile("file", "roster.xlsx", "application/octet-stream", roster("20260002", "张小明")));

    assertThat(result.createdCount()).isZero();
    assertThat(result.skippedCount()).isEqualTo(1);
    verify(userRepository).findExistingUsernames(usernames);
    verifyNoInteractions(passwordEncoder);
  }

  private static byte[] roster() throws Exception {
    return roster("20260001", "张小明", "20260002", "李小华", "20260003", "王小明");
  }

  private static byte[] roster(String... values) throws Exception {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("学生名单");
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("学号");
      header.createCell(1).setCellValue("姓名");
      for (int index = 0; index < values.length; index += 2) {
        var row = sheet.createRow(index / 2 + 1);
        row.createCell(0).setCellValue(values[index]);
        row.createCell(1).setCellValue(values[index + 1]);
      }
      workbook.write(output);
      return output.toByteArray();
    }
  }
}
