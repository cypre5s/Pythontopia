package com.pythontopia.coursepack.controller;

import com.pythontopia.coursepack.dto.CourseExampleDto;
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
@RequestMapping("/api/course/examples")
public class CourseExampleController {

  private final CoursePackService service;

  public CourseExampleController(CoursePackService service) {
    this.service = service;
  }

  @GetMapping
  public List<CourseExampleDto> list(
      @RequestParam(required = false) Long packId,
      @RequestParam(required = false) @Pattern(regexp = "^PY43-KC-[0-9]{3}$") String kcCode) {
    return service.listExamples(packId, kcCode).stream().map(CourseExampleDto::from).toList();
  }

  @GetMapping("/{exampleId}")
  public CourseExampleDto detail(
      @PathVariable @Positive Long exampleId,
      @RequestParam(required = false) Long packId) {
    return CourseExampleDto.from(service.getExample(packId, exampleId));
  }
}
