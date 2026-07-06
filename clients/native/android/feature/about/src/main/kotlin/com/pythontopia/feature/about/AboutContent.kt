package com.pythontopia.feature.about

/**
 * 「关于」域静态内容：操作指南（触屏适配的精简版）+ 素材署名（致谢）。
 *
 * - 指南：对齐 Web `content/guideSections.ts` 的 12 个主题，取「是什么 + 与闭环关系 + 关键要点」精华，
 *   并把网页键盘操作改写为安卓触屏 / 顶部 dock 导航；非整页 1:1 转录。
 * - 致谢：1:1 移植 Web `data/credits.ts` 的素材署名与 license 分组（CC-BY/SA 署名为 AGENTS.md 强制要求）。
 * - 落地页（/home）有意不移植：原生端登录后直接进入小镇，营销式落地页冗余。
 */

data class GuideSection(
    val navTitle: String,
    val title: String,
    val summary: String,
    val loop: String,
    val points: List<String>,
)

val GUIDE_SECTIONS: List<GuideSection> = listOf(
    GuideSection(
        navTitle = "小镇操作",
        title = "在 Pythontopia 里行走与交互",
        summary = "整门《Python语言基础》装进一座中央小镇：图书馆、百晓工坊、训练场、Bug 矿洞、复盘大厅都是镇上建筑。",
        loop = "小镇是「看 → 学 → 练 → 测」四环的总入口，走进哪栋建筑就进入哪一环。",
        points = listOf(
            "用屏幕方向控制角色移动（支持八方向）；靠近 NPC / 建筑门口出现提示即可交互",
            "顶部功能 dock 可直达任意学习页：小镇 / 冒险 / 工坊 / 训练场 / 图书馆 / 复盘 …",
            "迷路时回到「小镇」标签，所有入口都在 dock 上一字排开",
        ),
    ),
    GuideSection(
        navTitle = "AI 助教",
        title = "你的随身导学助手",
        summary = "AI 助教清楚你的进度与画像，回答「下一步去哪、做什么、为什么」，并把你带到该去的页面。",
        loop = "卡在闭环任意一环先问助教：它判断你该回去「看」、深入「学」、动手「练」还是去「测」。",
        points = listOf(
            "答疑分阶段：审题导读 → 思路分析 → 骨架代码 → 错因诊断 → AC 复盘",
            "做题 / 考试场景只给思路与提示，不给可直接提交的完整答案",
            "说出学习目标，助教拆成「看→学→练→测」四步计划，每步一键直达",
        ),
    ),
    GuideSection(
        navTitle = "主线任务",
        title = "从新生到毕业，走通七章 Python 之旅",
        summary = "以新生身份来到小镇，沿冒险地图走通《Python语言基础》七章、通过每章大测试，最终毕业。",
        loop = "主线是闭环骨架：每个小节内部就是一次完整「看→学→练」，章末大测试负责「测」。",
        points = listOf(
            "冒险地图 = 课程目录游戏化：一章一片区域，一小节一个关卡，逐章解锁",
            "一个小节按「视频 → 课件 → 例题」三步走，全部完成即通关",
            "每章 0–3 星；没满可去错题矿洞或无尽挑战补星",
        ),
    ),
    GuideSection(
        navTitle = "图书馆",
        title = "看课件、查资料的主阵地",
        summary = "收录全部 7 份课件共 561 页，按七章组织；内置课件检索（RAG），命中片段标注出处页码。",
        loop = "图书馆承担「看」：后续的学、练、测都建立在课件这个唯一知识底座上。",
        points = listOf(
            "按章节目录逐页阅读，文本可选中复制",
            "检索支持中英混写（如「input函数」「for循环」），每条结果带出处页码",
            "答案永远以课件原文为证据，不凭空编造",
        ),
    ),
    GuideSection(
        navTitle = "百晓工坊",
        title = "让 AI 伙伴「百晓」陪你学",
        summary = "镇上 AI 学习伙伴「百晓」的工作室：答疑、出题、生成学习资源、规划路线，共 16 项能力分四组。",
        loop = "工坊承担「学」：把你「看」过的课件加工成贴合画像的讲解与练习，为「练」做准备。",
        points = listOf(
            "直接说人话，百晓自动选能力；也可用「/」点名、用「@」锚定知识点",
            "可交付讲义 / 思维导图 / 练习题 / 可运行代码案例 / 拓展阅读 / 分镜",
            "讲解里的「[页 N]」可点开看课件原文；课外通识会明确标注",
        ),
    ),
    GuideSection(
        navTitle = "代码剧场",
        title = "看你的代码「走」给你看",
        summary = "把看不见的程序执行变成可观察的表演：写真实 Python，代码傀儡按你的代码一行行走。",
        loop = "补上闭环最难一跳——真正「看懂代码怎么跑」，在动手「练」前先把运行过程吃透。",
        points = listOf(
            "脚本梯 / 变量牌 / 循环跑道 / 岔路口把执行过程可视化",
            "用「世界条件」代替隐藏断言：漏点的灯还黑着，比一句 WA 直观",
            "运行仅「学习预览」，正式判分仍以训练场服务端判题为准",
        ),
    ),
    GuideSection(
        navTitle = "训练场",
        title = "写代码、交真题的主场",
        summary = "提供课程配套 39 道真题，在编辑器作答；先即时预览，确认后正式提交，服务端判题记录成绩。",
        loop = "训练场承担「练」：判题结果更新知识点掌握度，反过来影响主线进度与画像。",
        points = listOf(
            "AC 通过 / WA 答错 / RE 运行错误，每种结果都有针对性提示",
            "同账号 10 秒内只能正式提交一次——先预览调通再提交",
            "提交未通过会变成 Bug 矿洞素材，调试本身就是高质量练习",
        ),
    ),
    GuideSection(
        navTitle = "Bug 矿洞",
        title = "把错题变成调试副本",
        summary = "训练场没通过的提交沉淀成矿洞矿层；进洞逐层下探：读懂报错、定位、修复、验证。",
        loop = "矿洞是「练」的纠错回路：直面真实错误，修复记录写进画像「易错模式」维度。",
        points = listOf(
            "每道错题按 L1→L5 逐层开放诊断线索，看懂再出坑、不直接给答案",
            "错题旁的知识点与掌握度随修复进度实时更新",
            "矿洞内不挂随身向导——刻意让你独立完成一次完整排错",
        ),
    ),
    GuideSection(
        navTitle = "复盘大厅",
        title = "阶段复盘与费曼反演",
        summary = "汇总最近学习表现生成阶段评估；独特的「费曼反演」——教会犯你同款错的 AI 学弟才算真懂。",
        loop = "复盘大厅承担「测」：用评估检验掌握度，用费曼反演逼出「以为会了、其实没会」的盲区。",
        points = listOf(
            "评估依据真实练习与判题记录，而非问卷自评",
            "费曼反演：用自己的话讲清错在哪、怎么改，讲解回流更新掌握度",
        ),
    ),
    GuideSection(
        navTitle = "个人画像",
        title = "系统眼中的你",
        summary = "从八维刻画学习状态：专业背景、知识基础、学习目标、认知风格、学习偏好、易错模式、实践能力、情绪与坚持度。",
        loop = "画像是闭环导航仪：看/学/练/测都参考画像，闭环每一步又反过来更新画像。",
        points = listOf(
            "大部分维度由练习 / 判题 / 矿洞 / 反馈 / 任务被动更新",
            "学习目标、认知风格、学习偏好、情绪与坚持度 4 个主观维度支持自评修正",
        ),
    ),
    GuideSection(
        navTitle = "教师班级",
        title = "把学习闭环带进课堂",
        summary = "教师创建班级并发布任务，学生加入后在公告板领取；教师端看全班知识点热力图、完成率与高频误区。",
        loop = "班级让闭环从单人扩展到课堂：教师依据全班「练/测」数据精准布置任务。",
        points = listOf(
            "学生：加入班级 → 公告板领班级任务 → 完成自动同步给老师",
            "教师：建班绑课 → 发任务 → 看仪表盘（热力图 / 完成率 / 误区）",
            "注册教师账号需邀请码；学生注册即用",
        ),
    ),
    GuideSection(
        navTitle = "多端",
        title = "网页 / Android / Windows / 微信共享进度",
        summary = "同一账号、同一套后端、同一条学习闭环，换设备无缝接着学；正式判题口径各端一致。",
        loop = "多端共用「看 → 学 → 练 → 测」闭环：任一端的进度登录其它端即可继续。",
        points = listOf(
            "Android / Windows / 微信小游戏端同样围绕中央小镇组织学习入口",
            "正式判题统一由服务端完成，各端成绩口径一致",
        ),
    ),
)

// —— 致谢：素材署名（1:1 对齐 Web data/credits.ts）——

data class CreditEntry(
    val category: String,
    val name: String,
    val authors: List<String>,
    val source: String,
    val license: String,
    val usedFor: String,
)

data class LicenseGroup(
    val license: String,
    val title: String,
    val note: String,
    val requiresAttribution: Boolean,
    val shareAlike: Boolean,
)

val CREDITS: List<CreditEntry> = listOf(
    CreditEntry("tileset", "Tiny Town", listOf("Kenney Vleugels"), "kenney.nl", "CC0", "小镇地面 / 建筑外墙"),
    CreditEntry("tileset", "Tiny Dungeon", listOf("Kenney Vleugels"), "kenney.nl", "CC0", "Bug 矿洞地形"),
    CreditEntry("tileset", "Roguelike Modern City", listOf("Kenney Vleugels"), "kenney.nl", "CC0", "小镇街景"),
    CreditEntry("tileset", "Roguelike Caves", listOf("Kenney Vleugels"), "kenney.nl", "CC0", "矿洞副本"),
    CreditEntry("ui", "Pixel UI Pack", listOf("Kenney Vleugels"), "kenney.nl", "CC0", "按钮 / 对话框 / 9-slice 面板"),
    CreditEntry("character", "LPC Sara", listOf("LPC Project", "bluecarrot16", "Sharm"), "OpenGameArt", "CC-BY-SA 3.0", "玩家角色 sheet"),
    CreditEntry(
        "character",
        "LPC Medieval Fantasy Characters",
        listOf("LPC Project", "Sharm", "Reemax", "Jetrel", "J. Taylor", "Ivan Voirol", "TheraHedwig"),
        "OpenGameArt",
        "CC-BY-SA 3.0",
        "NPC sprite 集",
    ),
    CreditEntry("tileset", "Farming Game Basics", listOf("Jana Ochse (2DPIXX)"), "OpenGameArt", "CC-BY 4.0", "知识田作物 5 阶段"),
    CreditEntry("effect", "Pixel Lightning", listOf("Marco L."), "OpenGameArt", "CC-BY 4.0", "雷电特效"),
    CreditEntry("tileset", "LPC Farm", listOf("bluecarrot16", "TheraHedwig", "Ivan Voirol"), "OpenGameArt", "CC-BY 4.0", "农场围栏与作物"),
    CreditEntry("character", "Pepper&Carrot 24x32", listOf("David Revoy"), "peppercarrot.com", "CC-BY 4.0", "部分 NPC"),
    CreditEntry(
        "icon",
        "Game Icons",
        listOf("Lorc", "Delapouite", "Skoll", "sbed", "Carl Olsen"),
        "game-icons.net",
        "CC-BY 3.0",
        "KC 知识点图标 / 任务图标",
    ),
    CreditEntry("font", "Ark Pixel Font", listOf("TakWolf"), "GitHub", "OFL-1.1", "中文像素字体 12px"),
)

val LICENSE_GROUPS: List<LicenseGroup> = listOf(
    LicenseGroup("CC0", "CC0 1.0（公有领域贡献）", "作者放弃版权，无需署名。本项目仍按推荐致谢列出。", false, false),
    LicenseGroup("CC-BY 3.0", "CC-BY 3.0（署名 3.0）", "必须保留作者署名与协议名称，可商用、可改编，不传染。", true, false),
    LicenseGroup("CC-BY 4.0", "CC-BY 4.0（署名 4.0）", "必须保留作者署名与协议名称，可商用、可改编，不传染。", true, false),
    LicenseGroup("CC-BY-SA 3.0", "CC-BY-SA 3.0（署名-相同方式共享 3.0）", "必须署名，且基于其修改的作品须以兼容协议发布（传染）。", true, true),
    LicenseGroup("OFL-1.1", "SIL Open Font License 1.1（字体专用）", "保留字体内 OFL 协议文本与作者署名，传染仅限字体文件本身。", true, false),
)
