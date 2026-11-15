export const kinds = {
  single_choice: "单选题",
  boolean: "判断题",
  fill: "填空题",
  python: "编程题",
};
export const statuses = { draft: "待审核", ready: "可用", disabled: "停用" };
export type Case = { input: string; expected_output: string };
