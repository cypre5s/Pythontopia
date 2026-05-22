package com.pythontopia.world.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.pythontopia.common.error.BusinessException;

/**
 * Item catalog + per-learner inventory store. Mutations honour the V103 CHECK ({@code qty >= 0})
 * and uniqueness ({@code user_id, item_key}) constraints.
 */
@Repository
public class InventoryRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public InventoryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<ItemCatalogEntry> findCatalog() {
    return jdbcTemplate.query(
        """
        SELECT item_key, display_name, category, base_price, gift_tier_map, description,
               create_time
        FROM item_catalog
        ORDER BY item_key
        """,
        this::mapCatalog);
  }

  public Optional<ItemCatalogEntry> findCatalogEntry(String itemKey) {
    return jdbcTemplate
        .query(
            """
            SELECT item_key, display_name, category, base_price, gift_tier_map, description,
                   create_time
            FROM item_catalog
            WHERE item_key = ?
            """,
            this::mapCatalog,
            itemKey)
        .stream()
        .findFirst();
  }

  public List<InventoryItem> findInventory(Long userId) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, item_key, qty, updated_at
        FROM inventory_item
        WHERE user_id = ?
        ORDER BY item_key
        """,
        this::mapInventory,
        userId);
  }

  public Optional<InventoryItem> findInventoryItem(Long userId, String itemKey) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, item_key, qty, updated_at
            FROM inventory_item
            WHERE user_id = ? AND item_key = ?
            """,
            this::mapInventory,
            userId,
            itemKey)
        .stream()
        .findFirst();
  }

  /**
   * Add {@code qty} of {@code itemKey} to the learner's inventory. Caller must have validated the
   * catalog entry; this method blindly trusts the foreign-key constraint to enforce existence.
   */
  public InventoryItem addItem(Long userId, String itemKey, int qty) {
    if (qty <= 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVENTORY_QTY_INVALID", "Inventory delta must be positive");
    }
    jdbcTemplate.update(
        """
        INSERT INTO inventory_item (user_id, item_key, qty)
        VALUES (?, ?, ?)
        ON CONFLICT (user_id, item_key) DO UPDATE
        SET qty = inventory_item.qty + EXCLUDED.qty,
            updated_at = now()
        """,
        userId,
        itemKey,
        qty);
    return findInventoryItem(userId, itemKey).orElseThrow();
  }

  /**
   * Subtract {@code qty} of {@code itemKey} from the learner's inventory. Throws {@link
   * BusinessException} with code {@code INVENTORY_NOT_ENOUGH} when stock is insufficient.
   */
  public InventoryItem consumeItem(Long userId, String itemKey, int qty) {
    if (qty <= 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVENTORY_QTY_INVALID", "Consume qty must be positive");
    }
    InventoryItem current =
        findInventoryItem(userId, itemKey)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "INVENTORY_NOT_ENOUGH",
                        "Learner does not own item " + itemKey));
    if (current.qty() < qty) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INVENTORY_NOT_ENOUGH",
          "Only " + current.qty() + " " + itemKey + " in bag, requested " + qty);
    }
    jdbcTemplate.update(
        """
        UPDATE inventory_item
        SET qty = qty - ?, updated_at = now()
        WHERE user_id = ? AND item_key = ?
        """,
        qty,
        userId,
        itemKey);
    return findInventoryItem(userId, itemKey).orElseThrow();
  }

  /** Convenience for the controller to peek whether the learner has at least {@code qty} of an item. */
  public boolean hasQty(Long userId, String itemKey, int qty) {
    return findInventoryItem(userId, itemKey).map(it -> it.qty() >= qty).orElse(false);
  }

  private ItemCatalogEntry mapCatalog(ResultSet rs, int rowNum) throws SQLException {
    return new ItemCatalogEntry(
        rs.getString("item_key"),
        rs.getString("display_name"),
        rs.getString("category"),
        rs.getInt("base_price"),
        readJson(rs.getString("gift_tier_map")),
        rs.getString("description"),
        offset(rs, "create_time"));
  }

  private InventoryItem mapInventory(ResultSet rs, int rowNum) throws SQLException {
    return new InventoryItem(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("item_key"),
        rs.getInt("qty"),
        offset(rs, "updated_at"));
  }

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to parse item_catalog.gift_tier_map JSON", exception);
    }
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
