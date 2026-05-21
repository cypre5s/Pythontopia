package com.pythontopia.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
      HttpSecurity http, JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        "/api/auth/**",
                        "/api/course/**",
                        "/healthz",
                        "/v3/api-docs/**",
                        "/swagger-ui/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
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
