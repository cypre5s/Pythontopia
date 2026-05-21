package com.pythontopia.coursepack.controller;

import com.pythontopia.coursepack.domain.CourseChapter;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.domain.CoursePack;
import com.pythontopia.coursepack.domain.CoursePage;
import com.pythontopia.coursepack.domain.CourseProblem;
import com.pythontopia.coursepack.dto.ChapterDto;
import com.pythontopia.coursepack.dto.CoursePackDto;
import com.pythontopia.coursepack.dto.KcDto;
import com.pythontopia.coursepack.dto.PageDto;
import com.pythontopia.coursepack.dto.ProblemDto;
import com.pythontopia.coursepack.service.CoursePackService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/course")
public class CoursePackController {

  private final CoursePackService service;

  public CoursePackController(CoursePackService service) {
    this.service = service;
  }

  @GetMapping("/pack")
  public CoursePackDto getPack() {
    CoursePack pack = service.getActivePack();
    return new CoursePackDto(
        pack.id(),
        pack.name(),
        pack.primaryLanguage(),
        pack.documentCount(),
        pack.pageCount(),
        pack.chapterCount(),
        pack.kcCount(),
        pack.exampleCount(),
        pack.problemCount());
  }

  @GetMapping("/chapters")
  public List<ChapterDto> listChapters() {
    return service.listChapters().stream().map(this::toDto).toList();
  }

  @GetMapping("/kcs")
  public List<KcDto> listKcs(@RequestParam(required = false) @Positive Long chapterId) {
    return service.listKcsByChapter(chapterId).stream().map(this::toDto).toList();
  }

  @GetMapping("/problems/{id}")
  public ProblemDto getProblem(@PathVariable @Positive Long id) {
    return toDto(service.getProblem(id));
  }

  @GetMapping("/pages")
  public List<PageDto> listPages(
      @RequestParam @Pattern(regexp = "^PY43-KC-[0-9]{3}$") String kcCode) {
    return service.listPagesByKc(kcCode).stream().map(this::toDto).toList();
  }

  private ChapterDto toDto(CourseChapter chapter) {
    return new ChapterDto(
        chapter.id(), chapter.orderIndex(), chapter.title(), chapter.summaryMarkdown());
  }

  private KcDto toDto(CourseKc kc) {
    return new KcDto(
        kc.id(),
        kc.kcCode(),
        kc.kcName(),
        kc.chapterId(),
        kc.chapterOrder(),
        kc.difficulty(),
        kc.summaryMarkdown());
  }

  private ProblemDto toDto(CourseProblem problem) {
    return new ProblemDto(
        problem.id(),
        problem.kcCode(),
        problem.title(),
        problem.statementMarkdown(),
        problem.inputDescription(),
        problem.outputDescription(),
        problem.difficulty(),
        problem.sourceLabel());
  }

  private PageDto toDto(CoursePage page) {
    return new PageDto(
        page.id(),
        page.documentId(),
        page.chapterId(),
        page.pageNo(),
        page.title(),
        page.contentMarkdown(),
        page.excerpt());
  }
}
