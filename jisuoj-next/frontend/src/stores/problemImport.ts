import { computed, reactive, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import type { ProblemDraft } from '../types/domain'
import { testDataFingerprint } from '../utils/testDataHandoff.ts'

export interface ImportDraftState {
  source: { sourceType: string; sourceRef: string; rawContent: string; extraPrompt: string }
  step: number
  maxStep: number
  draft: ProblemDraft | null
  specialPropertiesText: string
  testZip: File | null
  generatedFingerprint: string
  dataNotice: string
}
interface SavedDraft { version: 1; savedAt: number; value: ImportDraftState }
interface DraftStorage {
  read(userId: string): Promise<SavedDraft | undefined>
  write(userId: string, record: SavedDraft): Promise<void>
  remove(userId: string): Promise<void>
}
export interface PublishedImport { problemId: number; testCaseCount: number; warnings: string[] }
export const IMPORT_DRAFT_TTL = 24 * 60 * 60 * 1000

export function emptyImportDraft(): ImportDraftState {
  return {
    source: { sourceType: 'codeforces', sourceRef: '', rawContent: '', extraPrompt: '' },
    step: 1, maxStep: 1, draft: null, specialPropertiesText: '', testZip: null,
    generatedFingerprint: '', dataNotice: '',
  }
}
function copyDraft(value: ImportDraftState): ImportDraftState {
  const { testZip, ...text } = value
  return { ...JSON.parse(JSON.stringify(text)), testZip: testZip
    ? new File([testZip], testZip.name, { type: testZip.type, lastModified: testZip.lastModified }) : null }
}
function hasContent(value: ImportDraftState) {
  return !!(value.draft || value.testZip || value.source.sourceRef || value.source.rawContent || value.source.extraPrompt)
}

// Keep an in-memory copy as well, so navigation still works if browser storage is unavailable.
// Serialize writes and deletes to prevent a pending autosave from reviving a cleared draft.
export function createImportDraftRepository(storage: DraftStorage, now = Date.now) {
  const memory = new Map<string, SavedDraft | null>()
  let pending: Promise<unknown> = Promise.resolve()
  function enqueue<T>(operation: () => Promise<T>): Promise<T> {
    const result = pending.then(operation)
    pending = result.catch(() => {})
    return result
  }
  return {
    async load(userId: string): Promise<SavedDraft | null> {
      return enqueue(async () => {
        const record = memory.has(userId) ? memory.get(userId) : await storage.read(userId)
        if (!record) return null
        if (record.version !== 1 || !Number.isFinite(record.savedAt) || now() - record.savedAt >= IMPORT_DRAFT_TTL
          || !record.value?.source || !Number.isInteger(record.value.step) || !Number.isInteger(record.value.maxStep)) {
          memory.set(userId, null)
          await storage.remove(userId)
          return null
        }
        memory.set(userId, record)
        return { ...record, value: copyDraft(record.value) }
      })
    },
    save(userId: string, value: ImportDraftState): Promise<number> {
      const record: SavedDraft = { version: 1, savedAt: now(), value: copyDraft(value) }
      memory.set(userId, record)
      return enqueue(async () => { await storage.write(userId, record); return record.savedAt })
    },
    clear(userId: string): Promise<void> {
      memory.set(userId, null)
      return enqueue(() => storage.remove(userId))
    },
  }
}

let database: Promise<IDBDatabase> | undefined
function openDatabase() {
  if (!database) {
    database = new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open('jisuoj-problem-import', 1)
      request.onupgradeneeded = () => request.result.createObjectStore('drafts')
      request.onsuccess = () => {
        const db = request.result
        db.onversionchange = () => { db.close(); database = undefined }
        resolve(db)
      }
      request.onerror = () => reject(request.error)
      request.onblocked = () => reject(new Error('草稿存储被其他页面占用'))
    }).catch(error => { database = undefined; throw error })
  }
  return database
}
async function draftTransaction<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDatabase()
  return new Promise((resolve, reject) => {
    const transaction = db.transaction('drafts', mode)
    const request = action(transaction.objectStore('drafts'))
    transaction.oncomplete = () => resolve(request.result)
    transaction.onerror = () => reject(transaction.error || request.error)
    transaction.onabort = () => reject(transaction.error || new Error('草稿暂存失败'))
  })
}
const repository = createImportDraftRepository({
  read: userId => draftTransaction('readonly', store => store.get(userId)),
  write: async (userId, record) => { await draftTransaction('readwrite', store => store.put(record, userId)) },
  remove: async userId => { await draftTransaction('readwrite', store => store.delete(userId)) },
})

export function useProblemImportStore(userId: string, drafts = repository) {
  return defineStore(`problem-import:${userId}`, () => {
    const state = reactive({ ...emptyImportDraft(), busy: false, error: '', confirmed: false, published: null as PublishedImport | null })
    const ready = ref(false), savedAt = ref(0), saving = ref(false), storageError = ref(''), restored = ref(false)
    let timer: ReturnType<typeof setTimeout> | undefined
    let restoring: Promise<void> | undefined
    const generationDraft = computed(() => state.draft ? {
      ...state.draft,
      specialProperties: state.specialPropertiesText.split(/\r?\n/).map(item => item.trim()).filter(Boolean),
    } : null)
    const snapshot = (): ImportDraftState => ({
      source: state.source, step: state.step, maxStep: state.maxStep, draft: state.draft,
      specialPropertiesText: state.specialPropertiesText, testZip: state.testZip,
      generatedFingerprint: state.generatedFingerprint, dataNotice: state.dataNotice,
    })
    function invalidateOldData() {
      if (state.generatedFingerprint && generationDraft.value && state.generatedFingerprint !== testDataFingerprint(generationDraft.value)) {
        state.testZip = null; state.generatedFingerprint = ''; state.confirmed = false
        state.dataNotice = '题面或约束已修改，已取消采用旧测试数据，请重新生成。'
      }
    }
    watch(() => generationDraft.value ? testDataFingerprint(generationDraft.value) : '', () => {
      if (ready.value) invalidateOldData()
    }, { flush: 'sync' })

    async function saveNow() {
      if (timer) clearTimeout(timer)
      if (!ready.value || state.published) return
      saving.value = true
      try {
        savedAt.value = hasContent(state) ? await drafts.save(userId, snapshot()) : (await drafts.clear(userId), 0)
        storageError.value = ''
      } catch {
        storageError.value = '浏览器暂存不可用，内容仅在本次打开网站期间保留，请勿刷新或关闭页面。'
      } finally { saving.value = false }
    }
    watch(snapshot, () => {
      if (!ready.value || state.published) return
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => void saveNow(), 400)
    }, { deep: true, flush: 'sync' })

    function restore() {
      if (!restoring) restoring = (async () => {
        try {
          const record = await drafts.load(userId)
          if (record) {
            Object.assign(state, record.value)
            state.maxStep = state.draft ? Math.max(2, Math.min(4, state.maxStep)) : 1
            state.step = Math.max(1, Math.min(state.maxStep, state.step))
            savedAt.value = record.savedAt; restored.value = true
          }
        } catch { storageError.value = '无法读取浏览器暂存，当前页面仍可正常编辑。' }
        finally { ready.value = true; invalidateOldData() }
      })()
      return restoring
    }
    async function clearSaved() {
      if (timer) clearTimeout(timer)
      try { await drafts.clear(userId); savedAt.value = 0; restored.value = false; storageError.value = '' }
      catch { storageError.value = '暂存清除失败，请重试。' }
    }
    async function reset() {
      if (state.busy) return
      Object.assign(state, emptyImportDraft(), { error: '', confirmed: false, published: null })
      await clearSaved()
    }
    return { state, generationDraft, ready, savedAt, saving, restored, storageError, restore, saveNow, clearSaved, reset }
  })()
}
