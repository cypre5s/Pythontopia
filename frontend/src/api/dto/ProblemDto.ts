export interface ProblemDto {
  problemId: number;
  kcCode: string;
  title: string;
  statementMarkdown: string;
  inputDescription: string | null;
  outputDescription: string | null;
  difficulty: string;
  sourceLabel: string;
}
