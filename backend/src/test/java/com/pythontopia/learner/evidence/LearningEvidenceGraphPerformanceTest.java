package com.pythontopia.learner.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LearningEvidenceGraphPerformanceTest {

  @Test
  void buildsPersonalizationSignalsWithLinearEdgeReads() {
    List<EvidenceNodeDto> nodes = new ArrayList<>();
    List<EvidenceEdgeDto> sourceEdges = new ArrayList<>();
    int resourceCount = 80;
    int kcCount = 65;
    int pagesPerResource = 4;
    int kcsPerResource = 6;

    for (int kcIndex = 1; kcIndex <= kcCount; kcIndex++) {
      String kcCode = "PY43-KC-%03d".formatted(kcIndex);
      nodes.add(new EvidenceNodeDto("kc:" + kcCode, "KC", kcCode, "kc", kcCode, Map.of()));
    }
    for (int resourceIndex = 0; resourceIndex < resourceCount; resourceIndex++) {
      String resourceId = "resource:" + resourceIndex;
      nodes.add(
          new EvidenceNodeDto(resourceId, "RESOURCE", resourceId, "resource", resourceId, Map.of()));
      for (int pageIndex = 0; pageIndex < pagesPerResource; pageIndex++) {
        String pageId = String.valueOf(resourceIndex * pagesPerResource + pageIndex + 1);
        sourceEdges.add(
            new EvidenceEdgeDto(
                resourceId, "page:" + pageId, "GROUNDED_BY_PAGE", 1.0, Map.of()));
      }
      for (int kcOffset = 0; kcOffset < kcsPerResource; kcOffset++) {
        String kcCode = "PY43-KC-%03d".formatted((resourceIndex * kcsPerResource + kcOffset) % kcCount + 1);
        sourceEdges.add(
            new EvidenceEdgeDto(resourceId, "kc:" + kcCode, "COVERS_KC", 0.9, Map.of()));
      }
    }

    CountingEdgeList edges = new CountingEdgeList(sourceEdges);
    LearningEvidenceGraphRepository repository = mock(LearningEvidenceGraphRepository.class);
    when(repository.load(1L, null, 80))
        .thenReturn(new LearningEvidenceGraphRepository.RawEvidenceGraph(nodes, edges));

    LearningEvidenceGraphDto graph =
        new LearningEvidenceGraphService(repository).buildForUser(1L, null, 80);

    assertThat(graph.personalizationSignals())
        .filteredOn(signal -> signal.kcCode().equals("PY43-KC-001"))
        .singleElement()
        .satisfies(signal -> assertThat(signal.groundedPageCount()).isGreaterThan(4));
    assertThat(edges.readCount()).isLessThan((long) sourceEdges.size() * 20);
  }

  private static final class CountingEdgeList extends AbstractList<EvidenceEdgeDto> {
    private final List<EvidenceEdgeDto> delegate;
    private final AtomicLong reads = new AtomicLong();

    private CountingEdgeList(List<EvidenceEdgeDto> delegate) {
      this.delegate = delegate;
    }

    @Override
    public EvidenceEdgeDto get(int index) {
      reads.incrementAndGet();
      return delegate.get(index);
    }

    @Override
    public int size() {
      return delegate.size();
    }

    long readCount() {
      return reads.get();
    }
  }
}
