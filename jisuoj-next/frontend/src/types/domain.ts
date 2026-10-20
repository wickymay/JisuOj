export interface UserPrincipal { userId: string; nick: string; roles: string[] }

export interface ProblemSummary {
  id: number; title: string; source: string; accepted: number; submit: number
  acceptanceRate: number; userStatus: string | null
}

export interface ProblemDetail extends ProblemSummary {
  description: string; input: string; output: string; sampleInput: string; sampleOutput: string
  hint: string; timeLimit: number; memoryLimit: number; specialJudge: boolean; hidden: boolean; createdAt: string
}

export interface AdminProblemSummary {
  id: number; title: string; source: string; hidden: boolean
  accepted: number; submit: number; createdAt: string | null
}

export interface AcceptedUser {
  userId: string; nick: string; school: string; firstAcceptedAt: string
}

export interface Submission {
  id: number; problemId: number; problemTitle: string; userId: string; nick: string
  result: string; resultId: number; memory: number; time: number; language: string
  languageId: number; codeLength: number; submittedAt: string; judger: string; contestId: number | null
}

export interface ContestSummary {
  id: number; title: string; startTime: string; endTime: string; status: string
  privateContest: boolean; type: number; creator: string
}

export interface ContestProblem {
  id: number; number: number; label: string; title: string; accepted: number; submit: number
}

export interface ContestDetail extends ContestSummary { description: string; hidden: boolean; problems: ContestProblem[] }

export interface Standing {
  rank: number; userId: string; nick: string; solved: number; penalty: number
  problems: Record<string, { accepted: boolean; attempts: number; acceptedMinutes: number | null; result: string }>
}

export interface RankRow {
  rank: number; userId: string; nick: string; school: string; groupName: string
  solved: number; submit: number; ratio: number; lastActiveAt: string | null
}

export interface TopicSummary {
  id: number; title: string; author: string; problemId: number; contestId: number | null
  replyCount: number; lastReplyAt: string; topLevel: number
}

export interface NewsSummary { id: number; title: string; author: string; publishedAt: string; importance: number }

export interface ProblemDraft {
  title: string; description: string; input: string; output: string; constraints: string
  specialProperties: string[]; sampleInput: string; sampleOutput: string; hint: string
  timeLimit: number; memoryLimit: number; source: string; sourceUrl: string; sourceType: string
  sourceRef: string; problemType: string; spj: number; testGroupCount: number
  testInput: string; testOutput: string; referenceSolutionCpp: string; validatorCpp: string
  generatorCpp: string; warnings: string[]; hidden: boolean
}
