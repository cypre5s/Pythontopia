package com.pythontopia.classroom.remediation;

import java.util.List;

/**
 * 班级薄弱点一键补救包结果（P6.1）。<b>PINNED 契约</b>：前端 lane 依赖此精确形状（JSON camelCase），
 * 不得增删/改名字段。每个 {@link Pack} 对应一个薄弱 KC，含生成的资源 id 与其证据页 id（可溯源）。
 */
public record RemediationResultDto(Long classroomId, int weakKcCount, List<Pack> packs) {

  public record Pack(
      String kcCode, String kcName, List<Long> resourceIds, List<Long> evidencePageIds) {}
}
