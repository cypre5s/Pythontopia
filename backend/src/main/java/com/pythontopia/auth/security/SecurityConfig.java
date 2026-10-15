package com.pythontopia.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
  JwtProperties.class,
  TeacherInviteProperties.class,
  GraphServiceProperties.class
})
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtFilter,
      ObjectMapper objectMapper,
      CorsConfigurationSource corsConfigurationSource,
      @Value("${pythontopia.security.expose-api-docs:true}") boolean exposeApiDocs)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests -> {
              // Internal handlers verify the shared service token themselves.
              requests
                  .requestMatchers("/api/internal/**")
                  .permitAll();
              requests
                  .requestMatchers(
                      HttpMethod.POST,
                      "/api/auth/register",
                      "/api/auth/login",
                      "/api/auth/refresh",
                      "/api/downloads/*/hit")
                  .permitAll();
              // Public course reads; the asset controller checks media signatures or ownership.
              requests
                  .requestMatchers(
                      HttpMethod.GET,
                      "/api/course/**",
                      "/api/media/assets/**",
                      "/api/downloads/**",
                      "/healthz",
                      "/readyz")
                  .permitAll();
              requests
                  .requestMatchers(
                      HttpMethod.HEAD,
                      "/api/course/**",
                      "/api/media/assets/**",
                      "/api/downloads/**")
                  .permitAll();
              // OpenAPI/Swagger 默认开放（dev 联调方便）；生产设 PYT_EXPOSE_API_DOCS=false 后
              // 落到 anyRequest().authenticated()，需有效 JWT 才能访问，避免公开泄露完整 API 面。
              if (exposeApiDocs) {
                requests
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              }
              requests.anyRequest().authenticated();
            })
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Authentication is required"))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN",
                                "Access denied")))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value(
              "${pythontopia.security.cors.allowed-origins:"
                  + "http://localhost:8080,http://127.0.0.1:8080,"
                  + "http://localhost:5173,http://localhost:5174,"
                  + "http://127.0.0.1:5173,http://127.0.0.1:5174}")
          List<String> allowedOrigins) {
    if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
      throw new IllegalArgumentException("CORS wildcard origins cannot be used with credentials");
    }
    CorsConfiguration cfg = new CorsConfiguration();
    // 允许来源外置：默认本地 dev 入口；生产用 PYT_CORS_ALLOWED_ORIGINS 配置精确来源。
    cfg.setAllowedOrigins(allowedOrigins);
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setExposedHeaders(List.of("Authorization"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", cfg);
    return src;
  }

  private void writeError(
      HttpServletResponse response,
      ObjectMapper objectMapper,
      int status,
      String code,
      String message)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), ApiError.of(code, message, List.of(), ""));
  }
}
