import {
  getCoursePack,
  listChapters,
  listKcs,
  listPages,
  pageImageUrl,
} from '../../api/course';
import { Chapter, CoursePack, CoursePage, Kc } from '../../api/dto/course';
import { isLoggedIn } from '../../store/session';
import { ApiError } from '../../utils/request';

interface PageVM {
  pageId: number;
  title: string;
  excerpt: string;
  imageUrl: string;
}

interface KcVM {
  kcId: number;
  kcCode: string;
  kcName: string;
  difficulty: string;
  summary: string;
  expanded: boolean;
  pagesLoaded: boolean;
  pageLoading: boolean;
  pages: PageVM[];
  pageIdx: number;
  currentPage: PageVM | null;
  pageInfo: string;
}

interface ChapterVM {
  chapterId: number;
  orderIndex: number;
  title: string;
  summary: string;
  expanded: boolean;
  kcsLoaded: boolean;
  kcsLoading: boolean;
  kcs: KcVM[];
}

interface CourseData {
  loading: boolean;
  errorMsg: string;
  pack: CoursePack | null;
  chapters: ChapterVM[];
}

function toChapterVM(ch: Chapter): ChapterVM {
  return {
    chapterId: ch.chapterId,
    orderIndex: ch.orderIndex,
    title: ch.title,
    summary: ch.summaryMarkdown ?? '',
    expanded: false,
    kcsLoaded: false,
    kcsLoading: false,
    kcs: [],
  };
}

function toKcVM(kc: Kc): KcVM {
  return {
    kcId: kc.kcId,
    kcCode: kc.kcCode,
    kcName: kc.kcName,
    difficulty: kc.difficulty ?? '',
    summary: kc.summaryMarkdown ?? '',
    expanded: false,
    pagesLoaded: false,
    pageLoading: false,
    pages: [],
    pageIdx: 0,
    currentPage: null,
    pageInfo: '',
  };
}

function toPageVM(page: CoursePage): PageVM {
  return {
    pageId: page.pageId,
    title: page.title ?? `第 ${page.pageNo} 页`,
    excerpt: page.excerpt ?? page.contentMarkdown ?? '',
    imageUrl: pageImageUrl(page.pageId),
  };
}

function withCurrentPage(kc: KcVM): KcVM {
  const current = kc.pages.length > 0 ? kc.pages[kc.pageIdx] : null;
  const pageInfo = kc.pages.length > 0 ? `第 ${kc.pageIdx + 1} / ${kc.pages.length} 页` : '';
  return { ...kc, currentPage: current, pageInfo };
}

Page({
  data: {
    loading: false,
    errorMsg: '',
    pack: null,
    chapters: [],
  } as CourseData,

  onShow() {
    if (!isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    if (this.data.chapters.length === 0) {
      this.loadAll();
    }
  },

  onPullDownRefresh() {
    this.loadAll().then(() => wx.stopPullDownRefresh());
  },

  async loadAll() {
    this.setData({ loading: true, errorMsg: '' });
    const [packRes, chaptersRes] = await Promise.allSettled([getCoursePack(), listChapters()]);
    const patch: Partial<CourseData> = { loading: false };
    if (packRes.status === 'fulfilled') {
      patch.pack = packRes.value;
    }
    if (chaptersRes.status === 'fulfilled') {
      patch.chapters = chaptersRes.value.map(toChapterVM);
    } else {
      const reason: unknown = chaptersRes.reason;
      patch.errorMsg = reason instanceof ApiError ? reason.message : '课程加载失败';
    }
    this.setData(patch);
  },

  patchChapter(idx: number, patch: Partial<ChapterVM>) {
    const chapters = this.data.chapters.map((c, i) => (i === idx ? { ...c, ...patch } : c));
    this.setData({ chapters });
  },

  patchKc(cidx: number, kidx: number, updater: (kc: KcVM) => KcVM) {
    const chapters = this.data.chapters.map((c, i) => {
      if (i !== cidx) {
        return c;
      }
      return { ...c, kcs: c.kcs.map((k, j) => (j === kidx ? updater(k) : k)) };
    });
    this.setData({ chapters });
  },

  async toggleChapter(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { index?: string | number };
    const idx = Number(ds.index);
    const chapter = this.data.chapters[idx];
    if (!chapter) {
      return;
    }
    const expanded = !chapter.expanded;
    this.patchChapter(idx, { expanded });
    if (expanded && !chapter.kcsLoaded) {
      this.patchChapter(idx, { kcsLoading: true });
      try {
        const kcs = await listKcs(chapter.chapterId);
        this.patchChapter(idx, {
          kcs: kcs.map(toKcVM),
          kcsLoaded: true,
          kcsLoading: false,
        });
      } catch (e2) {
        this.patchChapter(idx, { kcsLoading: false });
        const msg = e2 instanceof ApiError ? e2.message : '知识点加载失败';
        wx.showToast({ title: msg, icon: 'none' });
      }
    }
  },

  toggleKc(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { cidx?: string | number; kidx?: string | number };
    const cidx = Number(ds.cidx);
    const kidx = Number(ds.kidx);
    this.patchKc(cidx, kidx, (kc) => ({ ...kc, expanded: !kc.expanded }));
  },

  async loadKcPages(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as {
      cidx?: string | number;
      kidx?: string | number;
      kcCode?: string;
    };
    const cidx = Number(ds.cidx);
    const kidx = Number(ds.kidx);
    const kcCode = ds.kcCode ?? '';
    if (!kcCode) {
      return;
    }
    this.patchKc(cidx, kidx, (kc) => ({ ...kc, pageLoading: true }));
    try {
      const pages = await listPages(kcCode);
      this.patchKc(cidx, kidx, (kc) =>
        withCurrentPage({
          ...kc,
          pages: pages.map(toPageVM),
          pageIdx: 0,
          pagesLoaded: true,
          pageLoading: false,
        }),
      );
    } catch (e2) {
      this.patchKc(cidx, kidx, (kc) => ({ ...kc, pageLoading: false, pagesLoaded: true }));
      const msg = e2 instanceof ApiError ? e2.message : '课件加载失败';
      wx.showToast({ title: msg, icon: 'none' });
    }
  },

  prevPage(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { cidx?: string | number; kidx?: string | number };
    const cidx = Number(ds.cidx);
    const kidx = Number(ds.kidx);
    this.patchKc(cidx, kidx, (kc) =>
      withCurrentPage({ ...kc, pageIdx: Math.max(0, kc.pageIdx - 1) }),
    );
  },

  nextPage(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { cidx?: string | number; kidx?: string | number };
    const cidx = Number(ds.cidx);
    const kidx = Number(ds.kidx);
    this.patchKc(cidx, kidx, (kc) =>
      withCurrentPage({ ...kc, pageIdx: Math.min(kc.pages.length - 1, kc.pageIdx + 1) }),
    );
  },

  goPracticeList(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { kcCode?: string };
    const kcCode = ds.kcCode ?? '';
    if (!kcCode) {
      return;
    }
    wx.navigateTo({ url: `/pages/practice/list?kcCode=${kcCode}` });
  },
});
