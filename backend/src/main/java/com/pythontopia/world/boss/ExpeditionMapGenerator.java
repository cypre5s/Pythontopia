package com.pythontopia.world.boss;

import com.pythontopia.world.boss.dto.FrontierPickDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates the canonical 10-layer expedition map from backend-selected learning-frontier picks.
 */
final class ExpeditionMapGenerator {

  private static final int MIDDLE_LAYERS = 9;
  private static final List<String> CHALLENGE_KINDS = List.of("dual_track", "closed_loop");
  private static final List<String> BUG_CHALLENGE_KINDS = List.of("debug", "bug_mine");
  private static final Set<String> NON_CHALLENGE_NODES = Set.of("rest", "chest");

  private ExpeditionMapGenerator() {}

  static MapData generate(List<FrontierPickDto> picks, long seed) {
    if (picks == null || picks.isEmpty() || picks.size() > 40) {
      throw new IllegalArgumentException("expedition map requires 1..40 frontier picks");
    }
    for (FrontierPickDto pick : picks) {
      if (pick == null
          || pick.kcCode() == null
          || !pick.kcCode().matches("PY43-KC-\\d{3}")
          || pick.chapter() < 1
          || pick.chapter() > 7
          || pick.difficulty() < 1
          || pick.difficulty() > 5) {
        throw new IllegalArgumentException("expedition frontier pick is invalid");
      }
    }

    Random random = new Random(seed);
    List<List<String>> kindsByLayer = buildKinds(random);
    List<List<Map<String, Object>>> nodesByLayer = new ArrayList<>();
    List<Map<String, Object>> nodes = new ArrayList<>();
    int pickIndex = 0;
    for (int layer = 0; layer < kindsByLayer.size(); layer++) {
      List<String> kinds = kindsByLayer.get(layer);
      List<Map<String, Object>> row = new ArrayList<>();
      for (int col = 0; col < kinds.size(); col++) {
        String kind = kinds.get(col);
        FrontierPickDto pick =
            NON_CHALLENGE_NODES.contains(kind) ? null : picks.get(pickIndex++ % picks.size());
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "L" + layer + "-" + col);
        node.put("layer", layer);
        node.put("col", col);
        node.put("kind", kind);
        node.put("kcCode", pick == null ? null : pick.kcCode());
        node.put("challengeKind", challengeKind(kind, random));
        node.put("difficulty", pick == null ? 0 : pick.difficulty());
        node.put("next", new ArrayList<String>());
        node.put("state", layer == 0 ? "available" : "locked");
        row.add(node);
        nodes.add(node);
      }
      nodesByLayer.add(row);
    }

    for (int layer = 0; layer < nodesByLayer.size() - 1; layer++) {
      connectLayer(nodesByLayer.get(layer), nodesByLayer.get(layer + 1), random);
    }
    return new MapData(nodes, MIDDLE_LAYERS + 1);
  }

  private static List<List<String>> buildKinds(Random random) {
    List<List<String>> rows = new ArrayList<>();
    for (int layer = 0; layer <= MIDDLE_LAYERS; layer++) {
      if (layer == 0) {
        rows.add(List.of("battle"));
      } else if (layer == MIDDLE_LAYERS) {
        rows.add(List.of("boss"));
      } else {
        int width = 2 + random.nextInt(3);
        boolean forceRest = layer % 3 == 0;
        List<String> row = new ArrayList<>();
        for (int col = 0; col < width; col++) {
          row.add(forceRest && col == 0 ? "rest" : pickKind(layer, random));
        }
        rows.add(row);
      }
    }
    if (rows.stream().flatMap(List::stream).noneMatch("elite"::equals)) {
      List<String> finalMiddleLayer = rows.get(MIDDLE_LAYERS - 1);
      finalMiddleLayer.set(random.nextInt(finalMiddleLayer.size()), "elite");
    }
    return rows;
  }

  private static String pickKind(int layer, Random random) {
    double progress = (double) layer / MIDDLE_LAYERS;
    List<String> pool = new ArrayList<>(List.of("battle", "battle", "event"));
    if (progress > 0.25) pool.add("bug");
    if (progress > 0.3) pool.add("chest");
    if (progress > 0.55) pool.add("elite");
    return pool.get(random.nextInt(pool.size()));
  }

  private static String challengeKind(String kind, Random random) {
    if (NON_CHALLENGE_NODES.contains(kind)) return null;
    List<String> pool =
        switch (kind) {
          case "bug" -> BUG_CHALLENGE_KINDS;
          case "event", "boss" -> List.of("dual_track");
          default -> CHALLENGE_KINDS;
        };
    return pool.get(random.nextInt(pool.size()));
  }

  private static void connectLayer(
      List<Map<String, Object>> current, List<Map<String, Object>> next, Random random) {
    for (Map<String, Object> node : current) {
      double position = position(node, current.size());
      List<Map<String, Object>> closest =
          next.stream()
              .sorted(
                  Comparator.comparingDouble(
                      target -> Math.abs(position(target, next.size()) - position)))
              .toList();
      int linkCount = 1 + (random.nextDouble() < 0.45 && closest.size() > 1 ? 1 : 0);
      @SuppressWarnings("unchecked")
      List<String> links = (List<String>) node.get("next");
      closest.stream()
          .limit(linkCount)
          .map(target -> (String) target.get("id"))
          .forEach(links::add);
    }

    Set<String> covered = new java.util.HashSet<>();
    for (Map<String, Object> node : current) {
      @SuppressWarnings("unchecked")
      List<String> links = (List<String>) node.get("next");
      covered.addAll(links);
    }
    for (Map<String, Object> target : next) {
      String targetId = (String) target.get("id");
      if (covered.contains(targetId)) continue;
      double position = position(target, next.size());
      Map<String, Object> source =
          current.stream()
              .min(
                  Comparator.comparingDouble(
                      node -> Math.abs(position(node, current.size()) - position)))
              .orElseThrow();
      @SuppressWarnings("unchecked")
      List<String> links = (List<String>) source.get("next");
      links.add(targetId);
      covered.add(targetId);
    }
  }

  private static double position(Map<String, Object> node, int rowSize) {
    int col = (int) node.get("col");
    return rowSize <= 1 ? 0.5 : (double) col / (rowSize - 1);
  }

  record MapData(List<Map<String, Object>> nodes, int layers) {
    MapData {
      nodes = List.copyOf(nodes);
    }
  }
}
