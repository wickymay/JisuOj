import test from 'node:test'
import assert from 'node:assert/strict'
import { descriptionWithConstraints, manualProblemDraft, problemPublishForm, testDataFingerprint, withGeneratedArtifacts } from '../src/utils/testDataHandoff.ts'

const form = { title: 'A+B', description: '<p>求和</p>', input: '两个整数', output: '它们的和',
  sampleInput: '1 2\n', sampleOutput: '3\n', hint: '使用64位整数', source: '自编',
  timeLimit: 1, memoryLimit: 128, specialJudge: false, hidden: true }
const result = { referenceSolutionCpp: 'verified-reference', generatorCpp: 'seeded-generator', validatorCpp: 'strict-validator' }

test('manual draft preserves statement, constraints, sample, limits and visibility for the import API', () => {
  const draft = manualProblemDraft(form, '0 <= a,b <= 10^9', 'existing-reference')
  for (const key of ['title', 'description', 'input', 'output', 'sampleInput', 'sampleOutput', 'hint', 'source', 'timeLimit', 'memoryLimit', 'hidden'])
    assert.equal(draft[key], form[key])
  assert.equal(draft.constraints, '0 <= a,b <= 10^9')
  assert.equal(draft.referenceSolutionCpp, 'existing-reference')
  assert.equal(draft.sourceType, 'manual')
  assert.equal(draft.spj, 0)
  assert.equal(manualProblemDraft({ ...form, specialJudge: true }, '', '').spj, 1)
})

test('adopted data uploads the actual ZIP and matching programs in one publish request', async () => {
  const draft = manualProblemDraft(form, 'a,b >= 0', '')
  const payload = withGeneratedArtifacts(draft, result)
  const zip = new File(['fixture-zip-bytes'], 'generated.zip', { type: 'application/zip' })
  const multipart = problemPublishForm(payload, zip)
  assert.deepEqual(JSON.parse(await multipart.get('problem').text()), payload)
  assert.equal(await multipart.get('testData').text(), 'fixture-zip-bytes')
  assert.equal(multipart.get('testData').name, 'generated.zip')
  assert.equal(payload.referenceSolutionCpp, 'verified-reference')
  assert.equal(payload.generatorCpp, 'seeded-generator')
  assert.equal(payload.validatorCpp, 'strict-validator')
  assert.equal(payload.hidden, true)
  assert.equal(draft.referenceSolutionCpp, '')
})

test('changes affecting correctness invalidate adopted data, while publication metadata does not', () => {
  const draft = manualProblemDraft(form, '0 <= a,b <= 100', '')
  const original = testDataFingerprint(draft)
  for (const field of ['title', 'description', 'input', 'output', 'constraints', 'sampleInput', 'sampleOutput', 'hint', 'referenceSolutionCpp'])
    assert.notEqual(testDataFingerprint({ ...draft, [field]: 'modified' }), original, field)
  assert.notEqual(testDataFingerprint({ ...draft, specialProperties: ['a <= b'] }), original)
  assert.notEqual(testDataFingerprint({ ...draft, timeLimit: 2 }), original)
  assert.notEqual(testDataFingerprint({ ...draft, spj: 1 }), original)
  assert.equal(testDataFingerprint({ ...draft, hidden: false, source: '另一来源' }), original)
})

test('manual save retains entered constraints without interpreting them as HTML', () => {
  assert.equal(descriptionWithConstraints(form.description, '   '), form.description)
  const saved = descriptionWithConstraints(form.description, 'a < b && b > 0\n<script>bad()</script>')
  assert.ok(saved.startsWith(form.description))
  assert.ok(saved.includes('a &lt; b &amp;&amp; b &gt; 0'))
  assert.ok(saved.includes('&lt;script&gt;'))
  assert.ok(!saved.includes('<script>'))
})
