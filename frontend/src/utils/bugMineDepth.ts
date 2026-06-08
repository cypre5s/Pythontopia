import type { BugMineCardDto, BugMineCardType } from '@/api/dto/BugMine';

export const DEPTH_LAYER_LABELS: Record<number, string> = {
  1: 'L1 裸重试',
  2: 'L2 反例',
  3: 'L3 变量轨迹',
  4: 'L4 分层提示',
  5: 'L5 同 KC 变式',
};

export function depthRequiredForCard(cardType: BugMineCardType): number {
  switch (cardType) {
    case 'COUNTER_EXAMPLE':
      return 2;
    case 'VARIABLE_TRACE':
      return 3;
    case 'WRONG_ASSUMPTION':
    case 'HINT_LAYER_1':
    case 'HINT_LAYER_2':
    case 'HINT_LAYER_3':
      return 4;
    default:
      return 1;
  }
}

export function canAccessCard(depthLayer: number, card: BugMineCardDto): boolean {
  return depthLayer >= depthRequiredForCard(card.cardType);
}

export function depthLockMessage(depthLayer: number, cardType: BugMineCardType): string {
  const required = depthRequiredForCard(cardType);
  if (depthLayer >= required) {
    return '';
  }
  return `需先下探到 L${required}（当前 L${depthLayer}）`;
}
