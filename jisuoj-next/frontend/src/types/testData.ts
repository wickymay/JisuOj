export interface TestDataCapabilities { providers: Record<string, boolean>; runnerConfigured: boolean; maxCaseCount: number }
export interface GeneratedCase { name: string; label: string; inputBytes: number; outputBytes: number; inputPreview: string; outputPreview: string }
export interface GeneratedData {
  referenceSolutionCpp: string; independentSolutionCpp: string; generatorCpp: string; validatorCpp: string; wrongSolutionCpp: string
  cases: GeneratedCase[]; notes: string[]; mutantRejected: number; models: Record<string, string>; seed: number
}
export interface GenerationJob {
  id: string; state: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'; stage: string; error: string; createdAt: string
  events: { stage: string; message: string; at: string }[]; result?: GeneratedData
}
