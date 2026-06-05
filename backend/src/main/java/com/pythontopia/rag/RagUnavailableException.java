package com.pythontopia.rag;

import com.pythontopia.common.error.BusinessException;
import org.springframework.http.HttpStatus;

/** RAG 课件检索服务不可达时抛出，映射为 HTTP 502 BAD_GATEWAY（错误码 {@code RAG_UNAVAILABLE}）。 */
public class RagUnavailableException extends BusinessException {

  public RagUnavailableException(String message, Throwable cause) {
    super(HttpStatus.BAD_GATEWAY, "RAG_UNAVAILABLE", message);
    initCause(cause);
  }
}
