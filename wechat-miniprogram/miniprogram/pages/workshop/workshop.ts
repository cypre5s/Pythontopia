import { listKcs } from '../../api/course';
import { routeIntent } from '../../api/intent';
import { askTutorStream } from '../../api/tutor';
import { getResource, streamResources } from '../../api/workshop';
import { IntentCourseKc, IntentRouteResponse, IntentType } from '../../api/dto/intent';
import { TutorAskRequest } from '../../api/dto/tutor';
import { LearningResource, ResourceStatusFlag, WorkshopResourceType } from '../../api/dto/workshop';
import { LOGIN_ROUTE, MINE_ROUTE } from '../../config';
import { isLoggedIn } from '../../store/session';
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

function asRecord(data: string): Record<string, unknown> {
  try {
    const value: unknown = JSON.parse(data);
    return value !== null && typeof value === 'object' ? (value as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

function readDelta(data: string): string {
  const delta = asRecord(data).delta;
  return typeof delta === 'string' ? delta : '';
}

function readResourceId(data: string): number {
  const value = asRecord(data).resourceId;
  return typeof value === 'number' ? value : 0;
}

function readAnswer(data: string): string {
  const record = asRecord(data);
  const candidate = record.answer ?? record.content ?? record.text;
  return typeof candidate === 'string' ? candidate : '';
}

function readErrorMessage(data: string): string {
  const message = asRecord(data).message;
  return typeof message === 'string' ? message : '';
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
  resourceTask: null as WechatMiniprogram.RequestTask | null,

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
    resourceTraceCount: 0,
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
    this.abortResource();
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

  abortResource() {
    if (this.resourceTask) {
      try {
        this.resourceTask.abort();
      } catch {
        // 忽略中断异常。
      }
      this.resourceTask = null;
    }
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
      resourceTraceCount: 0,
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
    this.abortResource();
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
        if (msg.event === 'token') {
          const delta = readDelta(msg.data);
          if (delta) {
            this.setData({ askText: this.data.askText + delta });
          }
        } else if (msg.event === 'plan') {
          this.setData({ askStatus: '已规划，生成中…' });
        } else if (msg.event === 'trace') {
          const next = this.data.askTraceCount + 1;
          this.setData({ askTraceCount: next, askStatus: `思考中…（${next} 步）` });
        } else if (msg.event === 'result') {
          const answer = readAnswer(msg.data);
          if (this.data.askText.trim().length === 0 && answer) {
            this.setData({ askText: answer });
          }
          this.setData({ askStreaming: false, askDone: true, askStatus: '' });
        } else if (msg.event === 'error') {
          this.setData({
            askStreaming: false,
            askError: readErrorMessage(msg.data) || '答疑失败',
            askStatus: '',
          });
        }
      },
      onError: (err) => {
        if (this.cancelled) {
          return;
        }
        this.setData({ askStreaming: false, askError: err.message, askStatus: '' });
      },
      onComplete: () => {
        if (this.cancelled) {
          return;
        }
        this.setData({ askStreaming: false, askStatus: '' });
      },
    });
  },

  runResource(route: IntentRouteResponse) {
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
    this.setData({
      dispatchKind: 'resource',
      resourceLoading: true,
      resourceError: '',
      resource: null,
      resourceStatus: '连接中…',
      resourceTraceCount: 0,
    });
    this.abortResource();
    this.resourceTask = streamResources(
      { kcCodes: route.kcCodes, type, focusHint: route.focusHint || undefined },
      {
        onMessage: (msg) => {
          if (this.cancelled) {
            return;
          }
          if (msg.event === 'plan') {
            this.setData({ resourceStatus: '已规划，多智能体生成中…' });
          } else if (msg.event === 'trace') {
            const next = this.data.resourceTraceCount + 1;
            this.setData({ resourceTraceCount: next, resourceStatus: `生成中…（${next} 步）` });
          } else if (msg.event === 'result') {
            const resourceId = readResourceId(msg.data);
            if (resourceId > 0) {
              this.setData({ resourceStatus: '证据审查通过，拉取内容…' });
              this.fetchResource(resourceId);
            }
          } else if (msg.event === 'error') {
            this.setData({
              resourceLoading: false,
              resourceStatus: '',
              resourceError: readErrorMessage(msg.data) || '资源生成失败',
            });
          }
        },
        onError: (err) => {
          if (this.cancelled) {
            return;
          }
          this.setData({ resourceLoading: false, resourceStatus: '', resourceError: err.message });
        },
        onComplete: () => {
          if (this.cancelled) {
            return;
          }
          // 流结束但未拿到 result（既无资源也无错误）时给个兜底提示。
          if (this.data.resourceLoading && !this.data.resource && !this.data.resourceError) {
            this.setData({ resourceLoading: false, resourceStatus: '', resourceError: '资源生成未完成，请重试' });
          }
        },
      },
    );
  },

  async fetchResource(resourceId: number) {
    try {
      const resource = await getResource(resourceId);
      this.applyResource(resource);
      this.setData({ resourceStatus: '' });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '资源拉取失败';
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
