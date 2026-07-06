package com.pythontopia.coursepack.service;

public class CoursePackNotFoundException extends RuntimeException {

  public CoursePackNotFoundException(Long packId) {
    super("Course pack not found: " + packId);
  }
}
