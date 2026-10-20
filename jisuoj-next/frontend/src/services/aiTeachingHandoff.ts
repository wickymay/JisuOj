const STORAGE_KEY = 'jisuoj-ai-teaching-handoff'
const MAX_AGE_MS = 10 * 60 * 1000

export interface AiTeachingHandoff {
  problemId: number
  problemTitle: string
  sourceCode: string
  sourceLanguage: string
  ownerUserId: string | null
}

export function saveAiTeachingHandoff(context: AiTeachingHandoff): string {
  const token = crypto.randomUUID()
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ ...context, token, createdAt: Date.now() }))
  return token
}

export function takeAiTeachingHandoff(token: string, userId: string): AiTeachingHandoff | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const value: unknown = JSON.parse(raw)
    if (!value || typeof value !== 'object') return null
    const item = value as Record<string, unknown>
    if (item.token !== token) return null
    sessionStorage.removeItem(STORAGE_KEY)
    if (typeof item.createdAt !== 'number' ||
        Date.now() - item.createdAt > MAX_AGE_MS || item.createdAt > Date.now() ||
        !Number.isInteger(item.problemId) || Number(item.problemId) < 1 ||
        typeof item.problemTitle !== 'string' || typeof item.sourceCode !== 'string' ||
        typeof item.sourceLanguage !== 'string' ||
        (item.ownerUserId !== null && item.ownerUserId !== userId)) return null
    return {
      problemId: Number(item.problemId),
      problemTitle: item.problemTitle,
      sourceCode: item.sourceCode,
      sourceLanguage: item.sourceLanguage,
      ownerUserId: item.ownerUserId as string | null,
    }
  } catch { return null }
}
