import { isLoggedIn, login, redirectHome, register } from '../../store/session';
import { ApiError } from '../../utils/request';

type AuthMode = 'login' | 'register';

interface LoginData {
  mode: AuthMode;
  username: string;
  password: string;
  displayName: string;
  submitting: boolean;
  errorMsg: string;
}

Page({
  data: {
    mode: 'login',
    username: '',
    password: '',
    displayName: '',
    submitting: false,
    errorMsg: '',
  } as LoginData,

  onShow() {
    if (isLoggedIn()) {
      redirectHome();
    }
  },

  onUsername(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ username: e.detail.value, errorMsg: '' });
  },

  onPassword(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ password: e.detail.value, errorMsg: '' });
  },

  onDisplayName(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ displayName: e.detail.value, errorMsg: '' });
  },

  setMode(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { mode?: AuthMode };
    if (dataset.mode && dataset.mode !== this.data.mode) {
      this.setData({ mode: dataset.mode, errorMsg: '' });
    }
  },

  validate(): string | null {
    const { mode, username, password, displayName } = this.data;
    if (username.trim().length < 3) {
      return '用户名至少 3 位';
    }
    if (!/^[A-Za-z0-9_.-]+$/.test(username.trim())) {
      return '用户名仅允许 字母 / 数字 / _ . -';
    }
    if (password.length < 8) {
      return '密码至少 8 位';
    }
    if (mode === 'register' && displayName.trim().length === 0) {
      return '请填写昵称';
    }
    return null;
  },

  async onSubmit() {
    if (this.data.submitting) {
      return;
    }
    const err = this.validate();
    if (err) {
      this.setData({ errorMsg: err });
      return;
    }
    this.setData({ submitting: true, errorMsg: '' });
    try {
      if (this.data.mode === 'login') {
        await login({ username: this.data.username.trim(), password: this.data.password });
      } else {
        await register({
          username: this.data.username.trim(),
          password: this.data.password,
          displayName: this.data.displayName.trim(),
          role: 'STUDENT',
        });
      }
      wx.showToast({ title: '欢迎来到小镇', icon: 'success' });
      redirectHome();
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '操作失败，请重试';
      this.setData({ errorMsg: msg });
    } finally {
      this.setData({ submitting: false });
    }
  },
});
