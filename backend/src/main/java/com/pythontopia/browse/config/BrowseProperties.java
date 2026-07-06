package com.pythontopia.browse.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 联网查证安全墙配置（§3.4）。<b>白名单由服务端注入，绝不信任前端。</b>
 *
 * <p>{@link #allowedDomains()} 始终包含冻结的演示安全默认（{@code docs.python.org} / {@code peps.python.org}
 * / {@code pypi.org}，均为只读官方文档站），再并入运行时配置项 {@code pythontopia.browse.allowed-domains}
 * （供注入本项目自有判题站点 host，对应契约 env {@code BROWSE_ALLOWED_DOMAINS}）。两者取并集、去空白、
 * 转小写、去重；冻结默认无法被移除（安全基线）。
 *
 * <p>零配置即可工作：未配置任何项时白名单即三个冻结默认（非空），保证 {@code BrowseRunService} 总有
 * 服务端白名单可注入。
 */
@ConfigurationProperties(prefix = "pythontopia.browse")
public record BrowseProperties(List<String> allowedDomains) {

  /** 契约 §3.1 默认有界步数。 */
  public static final int DEFAULT_MAX_STEPS = 6;

  /** 冻结的演示安全白名单（只读官方文档站），始终生效、不可移除。 */
  private static final List<String> FROZEN_DOMAINS =
      List.of("docs.python.org", "peps.python.org", "pypi.org");

  public BrowseProperties {
    LinkedHashSet<String> merged = new LinkedHashSet<>(FROZEN_DOMAINS);
    if (allowedDomains != null) {
      for (String domain : allowedDomains) {
        if (domain == null) {
          continue;
        }
        String normalized = domain.strip().toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty()) {
          merged.add(normalized);
        }
      }
    }
    allowedDomains = List.copyOf(new ArrayList<>(merged));
  }
}
