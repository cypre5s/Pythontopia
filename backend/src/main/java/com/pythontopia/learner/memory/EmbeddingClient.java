package com.pythontopia.learner.memory;

import java.util.List;
import java.util.Optional;

/**
 * 记忆 / 费曼语义检索用的文本向量化客户端：调用 pythontopia-rag 的 {@code POST /embed}。
 *
 * <p>契约为 fail-soft：服务不可达、超时、维度异常或响应缺失时返回 {@link Optional#empty()}（而非抛异常），
 * 以保证 embedding 失败不阻断学习事件发布（Phase 0 验收：embedding 写失败不影响事件）。
 */
public interface EmbeddingClient {

  /** 单条文本向量化；失败 / 离线 / 空文本 → {@link Optional#empty()}。 */
  Optional<float[]> embed(String text);

  /**
   * 批量文本向量化；返回列表与入参 {@code texts} 一一对应、顺序一致、长度相等；整体失败（服务不可达 /
   * 超时）时返回与入参等长的全 {@link Optional#empty()} 列表。空入参返回空列表且不发起 HTTP 调用。
   */
  List<Optional<float[]>> embedBatch(List<String> texts);
}
