package com.pythontopia.auth.controller;

import com.pythontopia.auth.dto.LoginRequest;
import com.pythontopia.auth.dto.LoginResponse;
import com.pythontopia.auth.dto.RefreshRequest;
import com.pythontopia.auth.dto.RegisterRequest;
import com.pythontopia.auth.dto.UserDto;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.service.AuthService;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
  }

  @PostMapping("/login")
  LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/refresh")
  LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return authService.refresh(request.token());
  }

  @PostMapping("/logout")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(@CurrentUser CurrentUserPrincipal user) {
    authService.logout(user);
  }
}
