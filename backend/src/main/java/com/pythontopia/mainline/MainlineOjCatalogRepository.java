package com.pythontopia.mainline;

import com.pythontopia.coursepack.domain.CourseProblem;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.mainline.dto.StageExampleDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 小节「练」OJ 编程题展示目录（{@code mainline_oj_catalog}，P1 于 V159 建表 + seed）。
 *
 * <p>R-14「safe」决策：不改 {@code course_problem.id}，目录仅作展示覆盖层——按 (章, 小节) 给出统一编号
 * （{@code display_no}，如 {@code OJ-01}）、规范题名与 KC。{@link StageExampleDto#id()} 仍是真实
 * {@code course_problem.id}，学生在训练场用它判题。
 *
 * <p>容错：目录行字段缺失（{@code display_name}/{@code kc_code} 为空）时，回退用
 * {@link CoursePackRepository#findProblem} 的题名 / KC 补齐；{@code display_no} 缺失兜底 {@code "OJ"}。
 * 运行期表由 P1 提供；本仓库用字符串 SQL，编译期不依赖该表存在。
 */
@Repository
public class MainlineOjCatalogRepository {

  private static final long PACK_ID = 43L;
  private static final String DEFAULT_DISPLAY_NO = "OJ";

  private final JdbcClient jdbcClient;
  private final CoursePackRepository coursePackRepository;

  public MainlineOjCatalogRepository(
      JdbcClient jdbcClient, CoursePackRepository coursePackRepository) {
    this.jdbcClient = jdbcClient;
    this.coursePackRepository = coursePackRepository;
  }

  /** 某小节的 OJ 展示条目（按 display_no, problem_id 升序）。 */
  public List<StageExampleDto> examplesForStage(int chapter, int stageIndex) {
    List<CatalogRow> rows =
        jdbcClient
            .sql(
                """
                SELECT problem_id, display_no, display_name, kc_code
                FROM mainline_oj_catalog
                WHERE chapter = :chapter AND stage_index = :stageIndex
                ORDER BY display_no, problem_id
                """)
            .param("chapter", chapter)
            .param("stageIndex", stageIndex)
            .query(
                (rs, rowNum) ->
                    new CatalogRow(
                        rs.getLong("problem_id"),
                        rs.getString("display_no"),
                        rs.getString("display_name"),
                        rs.getString("kc_code")))
            .list();
    // 先收尽目录行再补齐，避免在 ResultSet 迭代中嵌套查询占用同一连接。
    List<StageExampleDto> examples = new ArrayList<>(rows.size());
    for (CatalogRow row : rows) {
      examples.add(toExample(row));
    }
    return examples;
  }

  private StageExampleDto toExample(CatalogRow row) {
    String displayNo = blankToNull(row.displayNo());
    String displayName = blankToNull(row.displayName());
    String kcCode = blankToNull(row.kcCode());
    if (displayName == null || kcCode == null) {
      CourseProblem problem =
          coursePackRepository.findProblem(PACK_ID, row.problemId()).orElse(null);
      if (problem != null) {
        if (displayName == null) {
          displayName = problem.title();
        }
        if (kcCode == null) {
          kcCode = problem.kcCode();
        }
      }
    }
    return new StageExampleDto(
        row.problemId(),
        displayNo == null ? DEFAULT_DISPLAY_NO : displayNo,
        displayName == null ? "" : displayName,
        kcCode == null ? "" : kcCode);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private record CatalogRow(long problemId, String displayNo, String displayName, String kcCode) {}
}
