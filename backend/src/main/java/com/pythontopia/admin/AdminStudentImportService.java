package com.pythontopia.admin;

import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminStudentImportService {

  private static final int MAX_STUDENT_ROWS = 1_000;
  private static final int MAX_SHEET_ROWS_TO_SCAN = 10_000;
  private static final Set<String> STUDENT_ID_HEADERS =
      Set.of("学号", "学生学号", "student id", "studentid", "student_id");
  private static final Set<String> NAME_HEADERS = Set.of("姓名", "学生姓名", "name");

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public AdminStudentImportService(
      UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public AdminStudentImportResultDto importRoster(CurrentUserPrincipal admin, MultipartFile file) {
    requireAdmin(admin);
    List<RosterStudent> students = parse(file);
    List<AdminStudentCredentialDto> credentials = new ArrayList<>();
    int skippedCount = 0;
    for (RosterStudent student : students) {
      if (userRepository.findByUsername(student.studentId()).isPresent()) {
        skippedCount++;
        continue;
      }
      String password = new StringBuilder(student.studentId()).reverse().toString();
      try {
        userRepository.save(
            student.studentId(),
            student.name(),
            passwordEncoder.encode(password),
            null,
            Role.STUDENT);
      } catch (DuplicateKeyException exception) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "STUDENT_IMPORT_CONFLICT",
            "导入期间账号发生冲突，请刷新名单后重试");
      }
      credentials.add(
          new AdminStudentCredentialDto(student.studentId(), student.name(), password));
    }
    return new AdminStudentImportResultDto(credentials.size(), skippedCount, credentials);
  }

  private List<RosterStudent> parse(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getOriginalFilename() == null
        || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
      throw invalidRoster("请上传有效的 .xlsx 学生名单");
    }
    try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
      if (workbook.getNumberOfSheets() == 0) {
        throw invalidRoster("名单中没有工作表");
      }
      Sheet sheet = workbook.getSheetAt(0);
      if (sheet.getLastRowNum() > MAX_SHEET_ROWS_TO_SCAN) {
        throw invalidRoster("名单行数过多，请删除空白尾行后重试");
      }
      DataFormatter formatter = new DataFormatter(Locale.ROOT);
      HeaderColumns columns = findHeader(sheet, formatter);
      List<RosterStudent> students = new ArrayList<>();
      Set<String> studentIds = new HashSet<>();
      for (int index = columns.rowIndex() + 1; index <= sheet.getLastRowNum(); index++) {
        Row row = sheet.getRow(index);
        if (row == null) continue;
        String studentId = cellValue(row, columns.studentIdColumn(), formatter);
        String name = cellValue(row, columns.nameColumn(), formatter);
        if (studentId.isBlank() && name.isBlank()) continue;
        validateStudent(index + 1, studentId, name);
        if (!studentIds.add(studentId)) {
          throw invalidRoster("第 " + (index + 1) + " 行学号重复");
        }
        if (students.size() == MAX_STUDENT_ROWS) {
          throw invalidRoster("名单最多支持 " + MAX_STUDENT_ROWS + " 条学生记录");
        }
        students.add(new RosterStudent(studentId, name));
      }
      if (students.isEmpty()) throw invalidRoster("名单中没有学生记录");
      return students;
    } catch (BusinessException exception) {
      throw exception;
    } catch (IOException | RuntimeException exception) {
      throw invalidRoster("无法读取该 .xlsx 文件，请检查文件格式");
    }
  }

  private HeaderColumns findHeader(Sheet sheet, DataFormatter formatter) {
    int lastHeaderRow = Math.min(sheet.getLastRowNum(), 19);
    for (int rowIndex = 0; rowIndex <= lastHeaderRow; rowIndex++) {
      Row row = sheet.getRow(rowIndex);
      if (row == null) continue;
      int studentIdColumn = -1;
      int nameColumn = -1;
      for (int column = Math.max(0, row.getFirstCellNum()); column < row.getLastCellNum(); column++) {
        String value =
            formatter.formatCellValue(row.getCell(column)).trim().toLowerCase(Locale.ROOT);
        if (STUDENT_ID_HEADERS.contains(value)) {
          if (studentIdColumn >= 0) {
            throw invalidRoster("表头第 " + (rowIndex + 1) + " 行包含多个学号列候选，请只保留一列");
          }
          studentIdColumn = column;
        }
        if (NAME_HEADERS.contains(value)) {
          if (nameColumn >= 0) {
            throw invalidRoster("表头第 " + (rowIndex + 1) + " 行包含多个姓名列候选，请只保留一列");
          }
          nameColumn = column;
        }
      }
      if (studentIdColumn >= 0 && nameColumn >= 0) {
        return new HeaderColumns(rowIndex, studentIdColumn, nameColumn);
      }
    }
    throw invalidRoster("请确认首个工作表包含“学号”和“姓名”列");
  }

  private void validateStudent(int rowNumber, String studentId, String name) {
    if (!studentId.matches("[A-Za-z0-9_.-]{8,64}")) {
      throw invalidRoster("第 " + rowNumber + " 行学号必须为 8 至 64 位字母、数字或 ._-");
    }
    if (name.isBlank() || name.length() > 128 || name.matches(".*[<>&\"'`].*")) {
      throw invalidRoster("第 " + rowNumber + " 行姓名不符合账号格式要求");
    }
  }

  private String cellValue(Row row, int column, DataFormatter formatter) {
    return formatter.formatCellValue(row.getCell(column)).trim();
  }

  private static void requireAdmin(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "仅管理员可导入学生名单");
    }
  }

  private static BusinessException invalidRoster(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STUDENT_ROSTER", message);
  }

  private record HeaderColumns(int rowIndex, int studentIdColumn, int nameColumn) {}

  private record RosterStudent(String studentId, String name) {}
}
