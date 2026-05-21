package com.pythontopia.learner.mastery;

import com.pythontopia.learner.mastery.dto.MasterySnapshotDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MasteryService {

  private final MasteryEvaluator evaluator;

  public MasteryService(MasteryEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  public List<MasterySnapshotDto> listSnapshots(Long userId) {
    return evaluator.snapshots(userId).stream()
        .map(snapshot -> toDto(userId, snapshot))
        .toList();
  }

  private MasterySnapshotDto toDto(Long userId, MasterySnapshot snapshot) {
    List<MasteryEventRecord> events = evaluator.recentEvents(userId, snapshot.kcCode(), 1);
    String latestSource = events.isEmpty() ? null : events.get(0).source().name();
    String lastEventAt = snapshot.lastEventAt() == null ? null : snapshot.lastEventAt().toString();
    return new MasterySnapshotDto(
        snapshot.kcCode(),
        snapshot.masteryScore(),
        snapshot.confidence(),
        lastEventAt,
        latestSource);
  }
}
