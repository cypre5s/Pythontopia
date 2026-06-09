package com.pythontopia.common.error;

import com.pythontopia.coursepack.service.CoursePackNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

  // 客户端用错 HTTP 方法时返回 405 而非 500。
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
    return error(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "HTTP method " + exception.getMethod() + " not supported on this endpoint",
        List.of(),
        request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiError> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
    return error(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "Request media type not supported",
        List.of(),
        request);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  ResponseEntity<ApiError> handleNoHandler(
      NoHandlerFoundException exception, HttpServletRequest request) {
    return error(
        HttpStatus.NOT_FOUND,
        "NOT_FOUND",
        "Endpoint " + exception.getRequestURL() + " not found",
        List.of(),
        request);
  }

  // Spring Boot 3.2+ 默认由 ResourceHttpRequestHandler 处理无映射的 /api/** 路径，
  // 抛 NoResourceFoundException（而非 NoHandlerFoundException）。若被通用 Exception 兜底
  // 会错误返回 500，掩盖“路径不存在”的真实语义，前端也无法据此识别 404。这里显式映射 404。
  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> handleNoResource(
      NoResourceFoundException exception, HttpServletRequest request) {
    return error(
        HttpStatus.NOT_FOUND,
        "NOT_FOUND",
        "Endpoint " + request.getRequestURI() + " not found",
        List.of(),
        request);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
    return error(
        HttpStatus.BAD_REQUEST,
        "BAD_REQUEST",
        "Request payload is malformed",
        List.of(exception.getMessage() == null ? "" : exception.getMessage()),
        request);
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
