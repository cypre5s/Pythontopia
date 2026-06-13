package com.pythontopia.workshop.upload.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link UploadChunker} 纯逻辑单测：空白处理、重叠窗口、上限护栏。 */
class UploadChunkerTest {

  private final UploadChunker chunker = new UploadChunker();

  @Test
  void blankOrNullTextYieldsNoChunks() {
    assertThat(chunker.chunk(null)).isEmpty();
    assertThat(chunker.chunk("")).isEmpty();
    assertThat(chunker.chunk("   \n\t  ")).isEmpty();
  }

  @Test
  void shortTextBecomesSingleStrippedChunk() {
    List<String> chunks = chunker.chunk("  Python for 循环遍历列表  ");

    assertThat(chunks).containsExactly("Python for 循环遍历列表");
  }

  @Test
  void longTextSplitsIntoOverlappingWindows() {
    String text = "0123456789".repeat(200); // 2000 chars, no whitespace → strip is no-op
    int step = UploadChunker.CHUNK_SIZE_CHARS - UploadChunker.CHUNK_OVERLAP_CHARS;

    List<String> chunks = chunker.chunk(text);

    // starts at 0, 680, 1360 → 3 windows.
    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0)).hasSize(UploadChunker.CHUNK_SIZE_CHARS);
    assertThat(chunks.get(1)).hasSize(UploadChunker.CHUNK_SIZE_CHARS);
    assertThat(chunks.get(2)).hasSize(text.length() - 2 * step);
    // 相邻块尾部与下一块头部重叠 CHUNK_OVERLAP_CHARS。
    assertThat(chunks.get(0).substring(step))
        .isEqualTo(chunks.get(1).substring(0, UploadChunker.CHUNK_OVERLAP_CHARS));
  }

  @Test
  void chunkCountIsCappedAtMaxChunks() {
    int step = UploadChunker.CHUNK_SIZE_CHARS - UploadChunker.CHUNK_OVERLAP_CHARS;
    String huge = "x".repeat((UploadChunker.MAX_CHUNKS + 50) * step);

    assertThat(chunker.chunk(huge)).hasSize(UploadChunker.MAX_CHUNKS);
  }
}
