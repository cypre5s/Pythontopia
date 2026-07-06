/**
 * 具身任务「把还回的书逐本归位」——教列表 append 动态建表 + for 遍历。
 *
 * 教育锚点：packId=43 KC『PY43-KC-040 列表基本操作』+『PY43-KC-031 for循环与遍历』。
 * 玩法：NPC（图书管理员）给出真实需求——今天要归还第 1~5 号书。学生先用 `list.append()`
 *   把要上架的书号一个个收进清单（与「列表字面量」任务区分：这里强调用 append 往列表"加"元素），
 *   再用 `for book in books:` 遍历清单，对每本调用世界动作 `shelve(book)`。
 * 世界目标 = 可见条件「名单上每本都归位」；append 漏加一本会直接表现为「第 N 号书还没归位」，
 *   让「列表是怎么一个个 append 出来的」体现为世界现状。
 *
 * 世界动作 `shelve` 在 starterCode 里 `def` 出来（见 types.ts CoverageWorldGoal.actionName）。
 */

import type { CodeTheaterQuest } from './types';

/** 今天要归位的书号（第 1~5 号）。 */
const BOOK_TARGETS: readonly number[] = [1, 2, 3, 4, 5];

export const SHELVE_RETURNED_BOOKS_QUEST: CodeTheaterQuest = {
  id: 'shelve-returned-books',
  title: '把还回的书逐本归位',
  kcCodes: ['PY43-KC-040', 'PY43-KC-031'],
  prompt:
    '【图书管理员】今天要归还第 1~5 号共 5 本书。先用 append 把要上架的书号一个个收进清单 books，' +
    '再用 for 遍历清单挨本归位。动作 shelve(book) 会把第 book 号书归位上架。' +
    '可下面的清单好像漏 append 了一本——把它补上，再让 5 本书都归位。',
  starterCode: [
    '# shelve(book)：把第 book 号书归位上架。',
    'def shelve(book):',
    '    print(f"把第 {book} 号书归位")',
    '',
    '# 今天要归还第 1~5 号书。用 append 把书号一个个收进清单——好像漏了一本，补上它。',
    'books = []',
    'books.append(1)',
    'books.append(2)',
    'books.append(3)',
    'books.append(5)',
    '',
    '# 再用 for 遍历清单，挨本归位。',
    'for book in books:',
    '    shelve(book)',
    '',
  ].join('\n'),
  worldGoal: {
    kind: 'coverage',
    targets: BOOK_TARGETS,
    actionName: 'shelve',
    actionVerb: '归位',
    targetNoun: '图书',
    unit: '本',
  },
};
