package com.pythontopia.world.curriculum.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MapperRequest(
    @JsonProperty("user_id") long userId,
    @JsonProperty("profile_summary") MapperProfileSummaryDto profileSummary,
    List<MapperChapterDto> chapters,
    List<MapperKcDto> kcs,
    @JsonProperty("row_width") int rowWidth,
    @JsonProperty("field_origin_x") int fieldOriginX,
    @JsonProperty("field_origin_y") int fieldOriginY) {

  public MapperRequest {
    if (rowWidth <= 0) {
      rowWidth = 5;
    }
  }
}
