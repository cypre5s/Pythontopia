package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.world.boss.dto.FrontierPickDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ExpeditionMapGeneratorTest {

  @Test
  void serverMapIsAReachableTenLayerDagBuiltFromFrontierPicks() {
    List<FrontierPickDto> picks =
        List.of(
            new FrontierPickDto("PY43-KC-001", 1, 2, "weak"),
            new FrontierPickDto("PY43-KC-002", 1, 3, "spaced"));

    ExpeditionMapGenerator.MapData map = ExpeditionMapGenerator.generate(picks, 42L);

    assertThat(map.layers()).isEqualTo(10);
    Map<String, Map<String, Object>> byId =
        map.nodes().stream()
            .collect(Collectors.toMap(node -> (String) node.get("id"), Function.identity()));
    assertThat(byId).hasSizeBetween(18, 34);
    assertThat(map.nodes())
        .filteredOn(node -> ((Number) node.get("layer")).intValue() == 0)
        .singleElement()
        .satisfies(
            node -> {
              assertThat(node.get("kind")).isEqualTo("battle");
              assertThat(node.get("state")).isEqualTo("available");
            });
    assertThat(map.nodes())
        .filteredOn(node -> ((Number) node.get("layer")).intValue() == 9)
        .singleElement()
        .extracting(node -> node.get("kind"))
        .isEqualTo("boss");

    for (int layer = 1; layer < 9; layer++) {
      int currentLayer = layer;
      List<Map<String, Object>> row =
          map.nodes().stream()
              .filter(node -> ((Number) node.get("layer")).intValue() == currentLayer)
              .toList();
      assertThat(row).hasSizeBetween(2, 4);
      assertThat(row).allSatisfy(node -> assertThat(node.get("state")).isEqualTo("locked"));
    }

    Set<String> reached = new java.util.HashSet<>();
    reached.add("L0-0");
    for (int layer = 0; layer < 9; layer++) {
      int currentLayer = layer;
      List<Map<String, Object>> row =
          map.nodes().stream()
              .filter(node -> ((Number) node.get("layer")).intValue() == currentLayer)
              .toList();
      for (Map<String, Object> node : row) {
        @SuppressWarnings("unchecked")
        List<String> next = (List<String>) node.get("next");
        assertThat(next).isNotEmpty();
        for (String id : next) {
          assertThat(byId).containsKey(id);
          assertThat(((Number) byId.get(id).get("layer")).intValue()).isEqualTo(currentLayer + 1);
        }
        if (reached.contains(node.get("id"))) reached.addAll(next);
      }
    }
    assertThat(reached).containsAll(byId.keySet());
    assertThat(map.nodes())
        .filteredOn(node -> Set.of("rest", "chest").contains(node.get("kind")))
        .allSatisfy(
            node -> {
              assertThat(node.get("kcCode")).isNull();
              assertThat(node.get("challengeKind")).isNull();
            });
    assertThat(map.nodes())
        .filteredOn(node -> !Set.of("rest", "chest").contains(node.get("kind")))
        .allSatisfy(
            node -> {
              assertThat(node.get("kcCode")).isIn("PY43-KC-001", "PY43-KC-002");
              assertThat(((Number) node.get("difficulty")).intValue()).isBetween(1, 5);
            });
    assertThat(map.nodes())
        .filteredOn(node -> Set.of("event", "boss").contains(node.get("kind")))
        .allSatisfy(node -> assertThat(node.get("challengeKind")).isEqualTo("dual_track"));
  }
}
