package com.pythontopia.world.shop;

import com.pythontopia.common.error.BusinessException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Shop listing reads + stock writes. Stock is {@code NULL} for unlimited SKUs (see V106 comment);
 * decrement is a no-op for those entries.
 */
@Repository
public class ShopListingRepository {

  private final JdbcTemplate jdbcTemplate;

  public ShopListingRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<ShopListing> findByShop(String shopKey) {
    return jdbcTemplate.query(
        """
        SELECT id, shop_key, item_key, price_coins, price_gems, stock, available_days
        FROM shop_listing
        WHERE shop_key = ?
        ORDER BY id
        """,
        this::map,
        shopKey);
  }

  public Optional<ShopListing> findOne(String shopKey, String itemKey) {
    return jdbcTemplate
        .query(
            """
            SELECT id, shop_key, item_key, price_coins, price_gems, stock, available_days
            FROM shop_listing
            WHERE shop_key = ? AND item_key = ?
            """,
            this::map,
            shopKey,
            itemKey)
        .stream()
        .findFirst();
  }

  /**
   * Decrement stock when finite. Throws {@link BusinessException} (status 409, code {@code
   * SHOP_OUT_OF_STOCK}) when the deduction would push stock below zero.
   */
  public void decrementStock(Long listingId, int qty) {
    if (qty <= 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "SHOP_QTY_INVALID", "Purchase qty must be positive");
    }
    int affected =
        jdbcTemplate.update(
            """
            UPDATE shop_listing
            SET stock = stock - ?
            WHERE id = ? AND stock IS NOT NULL AND stock >= ?
            """,
            qty,
            listingId,
            qty);
    if (affected == 0) {
      Integer stock =
          jdbcTemplate.queryForObject(
              "SELECT stock FROM shop_listing WHERE id = ?", Integer.class, listingId);
      if (stock != null && stock < qty) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "SHOP_OUT_OF_STOCK",
            "Only " + stock + " left in stock for listing " + listingId);
      }
    }
  }

  private ShopListing map(ResultSet rs, int rowNum) throws SQLException {
    return new ShopListing(
        rs.getLong("id"),
        rs.getString("shop_key"),
        rs.getString("item_key"),
        rs.getInt("price_coins"),
        rs.getInt("price_gems"),
        (Integer) rs.getObject("stock"),
        readIntArray(rs.getArray("available_days")));
  }

  private List<Integer> readIntArray(Array array) throws SQLException {
    if (array == null) {
      return null;
    }
    Object raw = array.getArray();
    if (raw instanceof Integer[] ints) {
      List<Integer> result = new ArrayList<>(ints.length);
      for (Integer i : ints) {
        result.add(i);
      }
      return result;
    }
    if (raw instanceof Object[] objects) {
      List<Integer> result = new ArrayList<>(objects.length);
      for (Object o : objects) {
        if (o instanceof Number n) {
          result.add(n.intValue());
        }
      }
      return result;
    }
    return List.of();
  }
}
