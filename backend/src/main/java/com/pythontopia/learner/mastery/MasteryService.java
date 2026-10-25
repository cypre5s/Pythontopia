package com.pythontopia.learner.mastery;

import com.pythontopia.learner.mastery.dto.MasterySnapshotDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MasteryService {

  private final MasteryEvaluator evaluator;

  public MasteryService(MasteryEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  public List<MasterySnapshotDto> listSnapshots(Long userId) {
    List<MasterySnapshot> snapshots = evaluator.snapshots(userId);
    if (snapshots.isEmpty()) {
      return List.of();
    }
    Set<String> assessedKcs = evaluator.assessedKcCodes(userId);
    Map<String, MasterySource> latestSources = evaluator.latestEventSourcesByKc(userId);
    return snapshots.stream()
        .map(
            snapshot ->
                toDto(
                    snapshot,
                    latestSources.get(snapshot.kcCode()),
                    assessedKcs.contains(snapshot.kcCode())))
        .toList();
  }

  private MasterySnapshotDto toDto(
      MasterySnapshot snapshot, MasterySource source, boolean assessed) {
    String latestSource = source == null ? null : source.name();
    String lastEventAt = snapshot.lastEventAt() == null ? null : snapshot.lastEventAt().toString();
    return new MasterySnapshotDto(
        snapshot.kcCode(),
        snapshot.masteryScore(),
        snapshot.confidence(),
        lastEventAt,
        latestSource,
        assessed);
  }
}
