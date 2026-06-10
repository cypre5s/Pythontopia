package com.pythontopia.mainline.star;

/** star_award_event.source 取值（对齐 V156 CHECK 约束）。 */
public enum StarSource {
  /** 章末大测试提交后重算触发。 */
  TEST,
  /** stage 三件套完成触发。 */
  STAGE_COMPLETE,
  /** 掌握度刷新事件触发。 */
  MASTERY,
  /** 错题复盘补星（Wave 2 B1）。 */
  ERRORBOOK_RECOVERY,
  /** 无尽挑战补星（Wave 2 B1）。 */
  ENDLESS_RECOVERY,
  /** 管理员手工。 */
  ADMIN
}
