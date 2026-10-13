/**
 * 操作指南（/guide）结构化内容源。
 *
 * 指南页完全由本文件驱动渲染：调整章节、文案、截图只改数据，不动组件。
 * 截图 URL 是与截图生成基础设施约定的冻结契约——
 * 开发期文件可能尚未落盘，GuideScreenshot 会优雅降级为文字占位说明。
 */

import { FAST_TRAVEL_ENABLED } from '@/data/fastTravel';

export interface GuideScreenshotSpec {
  /** 冻结契约 URL，前缀固定为 /guide/screenshots/ */
  readonly src: string;
  /** 有意义的中文替代文本；截图缺失时同时作为占位说明展示 */
  readonly alt: string;
  /** 图注 */
  readonly caption: string;
}

export interface GuideKeyRow {
  readonly keys: string;
  readonly desc: string;
}

/** 可视化流程图的单个节点（纯 CSS 渲染，无第三方依赖，永远可见）。 */
export interface GuideDiagramNode {
  /** 节点主标题（如「看」「百晓工坊」）。 */
  readonly label: string;
  /** 可选副标题/说明。 */
  readonly desc?: string;
  /** 可选图标 emoji。 */
  readonly icon?: string;
}

/** 原生客户端下载项（Android APK / Windows 桌面端等）。 */
export interface GuideDownload {
  /** 平台名（如 Android / Windows），也用于 data-testid。 */
  readonly platform: string;
  /** 按钮文案。 */
  readonly label: string;
  /** 下载链接（静态托管于 /downloads/ 下，发布时落盘）。 */
  readonly href: string;
  /** 副说明：系统要求 / 安装提示。 */
  readonly note?: string;
  /** 可选图标 emoji。 */
  readonly icon?: string;
  /** 后端下载计数 key（如 android_apk）；有值时在按钮旁展示累计次数。 */
  readonly statKey?: string;
}

export type GuideBlock =
  | { readonly kind: 'paragraph'; readonly text: string }
  | { readonly kind: 'list'; readonly items: readonly string[] }
  | { readonly kind: 'steps'; readonly items: readonly string[] }
  | { readonly kind: 'keys'; readonly rows: readonly GuideKeyRow[] }
  | { readonly kind: 'screenshot'; readonly shot: GuideScreenshotSpec }
  | { readonly kind: 'tip'; readonly text: string }
  | { readonly kind: 'download'; readonly items: readonly GuideDownload[] }
  /** 可视化流程图：节点用箭头串联，row=横向、col=纵向；可选标题与图注。 */
  | {
      readonly kind: 'diagram';
      readonly title?: string;
      readonly flow: 'row' | 'col';
      readonly nodes: readonly GuideDiagramNode[];
      readonly caption?: string;
    };

export interface GuideSubsection {
  readonly id: string;
  readonly title: string;
  readonly blocks: readonly GuideBlock[];
}

export interface GuideSectionContent {
  readonly id: string;
  /** 正文完整标题 */
  readonly title: string;
  /** 侧栏 / 大纲使用的短标题 */
  readonly navTitle: string;
  /** 「是什么」概述（1-2 句） */
  readonly summary: string;
  /** 与「看 → 学 → 练 → 测」学习闭环的关系 */
  readonly loop: string;
  /** 主题主截图 */
  readonly heroShot: GuideScreenshotSpec;
  readonly subsections: readonly GuideSubsection[];
}

export interface GuideNavGroup {
  readonly id: string;
  readonly label: string;
  readonly description: string;
  readonly sectionIds: readonly string[];
}

/** 全站目录只在这里分组，不改变各主题既有 URL 与排序编号。 */
export const GUIDE_NAV_GROUPS: readonly GuideNavGroup[] = [
  {
    id: 'quick-start',
    label: '快速开始',
    description: '先会走，再知道下一步去哪',
    sectionIds: ['town-basics', 'ai-guide', 'mainline'],
  },
  {
    id: 'learning-loop',
    label: '看学练测',
    description: '完成一次真实学习闭环',
    sectionIds: ['loop', 'library', 'workshop', 'training', 'endless', 'bug-mine', 'review-hall'],
  },
  {
    id: 'growth-teaching',
    label: '成长与教学',
    description: '查看成长证据并连接班级',
    sectionIds: ['profile', 'teacher-classroom', 'clients'],
  },
];

const SCREENSHOT_BASE = '/guide/screenshots';

/**
 * 小镇全部快捷键（与 HUD 实际键位保持一致）。
 * 休眠玩法的键位不写进指南：R-14 单 hub 下「T · 传送」已无可去之处（见 data/fastTravel.ts），
 * 指南继续宣传它就是把学生指向一个打不开的面板。
 */
const ALL_TOWN_HOTKEYS: readonly GuideKeyRow[] = [
  { keys: 'WASD / 方向键', desc: '移动（支持八方向）' },
  { keys: 'F / Enter', desc: '与 NPC 交谈 / 进入建筑' },
  { keys: 'Tab', desc: '快捷轮盘' },
  { keys: 'ESC', desc: '暂停菜单（所有入口都在这里）' },
  { keys: 'Q', desc: '任务面板' },
  { keys: 'J', desc: '对话记录' },
  { keys: 'M', desc: '大地图' },
  { keys: 'K', desc: '技能面板' },
  { keys: 'L', desc: '课程面板' },
  { keys: 'Shift + A', desc: '无尽远征（小镇内单按 A 是向左移动）' },
  { keys: 'T', desc: '传送' },
  { keys: 'C', desc: '休息' },
  { keys: ',', desc: '设置' },
];

const TOWN_HOTKEYS: readonly GuideKeyRow[] = ALL_TOWN_HOTKEYS.filter(
  (row) => row.keys !== 'T' || FAST_TRAVEL_ENABLED,
);

const ALL_GUIDE_SECTIONS: readonly GuideSectionContent[] = [
  {
    id: 'town-basics',
    title: '小镇操作：在 Pythontopia 里行走与交互',
    navTitle: '小镇操作',
    summary:
      'Pythontopia 把整门《Python语言基础》课装进一座中央小镇：图书馆、百晓工坊、训练场、'
      + 'Bug 矿洞、复盘大厅都是镇上的建筑。掌握移动、交谈和暂停菜单，你就能去任何想去的地方。',
    loop: '小镇是「看 → 学 → 练 → 测」四个环节的总入口：每栋建筑对应闭环中的一环，走进哪栋建筑，就进入哪个环节。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/01-town-hub.webp`,
      alt: '中央小镇全景：玩家角色站在小镇广场上，四周分布着图书馆、百晓工坊、训练场等建筑入口',
      caption: '中央小镇 town_hub——所有学习场所都在同一张地图上，步行即可到达。',
    },
    subsections: [
      {
        id: 'town-basics-loop',
        title: '学习闭环：看 → 学 → 练 → 测',
        blocks: [
          {
            kind: 'paragraph',
            text:
              'Pythontopia 的一切都围绕「看 → 学 → 练 → 测」四步闭环展开，'
              + '每一步都对应小镇里的一处场所，走进哪栋建筑就进入哪一环：',
          },
          {
            kind: 'diagram',
            title: '看学练测 · 核心学习闭环',
            flow: 'row',
            nodes: [
              { icon: '📖', label: '看', desc: '课程图书馆：视频 / PPT / 课件问答' },
              { icon: '🛠️', label: '学', desc: '百晓工坊：多智能体生成讲义/导图/练习' },
              { icon: '⌨️', label: '练', desc: '训练场 / 例题：真题判题机评测' },
              { icon: '🎯', label: '测', desc: '掌握度达阈值 / 章末测试' },
            ],
            caption: '做错了去 Bug 矿洞调试，阶段性回顾去复盘大厅——它们是闭环的两条支线。',
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/live-town-hub.png`,
              alt: 'Pythontopia 中央小镇实拍：玩家角色在小镇广场，四周是图书馆、工坊、训练场等建筑与往来的 NPC',
              caption: '中央小镇实拍——所有学习场所都在同一张地图上，步行即可到达。',
            },
          },
        ],
      },
      {
        id: 'town-basics-move',
        title: '移动与交互',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '用 WASD 或方向键控制角色移动，支持八方向行走。走到 NPC 或建筑门口附近时，'
              + '画面会出现交互提示，按 F 或 Enter 即可交谈、进入建筑。',
          },
          {
            kind: 'list',
            items: [
              '和 NPC 交谈：靠近后按 F / Enter，对话中用方向键或鼠标选择选项',
              '进入建筑：站到门口高亮处按 F / Enter，切换到对应的学习页面',
              '离开学习页面：点击页面里的「返回小镇」按钮即可回到镇上',
            ],
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/13-npc-dialog.webp`,
              alt: '与镇长 Py 对话：底部木质对话框展示 NPC 台词与可选回应按钮',
              caption: '与 NPC 对话：E / Enter 推进台词，出现选项时点按钮或用 ↑↓ 选择。',
            },
          },
          {
            kind: 'paragraph',
            text:
              '小镇里的每位 NPC 都有自己的职责：镇长 Py 负责主线引导与每周复盘提醒，'
              + '图书馆的 Doc 馆长守着课件，工坊师傅会介绍百晓工坊，复盘师 Sage 在复盘大厅等你。'
              + '多和他们聊聊，很多功能入口和小提示都藏在对话里。',
          },
        ],
      },
      {
        id: 'town-basics-hotkeys',
        title: '快捷键速查',
        blocks: [
          {
            kind: 'paragraph',
            text: '全部快捷键如下。记不住也没关系——按 ESC 打开暂停菜单，所有功能入口都在里面。',
          },
          { kind: 'keys', rows: TOWN_HOTKEYS },
        ],
      },
      {
        id: 'town-basics-pause',
        title: '暂停菜单',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '按 ESC 随时打开暂停菜单。菜单按「继续 / 学习 / 我 / 系统」四组组织：'
              + '「学习」组放图书馆、训练场、百晓工坊、复盘大厅等入口；'
              + '「我」组放个人画像与冒险地图；「系统」组负责存档、设置，以及你正在读的这本指南。',
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/02-pause-menu.webp`,
              alt: 'ESC 暂停菜单：按「继续、学习、我、系统」四组排列的像素风菜单按钮',
              caption: '暂停菜单是最稳妥的导航方式，迷路时按 ESC 总能找到出口。',
            },
          },
          {
            kind: 'tip',
            text: '迷路时的万能公式：ESC → 选「学习」或「我」分组 → 点目标入口。',
          },
        ],
      },
    ],
  },
  {
    id: 'ai-guide',
    title: 'AI 助教：你的随身导学助手',
    navTitle: 'AI 助教',
    summary:
      'AI 助教（也叫随身助教、导学助手）是你的私人领路人：它清楚你的学习进度与个人画像，'
      + '能直接回答「下一步去哪、做什么、为什么」，也能就地解答 Python 疑问和小镇玩法，'
      + '并一键把你带到该去的页面。',
    loop: '卡在闭环任意一环时先问 AI 助教：它会判断你该回去「看」、深入「学」、动手「练」，还是去检验「测」，并把你直接送过去。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/03-ai-guide.webp`,
      alt: 'AI 助教对话面板：助教正根据学生当前进度推荐下一步学习行动，并给出可点击的跳转入口',
      caption: 'AI 助教结合你的进度与画像，给出「下一步去哪、为什么去」的具体建议。',
    },
    subsections: [
      {
        id: 'ai-guide-what',
        title: '什么时候应该找 AI 导学助手',
        blocks: [
          {
            kind: 'paragraph',
            text:
              'AI 导学助手会综合你的主线进度、个人画像，以及当前视频、课件页、题目与知识点来理解处境。'
              + '它先判断你卡在「看、学、练、测」哪一步，再给一个现在就能执行的下一步，而不是丢给你一段通用答案。',
          },
          {
            kind: 'list',
            items: [
              '不知道下一步：结合课程进度与掌握度，说明「去哪里、做什么、为什么现在做」',
              '概念没听懂：贴着当前材料换一种讲法，并用提问确认你真正卡住的位置',
              '题目不会做：分阶段给审题、思路、骨架和调试提示，让你保留独立完成代码的过程',
              '在小镇迷路：解释建筑与系统用途，并给出可点击的页面入口',
              '需要深入学习：把当前目标交给百晓工坊，继续生成讲义、导图、练习或学习计划',
            ],
          },
          {
            kind: 'diagram',
            title: '做题页 · AI 导学助手分阶段引导',
            flow: 'row',
            nodes: [
              { icon: '🔎', label: '审题导读', desc: '读懂题目要什么' },
              { icon: '💡', label: '思路分析', desc: '拆解步骤 + 追问' },
              { icon: '🧱', label: '骨架代码', desc: '一键插入编辑器' },
              { icon: '🐞', label: '错因诊断', desc: '提交失败时定位根因' },
              { icon: '🏆', label: 'AC 复盘', desc: '通过后总结 + 迁移练习' },
            ],
            caption:
              '每阶段都给「下一步」选项按钮，按你的画像因材施教、只给思路不给完整答案；'
              + '还能随时「画一下 / 拼装挑战 / 知识点复盘」。',
          },
          {
            kind: 'paragraph',
            text:
              '完整辅导链是「审题导读 → 思路分析 → 骨架代码 → 错因诊断 → AC 复盘」。'
              + '你可以逐段推进，也可以在某一步停下来追问；通过后再用迁移练习确认自己不是只记住了这一题。',
          },
        ],
      },
      {
        id: 'ai-guide-open',
        title: '怎么呼出 AI 助教',
        blocks: [
          {
            kind: 'steps',
            items: [
              '在小镇里随时按 G 键，呼出 / 收起 AI 助教',
              '大多数学习页面的右下角，都有常驻的助教浮层按钮',
              '主线推进到关键节点时，助教也会主动弹出指引',
              '也可以按 ESC 打开暂停菜单，选「AI 助教」打开完整对话',
            ],
          },
          {
            kind: 'paragraph',
            text:
              '在输入框里用中文直接说出你的目标或困惑即可，例如「我现在该学什么」「for 循环没看懂」。'
              + '回答支持随时停止、重试，多轮对话也会被记住。',
          },
          {
            kind: 'list',
            items: [
              '问路径：「我刚学完列表，下一步先练什么？」',
              '问概念：「用一个生活例子解释 for 循环和 while 循环的区别」',
              '问题目：「先帮我读懂题意，不要直接写答案」',
              '问调试：「这次 Wrong Answer 更像边界条件还是循环范围问题？」',
            ],
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/14-ai-guide-plan.webp`,
              alt: 'AI 助教给出的看学练测四步学习计划，每一步带「去上课 / 去工坊 / 去训练场 / 去题库」跳转按钮',
              caption: '说出学习目标后，助教会拆成「看 → 学 → 练 → 测」四步计划，每步都能一键直达。',
            },
          },
          {
            kind: 'paragraph',
            text:
              '当你说出一个学习目标（比如「我想从第 1 章开始学」），助教会把它拆成'
              + '「看 → 学 → 练 → 测」四个步骤的计划：看视频、读讲义、动手练习、检验掌握。'
              + '每一步右侧都有跳转按钮——「去上课」「去工坊」「去训练场」「去题库」，点了直接落地，'
              + '不用自己再找路。如果你的目标太模糊，它会先反问一句帮你聚焦，再给计划。',
          },
        ],
      },
      {
        id: 'ai-guide-inline',
        title: '看视频、读课件、做题时的就地助教',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '在主线小节的视频页、课件阅读器和训练场做题页，右侧还内置了「就地助教」。'
              + '这时你不必重新描述背景：助教会紧扣当前材料回答，并把课件依据、学习目标与刚才的对话连续起来。',
          },
          {
            kind: 'tip',
            text:
              '做题与考试场景的助教只给思路与提示，不会直接给出可复制提交的完整答案；'
              + '章末大测试、Bug 矿洞等需要专注或防作弊的页面会暂时收起助教，这是刻意设计。',
          },
        ],
      },
      {
        id: 'ai-guide-loop-progress',
        title: '它怎样把聊天变成导学循环',
        blocks: [
          {
            kind: 'paragraph',
            text:
              'AI 导学助手不是一个泛泛聊天框，而是一条有边界的导学循环。'
              + '它会把你的原话先放进当前章节、知识点、画像和页面上下文里判断，再决定是带路、讲概念、拆题、规划，还是先追问。',
          },
          {
            kind: 'diagram',
            title: '目标识别 → 澄清 → 计划 → 执行 → 复盘',
            flow: 'row',
            nodes: [
              { icon: '🎯', label: '目标识别', desc: '判断你是在找路、问概念、做题还是复盘' },
              { icon: '❓', label: '澄清', desc: '目标太宽时先问清楚，不硬塞通用答案' },
              { icon: '🧭', label: '计划', desc: '拆成看、学、练、测的可执行步骤' },
              { icon: '🔗', label: '执行', desc: '带着章节 / KC 上下文跳到目标页面' },
              { icon: '📌', label: '复盘', desc: '把结果回收到进度、画像与下一步建议' },
            ],
            caption:
              '这条循环的价值在于「闭环」：不是回答完就结束，而是把你推进到下一次真实学习行为。',
          },
          {
            kind: 'list',
            items: [
              '状态可见：流式回复时输入框会锁定，完成后自动恢复；计划卡会显示可点击步骤，而不是只给一段建议',
              '可中断：回复跑偏或目标已变时，可以停止当前回合，保留已产生的信息再重新提问',
              '可重试：网络或后端偶发失败时会显示失败态与重试按钮，失败不会被静默吞掉',
              '有证据边界：涉及课件内容时优先使用课程包证据；涉及做题时坚持分阶段提示，不绕过你的练习过程',
            ],
          },
          {
            kind: 'tip',
            text:
              '最有效的问法是同时给「目标 + 当前状态 + 约束」：例如「我刚看完 input()，能运行代码但题不会拆，下一步先练什么？」。',
          },
        ],
      },
      {
        id: 'ai-guide-vs-workshop',
        title: 'AI 助教 与 百晓工坊 有什么区别',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '一句话：AI 助教是「随身的快速向导」，在任何页面都能呼出，问下一步、问思路、求带路；'
              + '百晓工坊是「专门的 AI 工作室」，适合更深入的答疑，还能为你生成讲义、思维导图、练习等学习资源。'
              + '两者其实是同一位 AI 伙伴——需要深入时，助教也会把你带进工坊。',
          },
          {
            kind: 'tip',
            text:
              '判断方法：只想知道「现在怎么办」就先问助教；需要一份可反复使用的学习产物，就让助教把当前知识点和学习目标一起带进百晓工坊。',
          },
        ],
      },
    ],
  },
  {
    id: 'mainline',
    title: '游戏主线：从新生到毕业，走通七章 Python 之旅',
    navTitle: '主线任务',
    summary:
      '你以新生身份来到 Pythontopia。镇长 Py 把整座小镇交给你当「学习营地」，'
      + '你的使命是沿冒险地图走通《Python语言基础》七章、通过每一章的大测试，'
      + '最终在毕业典礼上学成出师。',
    loop: '主线是闭环的骨架：每个小节关卡内部就是一次完整的「看 → 学 → 练」，章末大测试负责「测」，七章全部通关即可迎来毕业典礼。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/04-mainline-map.webp`,
      alt: '主线冒险地图：七个章节按路线排列，已解锁的小节点亮、未解锁的小节置灰',
      caption: '冒险地图就是课程目录的游戏化形态：一章一片区域，一小节一个关卡。',
    },
    subsections: [
      {
        id: 'mainline-story',
        title: '故事背景',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '一进城，镇长 Py（自称「Python 之父的化身」）就会迎上来，'
              + '告诉你这座小镇就是你的学习营地：抬脚就能到图书馆、百晓工坊、训练场、Bug 矿洞和复盘大厅，'
              + '不用东奔西跑。他会把第一章的任务交到你手上——你的 Python 冒险，从这里开始。',
          },
          {
            kind: 'paragraph',
            text:
              '主线没有打打杀杀，真正的「关卡」是七章课程知识本身：每学懂一节、每通过一场大测试，'
              + '你就在镇上更进一步，直到七章学成、走上毕业典礼。',
          },
        ],
      },
      {
        id: 'mainline-cast',
        title: '你会遇到谁',
        blocks: [
          {
            kind: 'list',
            items: [
              '镇长 Py —— 小镇的引路人，发放主线任务、随时跟你聊进度',
              '馆长 Doc —— 图书馆管理者，带你查阅 561 页课件',
              '工坊师傅 —— 百晓工坊的主人，AI 伙伴「百晓」在这里陪你学',
              '教务 Q —— 训练场的教练，陪你刷题练手',
              '矿头 Bug 与守矿人 —— Bug 矿洞的引导者，陪你把错题一层层挖通',
              '复盘师 Sage —— 复盘大厅的导师，带你阶段复盘、巩固记忆',
              '教授 —— 坐镇章末大测试与终极挑战的出题人',
              '学生 A —— 带着 bug 代码来求教的 AI 学弟，等你在「费曼反演」里教他',
              '班长 —— 班级公告板的联络人，把老师布置的班级任务带给你',
            ],
          },
        ],
      },
      {
        id: 'mainline-chapters',
        title: '七章冒险地图',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '冒险地图按课程的七个章节铺开，由浅入深、循序解锁——通过上一章的大测试，才会点亮下一章：',
          },
          {
            kind: 'list',
            items: [
              '第 1 章 · 计算工具与计算思维 —— 认识计算机，建立「用代码解决问题」的思路',
              '第 2 章 · Python 语言基础 —— 装好环境、写出第一个程序，掌握变量、类型与输入输出',
              '第 3 章 · 信息表示及计算 —— 进制与编码、数值与字符串处理',
              '第 4 章 · 流程自动化 —— 顺序、分支、循环与嵌套，让程序自己跑起来',
              '第 5 章 · 数据结构 —— 列表、字典、集合，学会组织数据',
              '第 6 章 · 数据存取与分析 —— 文件读写与简单的数据分析',
              '第 7 章 · 归纳与抽象 —— 函数进阶、递归、模块与包、面向对象，为旅程收尾',
            ],
          },
        ],
      },
      {
        id: 'mainline-map',
        title: '打开冒险地图',
        blocks: [
          {
            kind: 'steps',
            items: [
              '按 ESC 打开暂停菜单，选「我 → 冒险地图」（小镇内也可按 Q 查看当前任务）',
              '地图上点亮的小节代表已解锁，置灰的需要先完成前置小节',
              '点击已解锁的小节，进入对应的小节学习页',
            ],
          },
        ],
      },
      {
        id: 'mainline-stage',
        title: '一个小节怎么过',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '每个小节按「视频 → 课件 → 例题」三步走：先看本节的视频讲解，'
              + '再读课件原页巩固概念，最后在页面内完成配套例题练习。'
              + '三步全部完成，小节即告通关，主线进度自动推进。',
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/05-stage-learning.webp`,
              alt: '小节学习页三件套界面：视频讲解、课件原页阅读与例题练习入口并列展示',
              caption: '看 · 视频讲解：选集播放 + 累计观看计时，右侧可以就这段视频直接提问。',
            },
          },
          {
            kind: 'paragraph',
            text:
              '「学 · 课件阅读」会把本节对应的课件页装进阅读器：左右翻页、进度自动记录，'
              + '读到哪一页都算数；不明白的地方随时切到右侧就地助教提问。',
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/15-stage-reading.webp`,
              alt: '小节学习页的课件阅读步骤：课件原页 + 阅读进度 + 右侧就地提问入口',
              caption: '学 · 课件阅读：本节课件页逐页阅读，进度自动记录。',
            },
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/16-stage-practice.webp`,
              alt: '小节学习页的随堂练习步骤：题目卡片与作答区，完成后小节即通关',
              caption: '练 · 随堂练习：做完本节配套题，小节通关、主线进度自动推进。',
            },
          },
        ],
      },
      {
        id: 'mainline-boss',
        title: '章末大测试与 BOSS 挑战',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '一章的小节全部通关后，解锁章末挑战：章末大测试限时作答、考试期间禁用 AI 协助；'
              + '闯关塔与章末 BOSS 则用 AI 动态出题，检验你对整章的真实掌握。通过大测试，本章即点亮星星、解锁下一章。',
          },
          {
            kind: 'tip',
            text: '主线不强制一口气打完——随时可以离开去图书馆补「看」、去训练场补「练」，回来继续推进。',
          },
        ],
      },
      {
        id: 'mainline-stars',
        title: '星级与补星',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '每一章可获得 0–3 颗星：通过章末大测试拿到第一颗，章内小节与知识点掌握得越扎实，星越多。'
              + '一时没拿满也不要紧——去错题矿洞做一次错题复盘，或在无尽挑战里稳定发挥，都能把缺的星补回来。',
          },
        ],
      },
      {
        id: 'mainline-graduation',
        title: '终局 · 毕业典礼',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '当七章全部至少拿到一颗星（即通过全部章末大测试）、并打通第七章的终极 BOSS，'
              + '你就会迎来 Pythontopia 的毕业典礼——披上毕业袍，为这段 Python 之旅画上句点。',
          },
        ],
      },
    ],
  },
  {
    id: 'loop',
    title: '看学练测闭环：把零散功能拧成一条主线',
    navTitle: '看学练测闭环',
    summary:
      '小镇里的图书馆、百晓工坊、训练场、无尽闯关不是各玩各的——'
      + '它们被拧成一条「看 → 学 → 练 → 测」的闭环：看懂、学透、练会、测准，再把结果回流到下一轮。',
    loop:
      '这一篇就是整条闭环的总览：后面每一篇都是其中某一环的放大；'
      + '读不下去时回到这张图，就知道自己正卡在「看、学、练、测」的哪一步。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/27-loop-mastery.webp`,
      alt: '看学练测闭环回流示意：一次闯关 / 练习的结果汇成 KC 掌握度，更新八维画像，并把该补的弱项知识点推荐回来',
      caption: '闭环之所以「闭」：练与测的真实结果会回流成掌握度与画像，再反过来决定你下一步看什么、练什么。',
    },
    subsections: [
      {
        id: 'loop-overview',
        title: '一条主线：看 → 学 → 练 → 测',
        blocks: [
          {
            kind: 'paragraph',
            text:
              'Pythontopia 把一个知识点的学习拆成四个动作，每个动作都有专门的场所承接，'
              + '彼此首尾相连，构成一次完整的学习闭环：',
          },
          {
            kind: 'diagram',
            title: '看学练测 · 统一闭环（含无尽闯关）',
            flow: 'row',
            nodes: [
              { icon: '📖', label: '看', desc: '图书馆课件 / 视频' },
              { icon: '🛠️', label: '学', desc: '百晓工坊生成讲义 / 导图 / 练习' },
              { icon: '⌨️', label: '练', desc: '训练场判题 + 无尽闯关连续刷题' },
              { icon: '🎯', label: '测', desc: '掌握度达阈值 / 章末测试 / BOSS' },
            ],
            caption: '「看」不只是读字：看不懂就回图书馆课件 / 视频，再带着问题去百晓工坊深问。',
          },
          {
            kind: 'paragraph',
            text:
              '四步里最容易被跳过的是「学」：很多人课件读了就直接去做题，'
              + '没在百晓工坊把知识点整理成贴合自己的讲义 / 导图，结果练起来频频卡壳；'
              + '而「练」也不止训练场的单题，无尽闯关用 AI 连续出题，把同一类知识点练到稳。',
          },
        ],
      },
      {
        id: 'loop-feedback',
        title: '闭环为什么「闭」：结果会回流',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '真正让它成为「闭环」而不是「四步清单」的，是结果回流：你在「练」和「测」里的每一次提交，'
              + '都会被折算成知识点（KC）掌握度，并更新你的八维画像；画像又反过来决定系统给你看什么、出什么题。',
          },
          {
            kind: 'diagram',
            title: '结果回流 · 越练越懂你',
            flow: 'row',
            nodes: [
              { icon: '⌨️', label: '练 / 测', desc: '训练场、无尽闯关、章末 BOSS' },
              { icon: '📈', label: 'KC 掌握度', desc: '通过 / 失败折算到每个知识点' },
              { icon: '🧭', label: '八维画像', desc: '易错模式、实践能力等被动更新' },
              { icon: '🎁', label: '弱项推荐', desc: '优先把薄弱 KC 推回来再看 / 再练' },
            ],
            caption: '这条回流由判题结果与画像信号驱动，不是问卷自评——越练，系统越懂你。',
          },
          {
            kind: 'list',
            items: [
              '看：图书馆读课件、看视频，把当前知识点先弄懂',
              '学：把看过的内容带进百晓工坊，生成贴合画像的讲义、导图与练习',
              '练：训练场提交真题判题，或在无尽闯关里连续刷题、连胜拉高难度',
              '测：掌握度达阈值、通过章末大测试或 BOSS，即点亮进度',
              '回流：练与测的结果更新 KC 掌握度与八维画像，并推荐下一个弱项',
            ],
          },
          {
            kind: 'tip',
            text:
              '不确定现在卡在哪一环时，按 G 问 AI 助教——它会判断你该回去「看」、深入「学」、'
              + '动手「练」还是去「测」，并一键带你过去。',
          },
        ],
      },
      {
        id: 'loop-jump',
        title: '随时换挡：四环之间可以互相跳',
        blocks: [
          {
            kind: 'paragraph',
            text: '闭环不是只能单向走一圈。哪一环卡住，就近跳到能帮你的那一环，是最高效的用法：',
          },
          {
            kind: 'list',
            items: [
              '练 → 调试：训练场里提交没过，进 Bug 矿洞逐层定位、修复、验证，把错题变成调试副本',
              '练 → 学：连续做错同一类题，回百晓工坊就这个知识点生成讲义与针对性练习',
              '测 → 练：章末测试暴露的弱项 KC，会被推回训练场 / 无尽闯关重点补练',
              '看 → 学 → 练：从图书馆某页课件出发，带着这页证据去工坊深问，再去训练场上手',
            ],
          },
          {
            kind: 'tip',
            text: '哪一环卡住就近跳到能帮你的那一环，比从头到尾死磕一道题高效得多。',
          },
        ],
      },
    ],
  },
  {
    id: 'library',
    title: '图书馆：看课件、查资料的主阵地',
    navTitle: '图书馆',
    summary:
      '图书馆收录本课程全部 7 份课件、共 561 页，按七个章节组织。'
      + '除了翻页阅读，还内置课件检索：输入问题直接命中相关课件页，并标注出处页码。',
    loop: '图书馆承担闭环里的「看」：后续所有的学、练、测，都建立在课件这个唯一知识底座之上。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/06-library.webp`,
      alt: '图书馆课件阅读器：左侧是章节目录，中间是课件页面，右侧是检索结果与出处页码',
      caption: '图书馆收录 7 份课件共 561 页，检索结果都会标注出处页码。',
    },
    subsections: [
      {
        id: 'library-read',
        title: '阅读课件',
        blocks: [
          {
            kind: 'steps',
            items: [
              '走进小镇图书馆，或按 ESC 选「学习 → 课程图书馆」',
              '在左侧章节目录选择章节，正文区按页阅读，支持上一页 / 下一页与页码跳转',
              '页面文本可以直接选中复制，方便摘抄与记笔记',
            ],
          },
        ],
      },
      {
        id: 'library-search',
        title: '课件检索（RAG）',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '在检索框输入问题（例如「input 函数怎么用」），系统会在 561 页课件中找出最相关的片段，'
              + '每条结果都标注来源页码，点击即可跳转原页。检索答案永远以课件原文为证据，不会凭空编造。',
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/17-library-rag.webp`,
              alt: '课件检索结果列表：每条命中片段都带「第 N 页」出处徽标，点击可跳转课件原页',
              caption: '检索「input函数」：每条证据都标注出处页码，点击直达课件原页。',
            },
          },
          {
            kind: 'tip',
            text:
              '检索支持中英混写（如「input函数」「for循环」）。'
              + '想顺着证据深挖，点结果条目跳到原页继续往后读效果最好。',
          },
        ],
      },
    ],
  },
  {
    id: 'workshop',
    title: '百晓工坊：让 AI 伙伴「百晓」陪你学',
    navTitle: '百晓工坊',
    summary:
      '百晓工坊是镇上 AI 学习伙伴「百晓」的工作室：想学什么直接说，它能答疑、出题、'
      + '生成学习资源、规划路线、带你去地方。「能力一览」里共有 16 项本领、分成四组，'
      + '外加一整套边问边学的互动体验——远不止四个按钮那么简单。',
    loop: '工坊承担闭环里的「学」：把你「看」过的课件加工成贴合画像的讲解与练习，为「练」做好准备，也能一站式串起看、学、练、测。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/07-workshop.webp`,
      alt: '百晓工坊对话界面：学生提问后，AI 伙伴百晓正在生成讲解与配套练习，并附上课件页码',
      caption: '在工坊提出问题，百晓会生成讲解、导图或练习，并引用课件页码作为依据。',
    },
    subsections: [
      {
        id: 'workshop-ask',
        title: '带着目标进入工坊',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '百晓工坊接受三类输入：自然语言、@ 知识点和上传文件。你只要说明想解决的问题，'
              + '不必先理解背后有哪些智能体或命令；需要精确控制时，再用「/」点名能力。',
          },
          {
            kind: 'steps',
            items: [
              '按 ESC 选「学习 → 百晓工坊」进入（在小镇里找工坊师傅也能进）',
              '直接用人话说出你的问题或目标，例如「列表怎么用」「帮我规划这周」',
              '想点名要哪种帮助，在输入框敲「/」从能力菜单里挑，比如 /练习题、/思维导图',
              '想锚定某个知识点，用「@」把它带进问题里',
              '对话还空着时，点欢迎屏上的快捷提问气泡也能一键发问',
              '右上角可随时「开新对话」，之前聊过的也会留着',
            ],
          },
          {
            kind: 'diagram',
            title: '多智能体工作室 · 一次提问的流水线',
            flow: 'row',
            nodes: [
              { icon: '🙋', label: '你提问', desc: '说人话 / 选 @ 知识点' },
              { icon: '📚', label: '馆员', desc: '课件 RAG 检索证据' },
              { icon: '✍️', label: '导师/设计师', desc: '生成讲解 / 资源' },
              { icon: '🔍', label: '三审', desc: '引用·防幻觉·安全' },
              { icon: '📒', label: '入书房', desc: '产物沉淀可复用' },
            ],
            caption: '右侧「多智能体工作室」会实时点亮每个在岗的小人，并在每阶段旁概述「成果」。',
          },
          {
            kind: 'paragraph',
            text:
              '收到请求后，工坊会先理解意图，从 16 项能力中选择合适的处理方式；'
              + '如果目标太宽，它会先追问范围，如果识别偏了，你可以用纠错标签立刻切换能力。',
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/live-workshop-answer.png`,
              alt: '百晓工坊实拍：学生问「什么是列表推导式」，百晓给出带代码块的详细讲解，正文里标注课件「[页 25]」可点跳转，右侧多智能体工作室列出全员阵容',
              caption: '工坊实拍：百晓的回答带课件页码引用（正文「[页 25]」可点直达原文），右侧是多智能体工作室阵容。',
            },
          },
        ],
      },
      {
        id: 'workshop-abilities',
        title: '16 项能力如何匹配你的请求',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '展开输入框上方的「能力一览」，能看到百晓的全部 16 项本领，按用途分成四组——'
              + '「四组」只是分类，真正能用的是这 16 项：',
          },
          {
            kind: 'list',
            items: [
              'AI 助教（4 项）—— 问答（随时提问，附课本依据）、考考我（多轮填空小测，在题目气泡里直接填空、边填边评、循序渐进）、讲代码（逐行解释一段代码）、修报错（定位报错根因，引导你自己修）',
              '生成资源（6 项）—— 讲义、思维导图、练习题、能在网页里直接运行的代码案例、拓展阅读、动画分镜脚本',
              '看讲解（1 项）—— B 站精选讲解视频卡片',
              '学习管家（5 项）—— 学习规划、学情报告、带路、找资料、复习',
            ],
          },
          {
            kind: 'tip',
            text: '不必背命令：直接说人话，百晓会自己判断该用哪项本领；万一理解偏了，你也能一键纠正。',
          },
        ],
      },
      {
        id: 'workshop-interactive',
        title: '多智能体怎样完成一次任务',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '工坊不是「问一句、答一段」就完了。一次完整任务会经过课件证据、内容生成、引用审查、防幻觉与安全审查，'
              + '再把通过检查的结果流式交给你；右上方点「进度」可以查看当前步骤、课件命中和失败提示。',
          },
          {
            kind: 'list',
            items: [
              '边想边答：回答一字一句实时蹦出来，过程看得见，随时可点「⏹ 停止」',
              '进度可追踪：时间线会留下检索、生成、审查、外部查证等必要步骤',
              '为你定制：贴合你画像的回答会标上「✨ 为你定制」',
              '换个讲法：没看懂就点「图解 / 逻辑链 / 示例上手」，让百晓换种方式重讲一遍',
              '先问清楚：问题太笼统时，它会先反问澄清，而不是硬塞一份通用答案',
              '一键纠正：如果它理解偏了，点一下纠错小标签就能改成你真正想要的本领',
              '轻反馈与重试：每条回答都能点 👍 / 👎，不满意还能「↻ 重试」',
            ],
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/18-workshop-agents.webp`,
              alt: '百晓工坊处理进度：展示一次学习规划的检索、生成与审查步骤',
              caption: '点「进度」查看当前步骤、课件命中和失败提示。',
            },
          },
          {
            kind: 'paragraph',
            text:
              '目标较大时，点「进度」查看当前做到哪一步。需要处理时可以继续留在对话中，结果会自动出现；'
              + '失败时按提示重试即可。',
          },
        ],
      },
      {
        id: 'workshop-resources',
        title: '会交付哪些学习产物',
        blocks: [
          {
            kind: 'paragraph',
            text: '除了即时答疑，百晓还会把内容做成可收藏、可复用的卡片：',
          },
          {
            kind: 'list',
            items: [
              '讲义、拓展阅读：成段讲解 + 课件页引用',
              '思维导图：自动画成可视化的知识结构图',
              '练习题：1–3 道题，留白给你做，不直接给完整答案',
              '代码案例：能在网页里直接运行、当场看结果',
              '动画分镜脚本：把一个概念拆成 3–5 个分镜',
              '学习规划会生成「计划卡」并随你推进逐步点亮每一步；学情报告会生成「学情卡」一图看强弱项',
            ],
          },
          {
            kind: 'tip',
            text: '生成的资源可以保存到「我的书房」继续复用，之后用 /找资料 随时翻回来，不必在历史对话里逐条寻找。',
          },
        ],
      },
      {
        id: 'workshop-upload',
        title: '用自己的文件继续学',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '遇到看不懂的代码、跑不通的报错日志，或一份想吃透的讲义？把文件直接拖进工坊输入框，'
              + '百晓就会就着你上传的内容回答——你自己的 .py 错在哪、这页 PDF 讲了什么，都能问。',
          },
          {
            kind: 'steps',
            items: [
              '点输入框的 📎 按钮，或把文件拖到输入框（也支持直接粘贴）',
              '支持 .py / .txt / .md / .csv / .pdf / .docx，单个文件 ≤ 5MB',
              'PDF、Word 会自动抽取文字，解析好后变成一枚可删除的小卡片',
              '连同你的问题一起发送，百晓便结合文件内容作答',
            ],
          },
          {
            kind: 'list',
            items: [
              '答疑上下文：上传内容作为「你提供的资料」喂给百晓，并始终标注来源，绝不冒充课件页码',
              '个人资料库：上传的文档会收进你的个人语料，之后可被语义检索复用，与全课程课件相互独立',
            ],
          },
          {
            kind: 'tip',
            text: '上传内容只用于帮你答疑，不会被当成课件证据，也不会替你写出作业的完整答案。',
          },
        ],
      },
      {
        id: 'workshop-output',
        title: '证据、边界与学习安全',
        blocks: [
          {
            kind: 'list',
            items: [
              '课件页引用：讲解里的「[页 N]」可以点开，直接看课件原文，绝不凭空编造',
              '课内 / 课外：内容若来自课件之外的通识知识，会被明确标注',
              '外部查证：使用「找资料」时，外部来源会进入单独的查证时间线，和课程课件证据分开呈现',
              '练习与考核不给完整答案：留给你独立思考，答案靠自己写或去训练场判题',
            ],
          },
          {
            kind: 'tip',
            text: '「考考我」会进入 3 轮口头小测模式（顶部有进度条）：它出题、你作答、它点评，全程只引导、不直接给答案。',
          },
        ],
      },
    ],
  },
  {
    id: 'code-theater',
    title: '代码剧场：看你的代码「走」给你看',
    navTitle: '代码剧场',
    summary:
      '代码剧场（机关广场）把看不见的程序执行过程变成一场可观察的表演：你写真实 Python，'
      + '一个「代码傀儡」按你的代码一行行走——循环绕圈、变量翻牌、分支选路、出错绊倒，全看得见。'
      + '它本质是一套「看执行」（notional machine，关于代码如何运行的心智模型）脚手架：'
      + '训练场或无尽闯关里一道题做错了，可以跳进来把它「走」一遍，亲眼看清错在哪一行、哪一步。',
    loop:
      '代码剧场补上闭环里最难的一跳——真正「看懂代码怎么跑」：它把抽象的执行模型变成可见的空间动作，'
      + '既能在动手「练」之前先把运行过程吃透，也能在练习失败后跳进来复盘，是「看」与「练」之间的桥。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/22-code-theater.webp`,
      alt: '代码剧场机关广场：脚本梯逐行发光，代码傀儡作为程序计数器沿梯行走，旁边是变量牌、环形循环跑道与分支岔路口',
      caption: '代码傀儡按你的代码一步步走：哪行在执行、变量怎么变、循环转了几圈，一目了然。',
    },
    subsections: [
      {
        id: 'code-theater-what',
        title: '它解决什么问题',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '初学者最大的拦路虎，是「代码执行」看不见：循环转了几圈、变量何时变化、'
              + '分支为什么走这边、为什么会死循环——这些都藏在脑子里。代码剧场把它们搬上舞台。',
          },
          {
            kind: 'diagram',
            title: '从代码到表演',
            flow: 'row',
            nodes: [
              { icon: '⌨️', label: '写 Python', desc: '真实代码，不是玩具语言' },
              { icon: '🧭', label: '执行轨迹', desc: '逐行记录每一步' },
              { icon: '🎭', label: '傀儡走代码', desc: '循环 / 变量 / 分支可见' },
              { icon: '🌿', label: '世界反馈', desc: '达成目标即改变世界' },
            ],
            caption: '运行只是「学习预览」，正式判分仍以训练场服务端判题为准。',
          },
        ],
      },
      {
        id: 'code-theater-enter',
        title: '怎么进入',
        blocks: [
          {
            kind: 'steps',
            items: [
              '在小镇里按 ESC 打开暂停菜单，选「学习 → 代码剧场」，或找镇上「教授」对话进入机关广场（也可直接访问 /code-theater）',
              '读懂 NPC 给的真实需求（如「把整条街的 4 盏路灯都点亮」）',
              '在编辑器里写 / 改 Python，点「▶ 运行」',
              '看执行过程一步步展开，并对照「世界目标」看自己漏了哪个',
            ],
          },
        ],
      },
      {
        id: 'code-theater-read',
        title: '怎么看懂这场表演',
        blocks: [
          {
            kind: 'list',
            items: [
              '脚本梯：傀儡是「程序计数器」，走到哪行、哪行就发光',
              '变量牌：赋值时牌面数字当场翻新，旧值淡出',
              '循环跑道：每一轮绕一圈、计数 +1；死循环会被看见（原地无限踱步）',
              '岔路口：if / else 按条件真假选路，走哪条一目了然',
              '世界条件代替隐藏断言：点亮的灯变绿、漏掉的灯还黑着——错误是「看得见的世界现状」，不是冰冷的一句 WA',
            ],
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/24-code-theater-stage.webp`,
              alt: '代码剧场执行中特写：代码傀儡停在脚本梯发光的那一行，旁边变量牌翻出最新数值，环形循环跑道标着当前绕到第几圈',
              caption: '执行中特写：脚本梯发光行 = 正在执行的行，变量牌 = 当前变量值，循环跑道 = 已经绕了几圈。',
            },
          },
          {
            kind: 'tip',
            text:
              'range(1, 4) 会漏掉第 4 盏路灯——这种经典的「左闭右开 off-by-one」，'
              + '在剧场里会直接表现为「第 4 盏还黑着」，比报错信息直观得多。',
          },
        ],
      },
      {
        id: 'code-theater-debug',
        title: '从一道做错的练习跳进来',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '代码剧场最实用的时刻，是你在训练场或无尽闯关里卡住的时候。'
              + '提交没过、又看不懂为什么时，把这段代码带进剧场跑一遍——'
              + '与其盯着一句「WA / 答案错误」发呆，不如亲眼看傀儡在哪一行走偏、哪个变量变成了意外的值。',
          },
          {
            kind: 'steps',
            items: [
              '在训练场 / 无尽闯关遇到做错又想不通的题，选择「去代码剧场看执行」',
              '剧场载入你那段没通过的代码，点「▶ 运行」让傀儡逐行走一遍',
              '对照「世界目标」：没达成的目标（如还黑着的那盏灯）就是 bug 的现场',
              '在剧场里改到世界目标全部达成，再回训练场正式提交',
            ],
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/28-code-theater-debug.webp`,
              alt: '从失败练习跳进代码剧场：载入未通过的代码运行后，世界目标只点亮了一部分，第 4 盏路灯仍然黑着，直观暴露 off-by-one 错误',
              caption: '带着没通过的代码进剧场：世界目标没全亮，就是 bug 的「案发现场」，比一行报错更好懂。',
            },
          },
          {
            kind: 'tip',
            text:
              '剧场里的运行只是「看执行」的学习预览，正式判分仍以训练场服务端判题为准——看懂了，记得回去正式提交。',
          },
        ],
      },
    ],
  },
  {
    id: 'training',
    title: '训练场：写代码、交真题的主场',
    navTitle: '训练场',
    summary:
      '训练场提供课程配套的 39 道真题：在内置代码编辑器中作答，'
      + '先用浏览器内运行做即时预览，确认无误后正式提交，由服务端判题并记录成绩。',
    loop: '训练场承担闭环里的「练」：判题结果会更新知识点掌握度，反过来影响主线进度与个人画像。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/08-training.webp`,
      alt: '训练场做题界面：左侧是题目描述与样例，右侧是代码编辑器和运行结果',
      caption: '训练场支持浏览器内即时预览与服务端正式判题两条轨道。',
    },
    subsections: [
      {
        id: 'training-flow',
        title: '做题流程',
        blocks: [
          {
            kind: 'steps',
            items: [
              '按 ESC 选「学习 → 训练场」，按章节或难度挑一道题',
              '阅读题面与样例，在编辑器里写代码',
              '点「运行」做即时预览：代码在浏览器内执行，立刻看到输出',
              '预览满意后点「提交」：服务端正式判题，结果入库并计入掌握度',
            ],
          },
        ],
      },
      {
        id: 'training-submit',
        title: '预览与正式提交的区别',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '即时预览只在你的浏览器里运行，方便快速试错，不计成绩；'
              + '正式提交才由服务端判题、记录成绩并更新掌握度。两者结果偶有差异时，以正式判题为准。',
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/19-training-verdict.webp`,
              alt: '正式提交判题结果：AC 通过，显示测试点通过情况',
              caption: '正式判题结果：AC 通过 / WA 答错 / RE 运行错误，每种结果都有针对性提示。',
            },
          },
          {
            kind: 'list',
            items: [
              'AC（通过）：恭喜，掌握度上涨，主线进度同步推进',
              'WA（答错）：会展示期望输出与你的实际输出的差异，并给出「进入 Bug 矿洞」入口',
              'RE（运行错误）：展示你的程序自己的报错信息（Traceback），照着排查即可',
              '同一账号 10 秒内只能正式提交一次——先用「运行」预览调通，再正式提交',
            ],
          },
          {
            kind: 'tip',
            text: '提交未通过不是坏事——失败的提交会变成 Bug 矿洞的素材，调试本身就是高质量的练习。',
          },
        ],
      },
    ],
  },
  {
    id: 'endless',
    title: '无尽闯关：AI 出题，连胜闯塔',
    navTitle: '无尽闯关',
    summary:
      '无尽闯关是训练场之外的「连续练习」模式：AI 按你的画像不断出题，做对一题接一题。'
      + '它有两种形态——无尽模式（/endless）无限连答、冲连胜与积分；'
      + '无尽远征（/expedition）按你的掌握度 + 画像生成知识前沿分支地图，选路闯关、攒气势、收学习增益。',
    loop:
      '无尽闯关承担「练」并直接接通「测」：每一关都是真判题，结果折算成 KC 掌握度、回流八维画像，'
      + '系统再把你的弱项与久未练的知识点优先推回来——练得越多，远征越懂你。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/live-expedition-tower.png`,
      alt: '无尽远征像素塔实拍：底层在下、顶层是 BOSS 知识闸，房间按类型着色（判题战斗 / 精英挑战 / 抓 Bug / 概念考点 / 资源宝箱 / 复盘营地），可前往的节点高亮、未解锁的变暗，节点间有分支连线，右上角显示体力与得分',
      caption: '无尽远征像素塔实拍：一座按你学情即时生成的分支秘境塔，从底层一路选路打到顶层 BOSS。',
    },
    subsections: [
      {
        id: 'endless-modes',
        title: '一局无尽远征长什么样',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '过去的「7 章 × 10 关」固定闯关塔已退役，取而代之的是无尽远征：一局远征就是一张'
              + '分层分支地图（类杀戮尖塔）——系统按你的掌握度 + 画像在每层放置不同类型的房间，'
              + '你从底层沿连线选一条路往上爬，自己决定先打哪、绕过哪，一路通到顶层 BOSS 知识闸。',
          },
          {
            kind: 'diagram',
            title: '无尽远征 · 房间（节点）类型',
            flow: 'row',
            nodes: [
              { icon: '⚔', label: '判题战斗', desc: '常规真判题，锚定一个知识点' },
              { icon: '✦', label: '精英挑战', desc: '难度 +1、奖励更高，过关给学习增益' },
              { icon: '❓', label: '概念考点', desc: '先选应对（钻研 / 稳练 / 复盘）再开打' },
              { icon: '🐞', label: '抓 Bug', desc: '改错 / 调试题，专治易错' },
              { icon: '⛺', label: '复盘营地', desc: '回血休整，不出题' },
              { icon: '★', label: 'BOSS', desc: '顶层知识闸，连过多阶段才算击穿' },
            ],
            caption: '另有「资源宝箱」给学习增益（relics）；每个战斗类房间的知识点 / 题型 / 难度由后端选择器按学情现编。',
          },
          {
            kind: 'list',
            items: [
              '分支 = 自主性：同层选了一个房间，其它岔路就走不回头了，每局路线都不一样',
              '题型沿用九宫格：双轨判题、填空、改错、简答、看 PDF、多轮组合、调试闭环等，按房间类型与画像挑选',
              '聚焦章节可选：默认按画像全局自适应，也可只聚焦某一章；还有「今日挑战 · 弱项」每日一局',
              '跨局 meta：65 个知识点画成「知识星座」，掌握度达阈值的点亮，是看得见的成长地图',
            ],
          },
        ],
      },
      {
        id: 'endless-pixel-tower',
        title: '像素远征塔：把地图画成一座可见的塔',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '远征地图有两种看法，随时一键切换：「🗼 像素塔」用 Phaser 把分支地图渲染成一座可见的'
              + '像素秘境塔——底层在下、顶层 BOSS，房间按类型着色、按状态点亮（已过）/ 高亮（可前往）/'
              + '变暗（未解锁）/ 变灰（已绕过），你的小人随通关向上攀爬，打通 BOSS 触发通关庆祝；'
              + '「☰ 列表」是同一张地图的无障碍 / 移动端视图。两种视图点同一个房间是同一个动作。',
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/live-expedition-challenge.png`,
              alt: '在像素远征塔上点击一个可前往的「判题战斗」房间后，进入该知识点的真判题：顶部显示节点类型、知识点、难度与计时，下方是题面与作答区',
              caption: '点像素塔上高亮的房间 = 进入该知识点的真判题（与列表视图完全同一动作）。',
            },
          },
          {
            kind: 'tip',
            text: '窄屏 / 降级环境自动用列表视图；像素塔是渐进增强，看不到时学习闭环完全不受影响。',
          },
        ],
      },
      {
        id: 'endless-enter',
        title: '进入、续局与温和失败',
        blocks: [
          {
            kind: 'steps',
            items: [
              '在小镇里按 Shift + A 打开无尽远征（/expedition），或从暂停菜单进入',
              '选「全局自适应」或某个聚焦章节，点「开始远征」生成本局分支塔',
              '点高亮房间进入：战斗类出真题、判题入库；概念考点先选应对，营地回血，宝箱给增益',
              '答错按固定规则扣体力，体力归零本局结束；打通顶层 BOSS 即通关',
            ],
          },
          {
            kind: 'paragraph',
            text:
              'run 进度（体力 / 气势 / 得分 / 学习增益 / 地图 / 当前位置）现在由后端权威持久化：'
              + '中途「返回小镇」会保留这一局，下次回来或换一台设备登录都能接着打；'
              + '想重开一局，用结算页的「再来一局」或地图上的「🏳 弃局」。无论如何，做过的每道题都已真实入库，'
              + '掌握度与画像永不回滚。',
          },
          {
            kind: 'tip',
            text: '小镇里单按 A 是向左移动，呼出无尽远征要按 Shift + A——这点在「小镇操作」的快捷键速查里也能查到。',
          },
        ],
      },
      {
        id: 'endless-scoring',
        title: '连胜、积分与自适应难度',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '无尽模式的核心是「越战越勇、量力而行」：连胜会自动抬高下一题难度，失败则下调，'
              + '让题目始终卡在你「跳一跳够得着」的区间。',
          },
          {
            kind: 'list',
            items: [
              '连胜（streak）：连续答对累加，连 1 题难度 +0.5、连 2 题及以上 +1；答错清零并下调难度',
              '积分（score）：每通过一题按「难度 × 10」计分，答得快还有时间加成',
              '自适应难度：在 1–5 之间浮动，并参考你画像里的成功率与坚持度——高水平起步更难，连挫时主动降难度、不硬逼你',
              '失败只结束当前这一局、连胜归零，但随时可以再开一局继续刷',
            ],
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/25-endless-result.webp`,
              alt: '无尽模式判题结果：顶部显示当前连胜数与累计积分，中间是本题 AC / 未通过的判定与反馈，下一题难度随结果自适应变化',
              caption: '无尽模式结算：连胜数、累计积分一目了然，下一题难度按这次结果自动升降。',
            },
          },
        ],
      },
      {
        id: 'endless-loop',
        title: '闯关如何回流掌握度与画像',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '无尽闯关不是「刷完就算」的小游戏。每次提交都会真实判题、入库，并把结果折算进知识点掌握度；'
              + '章末 BOSS 通关还会直接奖励对应 KC 的掌握度。这些结果同时回流你的八维画像（尤其是易错模式与实践能力）。',
          },
          {
            kind: 'diagram',
            title: '闯关结果 · 回流闭环',
            flow: 'row',
            nodes: [
              { icon: '✅', label: '通关 / 失败', desc: '每题真实判题并入库' },
              { icon: '📈', label: 'KC 掌握度', desc: '折算到具体知识点' },
              { icon: '🧭', label: '八维画像', desc: '被动更新易错模式等维度' },
              { icon: '🎯', label: '弱项推荐', desc: '优先把薄弱 KC 推回来再练' },
            ],
            caption: '弱项 KC 会被优先选作下一题，让你「专挑不会的练」，而不是重复刷已经会的。',
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/29-endless-weak-kc.webp`,
              alt: '无尽模式按画像弱项出题：下方历史记录列出每题的知识点、难度与对错，系统据此把薄弱知识点优先推回来',
              caption: '系统读你的画像弱项优先出题；历史记录里每条都标着知识点与难度，方便回看进步。',
            },
          },
          {
            kind: 'tip',
            text: '没接入画像信号时，系统按默认难度出题；多练几局、让画像积累证据后，弱项推荐会越来越准。',
          },
        ],
      },
    ],
  },
  {
    id: 'bug-mine',
    title: 'Bug 矿洞：把错题变成调试副本',
    navTitle: 'Bug 矿洞',
    summary:
      '训练场里没通过的提交，会沉淀成 Bug 矿洞中一层层的矿层。'
      + '进洞后逐层下探：读懂报错、定位问题、修复代码、验证通过，挖穿一层前进一层。',
    loop: '矿洞是「练」的纠错回路：它让你直面自己的真实错误，而修复记录会写进画像的「易错模式」维度。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/09-bug-mine.webp`,
      alt: 'Bug 矿洞调试界面：展示报错信息、出错代码与逐层下探的矿洞进度',
      caption: '每一次失败提交都可能变成一层矿洞，修好 Bug 才能继续下探。',
    },
    subsections: [
      {
        id: 'bug-mine-enter',
        title: '怎么进入矿洞',
        blocks: [
          {
            kind: 'list',
            items: [
              '训练场提交失败后，结果提示里会出现「进入矿洞调试」入口',
              '也可以从小镇的矿洞入口直接进入，挑选历史错题副本练手',
            ],
          },
        ],
      },
      {
        id: 'bug-mine-fix',
        title: '调试一层的流程',
        blocks: [
          {
            kind: 'steps',
            items: [
              '阅读这一层的出错代码与报错信息',
              '提出修复假设，动手修改代码',
              '运行验证：通过则挖穿此层继续下探，未通过则继续排查',
            ],
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/20-bugmine-debug.webp`,
              alt: '错题矿洞调试现场：左侧错题本按掌握度排序，右侧是逐层诊断的调试副本',
              caption: '失败提交自动入册：按掌握度与遗忘风险排序，逐层诊断（L1→L5）直到真正看懂。',
            },
          },
          {
            kind: 'paragraph',
            text:
              '矿洞的口号是「看懂再出坑，而非直接给答案」：每道错题按 L1→L5 逐层开放诊断线索，'
              + '从复述题意、定位错因到独立修复，层层递进。错题旁标注的知识点与掌握度，'
              + '会随你的修复进度实时更新。',
          },
          {
            kind: 'tip',
            text: '矿洞里不挂随身向导——这是刻意设计，让你独立完成一次完整的排错训练。',
          },
        ],
      },
    ],
  },
  {
    id: 'review-hall',
    title: '复盘大厅：阶段复盘与费曼反演',
    navTitle: '复盘大厅',
    summary:
      '复盘大厅汇总你最近的学习表现并生成阶段评估；这里还有独特的「费曼反演」玩法——'
      + '教一个带 bug 代码来求教的 AI 学弟，讲明白了才算真的懂。',
    loop: '复盘大厅承担闭环里的「测」：用评估检验掌握度，用费曼反演逼出「以为会了、其实没会」的盲区。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/10-review-hall.webp`,
      alt: '复盘大厅界面：阶段学习评估报告与费曼反演入口并列展示',
      caption: '复盘大厅汇总你的阶段表现，并提供「教 AI 学弟」的费曼反演练习。',
    },
    subsections: [
      {
        id: 'review-hall-report',
        title: '阶段评估',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '进入大厅即可查看最近的评估报告：哪些知识点已经稳固、哪些还薄弱、'
              + '推荐的下一步学习路径是什么。评估依据是你的真实练习与判题记录，而不是问卷自评。',
          },
        ],
      },
      {
        id: 'review-hall-feynman',
        title: '费曼反演',
        blocks: [
          {
            kind: 'steps',
            items: [
              '大厅 →「测 · 费曼反演」',
              '代码找茬：30 秒内点 bug 行',
              '微信式对话框里讲明白 → 真判题出师',
            ],
          },
          {
            kind: 'paragraph',
            text:
              '学弟在微信式聊天对话框里只中性描述代码在做什么、不剧透 bug；'
              + '你找茬命中后，再在对话里讲清它为什么错，最后接真判题出师。'
              + 'bug 库结合真实错题、由 LLM 大规模生成，代码更长、错误类型更多，足够每日教学常练常新。',
          },
        ],
      },
    ],
  },
  {
    id: 'profile',
    title: '个人画像：我的学习分身',
    navTitle: '个人画像',
    summary:
      '学习分身汇总自述目标与偏好、已验证的知识掌握和复习状态，用来解释推荐的下一步。'
      + '答题成败不会被当作情绪、人格或固定学习风格。',
    loop: '正式练习记录更新可信学情，规则策略选择下一步；分身展示证据和推荐理由，形成看、学、练、测闭环。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/11-profile.webp`,
      alt: '学习分身页面：展示知识状态、证据和下一步推荐',
      caption: '分身依据已验证练习结果成长；目标和学习媒介偏好由你自述。',
    },
    subsections: [
      {
        id: 'profile-dimensions',
        title: '画像信息与证据',
        blocks: [
          {
            kind: 'list',
            items: [
              '自述信息：专业背景、已有经验、学习目标和媒介偏好',
              '知识掌握与复习状态：来源于服务端记录的正式练习和复习事件',
              '证据不足时显示“尚未评估”；AI 洞察只作为学习建议',
            ],
          },
        ],
      },
      {
        id: 'profile-update',
        title: '画像怎么更新',
        blocks: [
          {
            kind: 'paragraph',
            text:
              '判题、调试与复习留下可核对的学习记录；访谈只收集目标、已有经验与偏好。'
              + '系统不会从答题结果推断情绪、人格或固定学习风格。',
          },
          {
            kind: 'tip',
            text: '掌握状态随真实练习记录更新；偏好可以在画像页调整。',
          },
        ],
      },
    ],
  },
  {
    id: 'teacher-classroom',
    title: '教师班级：把学习闭环带进课堂',
    navTitle: '教师班级',
    summary:
      'Pythontopia 支持轻量班级教学：教师创建班级并发布任务，学生加入班级后在小镇公告板领取；'
      + '教师端可查看全班知识点热力图、任务完成率与高频误区。',
    loop: '班级让闭环从单人扩展到课堂：教师依据全班的「练 / 测」数据调整教学，把任务精准发到需要补强的环节上。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/12-teacher-classroom.webp`,
      alt: '教师班级面板：班级知识点掌握热力图、任务完成率与高频误区列表',
      caption: '教师视角能看到全班的知识点热力图与高频误区，便于针对性布置任务。',
    },
    subsections: [
      {
        id: 'teacher-classroom-student',
        title: '学生视角',
        blocks: [
          {
            kind: 'steps',
            items: [
              '从老师处拿到班级邀请信息，在班级入口加入班级',
              '到小镇公告板查看老师发布的班级任务并领取',
              '任务完成情况自动同步给老师，无需手动上报',
            ],
          },
        ],
      },
      {
        id: 'teacher-classroom-teacher',
        title: '教师视角',
        blocks: [
          {
            kind: 'list',
            items: [
              '创建班级并绑定《Python语言基础》课程',
              '发布班级任务：指定知识点、题目或学习资源',
              '查看班级仪表盘：知识点掌握热力图、任务完成率、高频误区，定位需要帮扶的学生',
            ],
          },
          {
            kind: 'screenshot',
            shot: {
              src: `${SCREENSHOT_BASE}/21-teacher-board.webp`,
              alt: '教师班级看板：班级成员、任务完成情况与知识点掌握热力图',
              caption: '教师看板：进班码邀请学生，热力图与完成率一眼看出全班薄弱点。',
            },
          },
          {
            kind: 'tip',
            text: '注册教师账号需要邀请码（由管理员发放）；学生账号无需邀请码，注册即用。',
          },
        ],
      },
    ],
  },
  {
    id: 'clients',
    title: '多端下载：把 Pythontopia 装进手机和电脑',
    navTitle: '多端下载',
    summary:
      '除了网页版，Pythontopia 还面向 Android、Windows 与微信端推进：'
      + '同一个账号、同一套后端、同一条学习闭环，换设备也能无缝接着学。',
    loop:
      '多端共用同一套后端与「看 → 学 → 练 → 测」闭环：你在网页里的进度，'
      + '登录手机、电脑或微信端即可继续，正式判题口径保持一致。',
    heroShot: {
      src: `${SCREENSHOT_BASE}/23-clients.webp`,
      alt: 'Pythontopia 多端下载说明：Android APK、Windows 客户端与微信端共用同一套学习进度',
      caption: '网页 / Android / Windows / 微信端共享账号与学习进度，随时换设备继续学。',
    },
    subsections: [
      {
        id: 'clients-download',
        title: '下载原生客户端',
        blocks: [
          {
            kind: 'paragraph',
            text: '点击下方按钮会在新标签页打开验证页，点选正确答案后即可开始下载；登录已有账号即可同步学习进度。',
          },
          {
            kind: 'download',
            items: [
              {
                platform: 'Windows-ZIP',
                icon: '🪟',
                label: 'Windows 客户端（ZIP）',
                href: '/download-gate/?file=pythontopia-windows.zip',
                note: '解压后运行 Pythontopia.exe',
                statKey: 'windows_zip',
              },
              {
                platform: 'Android',
                icon: '📱',
                label: 'Android App（APK）',
                href: '/download-gate/?file=pythontopia.apk',
                note: 'Android 8.0+（minSdk 26）· 像素小镇 + 完整学习闭环',
                statKey: 'android_apk',
              },
            ],
          },
          {
            kind: 'tip',
            text:
              'Windows：解压 ZIP 后双击 Pythontopia.exe。'
              + '首次安装 Android APK 时系统可能提示「未知来源」，按提示允许即可；'
              + 'Windows 若被 SmartScreen 拦截，选「仍要运行」。',
          },
        ],
      },
      {
        id: 'clients-sync',
        title: '进度同步',
        blocks: [
          {
            kind: 'list',
            items: [
              '同一账号：网页、手机、桌面共用一个账号与后端，进度互通',
              '微信端：微信小程序侧重学习陪伴，微信小游戏侧重像素小镇；两者都不复用 Web 的 Phaser 代码',
              '像素小镇：Android、Windows 与微信小游戏端同样围绕中央小镇组织学习入口',
              '判题一致：正式判题统一由服务端完成，各端成绩口径一致',
            ],
          },
        ],
      },
    ],
  },
];

// 代码剧场暂时下线（不上线）：从对外导出的指南章节里过滤掉，正文 / 左侧导航 / 翻页都不再出现。
// 章节内容仍保留在 ALL_GUIDE_SECTIONS（与页面、NPC、引擎实现一并保留），重新上线时把 id 从下面这份
// 过滤名单移除即可恢复。
const HIDDEN_GUIDE_SECTION_IDS: ReadonlySet<string> = new Set(['code-theater']);
export const GUIDE_SECTIONS: readonly GuideSectionContent[] = ALL_GUIDE_SECTIONS.filter(
  (section) => !HIDDEN_GUIDE_SECTION_IDS.has(section.id),
);

/** 全部截图数（hero + 正文内嵌），供页头徽标等展示用。 */
export const GUIDE_SCREENSHOT_TOTAL: number = GUIDE_SECTIONS.reduce(
  (count, section) =>
    count
    + 1
    + section.subsections.reduce(
      (subCount, sub) =>
        subCount + sub.blocks.filter((block) => block.kind === 'screenshot').length,
      0,
    ),
  0,
);
