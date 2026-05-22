package com.pythontopia.world.npc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.inventory.InventoryRepository;
import com.pythontopia.world.inventory.ItemCatalogEntry;
import com.pythontopia.world.npc.dto.NpcDialogEffectDto;
import com.pythontopia.world.npc.dto.NpcDialogStateDto;
import com.pythontopia.world.npc.dto.NpcGiftRequest;
import com.pythontopia.world.npc.dto.NpcGiftResponse;
import com.pythontopia.world.npc.dto.NpcTalkRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the talk/gift business rules on top of the {@link NpcDialogStateRepository} primitive
 * writes. Gift-tier scoring uses the per-NPC mapping stored in {@code
 * item_catalog.gift_tier_map.<npcKey>} with a {@code neutral} fallback when the NPC is absent from
 * the map.
 */
@Service
public class NpcDialogService {

  /** Friendship deltas per V102 gift_tier check enum. */
  static final Map<String, Integer> TIER_DELTAS =
      Map.of(
          "love", 8,
          "like", 4,
          "neutral", 1,
          "dislike", -2,
          "hate", -5);

  private final NpcDialogStateRepository stateRepository;
  private final InventoryRepository inventoryRepository;
  private final ObjectMapper objectMapper;

  public NpcDialogService(
      NpcDialogStateRepository stateRepository,
      InventoryRepository inventoryRepository,
      ObjectMapper objectMapper) {
    this.stateRepository = stateRepository;
    this.inventoryRepository = inventoryRepository;
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

    int friendshipDelta = 0;
    ObjectNode flagsPatch = objectMapper.createObjectNode();
    List<NpcDialogEffectDto> effects =
        request.effects() == null ? List.of() : request.effects();
    for (NpcDialogEffectDto effect : effects) {
      if (effect == null || effect.type() == null) {
        continue;
      }
      switch (effect.type()) {
        case "FRIENDSHIP_DELTA" -> friendshipDelta += effect.delta() == null ? 0 : effect.delta();
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
          // ignored for forward compatibility
        }
      }
    }

    String nextNodeId = request.nextNodeId();
    if (nextNodeId == null || nextNodeId.isBlank()) {
      nextNodeId = current.currentNodeId();
    }
    stateRepository.upsertNodeProgress(
        userId, npcKey, nextNodeId, new ArrayList<>(completed));
    if (friendshipDelta != 0) {
      stateRepository.incrementFriendship(userId, npcKey, friendshipDelta);
    }
    if (flagsPatch.size() > 0) {
      stateRepository.mergeFlags(userId, npcKey, flagsPatch);
    }
    return NpcDialogStateDto.from(stateRepository.findOrInit(userId, npcKey));
  }

  @Transactional
  public NpcGiftResponse gift(Long userId, String npcKey, NpcGiftRequest request) {
    if (request == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "NPC_GIFT_REQUEST_EMPTY", "Gift request body is required");
    }
    ItemCatalogEntry catalog =
        inventoryRepository
            .findCatalogEntry(request.itemKey())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "ITEM_NOT_FOUND",
                        "Unknown item " + request.itemKey()));
    inventoryRepository.consumeItem(userId, request.itemKey(), request.qty());
    String giftTier = resolveGiftTier(catalog.giftTierMap(), npcKey);
    int delta = TIER_DELTAS.getOrDefault(giftTier, 0) * request.qty();
    int newFriendship = stateRepository.incrementFriendship(userId, npcKey, delta);
    stateRepository.logGift(userId, npcKey, request.itemKey(), giftTier, delta);
    return new NpcGiftResponse(npcKey, request.itemKey(), giftTier, delta, newFriendship);
  }

  static String resolveGiftTier(JsonNode giftTierMap, String npcKey) {
    if (giftTierMap == null) {
      return "neutral";
    }
    JsonNode tier = giftTierMap.get(npcKey);
    if (tier == null || !tier.isTextual()) {
      tier = giftTierMap.get("default");
    }
    if (tier != null && tier.isTextual() && TIER_DELTAS.containsKey(tier.asText())) {
      return tier.asText();
    }
    return "neutral";
  }
}
