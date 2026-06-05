package com.pythontopia.world.save;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.world.save.dto.SaveSlotDto;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SaveSlotService {

  private final SaveSlotRepository repository;
  private final ObjectMapper objectMapper;

  public SaveSlotService(SaveSlotRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public List<SaveSlotDto> list(Long userId) {
    return repository.findAll(userId).stream().map(SaveSlotDto::from).toList();
  }

  public Optional<SaveSlotDto> findActive(Long userId) {
    return repository.findActive(userId).map(SaveSlotDto::from);
  }

  public SaveSlotDto create(Long userId, int slotIndex, String displayName) {
    return SaveSlotDto.from(repository.createSlot(userId, slotIndex, displayName));
  }

  public SaveSlotDto activate(Long userId, int slotIndex) {
    return SaveSlotDto.from(repository.activateSlot(userId, slotIndex));
  }

  public SaveSlotDto saveSnapshot(Long userId, int slotIndex, String snapshotJson) {
    return SaveSlotDto.from(repository.updateSnapshot(userId, slotIndex, snapshotJson));
  }

  /**
   * patch slot 的 V114 扩展列。{@code null} 入参保持原值不变，便于增量更新（如仅 scene_name）。
   *
   * <p>{@code inventoryItems} 在此序列化，调用方可传任意 JSON 友好值（record List、ObjectNode、原始 String 等）。
   */
  public SaveSlotDto updateExtension(
      Long userId,
      int slotIndex,
      String sceneName,
      List<String> unlockedBuildings,
      Long accumulatedPlaySeconds,
      Object inventoryItems) {
    String inventoryJson = serialiseInventory(inventoryItems);
    return SaveSlotDto.from(
        repository.updateExtension(
            userId,
            slotIndex,
            sceneName,
            unlockedBuildings,
            accumulatedPlaySeconds,
            inventoryJson));
  }

  private String serialiseInventory(Object inventoryItems) {
    if (inventoryItems == null) {
      return null;
    }
    if (inventoryItems instanceof String raw) {
      return raw.isBlank() ? null : raw;
    }
    try {
      return objectMapper.writeValueAsString(inventoryItems);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Failed to serialise save_slot.inventory_items payload", exception);
    }
  }
}
