import { getEchoes, getErrorBook, openEcho } from '../../api/bugMine';
import {
  BugMineEchoCandidate,
  BugMineErrorBookItem,
  BugMineSessionStatus,
} from '../../api/dto/bugMine';
import { LOGIN_ROUTE, MINE_SESSION_ROUTE } from '../../config';
import { isLoggedIn } from '../../store/session';
import { toPercent } from '../../utils/format';
import { ApiError } from '../../utils/request';

const STATUS_LABEL: Record<BugMineSessionStatus, string> = {
  OPEN: '进行中',
  RESOLVED: '已解决',
  ABANDONED: '已放弃',
};

interface ErrorVM {
  sessionId: number;
  title: string;
  kcCode: string;
  statusLabel: string;
  depthText: string;
  decayText: string;
  priorityText: string;
  reason: string;
  guidance: string;
}

interface EchoVM {
  kcCode: string;
  title: string;
  recommendText: string;
  decayText: string;
  masteryText: string;
  reason: string;
}

interface MineData {
  loading: boolean;
  errorMsg: string;
  errorBook: ErrorVM[];
  echoes: EchoVM[];
}

function pct(value: number | null): string {
  return value === null ? '—' : toPercent(value);
}

function toErrorVM(item: BugMineErrorBookItem): ErrorVM {
  return {
    sessionId: item.sessionId,
    title: item.problemTitle,
    kcCode: item.kcCode,
    statusLabel: STATUS_LABEL[item.status],
    depthText: `L${item.depthLayer} / 目标 L${item.profileDepthTarget}`,
    decayText: pct(item.decayRisk),
    priorityText: pct(item.minePriority),
    reason: item.recommendationReason,
    guidance: item.profileGuidance ?? '',
  };
}

function toEchoVM(item: BugMineEchoCandidate): EchoVM {
  return {
    kcCode: item.kcCode,
    title: item.problemTitle,
    recommendText: pct(item.minePriority),
    decayText: pct(item.decayRisk),
    masteryText: pct(item.masteryScore),
    reason: item.recommendationReason,
  };
}

Page({
  data: {
    loading: false,
    errorMsg: '',
    errorBook: [],
    echoes: [],
  } as MineData,

  onShow() {
    if (!isLoggedIn()) {
      wx.reLaunch({ url: LOGIN_ROUTE });
      return;
    }
    this.loadAll();
  },

  onPullDownRefresh() {
    this.loadAll().then(() => wx.stopPullDownRefresh());
  },

  async loadAll() {
    this.setData({ loading: true, errorMsg: '' });
    const [errorRes, echoRes] = await Promise.allSettled([getErrorBook(), getEchoes()]);
    const patch: Partial<MineData> = { loading: false };
    if (errorRes.status === 'fulfilled') {
      patch.errorBook = errorRes.value.map(toErrorVM);
    }
    if (echoRes.status === 'fulfilled') {
      patch.echoes = echoRes.value.map(toEchoVM);
    }
    const failed = [errorRes, echoRes].find((r) => r.status === 'rejected');
    if (failed && failed.status === 'rejected') {
      const reason: unknown = failed.reason;
      patch.errorMsg = reason instanceof ApiError ? reason.message : '矿洞数据加载失败';
    }
    this.setData(patch);
  },

  goSession(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { id?: string | number };
    const id = Number(ds.id);
    if (!id) {
      return;
    }
    wx.navigateTo({ url: `${MINE_SESSION_ROUTE}?id=${id}` });
  },

  async onOpenEcho(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { kc?: string };
    const kcCode = ds.kc;
    if (!kcCode) {
      return;
    }
    wx.showLoading({ title: '入矿中…', mask: true });
    try {
      const session = await openEcho(kcCode);
      wx.hideLoading();
      wx.navigateTo({ url: `${MINE_SESSION_ROUTE}?id=${session.id}` });
    } catch (err) {
      wx.hideLoading();
      const msg = err instanceof ApiError ? err.message : '入矿失败';
      wx.showToast({ title: msg, icon: 'none' });
    }
  },
});
