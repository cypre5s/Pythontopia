package com.pythontopia.rag;

import com.pythontopia.rag.dto.RagHitDto;
import com.pythontopia.rag.dto.RagQueryRequestDto;
import com.pythontopia.rag.dto.RagQueryResponseDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RagFacade {

  private final RagClient client;

  public RagFacade(RagClient client) {
    this.client = client;
  }

  public RagQueryResponseDto searchByKcCodes(String query, List<String> kcCodes, int topK) {
    return client.query(new RagQueryRequestDto(query, topK, kcCodes, null, 0.3));
  }

  public RagQueryResponseDto searchByChapter(String query, Long chapterId, int topK) {
    List<Long> chapters = chapterId == null ? null : List.of(chapterId);
    return client.query(new RagQueryRequestDto(query, topK, null, chapters, 0.3));
  }

  public RagQueryResponseDto searchOpen(String query, int topK) {
    return client.query(new RagQueryRequestDto(query, topK, null, null, 0.3));
  }

  public List<RagHitDto> hitsOnly(RagQueryResponseDto response) {
    return response == null ? List.of() : response.hits();
  }
}
