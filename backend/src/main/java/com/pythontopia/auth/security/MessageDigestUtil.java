package com.pythontopia.auth.security;

import java.security.MessageDigest;

final class MessageDigestUtil {

  private MessageDigestUtil() {}

  static boolean constantTimeEquals(byte[] left, byte[] right) {
    return MessageDigest.isEqual(left, right);
  }
}
