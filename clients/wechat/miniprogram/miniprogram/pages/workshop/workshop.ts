import { listKcs } from '../../api/course';
import { routeIntent } from '../../api/intent';
import { askTutorStream } from '../../api/tutor';
import { createResource, getResource } from '../../api/workshop';
import { IntentCourseKc, IntentRouteResponse, IntentType } from '../../api/dto/intent';
import { TutorAskRequest } from '../../api/dto/tutor';
import { LearningResource, ResourceStatusFlag, WorkshopResourceType } from '../../api/dto/workshop';
import { LOGIN_ROUTE, MINE_ROUTE } from '../../config';
import { isLoggedIn } from '../../store/session';
import { toAguiEvent } from '../../utils/agui';
import { toPercent } from '../../utils/format';
import { MarkdownBlock, parseMarkdown } from '../../utils/markdown';
import { ApiError } from '../../utils/request';

const INTENT_LABEL: Record<IntentType, string> = {
  ASK: '答疑',
  VIDEO: 'B站视频',
  REVIEW: '记忆矿脉复盘',
  HANDOUT: '讲义',
  MINDMAP: '思维导图',
  PRACTICE: '练习题',
  CODE_LAB: '代码实验',
  READING: '拓展阅读',
  STORYBOARD: '动画分镜',
};

const RESOURCE_INTENTS: IntentType[] = [
  'HANDOUT',
  'MINDMAP',
  'PRACTICE',
  'CODE_LAB',
  'READING',
  'STORYBOARD',
];

type DispatchKind = '' | 'ask' | 'resource' | 'review' | 'video';

interface RouteVM {
  intentLabel: string;
  focusHint: string;
  kcText: string;
  reason: string;
  confidencePct: string;
}

interface MdBlock extends MarkdownBlock {
  id: number;
}

interface ResourceVM {
  title: string;
  typeLabel: string;
  formatLabel: string;
  kcText: string;
  summary: string;
  groundingLabel: string;
  groundingClass: string;
  safetyLabel: string;
  safetyClass: string;
  isMarkdown: boolean;
  blocks: MdBlock[];
  code: string;
}

interface WorkshopData {
  input: string;
  routing: boolean;
  errorMsg: string;
  route: RouteVM | null;
  dispatchKind: DispatchKind;
  askStreaming: boolean;
  askDone: boolean;
  askText: string;
  askError: string;
  askStatus: string;
  askTraceCount: number;
  resourceLoading: boolean;
  resourceError: string;
  resourceStatus: string;
  resourceTraceCount: number;
  resource: ResourceVM | null;
  reviewKcText: string;
  videoFocus: string;
  videoKcText: string;
}

function toRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' ? (value as Record<string, unknown>) : {};
}

/** 导师答疑 RUN_FINISHED 结果里取答案文本（graph 结果 snake_case，answer_md 优先）。 */
function extractTutorAnswer(result: unknown): string {
  const r = toRecord(result);
  const candidate = r.answer_md ?? r.answer ?? r.content ?? r.text;
  return typeof candidate === 'string' ? candidate : '';
}

function isResourceIntent(intent: IntentType): boolean {
  return RESOURCE_INTENTS.indexOf(intent) >= 0;
}

function flagLabel(flag: ResourceStatusFlag): string {
  if (flag === 'PASSED') {
    return '通过';
  }
  if (flag === 'FAILED') {
    return '未通过';
  }
  return '无证据';
}

function flagClass(flag: ResourceStatusFlag): string {
  if (flag === 'PASSED') {
    return 'badge--done';
  }
  if (flag === 'FAILED') {
    return 'badge--fail';
  }
  return 'badge--progress';
}

Page({
  cancelled: false,
  lastMessage: '',
  courseKcs: [] as IntentCourseKc[],
  askTask: null as WechatMiniprogram.RequestTask | null,
  // 导师答疑流式文本节流：累积增量、80ms 合并一次 setData，避免每 token 全量回传。
  askBuffer: '',
  askFlushTimer: 0,

  data: {
    input: '',
    routing: false,
    errorMsg: '',
    route: null,
    dispatchKind: '',
    askStreaming: false,
    askDone: false,
    askText: '',
    askError: '',
    askStatus: '',
    askTraceCount: 0,
    resourceLoading: false,
    resourceError: '',
    resourceStatus: '',
    resource: null,
    reviewKcText: '',
    videoFocus: '',
    videoKcText: '',
  } as WorkshopData,

  onLoad() {
    this.cancelled = false;
    this.loadKcs();
  },

  onShow() {
    if (!isLoggedIn()) {
      wx.reLaunch({ url: LOGIN_ROUTE });
    }
  },

  onUnload() {
    this.cancelled = true;
    this.abortAsk();
    if (this.askFlushTimer) {
      clearTimeout(this.askFlushTimer);
      this.askFlushTimer = 0;
    }
  },

  async loadKcs() {
    try {
      const kcs = await listKcs();
      this.courseKcs = kcs.map((kc) => ({ code: kc.kcCode, name: kc.kcName }));
    } catch {
      this.courseKcs = [];
    }
  },

  onInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ input: e.detail.value });
  },

  abortAsk() {
    if (this.askTask) {
      try {
        this.askTask.abort();
      } catch {
        // 忽略中断异常。
      }
      this.askTask = null;
    }
  },

  scheduleAskFlush() {
    if (this.askFlushTimer) {
      return;
    }
    this.askFlushTimer = setTimeout(() => {
      this.askFlushTimer = 0;
      this.flushAsk();
    }, 80) as unknown as number;
  },

  flushAsk() {
    if (this.askBuffer.length === 0) {
      return;
    }
    const buffered = this.askBuffer;
    this.askBuffer = '';
    this.setData({ askText: this.data.askText + buffered });
  },

  flushAskNow() {
    if (this.askFlushTimer) {
      clearTimeout(this.askFlushTimer);
      this.askFlushTimer = 0;
    }
    this.flushAsk();
  },

  resetResults() {
    this.setData({
      dispatchKind: '',
      askStreaming: false,
      askDone: false,
      askText: '',
      askError: '',
      askStatus: '',
      askTraceCount: 0,
      resourceLoading: false,
      resourceError: '',
      resourceStatus: '',
      resource: null,
      reviewKcText: '',
      videoFocus: '',
      videoKcText: '',
    });
  },

  async onSend() {
    const message = this.data.input.trim();
    if (message.length === 0) {
      wx.showToast({ title: '请输入内容', icon: 'none' });
      return;
    }
    if (this.data.routing) {
      return;
    }
    this.abortAsk();
    this.lastMessage = message;
    this.setData({ routing: true, route: null, errorMsg: '' });
    this.resetResults();
    try {
      const route = await routeIntent({
        message,
        courseKcs: this.courseKcs.length > 0 ? this.courseKcs : undefined,
      });
      this.setData({
        routing: false,
        route: {
          intentLabel: INTENT_LABEL[route.intent] ?? route.intent,
          focusHint: route.focusHint,
          kcText: route.kcCodes.join('、'),
          reason: route.reason,
          confidencePct: toPercent(route.confidence),
        },
      });
      this.dispatch(route);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '意图分析失败';
      this.setData({ routing: false, errorMsg: msg });
    }
  },

  dispatch(route: IntentRouteResponse) {
    if (route.intent === 'ASK') {
      this.runAsk(route);
      return;
    }
    if (route.intent === 'REVIEW') {
      this.setData({ dispatchKind: 'review', reviewKcText: route.kcCodes.join('、') });
      return;
    }
    if (route.intent === 'VIDEO') {
      this.setData({
        dispatchKind: 'video',
        videoFocus: route.focusHint,
        videoKcText: route.kcCodes.join('、'),
      });
      return;
    }
    if (isResourceIntent(route.intent)) {
      this.runResource(route);
      return;
    }
    this.setData({ dispatchKind: 'review', reviewKcText: route.kcCodes.join('、') });
  },

  runAsk(route: IntentRouteResponse) {
    this.askBuffer = '';
    if (this.askFlushTimer) {
      clearTimeout(this.askFlushTimer);
      this.askFlushTimer = 0;
    }
    this.setData({
      dispatchKind: 'ask',
      askStreaming: true,
      askDone: false,
      askText: '',
      askError: '',
      askStatus: '连接中…',
      askTraceCount: 0,
    });
    const body: TutorAskRequest = {
      question: this.lastMessage,
      kcCodes: route.kcCodes.length > 0 ? route.kcCodes : undefined,
      context: route.focusHint || undefined,
    };
    this.askTask = askTutorStream(body, {
      onMessage: (msg) => {
        if (this.cancelled) {
          return;
        }
        const ev = toAguiEvent(msg.event, msg.data);
        if (!ev) {
          return;
        }
        if (ev.type === 'TEXT_MESSAGE_CONTENT') {
          if (ev.delta) {
            this.askBuffer += ev.delta;
            this.scheduleAskFlush();
          }
        } else if (ev.type === 'RUN_STARTED') {
          this.setData({ askStatus: '生成中…' });
        } else if (ev.type === 'STEP_STARTED' || ev.type === 'STEP_FINISHED') {
          const next = this.data.askTraceCount + 1;
          this.setData({ askTraceCount: next, askStatus: `思考中…（${next} 步）` });
        } else if (ev.type === 'CUSTOM' && ev.name === 'plan') {
          this.setData({ askStatus: '已规划，生成中…' });
        } else if (ev.type === 'RUN_FINISHED') {
          this.flushAskNow();
          const answer = extractTutorAnswer(ev.result);
          if (this.data.askText.trim().length === 0 && answer) {
            this.setData({ askText: answer });
          }
          this.setData({ askStreaming: false, askDone: true, askStatus: '' });
        } else if (ev.type === 'RUN_ERROR') {
          this.flushAskNow();
          this.setData({ askStreaming: false, askError: ev.message || '答疑失败', askStatus: '' });
        }
      },
      onError: (err) => {
        if (this.cancelled) {
          return;
        }
        this.flushAskNow();
        this.setData({ askStreaming: false, askError: err.message, askStatus: '' });
      },
      onComplete: () => {
        if (this.cancelled) {
          return;
        }
        this.flushAskNow();
        this.setData({ askStreaming: false, askStatus: '' });
      },
    });
  },

  async runResource(route: IntentRouteResponse) {
    const type = route.intent as WorkshopResourceType;
    if (route.kcCodes.length === 0) {
      this.setData({
        dispatchKind: 'resource',
        resourceLoading: false,
        resourceError: '未能锚定到具体知识点，请在消息里点名某个知识点（如「for 循环」）后再试。',
        resource: null,
      });
      return;
    }
    // 工坊资源走非流式 create（持久化 → 返回 resourceId），再 getResource 取干净的
    // LearningResource 渲染。流式端点 /resources/stream 仅回 graph 内联 artifact（无 resourceId，
    // 难直接渲染），故此处用非流式以保证产物可读、契约稳定（实测 2026-06-13 在公网验证）。
    this.setData({
      dispatchKind: 'resource',
      resourceLoading: true,
      resourceError: '',
      resource: null,
      resourceStatus: '多智能体生成中（含证据审查），约十几秒…',
    });
    try {
      const created = await createResource({
        kcCodes: route.kcCodes,
        type,
        focusHint: route.focusHint || undefined,
      });
      const resource = await getResource(created.resourceId);
      if (this.cancelled) {
        return;
      }
      this.applyResource(resource);
      this.setData({ resourceStatus: '' });
    } catch (e) {
      if (this.cancelled) {
        return;
      }
      const msg = e instanceof ApiError ? e.message : '资源生成失败';
      this.setData({ resourceLoading: false, resourceStatus: '', resourceError: msg });
    }
  },

  applyResource(resource: LearningResource) {
    const isMarkdown = resource.contentFormat === 'MARKDOWN' && typeof resource.content === 'string';
    const blocks: MdBlock[] = isMarkdown
      ? parseMarkdown(resource.content as string).map((block, index) => ({ ...block, id: index }))
      : [];
    const code = isMarkdown
      ? ''
      : typeof resource.content === 'string'
        ? resource.content
        : JSON.stringify(resource.content, null, 2);
    this.setData({
      resourceLoading: false,
      resource: {
        title: resource.title,
        typeLabel: INTENT_LABEL[resource.type],
        formatLabel: resource.contentFormat,
        kcText: resource.kcCodes.join('、'),
        summary: resource.summary ?? '',
        groundingLabel: flagLabel(resource.groundingStatus),
        groundingClass: flagClass(resource.groundingStatus),
        safetyLabel: flagLabel(resource.safetyStatus),
        safetyClass: flagClass(resource.safetyStatus),
        isMarkdown,
        blocks,
        code,
      },
    });
  },

  goMine() {
    wx.navigateTo({ url: MINE_ROUTE });
  },
});
