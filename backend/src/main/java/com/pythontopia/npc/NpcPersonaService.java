package com.pythontopia.npc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 从 data/npcs/<key>/meta.json 幂等种子 NPC 人格，并提供按 npcKey 加载能力。 */
@Service
public class NpcPersonaService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public NpcPersonaService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public NpcPersona load(String npcKey) {
    return jdbc.query(
            "SELECT npc_key, display_name, core_md FROM npc_persona WHERE npc_key = ?",
            (rs, rowNum) ->
                new NpcPersona(
                    rs.getString("npc_key"), rs.getString("display_name"), rs.getString("core_md")),
            npcKey)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown npc persona: " + npcKey));
  }

  public void seedFromMeta() {
    Path root = resolveNpcRoot();
    if (!Files.isDirectory(root)) {
      return;
    }
    try (var stream = Files.list(root)) {
      List<Path> metas =
          stream
              .map(path -> path.resolve("meta.json"))
              .filter(Files::isRegularFile)
              .sorted()
              .toList();
      for (Path meta : metas) {
        seedOne(meta);
      }
    } catch (IOException ignored) {
      // 人格种子是可重放旁路，读取失败不影响应用启动。
    }
  }

  private void seedOne(Path metaPath) {
    try {
      JsonNode meta = objectMapper.readTree(metaPath.toFile());
      String key = text(meta, "key");
      if (key.isBlank()) {
        return;
      }
      String name = text(meta, "name").isBlank() ? key : text(meta, "name");
      String core = "- 名字：" + name + "\n- 身份：" + text(meta, "description") + "\n";
      jdbc.update(
          "INSERT INTO npc_persona (npc_key, display_name, core_md) VALUES (?, ?, ?) "
              + "ON CONFLICT (npc_key) DO UPDATE SET display_name = EXCLUDED.display_name, "
              + "core_md = EXCLUDED.core_md, updated_at = now()",
          key,
          name,
          core);
    } catch (IOException ignored) {
      // 单个 meta 损坏时跳过，避免阻断其它 NPC。
    }
  }

  private static String text(JsonNode node, String key) {
    JsonNode value = node == null ? null : node.get(key);
    return value == null || value.isNull() ? "" : value.asText("");
  }

  private static Path resolveNpcRoot() {
    Path repoRoot = Path.of("data", "npcs");
    if (Files.isDirectory(repoRoot)) {
      return repoRoot;
    }
    return Path.of("..", "data", "npcs");
  }
}
