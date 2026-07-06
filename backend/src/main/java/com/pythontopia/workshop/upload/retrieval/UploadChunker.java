package com.pythontopia.workshop.upload.retrieval;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 把上传文件的 {@code extracted_text} 切成带重叠的字符窗口块，供逐块向量化与检索。
 *
 * <p>固定字符窗口 + 重叠：实现简单、确定性强（便于单测），对代码/纯文本/Markdown 均稳健，不依赖语言分词。
 * 上限 {@link #MAX_CHUNKS} 防止超大文件（≤5MB 可达数百万字）切出海量块拖垮 embedding 批量与库。
 */
@Component
public class UploadChunker {

  /** 单块目标字符数。 */
  static final int CHUNK_SIZE_CHARS = 800;

  /** 相邻块重叠字符数（保留跨块上下文，提升检索召回）。 */
  static final int CHUNK_OVERLAP_CHARS = 120;

  /** 单次上传最多切出的块数（护栏：超出部分丢弃，仅索引前缀语料）。 */
  static final int MAX_CHUNKS = 300;

  /** 把文本切成重叠块；空白文本 → 空列表。块内已 strip，纯空白块跳过。 */
  public List<String> chunk(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
    int length = normalized.length();
    int step = CHUNK_SIZE_CHARS - CHUNK_OVERLAP_CHARS;
    List<String> chunks = new ArrayList<>();
    for (int start = 0; start < length; start += step) {
      int end = Math.min(start + CHUNK_SIZE_CHARS, length);
      String piece = normalized.substring(start, end).strip();
      if (!piece.isEmpty()) {
        chunks.add(piece);
        if (chunks.size() >= MAX_CHUNKS) {
          break;
        }
      }
      if (end >= length) {
        break;
      }
    }
    return chunks;
  }
}
