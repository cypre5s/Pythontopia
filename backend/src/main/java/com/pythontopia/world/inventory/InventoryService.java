package com.pythontopia.world.inventory;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.inventory.dto.InventoryItemDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

  private final InventoryRepository repository;

  public InventoryService(InventoryRepository repository) {
    this.repository = repository;
  }

  public List<InventoryItemDto> list(Long userId) {
    return repository.findInventory(userId).stream().map(InventoryItemDto::from).toList();
  }

  /** Consume exactly one of the requested item; throws when the learner has none. */
  public InventoryItemDto useOne(Long userId, String itemKey) {
    repository
        .findCatalogEntry(itemKey)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Unknown item " + itemKey));
    return InventoryItemDto.from(repository.consumeItem(userId, itemKey, 1));
  }

  /** Admin-only grant — assumes caller authorisation has already been enforced. */
  public InventoryItemDto grant(Long userId, String itemKey, int qty) {
    repository
        .findCatalogEntry(itemKey)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Unknown item " + itemKey));
    return InventoryItemDto.from(repository.addItem(userId, itemKey, qty));
  }
}
