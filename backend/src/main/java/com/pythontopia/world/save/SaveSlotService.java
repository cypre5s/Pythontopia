package com.pythontopia.world.save;

import com.pythontopia.world.save.dto.SaveSlotDto;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SaveSlotService {

  private final SaveSlotRepository repository;

  public SaveSlotService(SaveSlotRepository repository) {
    this.repository = repository;
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
}
