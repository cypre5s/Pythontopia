package com.pythontopia.rag;

import com.pythontopia.common.error.BusinessException;
import org.springframework.http.HttpStatus;

public class RagUnavailableException extends BusinessException {

  public RagUnavailableException(String message, Throwable cause) {
    super(HttpStatus.BAD_GATEWAY, "RAG_UNAVAILABLE", message);
    initCause(cause);
  }
}
