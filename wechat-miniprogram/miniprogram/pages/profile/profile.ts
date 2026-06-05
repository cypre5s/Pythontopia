import { getDimensions, getProfile, updateDimension } from '../../api/learner';
import { ProfileDimension } from '../../api/dto/learner';
import { getUser, isLoggedIn, logout } from '../../store/session';
import { ApiError } from '../../utils/request';
import { percentWidth, toPercent } from '../../utils/format';

interface DimensionVM {
  key: string;
  label: string;
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
    this.setData(patch);
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
