package com.pythontopia.world.curriculum;

import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.agent.run.RunStatus;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseChapter;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.world.curriculum.dto.CurriculumBootstrapResult;
import com.pythontopia.world.curriculum.dto.MapperChapterDto;
import com.pythontopia.world.curriculum.dto.MapperKcDto;
import com.pythontopia.world.curriculum.dto.MapperProfileSummaryDto;
import com.pythontopia.world.curriculum.dto.MapperRequest;
import com.pythontopia.world.curriculum.dto.MapperResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurriculumMapperService {

  static final int DEFAULT_PACK_ID = 43;
  static final int DEFAULT_ROW_WIDTH = 5;
  static final int DEFAULT_FIELD_ORIGIN_X = 2;
  static final int DEFAULT_FIELD_ORIGIN_Y = 2;

  private final ProfileRepository profileRepository;
  private final CoursePackRepository coursePackRepository;
  private final CurriculumMapperClient client;
  private final ResourceRunRecorder recorder;
  private final ApplicationEventPublisher eventPublisher;

  public CurriculumMapperService(
      ProfileRepository profileRepository,
      CoursePackRepository coursePackRepository,
      CurriculumMapperClient client,
      ResourceRunRecorder recorder,
      ApplicationEventPublisher eventPublisher) {
    this.profileRepository = profileRepository;
    this.coursePackRepository = coursePackRepository;
    this.client = client;
    this.recorder = recorder;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public CurriculumBootstrapResult bootstrap(Long userId) {
    LearnerProfileDto profile =
        profileRepository
            .findProfile(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "PROFILE_REQUIRED",
                        "请先在画像屋完成画像访谈后再开垦知识田。"));

    List<CourseChapter> chapters =
        coursePackRepository.findChapters((long) DEFAULT_PACK_ID);
    List<CourseKc> kcs = coursePackRepository.findKcs((long) DEFAULT_PACK_ID, null);
    if (kcs.isEmpty()) {
      throw new BusinessException(
          HttpStatus.PRECONDITION_FAILED,
          "PACK_EMPTY",
          "课程包 43 未导入任何 KC，无法初始化知识田。");
    }

    MapperRequest request =
        new MapperRequest(
            userId,
            new MapperProfileSummaryDto(
                profile.major() == null ? "OTHER" : profile.major(),
                profile.baseLevel() == null ? 1 : profile.baseLevel().pythonSelfRatedLevel(),
                profile.learningGoal() == null
                    ? List.of()
                    : profile.learningGoal().targetKcCodes(),
                profile.learningPreference() == null
                    ? List.of()
                    : profile.learningPreference().mediaPriority()),
            chapters.stream()
                .map(
                    chapter ->
                        new MapperChapterDto(chapter.id(), chapter.orderIndex(), chapter.title()))
                .toList(),
            kcs.stream()
                .map(
                    kc ->
                        new MapperKcDto(
                            kc.kcCode(),
                            kc.kcName(),
                            kc.chapterId(),
                            kc.chapterOrder(),
                            mapDifficulty(kc.difficulty())))
                .toList(),
            DEFAULT_ROW_WIDTH,
            DEFAULT_FIELD_ORIGIN_X,
            DEFAULT_FIELD_ORIGIN_Y);

    MapperResponse response;
    try {
      response = client.callMap(request);
    } catch (Exception exception) {
      recorder.record(
          userId,
          "CurriculumMapperAgent",
          "CURRICULUM_BOOTSTRAP",
          Map.of("packId", DEFAULT_PACK_ID, "kcCount", kcs.size()),
          Map.of("error", "graph-service-error"),
          List.of(),
          Map.of("passed", true),
          Map.of("passed", false, "reasons", List.of("graph-service-error")),
          RunStatus.FAILED);
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "CURRICULUM_MAPPER_UNAVAILABLE",
          "知识田映射服务暂不可用，请稍后再试。");
    }
    if (response == null
        || response.tileAssignments() == null
        || response.unlockedKcs() == null) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "CURRICULUM_MAPPER_EMPTY",
          "知识田映射服务返回空数据。");
    }

    eventPublisher.publishEvent(
        new LearningEvent.ProfileBootstrapped(userId, response.unlockedKcs()));

    recorder.record(
        userId,
        "CurriculumMapperAgent",
        "CURRICULUM_BOOTSTRAP",
        Map.of("packId", DEFAULT_PACK_ID, "kcCount", kcs.size()),
        Map.of(
            "unlockedCount",
            response.unlockedKcs().size(),
            "signature",
            response.deterministicSignature()),
        List.of(),
        Map.of("passed", true),
        Map.of("passed", true),
        RunStatus.SUCCESS);

    return new CurriculumBootstrapResult(
        kcs.size(), response.unlockedKcs().size(), response.deterministicSignature());
  }

  static int mapDifficulty(String raw) {
    if (raw == null) {
      return 1;
    }
    String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
    return switch (normalized) {
      case "easy", "low", "basic", "beginner" -> 1;
      case "medium", "intermediate" -> 2;
      case "hard", "high", "advanced" -> 3;
      default -> {
        try {
          int parsed = Integer.parseInt(normalized);
          yield Math.max(1, Math.min(5, parsed));
        } catch (NumberFormatException ignored) {
          yield 2;
        }
      }
    };
  }
}
