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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CurriculumMapperServiceTest {

  @Test
  void failedGraphMappingStoresOnlyFixedErrorInRunArtifact() {
    ProfileRepository profiles = mock(ProfileRepository.class);
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    CurriculumMapperClient client = mock(CurriculumMapperClient.class);
    ResourceRunRecorder recorder = mock(ResourceRunRecorder.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    CurriculumMapperService service =
        new CurriculumMapperService(profiles, coursePack, client, recorder, eventPublisher);
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
    when(client.callMap(any())).thenThrow(new IllegalStateException(sentinel));

    assertThatThrownBy(() -> service.bootstrap(7L)).isInstanceOf(BusinessException.class);

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
}
