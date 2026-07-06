import { listProblems } from '../../api/course';
import { CourseProblem } from '../../api/dto/course';
import { ApiError } from '../../utils/request';

interface ListData {
  kcCode: string;
  loading: boolean;
  errorMsg: string;
  problems: CourseProblem[];
}

Page({
  data: {
    kcCode: '',
    loading: false,
    errorMsg: '',
    problems: [],
  } as ListData,

  onLoad(query: Record<string, string | undefined>) {
    const kcCode = query.kcCode ?? '';
    this.setData({ kcCode });
    wx.setNavigationBarTitle({ title: kcCode ? `练习 · ${kcCode}` : '练习 · 题目' });
    this.load(kcCode);
  },

  async load(kcCode: string) {
    this.setData({ loading: true, errorMsg: '' });
    try {
      const problems = await listProblems(kcCode || undefined);
      this.setData({ problems, loading: false });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '题目加载失败';
      this.setData({ loading: false, errorMsg: msg });
    }
  },

  goDetail(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { id?: string | number };
    const id = Number(ds.id);
    if (!id) {
      return;
    }
    wx.navigateTo({ url: `/pages/practice/detail?problemId=${id}` });
  },
});
