package com.pythontopia.learner.evidence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceMemoryService {

  private static final long PACK_ID = 43L;

  private final LearningEvidenceGraphService graphService;
  private final EvidenceMemoryRepository repository;
  private final JdbcTemplate jdbc;

  public EvidenceMemoryService(
      LearningEvidenceGraphService graphService,
      EvidenceMemoryRepository repository,
      JdbcTemplate jdbc) {
    this.graphService = graphService;
    this.repository = repository;
    this.jdbc = jdbc;
  }

  @Transactional
  public EvidenceMemorySnapshotDto createSnapshot(
      Long userId, String focusKc, int limit, boolean persist) {
    EvidenceMemorySnapshotDto snapshot = buildSnapshot(userId, focusKc, limit, null);
    if (!persist) {
      return snapshot;
    }
    Long snapshotId = repository.insert(snapshot);
    return buildSnapshot(userId, focusKc, limit, snapshotId);
  }

  @Transactional(readOnly = true)
  public List<EvidenceMemorySignalDto> signalsForUser(Long userId) {
    return buildSnapshot(userId, null, 160, null).signals();
  }

  private EvidenceMemorySnapshotDto buildSnapshot(
      Long userId, String focusKc, int limit, Long snapshotId) {
    LearningEvidenceGraphDto graph = graphService.buildForUser(userId, focusKc, limit);
    List<EvidenceMemorySignalDto> direct = directSignals(graph.personalizationSignals());
    List<EvidenceMemorySignalDto> propagated = propagatedSignals(direct);
    List<EvidenceMemorySignalDto> all = new ArrayList<>();
    all.addAll(direct);
    all.addAll(propagated);
    all.sort(Comparator.comparingDouble(EvidenceMemorySignalDto::priorityScore).reversed());

    int retainedEvidenceCount = retainedEvidenceCount(all);
    double naiveMemoryScore = naiveMemoryScore(userId, focusKc);
    double graphMemoryScore = graphMemoryScore(direct, propagated, retainedEvidenceCount);
    return new EvidenceMemorySnapshotDto(
        snapshotId,
        userId,
        focusKc,
        direct.size(),
        propagated.size(),
        retainedEvidenceCount,
        graphMemoryScore,
        naiveMemoryScore,
        all);
  }

  private List<EvidenceMemorySignalDto> directSignals(
      List<EvidencePersonalizationSignalDto> personalizationSignals) {
    List<EvidenceMemorySignalDto> signals = new ArrayList<>();
    for (EvidencePersonalizationSignalDto signal : personalizationSignals) {
      double directScore =
          round(
              signal.failedChecks() * 1.2
                  + signal.failedPracticeEvents() * 0.9
                  + signal.passedChecks() * 0.25
                  + Math.min(3, signal.groundedPageCount()) * 0.15);
      String retentionPolicy =
          signal.failedChecks() > 0 || signal.failedPracticeEvents() > 0 ? "retain" : "summarize";
      signals.add(
          new EvidenceMemorySignalDto(
              signal.kcCode(),
              "DIRECT",
              directScore,
              0.0,
              round(directScore),
              null,
              signal.evidenceEventIds(),
              signal.checkIds(),
              signal.pageIds(),
              retentionPolicy,
              signal.reason()));
    }
    return signals;
  }

  private List<EvidenceMemorySignalDto> propagatedSignals(List<EvidenceMemorySignalDto> direct) {
    if (direct.isEmpty()) {
      return List.of();
    }
    Map<String, EvidenceMemorySignalDto> directByKc = new HashMap<>();
    for (EvidenceMemorySignalDto signal : direct) {
      directByKc.put(signal.kcCode(), signal);
    }
    Map<String, EvidenceMemorySignalDto> best = new LinkedHashMap<>();
    jdbc.query(
        """
        SELECT subject_id, relation, object_id, confidence
        FROM course_kc_edge
        WHERE pack_id = ?
          AND subject_type = 'kc'
          AND object_type = 'kc'
          AND (subject_id = ANY(?::text[]) OR object_id = ANY(?::text[]))
        ORDER BY confidence DESC
        LIMIT 80
        """,
        ps -> {
          String[] codes = directByKc.keySet().toArray(String[]::new);
          ps.setLong(1, PACK_ID);
          ps.setArray(2, ps.getConnection().createArrayOf("text", codes));
          ps.setArray(3, ps.getConnection().createArrayOf("text", codes));
        },
        rs -> {
          String subject = rs.getString("subject_id");
          String object = rs.getString("object_id");
          String relation = rs.getString("relation");
          BigDecimal confidence = rs.getBigDecimal("confidence");
          String sourceKc = directByKc.containsKey(subject) ? subject : object;
          String targetKc = directByKc.containsKey(subject) ? object : subject;
          if (directByKc.containsKey(targetKc)) {
            return;
          }
          EvidenceMemorySignalDto source = directByKc.get(sourceKc);
          if (source == null) {
            return;
          }
          double propagatedScore =
              round(source.directScore() * confidence.doubleValue() * relationDecay(relation));
          EvidenceMemorySignalDto candidate =
              new EvidenceMemorySignalDto(
                  targetKc,
                  "PROPAGATED",
                  0.0,
                  propagatedScore,
                  propagatedScore,
                  relation,
                  source.evidenceEventIds(),
                  source.checkIds(),
                  source.pageIds(),
                  propagatedScore >= 0.5 ? "summarize" : "decay",
                  "由 " + sourceKc + " 通过 " + relation + " 扩散到 " + targetKc);
          EvidenceMemorySignalDto existing = best.get(targetKc);
          if (existing == null || candidate.priorityScore() > existing.priorityScore()) {
            best.put(targetKc, candidate);
          }
        });
    return new ArrayList<>(best.values());
  }

  private double relationDecay(String relation) {
    return switch (relation) {
      case "requires" -> 0.65;
      case "co_occurs_with" -> 0.55;
      case "has_common_mistake" -> 0.70;
      case "same_chapter" -> 0.35;
      default -> 0.40;
    };
  }

  private int retainedEvidenceCount(List<EvidenceMemorySignalDto> signals) {
    Set<String> ids = new LinkedHashSet<>();
    for (EvidenceMemorySignalDto signal : signals) {
      signal.evidenceEventIds().forEach(id -> ids.add("event:" + id));
      signal.checkIds().forEach(id -> ids.add("check:" + id));
      signal.pageIds().forEach(id -> ids.add("page:" + id));
    }
    return ids.size();
  }

  private double naiveMemoryScore(Long userId, String focusKc) {
    Integer events =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM learning_event_log
            WHERE user_id = ?
              AND (?::text IS NULL OR payload->>'kcCode' = ?::text)
            """,
            Integer.class,
            userId,
            blankToNull(focusKc),
            blankToNull(focusKc));
    return round(Math.min(1.0, (events == null ? 0 : events) * 0.15));
  }

  private double graphMemoryScore(
      List<EvidenceMemorySignalDto> direct,
      List<EvidenceMemorySignalDto> propagated,
      int retainedEvidenceCount) {
    double directScore = direct.stream().mapToDouble(EvidenceMemorySignalDto::directScore).sum();
    double propagatedScore =
        propagated.stream().mapToDouble(EvidenceMemorySignalDto::propagatedScore).sum();
    return round(
        Math.min(
            1.0,
            directScore * 0.22
                + propagatedScore * 0.14
                + Math.min(8, retainedEvidenceCount) * 0.04));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }
}
