package com.pythontopia.common.error;

import com.pythontopia.coursepack.service.CoursePackNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<String> details =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details, request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiError> handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    List<String> details =
        exception.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .toList();
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details, request);
  }

  @ExceptionHandler(CoursePackNotFoundException.class)
  ResponseEntity<ApiError> handleCoursePackNotFound(
      CoursePackNotFoundException exception, HttpServletRequest request) {
    return error(
        HttpStatus.NOT_FOUND,
        "COURSE_PACK_NOT_FOUND",
        exception.getMessage(),
        List.of(),
        request);
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiError> handleBusiness(BusinessException exception, HttpServletRequest request) {
    return error(exception.status(), exception.code(), exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ApiError> handleAuthentication(
      AuthenticationException exception, HttpServletRequest request) {
    return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required", List.of(), request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> handleAccessDenied(
      AccessDeniedException exception, HttpServletRequest request) {
    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", List.of(), request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handleException(Exception exception, HttpServletRequest request) {
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Unexpected server error",
        List.of(exception.getClass().getSimpleName()),
        request);
  }

  private ResponseEntity<ApiError> error(
      HttpStatus status,
      String code,
      String message,
      List<String> details,
      HttpServletRequest request) {
    String traceId = (String) request.getAttribute("traceId");
    if (traceId == null || traceId.isBlank()) {
      traceId = request.getRequestId();
    }
    return ResponseEntity.status(status).body(ApiError.of(code, message, details, traceId));
  }
}
