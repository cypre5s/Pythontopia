// R-13 迭代 13：前端静态节日映射。
//
// 与后端 V118 `world_festival` seed 对齐（11 个里程碑），但因 WorldFestivalService
// 还没暴露 Controller（后端有服务无端点），前端先用本地静态映射做 HUD ribbon。
// 真后端接入后可平滑替换为 useFestivalStore.fetch。

export interface WorldFestival {
  /** 后端 day_index 编号 */
  day: number;
  /** 节日中文显示名 */
  label: string;
  /** 节日图标（pixel emoji 或单字符） */
  icon: string;
  /** 节日描述（HUD ribbon 悬浮 tooltip 用） */
  description: string;
  /** 季节 hint（决定 ribbon 配色） */
  season: 'spring' | 'summer' | 'autumn' | 'winter';
}

export const FESTIVALS: readonly WorldFestival[] = [
  { day: 1, label: '开学日', icon: '[NEW]', description: '小镇正式开张，学习生涯开始', season: 'spring' },
  { day: 14, label: '春耕祭', icon: '[SPR]', description: '播撒第一批知识种子', season: 'spring' },
  { day: 28, label: '春日嘉年华', icon: '[FES]', description: '镇广场歌舞庆典', season: 'spring' },
  { day: 35, label: '入夏', icon: '[SUN]', description: '夏季 BGM 与天气切换', season: 'summer' },
  { day: 56, label: '夏夜星河', icon: '[STR]', description: '通宵观星，知识点暴击 +30%', season: 'summer' },
  { day: 60, label: '盛夏祭典', icon: '[FIR]', description: '港口烟花，全镇欢腾', season: 'summer' },
  { day: 70, label: '入秋', icon: '[AUT]', description: '秋收预备，复盘大厅开放', season: 'autumn' },
  { day: 84, label: '秋实节', icon: '[HRV]', description: '当季 KC 自动结算双倍掌握度', season: 'autumn' },
  { day: 90, label: '入冬', icon: '[SNW]', description: '雪景 + 取暖工坊', season: 'winter' },
  { day: 100, label: '冬日炉火', icon: '[WRM]', description: 'Bug 矿洞产出 +50%', season: 'winter' },
  { day: 112, label: '岁末庆', icon: '[END]', description: '一年学习闭环回顾', season: 'winter' },
] as const;

export function findCurrentFestival(dayIndex: number): WorldFestival | null {
  return FESTIVALS.find((f) => f.day === dayIndex) ?? null;
}

export function findUpcomingFestival(dayIndex: number): WorldFestival | null {
  const upcoming = FESTIVALS.find((f) => f.day > dayIndex);
  return upcoming ?? null;
}

export function daysUntilFestival(dayIndex: number, festival: WorldFestival): number {
  return Math.max(0, festival.day - dayIndex);
}
