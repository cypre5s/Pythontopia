import { getDimensions, getProfile, updateDimension } from '../../api/learner';
import { ProfileDimension } from '../../api/dto/learner';
import { getUser, isLoggedIn, logout } from '../../store/session';
import { ApiError } from '../../utils/request';
import { percentWidth, toPercent } from '../../utils/format';

// Canvas 2D 结构化子集（小程序 lib 无 DOM，不依赖 CanvasRenderingContext2D；边界处一次性 cast）。
interface RadarCtx {
  lineWidth: number;
  strokeStyle: string;
  fillStyle: string;
  font: string;
  textAlign: string;
  textBaseline: string;
  scale(x: number, y: number): void;
  clearRect(x: number, y: number, w: number, h: number): void;
  beginPath(): void;
  moveTo(x: number, y: number): void;
  lineTo(x: number, y: number): void;
  closePath(): void;
  stroke(): void;
  fill(): void;
  fillText(text: string, x: number, y: number): void;
}

interface RadarCanvas {
  width: number;
  height: number;
  getContext(type: '2d'): RadarCtx;
}

interface RadarNodeInfo {
  node: RadarCanvas | null;
  width: number;
  height: number;
}

function clamp01(value: number): number {
  if (Number.isNaN(value)) {
    return 0;
  }
  return Math.max(0, Math.min(1, value));
}

interface DimensionVM {
  key: string;
  label: string;
  value: number;
  percent: string;
  width: string;
  confidence: string;
  confidenceWidth: string;
  evidenceCount: number;
  selfEditable: boolean;
  tags: string[];
  lowConfidence: boolean;
}

interface ProfileData {
  loading: boolean;
  errorMsg: string;
  dimensions: DimensionVM[];
  hasProfile: boolean;
  narrative: string;
  major: string;
  displayName: string;
  username: string;
  updatingKey: string;
}

function toDimVM(dim: ProfileDimension): DimensionVM {
  return {
    key: dim.key,
    label: dim.label,
    value: dim.score,
    percent: toPercent(dim.score),
    width: percentWidth(dim.score),
    confidence: toPercent(dim.confidence),
    confidenceWidth: percentWidth(dim.confidence),
    evidenceCount: dim.evidenceCount,
    selfEditable: dim.selfEditable,
    tags: dim.tags,
    lowConfidence: dim.confidence < 0.3,
  };
}

Page({
  data: {
    loading: false,
    errorMsg: '',
    dimensions: [],
    hasProfile: false,
    narrative: '',
    major: '',
    displayName: '',
    username: '',
    updatingKey: '',
  } as ProfileData,

  onShow() {
    if (!isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  async load() {
    this.setData({ loading: true, errorMsg: '' });
    const [dimsRes, profileRes] = await Promise.allSettled([getDimensions(), getProfile()]);
    const patch: Partial<ProfileData> = { loading: false };
    if (dimsRes.status === 'fulfilled') {
      patch.dimensions = dimsRes.value.map(toDimVM);
    } else {
      const reason: unknown = dimsRes.reason;
      patch.errorMsg = reason instanceof ApiError ? reason.message : '画像加载失败';
    }
    if (profileRes.status === 'fulfilled') {
      const profile = profileRes.value;
      patch.hasProfile = profile !== null;
      patch.narrative = profile ? profile.narrativeSummary ?? '' : '';
      patch.major = profile ? profile.major ?? '' : '';
    }
    const user = getUser();
    patch.displayName = user ? user.displayName : '';
    patch.username = user ? user.username : '';
    this.setData(patch, () => this.drawRadar());
  },

  drawRadar() {
    const dims = this.data.dimensions;
    if (dims.length < 3) {
      return;
    }
    wx.createSelectorQuery()
      .select('#profileRadar')
      .fields({ node: true, size: true })
      .exec((res) => {
        const info = (res as unknown as RadarNodeInfo[])[0];
        if (!info || !info.node) {
          return;
        }
        const canvas = info.node;
        const ctx = canvas.getContext('2d');
        const dpr = wx.getSystemInfoSync().pixelRatio || 2;
        canvas.width = Math.floor(info.width * dpr);
        canvas.height = Math.floor(info.height * dpr);
        ctx.scale(dpr, dpr);
        this.renderRadar(ctx, info.width, info.height, dims);
      });
  },

  renderRadar(ctx: RadarCtx, w: number, h: number, dims: DimensionVM[]) {
    ctx.clearRect(0, 0, w, h);
    const n = dims.length;
    const cx = w / 2;
    const cy = h / 2;
    const maxR = Math.min(w, h) / 2 - 30;
    const angle = (i: number): number => -Math.PI / 2 + (i * 2 * Math.PI) / n;

    ctx.strokeStyle = 'rgba(90,62,27,0.30)';
    ctx.lineWidth = 1;
    for (let ring = 1; ring <= 4; ring += 1) {
      const r = (maxR * ring) / 4;
      ctx.beginPath();
      for (let i = 0; i < n; i += 1) {
        const a = angle(i);
        const x = cx + Math.cos(a) * r;
        const y = cy + Math.sin(a) * r;
        if (i === 0) {
          ctx.moveTo(x, y);
        } else {
          ctx.lineTo(x, y);
        }
      }
      ctx.closePath();
      ctx.stroke();
    }

    for (let i = 0; i < n; i += 1) {
      const a = angle(i);
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.lineTo(cx + Math.cos(a) * maxR, cy + Math.sin(a) * maxR);
      ctx.stroke();
    }

    ctx.beginPath();
    for (let i = 0; i < n; i += 1) {
      const a = angle(i);
      const r = maxR * clamp01(dims[i].value);
      const x = cx + Math.cos(a) * r;
      const y = cy + Math.sin(a) * r;
      if (i === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    }
    ctx.closePath();
    ctx.fillStyle = 'rgba(79,122,53,0.35)';
    ctx.fill();
    ctx.strokeStyle = '#4f7a35';
    ctx.lineWidth = 2;
    ctx.stroke();

    ctx.fillStyle = '#6b5836';
    ctx.font = '10px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    for (let i = 0; i < n; i += 1) {
      const a = angle(i);
      const lx = cx + Math.cos(a) * (maxR + 16);
      const ly = cy + Math.sin(a) * (maxR + 16);
      ctx.fillText(dims[i].label, lx, ly);
    }
  },

  async onAdjust(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { key?: string; dir?: string };
    const key = ds.key ?? '';
    const dir = ds.dir ?? '';
    if (!key || this.data.updatingKey) {
      return;
    }
    const delta = dir === 'up' ? 0.1 : -0.1;
    this.setData({ updatingKey: key });
    try {
      await updateDimension(key, { scoreDelta: delta });
      await this.load();
      wx.showToast({ title: '已更新自评', icon: 'success' });
    } catch (e2) {
      const msg = e2 instanceof ApiError ? e2.message : '更新失败';
      wx.showToast({ title: msg, icon: 'none' });
    } finally {
      this.setData({ updatingKey: '' });
    }
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: (res) => {
        if (res.confirm) {
          logout();
        }
      },
    });
  },
});
