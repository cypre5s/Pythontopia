package com.pythontopia.protocol;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/protocol")
public class ProtocolGatewayController {

  private final ProtocolGatewayService service;

  public ProtocolGatewayController(ProtocolGatewayService service) {
    this.service = service;
  }

  @GetMapping("/descriptor")
  public ProtocolGatewayDescriptorDto descriptor() {
    return service.descriptor();
  }
}
