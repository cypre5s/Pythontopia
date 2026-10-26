package com.pythontopia.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.npc.dto.NpcDialogResponse;
import com.pythontopia.npc.graph.NpcDialogGraphClient;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class NpcDialogServiceTest {

  private final NpcMemoryService memoryService = mock(NpcMemoryService.class);
  private final NpcPersonaService personaService = mock(NpcPersonaService.class);
  private final NpcAffinityService affinityService = mock(NpcAffinityService.class);
  private final NpcDialogGraphClient graphClient = mock(NpcDialogGraphClient.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PlatformTransactionManager transactionManager = new TestTransactionManager();
  private NpcDialogService service;

  @BeforeEach
  void createTransactionalProxy() {
    NpcDialogService target =
        new NpcDialogService(
            memoryService,
            personaService,
            affinityService,
            graphClient,
            jdbc,
            new ObjectMapper(),
            transactionManager);
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            transactionManager, new AnnotationTransactionAttributeSource()));
    service = (NpcDialogService) proxyFactory.getProxy();
  }

  @Test
  void graphWaitDoesNotHoldDatabaseTransaction() {
    when(memoryService.recall(7L, "coach_q", null, 5)).thenReturn(List.of());
    when(personaService.load("coach_q")).thenReturn(new NpcPersona("coach_q", "教务 Q", "core"));
    AtomicBoolean transactionDuringGraphCall = new AtomicBoolean(true);
    when(graphClient.talk(eq("coach_q"), eq("core"), any(), eq("你好")))
        .thenAnswer(
            invocation -> {
              transactionDuringGraphCall.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return Optional.of(new NpcDialogResponse("你好", List.of(), true));
            });

    service.talk(7L, "coach_q", "你好");

    assertThat(transactionDuringGraphCall).isFalse();
  }

  @Test
  void talkRecallsMemoryCallsGraphPersistsStateAndBumpsAffinity() {
    MemoryItemDto memory =
        new MemoryItemDto(
            9L,
            "practice_failed",
            "练习失败：PY43-KC-016",
            List.of("PY43-KC-016"),
            "2026-06-09T00:00:00Z",
            4,
            1.0,
            0.8,
            0.9,
            0.91);
    when(memoryService.recall(7L, "coach_q", null, 5)).thenReturn(List.of(memory));
    when(personaService.load("coach_q")).thenReturn(new NpcPersona("coach_q", "教务 Q", "core"));
    when(graphClient.talk(eq("coach_q"), eq("core"), any(), eq("我又错了")))
        .thenReturn(Optional.of(new NpcDialogResponse("记得你卡过 KC-016", List.of(9L), true)));

    NpcDialogResponse response = service.talk(7L, "coach_q", "我又错了");

    assertThat(response.replyMd()).contains("KC-016");
    assertThat(response.citedEventIds()).containsExactly(9L);
    assertThat(response.grounded()).isTrue();
    verify(jdbc).update(anyString(), eq(7L), eq("coach_q"), eq("[9]"), eq(true));
    verify(affinityService).bump(7L, "coach_q", 0.05);
  }

  @Test
  void graphUnavailableFallsBackToStaticScript() {
    when(memoryService.recall(7L, "coach_q", null, 5)).thenReturn(List.of());
    when(personaService.load("coach_q")).thenReturn(new NpcPersona("coach_q", "教务 Q", "core"));
    when(graphClient.talk(eq("coach_q"), eq("core"), any(), eq("你好"))).thenReturn(Optional.empty());

    NpcDialogResponse response = service.talk(7L, "coach_q", "你好");

    assertThat(response.replyMd()).isNotBlank();
    assertThat(response.grounded()).isFalse();
    assertThat(response.citedEventIds()).isEmpty();
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
