package com.pythontopia.tutor;

/** 从会话私有附件表读取的一张图片；仅在当前请求内使用。 */
record TutorWorkflowStoredImage(String contentType, byte[] content) {}
