package com.pythontopia.rag;

import com.pythontopia.rag.dto.RagHitDto;
import com.pythontopia.rag.dto.RagQueryRequestDto;
import com.pythontopia.rag.dto.RagQueryResponseDto;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 课件证据检索门面：封装 {@link RagClient}，按知识点、章节或全开放三种方式检索课件证据，
 * 统一使用 0.3 相关度阈值，供导师问答、工坊生成等需要 grounding 的服务复用。
 */
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
