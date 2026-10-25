package com.pythontopia.world.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.agent.run.RunStatus;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseChapter;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.world.curriculum.dto.MapperResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CurriculumMapperServiceTest {

  @Test
  void graphMappingDoesNotHoldDatabaseTransaction() {
    ProfileRepository profiles = mock(ProfileRepository.class);
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    CurriculumMapperClient client = mock(CurriculumMapperClient.class);
    ResourceRunRecorder recorder = mock(ResourceRunRecorder.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    PlatformTransactionManager transactionManager = new TestTransactionManager();
    CurriculumMapperService target =
        new CurriculumMapperService(
            profiles, coursePack, client, recorder, eventPublisher, transactionManager);
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            transactionManager, new AnnotationTransactionAttributeSource()));
    CurriculumMapperService service = (CurriculumMapperService) proxyFactory.getProxy();
    when(profiles.findProfile(7L))
        .thenReturn(
            Optional.of(
                new LearnerProfileDto(
                    1L, 7L, 43L, null, null, null, null, null, null, null, null, null)));
    when(coursePack.findChapters(43L))
        .thenReturn(List.of(new CourseChapter(179L, 43L, 1, "第一章", "")));
    when(coursePack.findKcs(43L, null))
        .thenReturn(
            List.of(
                new CourseKc(
                    1809L, 43L, "PY43-KC-001", "计算思维", 179L, 1, "basic", "课程知识点")));
    AtomicBoolean transactionDuringGraphCall = new AtomicBoolean(true);
    AtomicBoolean transactionDuringEventPublish = new AtomicBoolean(false);
    AtomicBoolean transactionDuringRunRecord = new AtomicBoolean(false);
    when(client.callMap(any()))
        .thenAnswer(
            invocation -> {
              transactionDuringGraphCall.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return new MapperResponse(List.of(), List.of(), List.of("PY43-KC-001"), "sig");
            });
    org.mockito.Mockito.doAnswer(
            invocation -> {
              transactionDuringEventPublish.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return null;
            })
        .when(eventPublisher)
        .publishEvent(any(LearningEvent.class));
    org.mockito.Mockito.doAnswer(
            invocation -> {
              transactionDuringRunRecord.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return null;
            })
        .when(recorder)
        .record(any(), any(), any(), any(), any(), any(), any(), any(), any());

    service.bootstrap(7L);

    assertThat(transactionDuringGraphCall).isFalse();
    assertThat(transactionDuringEventPublish).isTrue();
    assertThat(transactionDuringRunRecord).isTrue();
    verify(eventPublisher).publishEvent(any(LearningEvent.class));
    verify(recorder)
        .record(
            eq(7L),
            eq("CurriculumMapperAgent"),
            eq("CURRICULUM_BOOTSTRAP"),
            any(),
            any(),
            eq(List.of()),
            any(),
            any(),
            eq(RunStatus.SUCCESS));
  }

  @Test
  void failedGraphMappingStoresOnlyFixedErrorInRunArtifact() {
    ProfileRepository profiles = mock(ProfileRepository.class);
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    CurriculumMapperClient client = mock(CurriculumMapperClient.class);
    ResourceRunRecorder recorder = mock(ResourceRunRecorder.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    PlatformTransactionManager transactionManager = new TestTransactionManager();
    CurriculumMapperService target =
        new CurriculumMapperService(
            profiles, coursePack, client, recorder, eventPublisher, transactionManager);
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            transactionManager, new AnnotationTransactionAttributeSource()));
    CurriculumMapperService service = (CurriculumMapperService) proxyFactory.getProxy();
    String sentinel = "private-curriculum-response-sentinel";

    when(profiles.findProfile(7L))
        .thenReturn(
            Optional.of(
                new LearnerProfileDto(
                    1L, 7L, 43L, null, null, null, null, null, null, null, null, null)));
    when(coursePack.findChapters(43L))
        .thenReturn(List.of(new CourseChapter(179L, 43L, 1, "第一章", "")));
    when(coursePack.findKcs(43L, null))
        .thenReturn(
            List.of(
                new CourseKc(
                    1809L, 43L, "PY43-KC-001", "计算思维", 179L, 1, "basic", "课程知识点")));
    AtomicBoolean transactionDuringGraphCall = new AtomicBoolean(true);
    AtomicBoolean transactionDuringRunRecord = new AtomicBoolean(false);
    when(client.callMap(any()))
        .thenAnswer(
            invocation -> {
              transactionDuringGraphCall.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              throw new IllegalStateException(sentinel);
            });
    org.mockito.Mockito.doAnswer(
            invocation -> {
              transactionDuringRunRecord.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return null;
            })
        .when(recorder)
        .record(any(), any(), any(), any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> service.bootstrap(7L)).isInstanceOf(BusinessException.class);
    assertThat(transactionDuringGraphCall).isFalse();
    assertThat(transactionDuringRunRecord).isTrue();

    ArgumentCaptor<Object> artifact = ArgumentCaptor.forClass(Object.class);
    verify(recorder)
        .record(
            eq(7L),
            eq("CurriculumMapperAgent"),
            eq("CURRICULUM_BOOTSTRAP"),
            any(),
            artifact.capture(),
            eq(List.of()),
            any(),
            any(),
            eq(RunStatus.FAILED));
    assertThat(artifact.getValue()).isEqualTo(Map.of("error", "graph-service-error"));
    verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(LearningEvent.class));
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
