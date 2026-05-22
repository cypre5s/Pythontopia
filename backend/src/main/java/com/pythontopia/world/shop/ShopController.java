package com.pythontopia.world.shop;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.shop.dto.BuyRequest;
import com.pythontopia.world.shop.dto.BuyResponse;
import com.pythontopia.world.shop.dto.ShopListingDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world/shops")
public class ShopController {

  private final ShopService service;

  public ShopController(ShopService service) {
    this.service = service;
  }

  @GetMapping("/{shopKey}")
  public List<ShopListingDto> listing(
      @CurrentUser CurrentUserPrincipal user, @PathVariable String shopKey) {
    return service.listing(user.userId(), shopKey);
  }

  @PostMapping("/{shopKey}/buy")
  public BuyResponse buy(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable String shopKey,
      @Valid @RequestBody BuyRequest request) {
    return service.buy(user.userId(), shopKey, request);
  }
}
