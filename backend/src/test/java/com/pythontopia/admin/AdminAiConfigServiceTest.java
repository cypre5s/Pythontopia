package com.pythontopia.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminAiConfigServiceTest {

  private final AdminAiConfigRepository repository = Mockito.mock(AdminAiConfigRepository.class);
  private final AdminAiConfigGraphClient graph = Mockito.mock(AdminAiConfigGraphClient.class);
  private final AdminAiConfigService service =
      new AdminAiConfigService(repository, graph, new AdminAiConfigEncryption("a sufficiently long master secret for tests"));
  private final CurrentUserPrincipal admin = new CurrentUserPrincipal(1L, "admin", Role.ADMIN);

  @Test
  void deniesNonAdminConfigurationAccess() {
    assertThatThrownBy(
            () -> service.get(new CurrentUserPrincipal(2L, "student", Role.STUDENT)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void returnsOnlyMaskedKeyStateAndNeverTheSecret() {
    String cipher = new AdminAiConfigEncryption("a sufficiently long master secret for tests").encrypt("deepseek-secret-key");
    when(repository.find()).thenReturn(Optional.of(new AdminAiConfigRepository.Stored("deepseek-flash", cipher, 1L, Instant.EPOCH)));
    when(graph.status()).thenReturn(new AdminAiConfigGraphClient.GraphStatus("DeepSeek", "deepseek-v4-pro", true));

    AdminAiConfigDto result = service.get(admin);

    assertThat(result.apiKeyConfigured()).isTrue();
    assertThat(result.keyStored()).isTrue();
    assertThat(result.model()).isEqualTo("deepseek-flash");
    assertThat(result.toString()).doesNotContain("deepseek-secret-key");
  }

  @Test
  void preservesSavedKeyWhenUpdateOmitsTheOptionalReplacement() {
    String cipher = new AdminAiConfigEncryption("a sufficiently long master secret for tests").encrypt("deepseek-secret-key");
    when(repository.find()).thenReturn(Optional.of(new AdminAiConfigRepository.Stored("deepseek-v4-pro", cipher, 1L, Instant.EPOCH)));
    when(graph.apply("deepseek-flash", "deepseek-secret-key"))
        .thenReturn(new AdminAiConfigGraphClient.GraphStatus("DeepSeek", "deepseek-flash", true));
    when(repository.save("deepseek-flash", cipher, 1L))
        .thenReturn(new AdminAiConfigRepository.Stored("deepseek-flash", cipher, 1L, Instant.EPOCH));

    service.update(admin, "deepseek-flash", null);

    verify(repository).save("deepseek-flash", cipher, 1L);
    verify(graph).apply("deepseek-flash", "deepseek-secret-key");
  }

  @Test
  void rejectsUnsupportedProviderModelsBeforeCallingGraph() {
    assertThatThrownBy(() -> service.update(admin, "gpt-4o", "new-secret-key"))
        .isInstanceOf(BusinessException.class);
    Mockito.verifyNoInteractions(graph);
  }
}
