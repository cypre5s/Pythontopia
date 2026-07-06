package com.pythontopia.world.npc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.npc.dto.NpcDialogEffectDto;
import com.pythontopia.world.npc.dto.NpcDialogStateDto;
import com.pythontopia.world.npc.dto.NpcTalkRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在 {@link NpcDialogStateRepository} 的原子写之上施加 talk 业务规则（节点进度 + flags）。 */
@Service
public class NpcDialogService {

  private final NpcDialogStateRepository stateRepository;
  private final ObjectMapper objectMapper;

  public NpcDialogService(
      NpcDialogStateRepository stateRepository, ObjectMapper objectMapper) {
    this.stateRepository = stateRepository;
    this.objectMapper = objectMapper;
  }

  public NpcDialogStateDto getOrInit(Long userId, String npcKey) {
    return NpcDialogStateDto.from(stateRepository.findOrInit(userId, npcKey));
  }

  public List<NpcDialogStateDto> listAll(Long userId) {
    return stateRepository.findAll(userId).stream().map(NpcDialogStateDto::from).toList();
  }

  @Transactional
  public NpcDialogStateDto talk(Long userId, String npcKey, NpcTalkRequest request) {
    if (request == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "NPC_TALK_REQUEST_EMPTY", "Dialog request body is required");
    }
    NpcDialogState current = stateRepository.findOrInit(userId, npcKey);
    Set<String> completed = new LinkedHashSet<>(current.completedNodeIds());
    if (request.nodeId() != null && !request.nodeId().isBlank()) {
      completed.add(request.nodeId());
    }

    ObjectNode flagsPatch = objectMapper.createObjectNode();
    List<NpcDialogEffectDto> effects =
        request.effects() == null ? List.of() : request.effects();
    for (NpcDialogEffectDto effect : effects) {
      if (effect == null || effect.type() == null) {
        continue;
      }
      switch (effect.type()) {
        case "COMPLETE_NODE" -> {
          if (effect.key() != null && !effect.key().isBlank()) {
            completed.add(effect.key());
          }
        }
        case "SET_FLAG" -> {
          if (effect.key() != null && !effect.key().isBlank()) {
            flagsPatch.put(effect.key(), effect.value());
          }
        }
        default -> {
          // 忽略未知 effect 类型以保持前向兼容
        }
      }
    }

    String nextNodeId = request.nextNodeId();
    if (nextNodeId == null || nextNodeId.isBlank()) {
      nextNodeId = current.currentNodeId();
    }
    stateRepository.upsertNodeProgress(
        userId, npcKey, nextNodeId, new ArrayList<>(completed));
    if (flagsPatch.size() > 0) {
      stateRepository.mergeFlags(userId, npcKey, flagsPatch);
    }
    return NpcDialogStateDto.from(stateRepository.findOrInit(userId, npcKey));
  }

}
