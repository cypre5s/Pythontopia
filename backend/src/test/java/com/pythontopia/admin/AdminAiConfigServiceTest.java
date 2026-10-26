package com.pythontopia.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AdminAiConfigServiceTest {

  private final AdminAiConfigRepository repository = Mockito.mock(AdminAiConfigRepository.class);
  private final AdminAiConfigGraphClient graph = Mockito.mock(AdminAiConfigGraphClient.class);
  private final AdminAiConfigService service =
      new AdminAiConfigService(repository, graph, new AdminAiConfigEncryption("a sufficiently long master secret for tests"));
  private final CurrentUserPrincipal admin = new CurrentUserPrincipal(1L, "admin", Role.ADMIN);

  @Test
  void configUpdateDoesNotHoldDatabaseTransactionDuringGraphApply() {
    AtomicBoolean transactionDuringGraphApply = new AtomicBoolean(true);
    when(repository.find()).thenReturn(Optional.empty());
    when(graph.apply("deepseek-flash", "key"))
        .thenAnswer(
            invocation -> {
              transactionDuringGraphApply.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return new AdminAiConfigGraphClient.GraphStatus("DeepSeek", "deepseek-flash", true);
            });
    when(repository.save(eq("deepseek-flash"), anyString(), eq(1L)))
        .thenReturn(
            new AdminAiConfigRepository.Stored("deepseek-flash", "cipher", 1L, Instant.EPOCH));

    PlatformTransactionManager transactionManager = new TestTransactionManager();
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            transactionManager, new AnnotationTransactionAttributeSource()));
    AdminAiConfigService transactionalService = (AdminAiConfigService) proxyFactory.getProxy();
    transactionalService.update(admin, "deepseek-flash", "key");

    assertThat(transactionDuringGraphApply).isFalse();
  }

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

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(
        Object transaction, org.springframework.transaction.TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
