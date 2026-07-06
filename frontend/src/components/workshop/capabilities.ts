import type { IntentKind } from '@/api/intent';

export type WorkshopCapabilityNamespace = 'ask' | 'quiz' | 'make' | 'practice' | 'plan' | 'find';

export interface WorkshopCapability {
  namespace: WorkshopCapabilityNamespace;
  label: string;
  primaryIntent: IntentKind;
  intents: IntentKind[];
  description: string;
  examples: string[];
}

export const WORKSHOP_CAPABILITIES: WorkshopCapability[] = [
  {
    namespace: 'ask',
    label: '问一问',
    primaryIntent: 'ASK',
    intents: ['ASK', 'EXPLAIN_CODE', 'FIX_BUG'],
    description: '概念答疑、代码讲解和报错定位。',
    examples: ['解释列表推导式', '这段代码什么意思'],
  },
  {
    namespace: 'quiz',
    label: '考考我',
    primaryIntent: 'ASKME',
    intents: ['ASKME', 'REVIEW'],
    description: '用小题检验理解，或回到复习链路。',
    examples: ['考考我循环'],
  },
  {
    namespace: 'make',
    label: '生成资料',
    primaryIntent: 'HANDOUT',
    intents: ['HANDOUT', 'MINDMAP', 'READING', 'STORYBOARD'],
    description: '生成讲义、导图、拓展阅读和动画脚本。',
    examples: ['整理函数讲义'],
  },
  {
    namespace: 'practice',
    label: '练一练',
    primaryIntent: 'PRACTICE',
    intents: ['PRACTICE', 'CODE_LAB'],
    description: '生成练习题或代码案例。',
    examples: ['出几道循环题'],
  },
  {
    namespace: 'plan',
    label: '规划学习',
    primaryIntent: 'PLAN',
    intents: ['PLAN', 'PROGRESS', 'MY_STUDY'],
    description: '规划学习、查看学情、找历史资料。',
    examples: ['我学得怎么样'],
  },
  {
    namespace: 'find',
    label: '找内容/带路',
    primaryIntent: 'VIDEO',
    intents: ['VIDEO', 'BROWSE', 'NAVIGATE'],
    description: '找视频、联网查证或跳转到学习场所。',
    examples: ['查一下 Python 官方文档', '带我去图书馆'],
  },
];

export function capabilityForIntent(intent: IntentKind): WorkshopCapability | null {
  return WORKSHOP_CAPABILITIES.find((capability) => capability.intents.includes(intent)) ?? null;
}
