package com.pythontopia.world.crop;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.crop.dto.CropTileDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world/crops")
public class CropController {

  private final CropStateService service;

  public CropController(CropStateService service) {
    this.service = service;
  }

  @GetMapping
  public List<CropTileDto> crops(@CurrentUser CurrentUserPrincipal principal) {
    return service.getMap(principal.userId());
  }
}
