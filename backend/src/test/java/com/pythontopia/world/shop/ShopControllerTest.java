package com.pythontopia.world.shop;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.world.currency.dto.PlayerCurrencyDto;
import com.pythontopia.world.inventory.dto.InventoryItemDto;
import com.pythontopia.world.shop.dto.BuyRequest;
import com.pythontopia.world.shop.dto.BuyResponse;
import com.pythontopia.world.shop.dto.ShopListingDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ShopController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class ShopControllerTest {

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @MockBean ShopService service;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

  @BeforeEach
  void setUp() {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(11L, "stu-shop", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
  }

  @Test
  void listingFiltersByWeekday() throws Exception {
    when(service.listing(eq(11L), eq("merchant_port")))
        .thenReturn(
            List.of(
                new ShopListingDto("merchant_port", "python_book", 100, 0, 5, null),
                new ShopListingDto("merchant_port", "coffee", 30, 0, null, null)));

    mockMvc
        .perform(get("/api/world/shops/merchant_port"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].itemKey").value("python_book"));
  }

  @Test
  void buySucceedsAndReturnsItemAndCurrency() throws Exception {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-22T08:00:00Z");
    when(service.buy(eq(11L), eq("merchant_port"), any(BuyRequest.class)))
        .thenReturn(
            new BuyResponse(
                new InventoryItemDto("python_book", 1, now),
                new PlayerCurrencyDto(0, 0, now)));
    String body =
        objectMapper.writeValueAsString(Map.of("itemKey", "python_book", "qty", 1));

    mockMvc
        .perform(
            post("/api/world/shops/merchant_port/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.item.itemKey").value("python_book"))
        .andExpect(jsonPath("$.currency.coins").value(0));
  }

  @Test
  void buyRejectsWhenCoinsInsufficient() throws Exception {
    when(service.buy(eq(11L), eq("merchant_port"), any(BuyRequest.class)))
        .thenThrow(
            new BusinessException(
                HttpStatus.CONFLICT, "CURRENCY_NOT_ENOUGH", "Insufficient coins"));
    String body =
        objectMapper.writeValueAsString(Map.of("itemKey", "python_book", "qty", 1));

    mockMvc
        .perform(
            post("/api/world/shops/merchant_port/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CURRENCY_NOT_ENOUGH"));
  }

  @Test
  void buyRejectsInvalidQty() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("itemKey", "python_book", "qty", 0));

    mockMvc
        .perform(
            post("/api/world/shops/merchant_port/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
