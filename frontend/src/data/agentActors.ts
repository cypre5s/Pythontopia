// 多智能体「工作室人物」注册表（阶段0 冻结·单一事实源）。
//
// 把 graph SSE 的节点名 / role_key 映射成统一的：角色名 + 复用的小镇 NPC 头像 + emoji 兜底。
// 思考链组件 AgentStudioStream 缩略态显示「当前人物在干什么」、展开态显示全员流水线。
//
// 取代此前分散三处的标签映射：ThinkingStream.nodeLabel / orchestrator._agent_display_name /
// resource_graph._DESIGNER_LABELS。后端发了 role_key 就直接用；没发则按 node 名回退推断。
//
// 头像约定路径与 DialogTree 一致：/assets/pixel/portraits/heads/portrait_<npcKey>_head.png；
// 图缺失时组件 @error 回退 emoji（与现有对话立绘同款降级，不白屏）。

export interface AgentActor {
  roleKey: string;
  /** 中文角色名（思考链里显示的人物名）。 */
  label: string;
  /** 复用的小镇 NPC key；头像由 actorPortraitUrl 拼接。 */
  npcKey: string;
  /** 头像加载失败 / 缺图时的 emoji 兜底。 */
  emoji: string;
  /** 缩略态一句话动作模板（%s 处由 message/note 覆盖；纯展示）。 */
  doing: string;
}

/** role_key → 工作室人物。 */
export const AGENT_ACTORS: Record<string, AgentActor> = {
  video_navigator: { roleKey: 'video_navigator', label: '视频领航员', npcKey: 'professor', emoji: '🧭', doing: '正在读视频概览定位看点' },
  timeline_checker: { roleKey: 'timeline_checker', label: '时间轴核验官', npcKey: 'sage_review', emoji: '⏱', doing: '正在核对时间点真实性' },
  director: { roleKey: 'director', label: '故事板导演', npcKey: 'workshop_master', emoji: '🎬', doing: '正在编写分镜脚本' },
  illustrator: { roleKey: 'illustrator', label: '插画师', npcKey: 'gardener', emoji: '🎨', doing: '正在绘制分镜插画' },
  editor: { roleKey: 'editor', label: '剪辑师', npcKey: 'workshop_master', emoji: '✂', doing: '正在拼接合成短视频' },
  librarian: { roleKey: 'librarian', label: '证据馆员', npcKey: 'librarian_doc', emoji: '📚', doing: '正在检索课件证据' },
  critic: { roleKey: 'critic', label: '审查校对官', npcKey: 'professor', emoji: '🔍', doing: '正在核对引用与防幻觉' },
  safety: { roleKey: 'safety', label: '安全员', npcKey: 'coach_q', emoji: '🛡', doing: '正在做安全审查' },
  orchestrator: { roleKey: 'orchestrator', label: '学习总管', npcKey: 'mayor_py', emoji: '🧠', doing: '正在统筹各智能体协同' },
  designer: { roleKey: 'designer', label: '资源设计师', npcKey: 'workshop_master', emoji: '✎', doing: '正在生成学习资源' },
  tutor: { roleKey: 'tutor', label: '百晓助教', npcKey: 'professor', emoji: '💡', doing: '正在作答（课件 RAG）' },
};

/** 未知节点的兜底人物。 */
export const DEFAULT_ACTOR: AgentActor = {
  roleKey: 'worker',
  label: '工作室成员',
  npcKey: 'workshop_master',
  emoji: '⚙',
  doing: '正在处理',
};

/**
 * graph 节点名 → role_key。覆盖资源流 / 编排流 / 视频概述流 / 视频合成流；未知回退 worker。
 * 仅在后端未直接下发 role_key 时作回退推断（关键字匹配，顺序敏感：先专后泛）。
 */
export function nodeToRoleKey(node: string): string {
  const n = (node || '').toLowerCase();
  if (!n) return 'worker';
  if (n.includes('evidence')) return 'librarian';
  if (n.includes('grounding') || n.includes('hallucinat') || n.includes('semantic')) return 'critic';
  if (n.includes('safety')) return 'safety';
  if (n.includes('timeline')) return 'timeline_checker';
  if (n.includes('videoinsight') || n.includes('video_insight') || n.includes('navigator')) return 'video_navigator';
  if (n.includes('storyboard') || n.includes('director')) return 'director';
  if (n.includes('illustrat') || n.includes('image')) return 'illustrator';
  if (n.includes('editor') || n.includes('compose') || n.includes('ffmpeg') || n.includes('concat')) return 'editor';
  if (n.includes('orchestrat') || n.includes('replan') || n.includes('understand') || n.includes('decompose') || n.includes('dispatch')) return 'orchestrator';
  if (n.includes('tutor') || n.includes('image_understanding')) return 'tutor';
  if (
    n.includes('practice') || n.includes('handout') || n.includes('mindmap')
    || n.includes('reading') || n.includes('code') || n.includes('designer') || n.includes('agent')
  ) {
    return 'designer';
  }
  return 'worker';
}

/** 解析为工作室人物：优先把入参当 role_key，否则按 node 名推断。 */
export function resolveActor(roleKeyOrNode: string | undefined): AgentActor {
  if (roleKeyOrNode && AGENT_ACTORS[roleKeyOrNode]) {
    return AGENT_ACTORS[roleKeyOrNode];
  }
  return AGENT_ACTORS[nodeToRoleKey(roleKeyOrNode ?? '')] ?? DEFAULT_ACTOR;
}

/** 头像 URL（与 DialogTree 同款路径；缺图由组件 @error 回退 emoji）。 */
export function actorPortraitUrl(actor: AgentActor): string {
  return `/assets/pixel/portraits/heads/portrait_${actor.npcKey}_head.png`;
}
