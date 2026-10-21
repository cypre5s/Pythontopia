package com.pythontopia.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AdminAiConfigEncryptionTest {

  @Test
  void encryptsAndDecryptsWithRandomizedAuthenticatedCiphertext() {
    AdminAiConfigEncryption encryption = new AdminAiConfigEncryption("a sufficiently long master secret for tests");

    String first = encryption.encrypt("deepseek-api-key");
    String second = encryption.encrypt("deepseek-api-key");

    assertThat(first).isNotEqualTo(second);
    assertThat(encryption.decrypt(first)).isEqualTo("deepseek-api-key");
  }

  @Test
  void refusesToEncryptOrDecryptWithoutMasterSecret() {
    AdminAiConfigEncryption encryption = new AdminAiConfigEncryption("");

    assertThatThrownBy(() -> encryption.encrypt("key"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PYT_ADMIN_ENCRYPTION_KEY");
  }
}
