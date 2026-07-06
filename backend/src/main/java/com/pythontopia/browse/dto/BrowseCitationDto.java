package com.pythontopia.browse.dto;

/**
 * 外部命中引用。<b>统一标注「外部内容，非课件」</b>（§0 红线 + §3.8）：BROWSE 抓取的 url 只作外部参考，
 * 绝不与 packId=43 课件页码引用混淆。前端据 {@link #label()} 显著区分外链与课件引用。
 */
public record BrowseCitationDto(String url, String label) {

  /** 所有外部引用统一携带的标签文案。 */
  public static final String EXTERNAL_LABEL = "外部内容，非课件";

  public static BrowseCitationDto external(String url) {
    return new BrowseCitationDto(url, EXTERNAL_LABEL);
  }
}
