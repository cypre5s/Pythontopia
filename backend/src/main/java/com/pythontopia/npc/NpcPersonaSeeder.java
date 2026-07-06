package com.pythontopia.npc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时从 {@code data/npcs/<key>/meta.json} 幂等种子 NPC 人格到 {@code npc_persona}，
 * 使记忆化 NPC 对话能加载到每个 NPC 的 {@code core_md}（否则只能退化成通用人设）。
 *
 * <p>种子是可重放旁路：失败仅 warn，绝不阻断应用启动。
 */
@Component
public class NpcPersonaSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(NpcPersonaSeeder.class);

  private final NpcPersonaService personaService;

  public NpcPersonaSeeder(NpcPersonaService personaService) {
    this.personaService = personaService;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      personaService.seedFromMeta();
    } catch (RuntimeException ex) {
      log.warn("NPC persona seed skipped: {}", ex.getMessage());
    }
  }
}
