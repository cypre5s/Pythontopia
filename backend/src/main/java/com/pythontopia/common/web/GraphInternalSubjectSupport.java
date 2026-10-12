package com.pythontopia.common.web;

/** 后端认证完成后，向 stateful Graph 路由传递可信用户主体。 */
public final class GraphInternalSubjectSupport {

  public static final String HEADER = "X-Internal-Subject";

  private GraphInternalSubjectSupport() {}

  public static String user(Long userId) {
    if (userId == null || userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return "user:" + userId;
  }
}
