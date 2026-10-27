import type { ProblemDraft } from '../types/domain'
import type { GeneratedData } from '../types/testData'

export interface ManualProblemForm {
  title: string; description: string; input: string; output: string; sampleInput: string; sampleOutput: string
  hint: string; source: string; timeLimit: number; memoryLimit: number; specialJudge: boolean; hidden: boolean
}

export function manualProblemDraft(form: ManualProblemForm, constraints: string, referenceSolutionCpp: string): ProblemDraft {
  return {
    title: form.title, description: form.description, input: form.input, output: form.output,
    sampleInput: form.sampleInput, sampleOutput: form.sampleOutput, hint: form.hint, source: form.source,
    timeLimit: form.timeLimit, memoryLimit: form.memoryLimit, hidden: form.hidden,
    constraints, referenceSolutionCpp, specialProperties: [], sourceUrl: '', sourceType: 'manual', sourceRef: '',
    problemType: 'standard', spj: form.specialJudge ? 1 : 0, testGroupCount: 10,
    testInput: '', testOutput: '', validatorCpp: '', generatorCpp: '', warnings: [],
  }
}

/** Only changes affecting generated inputs/answers invalidate a selected generated ZIP. */
export function testDataFingerprint(draft: ProblemDraft): string {
  return JSON.stringify([draft.title, draft.description, draft.input, draft.output, draft.constraints,
    draft.specialProperties, draft.sampleInput, draft.sampleOutput, draft.hint, draft.timeLimit,
    draft.memoryLimit, draft.problemType, draft.spj, draft.referenceSolutionCpp])
}

export function withGeneratedArtifacts(draft: ProblemDraft, result: GeneratedData): ProblemDraft {
  return { ...draft, referenceSolutionCpp: result.referenceSolutionCpp,
    generatorCpp: result.generatorCpp, validatorCpp: result.validatorCpp }
}

export function problemPublishForm(draft: ProblemDraft, zip: File | null): FormData {
  const data = new FormData()
  data.append('problem', new Blob([JSON.stringify(draft)], { type: 'application/json' }))
  if (zip) data.append('testData', zip)
  return data
}

/** Preserve entered constraints on the existing JSON-only manual-create path too. */
export function descriptionWithConstraints(description: string, constraints: string): string {
  if (!constraints.trim()) return description
  const escaped = constraints.replace(/[&<>"']/g, value => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[value]!)
  return `${description}<section class="problem-constraints"><h3>数据范围</h3><pre>${escaped}</pre></section>`
}
