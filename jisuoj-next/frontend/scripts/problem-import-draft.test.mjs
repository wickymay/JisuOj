import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { createImportDraftRepository, emptyImportDraft, IMPORT_DRAFT_TTL, useProblemImportStore } from '../src/stores/problemImport.ts'
import { manualProblemDraft, problemPublishForm, testDataFingerprint } from '../src/utils/testDataHandoff.ts'

function storageFixture() {
  const records = new Map()
  return {
    records,
    storage: {
      async read(id) { return records.get(id) },
      async write(id, value) { records.set(id, value) },
      async remove(id) { records.delete(id) },
    },
  }
}
function draftFixture() {
  return manualProblemDraft({ title: '求和', description: '<p>计算 a+b</p>', input: '两个整数', output: '和',
    sampleInput: '1 2\n', sampleOutput: '3\n', hint: '', source: 'Codeforces', timeLimit: 1,
    memoryLimit: 128, specialJudge: false, hidden: true }, '0 <= a,b <= 100', 'verified-reference')
}

test('restores edited fields, step and the actual ZIP after a browser reload, without restoring confirmation', async () => {
  const { storage } = storageFixture()
  setActivePinia(createPinia())
  const page = useProblemImportStore('editor', createImportDraftRepository(storage))
  await page.restore()
  Object.assign(page.state, {
    source: { sourceType: 'pta', sourceRef: '123/456', rawContent: '<p>原始题面</p>', extraPrompt: '保留公式' },
    draft: draftFixture(), step: 4, maxStep: 4, specialPropertiesText: 'a <= b\n包含零',
    testZip: new File([new Uint8Array([80, 75, 0, 255, 10])], 'cases.zip', { type: 'application/zip', lastModified: 1234 }),
    confirmed: true,
  })
  page.state.generatedFingerprint = testDataFingerprint(page.generationDraft)
  await page.saveNow()
  page.$dispose()

  setActivePinia(createPinia())
  const returned = useProblemImportStore('editor', createImportDraftRepository(storage))
  await returned.restore()
  assert.equal(returned.restored, true)
  assert.equal(returned.state.step, 4)
  assert.equal(returned.state.maxStep, 4)
  assert.equal(returned.state.source.rawContent, '<p>原始题面</p>')
  assert.equal(returned.state.source.extraPrompt, '保留公式')
  assert.equal(returned.state.draft.referenceSolutionCpp, 'verified-reference')
  assert.equal(returned.state.specialPropertiesText, 'a <= b\n包含零')
  assert.equal(returned.state.confirmed, false)
  assert.equal(returned.state.testZip.name, 'cases.zip')
  assert.equal(returned.state.testZip.lastModified, 1234)
  const multipart = problemPublishForm(returned.state.draft, returned.state.testZip)
  assert.deepEqual(new Uint8Array(await multipart.get('testData').arrayBuffer()), new Uint8Array([80, 75, 0, 255, 10]))
  returned.$dispose()
})

test('navigation keeps the same workflow, including the result of a request completed after leaving', async () => {
  const { storage } = storageFixture()
  setActivePinia(createPinia())
  const repo = createImportDraftRepository(storage)
  const leftPage = useProblemImportStore('editor', repo)
  await leftPage.restore()
  leftPage.state.source.sourceRef = '4A'
  leftPage.state.busy = true
  const returnedPage = useProblemImportStore('editor', repo)
  assert.equal(returnedPage.state.busy, true)
  // The original request callback still writes into the retained workflow.
  leftPage.state.draft = draftFixture()
  leftPage.state.step = leftPage.state.maxStep = 2
  leftPage.state.busy = false
  assert.equal(returnedPage.state.draft.title, '求和')
  assert.equal(returnedPage.state.step, 2)
  await leftPage.saveNow()
  leftPage.$dispose()
})

test('typing automatically saves without clicking the save button', async () => {
  const { storage, records } = storageFixture()
  setActivePinia(createPinia())
  const page = useProblemImportStore('editor', createImportDraftRepository(storage))
  await page.restore()
  page.state.source.rawContent = '输入后直接切换页面也要保留'
  page.state.source.extraPrompt = '最后一次修改'
  await new Promise(resolve => setTimeout(resolve, 450))
  assert.equal(records.get('editor').value.source.rawContent, '输入后直接切换页面也要保留')
  assert.equal(records.get('editor').value.source.extraPrompt, '最后一次修改')
  assert.ok(page.savedAt > 0)
  page.$dispose()
})

test('clearing a draft wins over queued autosaves and successful publication removes it', async () => {
  const { storage, records } = storageFixture()
  setActivePinia(createPinia())
  const repo = createImportDraftRepository(storage)
  const page = useProblemImportStore('editor', repo)
  await page.restore()
  page.state.source.sourceRef = '4A'
  const pendingSave = page.saveNow()
  await page.reset()
  await pendingSave
  assert.equal(records.has('editor'), false)
  assert.equal(page.state.draft, null)
  assert.equal(page.state.source.sourceRef, '')
  assert.equal(await repo.load('editor'), null)

  page.state.draft = draftFixture()
  await page.saveNow()
  page.state.published = { problemId: 1000, testCaseCount: 10, warnings: [] }
  await page.clearSaved()
  await page.saveNow() // Leaving the success page must not recreate the draft.
  assert.equal(records.has('editor'), false)
  assert.equal(page.state.published.problemId, 1000)
  page.$dispose()
})

test('drafts are account-specific and expire after 24 hours', async () => {
  const { storage } = storageFixture()
  let now = 1_000_000
  const repo = createImportDraftRepository(storage, () => now)
  const value = emptyImportDraft()
  value.source.rawContent = '仅 editor-a 的题面'
  await repo.save('editor-a', value)
  assert.equal(await repo.load('editor-b'), null)
  assert.equal((await repo.load('editor-a')).value.source.rawContent, '仅 editor-a 的题面')
  now += IMPORT_DRAFT_TTL
  assert.equal(await repo.load('editor-a'), null)
  assert.equal(await createImportDraftRepository(storage, () => now).load('editor-a'), null)
})

test('changing restored constraints invalidates adopted data, while metadata edits keep it', async () => {
  const { storage } = storageFixture()
  setActivePinia(createPinia())
  const page = useProblemImportStore('editor', createImportDraftRepository(storage))
  await page.restore()
  page.state.draft = draftFixture()
  page.state.testZip = new File(['zip'], 'generated.zip')
  page.state.generatedFingerprint = testDataFingerprint(page.generationDraft)
  page.state.draft.hidden = false
  assert.equal(page.state.testZip.name, 'generated.zip')
  page.state.draft.constraints = '0 <= a,b <= 1000000'
  assert.equal(page.state.testZip, null)
  assert.match(page.state.dataNotice, /题面或约束已修改/)
  await page.saveNow()
  page.$dispose()
})

test('browser storage failure keeps a session copy usable on navigation and reports limited retention', async () => {
  const repo = createImportDraftRepository({
    async read() { throw new Error('storage unavailable') },
    async write() { throw new Error('quota exceeded') },
    async remove() { throw new Error('storage unavailable') },
  })
  setActivePinia(createPinia())
  const page = useProblemImportStore('editor', repo)
  await page.restore()
  assert.equal(page.ready, true)
  page.state.source.rawContent = '浏览器禁用存储时也能保留'
  await page.saveNow()
  assert.match(page.storageError, /仅在本次打开网站期间保留/)
  assert.equal((await repo.load('editor')).value.source.rawContent, '浏览器禁用存储时也能保留')
  page.$dispose()
})
