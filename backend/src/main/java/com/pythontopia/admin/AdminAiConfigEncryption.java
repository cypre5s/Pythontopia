package com.pythontopia.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AdminAiConfigEncryption {
  private static final int NONCE_BYTES = 12;
  private final String masterSecret;
  private final SecureRandom random = new SecureRandom();

  public AdminAiConfigEncryption(String masterSecret) {
    this.masterSecret = masterSecret == null ? "" : masterSecret.trim();
  }

  public String encrypt(String plainText) {
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, nonce, plainText.getBytes(StandardCharsets.UTF_8));
    byte[] payload = Arrays.copyOf(nonce, nonce.length + encrypted.length);
    System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
    return Base64.getEncoder().encodeToString(payload);
  }

  public String decrypt(String ciphertext) {
    byte[] payload = Base64.getDecoder().decode(ciphertext);
    if (payload.length <= NONCE_BYTES) throw new IllegalStateException("Stored AI API key ciphertext is invalid");
    byte[] nonce = Arrays.copyOf(payload, NONCE_BYTES);
    byte[] encrypted = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
    return new String(crypt(Cipher.DECRYPT_MODE, nonce, encrypted), StandardCharsets.UTF_8);
  }

  private byte[] crypt(int mode, byte[] nonce, byte[] value) {
    if (masterSecret.length() < 32) throw new IllegalStateException("PYT_ADMIN_ENCRYPTION_KEY must contain at least 32 characters");
    try {
      byte[] key = MessageDigest.getInstance("SHA-256").digest(masterSecret.getBytes(StandardCharsets.UTF_8));
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
      return cipher.doFinal(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Could not encrypt stored AI API key", exception);
    }
  }
}
