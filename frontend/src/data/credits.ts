/**
 * Pythontopia 素材署名数据（结构化版本）。
 *
 * 完整 license 总声明见仓库根 `LICENSE-ART.md`；
 * 简版前端发布声明见 `frontend/public/assets/pixel/LICENSE.md`。
 *
 * 数据用于 `/credits` 页面按 license 分组渲染。
 */

export type CreditCategory =
  | 'tileset'
  | 'character'
  | 'ui'
  | 'bgm'
  | 'sfx'
  | 'font'
  | 'icon'
  | 'effect';

export type CreditLicense =
  | 'CC0'
  | 'CC-BY 3.0'
  | 'CC-BY 4.0'
  | 'CC-BY-SA 3.0'
  | 'OFL-1.1';

export interface CreditEntry {
  readonly category: CreditCategory;
  readonly name: string;
  readonly authors: ReadonlyArray<string>;
  readonly source: string;
  readonly sourceUrl?: string;
  readonly license: CreditLicense;
  readonly usedFor: string;
}

export const CREDITS: ReadonlyArray<CreditEntry> = [
  {
    category: 'tileset',
    name: 'Tiny Town',
    authors: ['Kenney Vleugels'],
    source: 'kenney.nl',
    sourceUrl: 'https://kenney.nl',
    license: 'CC0',
    usedFor: '小镇地面 / 建筑外墙',
  },
  {
    category: 'tileset',
    name: 'Tiny Dungeon',
    authors: ['Kenney Vleugels'],
    source: 'kenney.nl',
    sourceUrl: 'https://kenney.nl',
    license: 'CC0',
    usedFor: 'Bug 矿洞地形',
  },
  {
    category: 'tileset',
    name: 'Roguelike Modern City',
    authors: ['Kenney Vleugels'],
    source: 'kenney.nl',
    sourceUrl: 'https://kenney.nl',
    license: 'CC0',
    usedFor: '小镇街景',
  },
  {
    category: 'tileset',
    name: 'Roguelike Caves',
    authors: ['Kenney Vleugels'],
    source: 'kenney.nl',
    sourceUrl: 'https://kenney.nl',
    license: 'CC0',
    usedFor: '矿洞副本',
  },
  {
    category: 'ui',
    name: 'Pixel UI Pack',
    authors: ['Kenney Vleugels'],
    source: 'kenney.nl',
    license: 'CC0',
    usedFor: '按钮 / 对话框 / 9-slice 面板',
  },

  {
    category: 'character',
    name: 'LPC Sara',
    authors: ['LPC Project', 'bluecarrot16', 'Sharm'],
    source: 'OpenGameArt',
    sourceUrl: 'https://opengameart.org/',
    license: 'CC-BY-SA 3.0',
    usedFor: '玩家角色 832x1344 sheet',
  },
  {
    category: 'character',
    name: 'LPC Medieval Fantasy Characters',
    authors: [
      'LPC Project',
      'Sharm',
      'Reemax',
      'Jetrel',
      'J. Taylor',
      'Ivan Voirol',
      'TheraHedwig',
    ],
    source: 'OpenGameArt',
    license: 'CC-BY-SA 3.0',
    usedFor: 'NPC sprite 集',
  },

  {
    category: 'tileset',
    name: 'Farming Game Basics',
    authors: ['Jana Ochse (2DPIXX)'],
    source: 'OpenGameArt',
    sourceUrl: 'https://opengameart.org/content/farming-game-basics',
    license: 'CC-BY 4.0',
    usedFor: '知识田作物 5 阶段',
  },
  {
    category: 'effect',
    name: 'Pixel Lightning',
    authors: ['Marco L.'],
    source: 'OpenGameArt',
    sourceUrl: 'https://opengameart.org/content/pixel-lightning',
    license: 'CC-BY 4.0',
    usedFor: '雷电特效',
  },
  {
    category: 'tileset',
    name: 'LPC Farm',
    authors: ['bluecarrot16', 'TheraHedwig', 'Ivan Voirol'],
    source: 'OpenGameArt',
    sourceUrl: 'https://opengameart.org/content/lpc-farm',
    license: 'CC-BY 4.0',
    usedFor: '农场围栏与作物',
  },
  {
    category: 'character',
    name: 'Pepper&Carrot 24x32',
    authors: ['David Revoy'],
    source: 'peppercarrot.com',
    license: 'CC-BY 4.0',
    usedFor: '部分 NPC',
  },

  {
    category: 'icon',
    name: 'Game Icons',
    authors: ['Lorc', 'Delapouite', 'Skoll', 'sbed', 'Carl Olsen'],
    source: 'game-icons.net',
    sourceUrl: 'https://game-icons.net',
    license: 'CC-BY 3.0',
    usedFor: 'KC 知识点图标 / 任务图标',
  },

  {
    category: 'font',
    name: 'Ark Pixel Font',
    authors: ['TakWolf'],
    source: 'GitHub',
    sourceUrl: 'https://github.com/TakWolf/ark-pixel-font',
    license: 'OFL-1.1',
    usedFor: '中文像素字体 12px (mono + prop, latin + zh_cn)',
  },
];

/** License 分组元信息：标题、说明、是否需要署名、是否传染。 */
export interface LicenseGroupMeta {
  readonly license: CreditLicense;
  readonly title: string;
  readonly requiresAttribution: boolean;
  readonly shareAlike: boolean;
  readonly note: string;
}

export const LICENSE_GROUPS: ReadonlyArray<LicenseGroupMeta> = [
  {
    license: 'CC0',
    title: 'CC0 1.0（公有领域贡献）',
    requiresAttribution: false,
    shareAlike: false,
    note: '作者放弃版权，无需署名。本项目仍按"推荐致谢"列出。',
  },
  {
    license: 'CC-BY 3.0',
    title: 'CC-BY 3.0（署名 3.0）',
    requiresAttribution: true,
    shareAlike: false,
    note: '必须保留作者署名与协议名称，可商用、可改编，不传染。',
  },
  {
    license: 'CC-BY 4.0',
    title: 'CC-BY 4.0（署名 4.0）',
    requiresAttribution: true,
    shareAlike: false,
    note: '必须保留作者署名与协议名称，可商用、可改编，不传染。',
  },
  {
    license: 'CC-BY-SA 3.0',
    title: 'CC-BY-SA 3.0（署名-相同方式共享 3.0）',
    requiresAttribution: true,
    shareAlike: true,
    note: '必须署名，且基于其修改的作品须以兼容协议发布（传染）。',
  },
  {
    license: 'OFL-1.1',
    title: 'SIL Open Font License 1.1（字体专用）',
    requiresAttribution: true,
    shareAlike: false,
    note: '保留字体内 OFL 协议文本与作者署名，传染仅限字体文件本身。',
  },
];
