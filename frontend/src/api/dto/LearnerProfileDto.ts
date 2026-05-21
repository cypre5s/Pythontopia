export interface LearnerProfileDto {
  profileId: number | null;
  userId: number;
  packId: number;
  major: string | null;
  baseLevel?: {
    programmingYears: number;
    languagesKnown: string[];
    pythonSelfRatedLevel: number;
  } | null;
  learningGoal?: {
    kind: string;
    deadlineIso: string | null;
    targetKcCodes: string[];
  } | null;
  cognitiveStyle?: {
    fieldDependent: number;
    sequentialVsHolistic: number;
  } | null;
  learningPreference?: {
    mediaPriority: string[];
    sessionMinutesPerDay: number;
  } | null;
  errorPatterns?: {
    frequentKcCodes: string[];
    commonCategories: string[];
  } | null;
  practiceCapability?: {
    avgAttemptsPerProblem: number;
    successRate: number;
  } | null;
  emotionPersistence?: {
    resilience: number;
    recentMood: string;
  } | null;
  narrativeSummary: string | null;
}
