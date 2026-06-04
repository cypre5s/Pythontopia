/**
 * Web Vitals 采集接入层（perfMonitor 的「调用方在 main.ts 接入」那一环）。
 *
 * 设计依据：`perfMonitor.ts` 头注释明确「不直接读 PerformanceObserver，由调用方接入后
 * 转发」「不引入 web-vitals 第三方依赖（保持体积预算）」。本模块即用浏览器原生
 * `PerformanceObserver` 拿到真实指标，转发给 `perfMonitor.recordWebVital`，供 PerfHud /
 * 班级 dashboard 只读消费。
 *
 * 仅采集可由原生 API 准确得到的四项：FCP / LCP / CLS / TTFB。INP 需要完整的交互聚合
 * （事件时序 + 会话窗口），手写易失真，宁可不显示也不造假——PerfHud 对缺失项显示「—」。
 */
import { perfMonitor, type WebVitalName } from './perfMonitor';

let initialized = false;
/** 累计布局偏移分值（CLS 为累加量，需跨多条 layout-shift 条目累计）。 */
let clsValue = 0;

function safeObserve(type: string, handle: (entries: PerformanceEntryList) => void): void {
  try {
    const observer = new PerformanceObserver((list) => handle(list.getEntries()));
    // buffered:true 取回 observer 建立前已产生的条目（FCP/LCP 常在注册前就发生）。
    observer.observe({ type, buffered: true } as PerformanceObserverInit);
  } catch {
    // 该 entryType 在当前浏览器不支持，安全跳过（诊断工具不应因此报错）。
  }
}

function record(name: WebVitalName, value: number): void {
  if (!Number.isFinite(value) || value < 0) {
    return;
  }
  try {
    perfMonitor.recordWebVital(name, value);
  } catch {
    // recordWebVital 对非法输入抛错；这里已过滤，兜底忽略以免影响主流程。
  }
}

/**
 * 接入浏览器 Web Vitals 采集。幂等：重复调用只生效一次。
 * 在不支持 PerformanceObserver 的环境（如 SSR / 老浏览器）直接 no-op。
 */
export function initWebVitals(): void {
  if (initialized || typeof PerformanceObserver === 'undefined') {
    return;
  }
  initialized = true;

  // FCP：首次内容绘制。
  safeObserve('paint', (entries) => {
    for (const entry of entries) {
      if (entry.name === 'first-contentful-paint') {
        record('FCP', entry.startTime);
      }
    }
  });

  // LCP：最大内容绘制。会随页面加载持续更新，取最新一条；perfMonitor 以最后一条评级为准。
  safeObserve('largest-contentful-paint', (entries) => {
    const last = entries[entries.length - 1] as
      | (PerformanceEntry & { renderTime?: number; loadTime?: number })
      | undefined;
    if (last) {
      record('LCP', last.renderTime || last.loadTime || last.startTime);
    }
  });

  // CLS：累计布局偏移（忽略近期由用户输入引发的偏移）。
  safeObserve('layout-shift', (entries) => {
    for (const entry of entries as Array<
      PerformanceEntry & { value?: number; hadRecentInput?: boolean }
    >) {
      if (!entry.hadRecentInput && typeof entry.value === 'number') {
        clsValue += entry.value;
      }
    }
    record('CLS', clsValue);
  });

  // TTFB：首字节时间（导航响应开始）。
  safeObserve('navigation', (entries) => {
    const nav = entries[0] as (PerformanceEntry & { responseStart?: number }) | undefined;
    if (nav && typeof nav.responseStart === 'number') {
      record('TTFB', nav.responseStart);
    }
  });
}
