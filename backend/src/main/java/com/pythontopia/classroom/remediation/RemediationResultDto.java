package com.pythontopia.classroom.remediation;

import java.util.List;

/**
 * 班级薄弱点一键补救包结果（P6.1）。<b>PINNED 契约</b>：前端 lane 依赖此形状（JSON camelCase），
 * 既有字段不得改名/删除。每个 {@link Pack} 对应一个薄弱 KC，含生成的资源 id 与其证据页 id（可溯源）。
 *
 * @param weakKcCount 检出的薄弱 KC 总数；可能大于 {@code packs.size()}（受工坊每小时配额裁剪）
 * @param partial 是否只产出了一部分。工坊 {@code maxResourcesPerHour} 会在第 N 个 KC 上抛 429，
 *     此时已生成的资源已各自提交事务，绝不能整单丢弃：这里照常返回半成品并置 {@code partial=true}，
 *     教师可稍后再次触发补齐剩余 KC。
 */
public record RemediationResultDto(
    Long classroomId, int weakKcCount, boolean partial, List<Pack> packs) {

  public record Pack(
      String kcCode, String kcName, List<Long> resourceIds, List<Long> evidencePageIds) {}
}
