package com.pythontopia.learner.evidence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningEvidenceGraphService {

  private static final int MIN_LIMIT = 1;
  private static final int MAX_LIMIT = 200;

  private final LearningEvidenceGraphRepository repository;

  public LearningEvidenceGraphService(LearningEvidenceGraphRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public LearningEvidenceGraphDto buildForUser(Long userId, String focusKc, int limit) {
    int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
    LearningEvidenceGraphRepository.RawEvidenceGraph raw =
        repository.load(userId, focusKc, safeLimit);
    EvidenceGraphMetricsDto metrics = metrics(raw.nodes(), raw.edges());
    List<EvidencePersonalizationSignalDto> signals = personalizationSignals(raw.nodes(), raw.edges());
    return new LearningEvidenceGraphDto(userId, focusKc, raw.nodes(), raw.edges(), metrics, signals);
  }

  private EvidenceGraphMetricsDto metrics(List<EvidenceNodeDto> nodes, List<EvidenceEdgeDto> edges) {
    int nodeCount = nodes.size();
    int edgeCount = edges.size();
    int touchedKcCount = countType(nodes, "KC");
    int groundedPageCount = countType(nodes, "PAGE");
    int resourceCount = countType(nodes, "RESOURCE");
    int checkCount = countType(nodes, "CHECK");
    int masteryCount = countType(nodes, "MASTERY_EVENT");
    int failedChecks = countChecks(nodes, "FAILED");
    int remediatedChecks = countRemediatedChecks(nodes);
    int kcRelationCount = countRelation(edges, "KC_RELATION");
    int groundedResources = nodesWithRelation(nodes, edges, "RESOURCE", "GROUNDED_BY_PAGE").size();

    double checkCoverageRate = ratio(resourcesWithChecks(edges).size(), Math.max(1, resourceCount));
    double masteryEvidenceRate = checkCount == 0 ? 0.0 : ratio(masteryCount, checkCount);
    double remediationRate = failedChecks == 0 ? 0.0 : ratio(remediatedChecks, failedChecks);
    ConnectedEvidence connected = connectedEvidence(nodes, edges);
    double groundingRate = ratio(groundedResources, Math.max(1, resourceCount));
    double relationRate = kcRelationCount > 0 ? 1.0 : 0.0;
    double graphCompletenessScore =
        round((checkCoverageRate
                + Math.min(1.0, masteryEvidenceRate)
                + groundingRate
                + connected.rate()
                + relationRate)
            / 5.0);

    return new EvidenceGraphMetricsDto(
        nodeCount,
        edgeCount,
        touchedKcCount,
        groundedPageCount,
        checkCoverageRate,
        Math.min(1.0, masteryEvidenceRate),
        connected.rate(),
        remediationRate,
        graphCompletenessScore,
        connected.isolatedCount());
  }

  private List<EvidencePersonalizationSignalDto> personalizationSignals(
      List<EvidenceNodeDto> nodes, List<EvidenceEdgeDto> edges) {
    Map<String, SignalAccumulator> acc = new LinkedHashMap<>();
    Map<String, EvidenceNodeDto> nodeIndex = new HashMap<>();
    Map<String, List<String>> kcCodesByEvidenceId = new HashMap<>();
    for (EvidenceNodeDto node : nodes) {
      nodeIndex.put(node.id(), node);
      if ("KC".equals(node.type())) {
        acc.putIfAbsent(node.refId(), new SignalAccumulator(node.refId()));
      }
    }
    for (EvidenceEdgeDto edge : edges) {
      if (!edge.toId().startsWith("kc:")) {
        continue;
      }
      String kc = edge.toId().substring("kc:".length());
      kcCodesByEvidenceId.computeIfAbsent(edge.fromId(), ignored -> new ArrayList<>()).add(kc);
      SignalAccumulator signal = acc.computeIfAbsent(kc, SignalAccumulator::new);
      EvidenceNodeDto from = nodeIndex.get(edge.fromId());
      if (from == null) {
        continue;
      }
      if ("CHECK".equals(from.type())) {
        Object status = from.attributes().get("status");
        if ("FAILED".equals(status)) {
          signal.failedChecks++;
        } else if ("PASSED".equals(status)) {
          signal.passedChecks++;
        }
        signal.checkIds.add(parseLong(from.refId()));
      }
      if ("EVENT".equals(from.type())) {
        Object eventType = from.attributes().get("eventType");
        if ("practice_failed".equals(eventType)) {
          signal.failedPracticeEvents++;
        }
        signal.eventIds.add(parseLong(from.refId()));
      }
    }
    for (EvidenceEdgeDto edge : edges) {
      if (!"GROUNDED_BY_PAGE".equals(edge.relation())) {
        continue;
      }
      Long pageId = parseLong(edge.toId().replace("page:", ""));
      for (String kc : kcCodesByEvidenceId.getOrDefault(edge.fromId(), List.of())) {
        SignalAccumulator signal = acc.computeIfAbsent(kc, SignalAccumulator::new);
        signal.pageIds.add(pageId);
      }
    }
    return acc.values().stream()
        .filter(SignalAccumulator::hasSignal)
        .map(SignalAccumulator::toDto)
        .sorted((a, b) -> Double.compare(b.priorityScore(), a.priorityScore()))
        .toList();
  }

  private static int countType(List<EvidenceNodeDto> nodes, String type) {
    return (int) nodes.stream().filter(node -> type.equals(node.type())).count();
  }

  private static int countRelation(List<EvidenceEdgeDto> edges, String relation) {
    return (int) edges.stream().filter(edge -> relation.equals(edge.relation())).count();
  }

  private static int countChecks(List<EvidenceNodeDto> nodes, String status) {
    return (int)
        nodes.stream()
            .filter(node -> "CHECK".equals(node.type()))
            .filter(node -> status.equals(node.attributes().get("status")))
            .count();
  }

  private static int countRemediatedChecks(List<EvidenceNodeDto> nodes) {
    return (int)
        nodes.stream()
            .filter(node -> "CHECK".equals(node.type()))
            .filter(node -> node.attributes().get("remediationType") != null)
            .count();
  }

  private static Set<String> resourcesWithChecks(List<EvidenceEdgeDto> edges) {
    Set<String> resources = new HashSet<>();
    for (EvidenceEdgeDto edge : edges) {
      if ("CHECKS_RESOURCE".equals(edge.relation())) {
        resources.add(edge.toId());
      }
    }
    return resources;
  }

  private static Set<String> nodesWithRelation(
      List<EvidenceNodeDto> nodes, List<EvidenceEdgeDto> edges, String type, String relation) {
    Set<String> ids =
        nodes.stream()
            .filter(node -> type.equals(node.type()))
            .map(EvidenceNodeDto::id)
            .collect(java.util.stream.Collectors.toSet());
    Set<String> matched = new HashSet<>();
    for (EvidenceEdgeDto edge : edges) {
      if (relation.equals(edge.relation()) && ids.contains(edge.fromId())) {
        matched.add(edge.fromId());
      }
    }
    return matched;
  }

  private static ConnectedEvidence connectedEvidence(
      List<EvidenceNodeDto> nodes, List<EvidenceEdgeDto> edges) {
    Set<String> evidenceNodeIds =
        nodes.stream()
            .filter(
                node ->
                    Set.of("RESOURCE", "EVENT", "CHECK", "MASTERY_EVENT", "AGENT_RUN")
                        .contains(node.type()))
            .map(EvidenceNodeDto::id)
            .collect(java.util.stream.Collectors.toSet());
    if (evidenceNodeIds.isEmpty()) {
      return new ConnectedEvidence(0.0, 0);
    }
    Map<String, Set<String>> nonTrivialRelations = new HashMap<>();
    for (EvidenceEdgeDto edge : edges) {
      if ("ACTED".equals(edge.relation()) || "COVERS_KC".equals(edge.relation())) {
        continue;
      }
      if (evidenceNodeIds.contains(edge.fromId())) {
        nonTrivialRelations.computeIfAbsent(edge.fromId(), ignored -> new HashSet<>()).add(edge.relation());
      }
      if (evidenceNodeIds.contains(edge.toId())) {
        nonTrivialRelations.computeIfAbsent(edge.toId(), ignored -> new HashSet<>()).add(edge.relation());
      }
    }
    int connected = 0;
    for (String nodeId : evidenceNodeIds) {
      if (!nonTrivialRelations.getOrDefault(nodeId, Set.of()).isEmpty()) {
        connected++;
      }
    }
    return new ConnectedEvidence(round(ratio(connected, evidenceNodeIds.size())), evidenceNodeIds.size() - connected);
  }

  private static double ratio(int numerator, int denominator) {
    if (denominator <= 0) {
      return 0.0;
    }
    return round((double) numerator / (double) denominator);
  }

  private static double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }

  private static Long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private record ConnectedEvidence(double rate, int isolatedCount) {}

  private static final class SignalAccumulator {
    final String kcCode;
    int failedChecks;
    int passedChecks;
    int failedPracticeEvents;
    final Set<Long> eventIds = new LinkedHashSet<>();
    final Set<Long> checkIds = new LinkedHashSet<>();
    final Set<Long> pageIds = new LinkedHashSet<>();

    SignalAccumulator(String kcCode) {
      this.kcCode = kcCode;
    }

    boolean hasSignal() {
      return failedChecks > 0 || passedChecks > 0 || failedPracticeEvents > 0 || !pageIds.isEmpty();
    }

    EvidencePersonalizationSignalDto toDto() {
      double priority =
          round(failedChecks * 1.0
              + failedPracticeEvents * 0.8
              + passedChecks * 0.35
              + Math.min(3, pageIds.size()) * 0.15);
      List<String> reasons = new ArrayList<>();
      if (failedChecks > 0) {
        reasons.add("微测失败 " + failedChecks + " 次");
      }
      if (failedPracticeEvents > 0) {
        reasons.add("练习失败 " + failedPracticeEvents + " 次");
      }
      if (!eventIds.isEmpty()) {
        reasons.add("证据事件 " + eventIds.size() + " 条");
      }
      if (!checkIds.isEmpty()) {
        reasons.add("检查证据 " + checkIds.size() + " 条");
      }
      if (!pageIds.isEmpty()) {
        reasons.add("课件页证据 " + pageIds.size() + " 页");
      }
      if (passedChecks > 0 && failedChecks == 0 && failedPracticeEvents == 0) {
        reasons.add("已有通过证据，可安排迁移巩固");
      }
      return new EvidencePersonalizationSignalDto(
          kcCode,
          failedChecks,
          passedChecks,
          failedPracticeEvents,
          pageIds.size(),
          eventIds.stream().filter(java.util.Objects::nonNull).toList(),
          checkIds.stream().filter(java.util.Objects::nonNull).toList(),
          pageIds.stream().filter(java.util.Objects::nonNull).toList(),
          priority,
          String.join("；", reasons));
    }
  }
}
