/** Boss 狂暴二阶段招式文案（7 章 × 4 阶段）。与 useBossSession 基础招式表配对使用。 */
export interface BossMoveEnrageText {
  name: string;
  preview: string;
}

export const BOSS_MOVES_ENRAGE: Readonly<Record<number, readonly BossMoveEnrageText[]>> = {
  1: [
    { name: '算珠崩裂', preview: '算珠炸裂成碎片雨，每一颗都在追问：什么叫「计算」？' },
    { name: '光阴逆流', preview: '算筹与芯片的光影倒卷而来，五步走必须一口气答对。' },
    { name: '木珠锁阵', preview: '迷阵收紧，科学与工程的边界在狂暴中变得锋利。' },
    { name: '终珠怒涛', preview: '巨型算珠轰然压顶，本章综合题在怒涛中决胜负。' },
  ],
  2: [
    { name: 'IDLE 暴走', preview: '终端窗口疯狂闪烁，启动符与解释器概念被连环追问。' },
    { name: '变量风暴', preview: 'a、b 在空中乱舞，赋值与类型在风暴里不容迟疑。' },
    { name: 'input 狂印', preview: 'input/print 双轨咒文叠加，输出格式错一点就被反击。' },
    { name: '解释终啸', preview: 'Python 蛇缠住终端，编译与解释的差别必须当场说清。' },
  ],
  3: [
    { name: '比特风暴', preview: '0/1 比特像冰雹砸落，进制转换容不得手滑。' },
    { name: '切片怒斩', preview: '字符串被撕成碎片，索引与切片规则在狂暴中加倍严苛。' },
    { name: '编码怒潮', preview: 'UTF-8 与 ASCII 的浪潮叠加，编码差异必须精确区分。' },
    { name: '进制终爆', preview: '2/8/10/16 四面体坍缩，综合进制题限时更短。' },
  ],
  4: [
    { name: '钟楼狂鸣', preview: '钟声连响，顺序结构输出必须零失误。' },
    { name: '分支怒摆', preview: '钟摆加速，if/elif 边界在狂暴中更容易踩坑。' },
    { name: 'for 怒环', preview: '齿轮疯转，range 与缩进错一行就被反击。' },
    { name: '循环终裂', preview: '塔身震颤，while + break/continue 综合考压顶而来。' },
  ],
  5: [
    { name: '红光怒射', preview: '棱镜过热，list 访问与越界在红光下无处躲藏。' },
    { name: '绿光怒折', preview: 'dict 键值对在绿光中翻涌，CRUD 一步不能错。' },
    { name: '蓝光怒散', preview: 'set 去重规则收紧，重复与无序在蓝光里被放大。' },
    { name: '三光怒涌', preview: '三色光束合流，list+dict+set 混合题全面加压。' },
  ],
  6: [
    { name: '卷轴怒翻', preview: '书页狂翻，with open 与资源管理必须当场答对。' },
    { name: '猫头鹰怒视', preview: '目光如炬，readlines 与行计数在限时里更苛刻。' },
    { name: '编码怒钥', preview: '金钥匙灼烧般旋转，utf-8 读中文不容乱码。' },
    { name: '词频终怒', preview: '卷轴封合前的最后一击，词频统计与文件 IO 合体。' },
  ],
  7: [
    { name: '类怒诞', preview: '镜面中的蛋剧烈颤动，class 与 object 关系被反复逼问。' },
    { name: '继承怒裂', preview: '镜面碎成无数碎片，继承写法必须一次写对。' },
    { name: '抽象怒塔', preview: '类金字塔崩塌重组，abstract 方法定义不容含糊。' },
    { name: '归纳终镜', preview: '元镜汇聚所有倒影，类 + lambda + 递归在终局对决。' },
  ],
};
