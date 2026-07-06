package com.pythontopia.npc;

/** NPC 记忆自编辑去重规则：余弦相似度超过阈值则视为近重复，不新增记忆，只提升 salience。 */
public final class NpcMemoryDedup {

  public static final double DEFAULT_THRESHOLD = 0.92;

  private NpcMemoryDedup() {}

  public static boolean isNearDuplicate(float[] a, float[] b, double threshold) {
    if (a == null || b == null || a.length == 0 || a.length != b.length) {
      return false;
    }
    return cosine(a, b) > threshold;
  }

  static double cosine(float[] a, float[] b) {
    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA <= 0.0 || normB <= 0.0) {
      return 0.0;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
