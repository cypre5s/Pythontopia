package com.pythontopia.coursepack.service;

import com.pythontopia.coursepack.domain.CourseChapter;
import com.pythontopia.coursepack.domain.CourseExample;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.domain.CoursePack;
import com.pythontopia.coursepack.domain.CoursePage;
import com.pythontopia.coursepack.domain.CourseProblem;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CoursePackService {

  public static final long ACTIVE_PACK_ID = 43L;

  private final CoursePackRepository repository;

  public CoursePackService(CoursePackRepository repository) {
    this.repository = repository;
  }

  public CoursePack getActivePack() {
    return repository
        .findPack(ACTIVE_PACK_ID)
        .orElseThrow(() -> new CoursePackNotFoundException(ACTIVE_PACK_ID));
  }

  public List<CourseChapter> listChapters() {
    return repository.findChapters(ACTIVE_PACK_ID);
  }

  public List<CourseKc> listKcsByChapter(Long chapterId) {
    return repository.findKcs(ACTIVE_PACK_ID, chapterId);
  }

  public CourseProblem getProblem(Long problemId) {
    return repository
        .findProblem(ACTIVE_PACK_ID, problemId)
        .orElseThrow(() -> new CoursePackNotFoundException(ACTIVE_PACK_ID));
  }

  public List<CourseProblem> listProblems(String kcCode) {
    return repository.findProblems(ACTIVE_PACK_ID, kcCode);
  }

  public List<CoursePage> listPagesByKc(String kcCode) {
    return repository.findPagesByKc(ACTIVE_PACK_ID, kcCode);
  }

  public List<CourseExample> listExamples(Long packId, String kcCode) {
    long pack = packId == null ? ACTIVE_PACK_ID : packId;
    return repository.findExamples(pack, kcCode);
  }

  public CourseExample getExample(Long packId, Long exampleId) {
    long pack = packId == null ? ACTIVE_PACK_ID : packId;
    return repository
        .findExample(pack, exampleId)
        .orElseThrow(() -> new CoursePackNotFoundException(pack));
  }
}
