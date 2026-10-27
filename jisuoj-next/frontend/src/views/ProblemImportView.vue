<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, toRefs } from 'vue'
import { api, ensureCsrf, errorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'
import { useProblemImportStore } from '../stores/problemImport'
import type { PublishedImport } from '../stores/problemImport'
import type { ProblemDraft } from '../types/domain'
import type { GeneratedData } from '../types/testData'
import TestDataGenerator from '../components/TestDataGenerator.vue'
import { problemPublishForm, testDataFingerprint, withGeneratedArtifacts } from '../utils/testDataHandoff'

const auth = useAuthStore()
const userId = auth.user!.userId
const session = useProblemImportStore(userId)
const { step, maxStep, busy, error, testZip, draft, specialPropertiesText, confirmed,
  published, source, generatedFingerprint, dataNotice } = toRefs(session.state)
confirmed.value = false
const aiConfigured = ref(false)
const generationDraft = computed(() => session.generationDraft)
const generatorKey = ref(0)
const savedTime = computed(() => session.savedAt ? new Date(session.savedAt).toLocaleTimeString('zh-CN', { hour12: false }) : '')

const sourceHint = computed(() => source.value.sourceType === 'codeforces'
  ? '例如 4A 或 https://codeforces.com/problemset/problem/4/A'
  : '例如 994805046380707840/1649748772841508873 或完整 PTA 题目网址')

function goStep(target: number) {
  if (target <= maxStep.value) step.value = target
}
function syncSpecialProperties() {
  if (!draft.value) return
  draft.value.specialProperties = specialPropertiesText.value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean)
}
function fileChanged(event: Event) {
  testZip.value = (event.target as HTMLInputElement).files?.[0] || null
  generatedFingerprint.value = ''; dataNotice.value = ''; confirmed.value = false
}
function useGeneratedData(file: File, result: GeneratedData) {
  if (!draft.value) return
  generatedFingerprint.value = ''
  draft.value = withGeneratedArtifacts(draft.value, result)
  generatedFingerprint.value = testDataFingerprint(generationDraft.value!)
  testZip.value = file
  dataNotice.value = `已采用 ${result.cases.length} 组生成数据，确认后随题目一起导入。`
  confirmed.value = false
  openTestDataStep()
}
async function refreshCapability() {
  const { data } = await api.get<{ aiConfigured: boolean }>('/admin/problem-imports/capabilities')
  aiConfigured.value = data.aiConfigured
}
async function preview() {
  if (!aiConfigured.value) {
    error.value = '智能体尚未配置，请先到“智能体配置”填写 API。'
    return
  }
  busy.value = true; error.value = ''; published.value = null
  try {
    await ensureCsrf()
    draft.value = (await api.post<ProblemDraft>('/admin/problem-imports/preview', {
      ...source.value,
      generateArtifacts: false,
    }, { timeout: 240_000 })).data
    specialPropertiesText.value = draft.value.specialProperties?.join('\n') || ''
    step.value = 2; maxStep.value = 2; confirmed.value = false
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
async function generateArtifacts() {
  if (!draft.value) return
  busy.value = true; error.value = ''
  syncSpecialProperties()
  try {
    await ensureCsrf()
    draft.value = (await api.post<ProblemDraft>('/admin/problem-imports/generate-artifacts', {
      draft: draft.value,
      extraPrompt: source.value.extraPrompt,
    }, { timeout: 240_000 })).data
    specialPropertiesText.value = draft.value.specialProperties?.join('\n') || ''
    step.value = 3; maxStep.value = Math.max(maxStep.value, 3)
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
function openTestDataStep() {
  syncSpecialProperties()
  step.value = 4
  maxStep.value = 4
}
async function handoff() {
  if (!draft.value) return
  error.value = ''
  syncSpecialProperties()
  try {
    await ensureCsrf()
    const response = await api.post('/admin/problem-imports/handoff', draft.value, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `${draft.value.title || 'problem'}-judgedata.json`
    link.click()
    URL.revokeObjectURL(url)
  } catch (e) { error.value = errorMessage(e) }
}
async function publish() {
  if (!draft.value || !confirmed.value) return
  busy.value = true; error.value = ''
  syncSpecialProperties()
  try {
    await ensureCsrf()
    const form = problemPublishForm(draft.value, testZip.value)
    published.value = (await api.post<PublishedImport>('/admin/problem-imports/publish', form)).data
    await session.clearSaved()
    clearGeneratorCache()
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
function clearGeneratorCache() {
  try { sessionStorage.removeItem(`jisuoj-testdata-job:${userId}:problem-import`) } catch { /* Storage may be unavailable. */ }
  generatorKey.value++
}
function resetFlow() {
  if (busy.value) return
  clearGeneratorCache()
  void session.reset()
}
const saveBeforeLeaving = () => { void session.saveNow() }
onMounted(async () => {
  window.addEventListener('pagehide', saveBeforeLeaving)
  await session.restore()
  try { await refreshCapability() } catch (e) { error.value = errorMessage(e) }
})
onBeforeUnmount(() => {
  window.removeEventListener('pagehide', saveBeforeLeaving)
  saveBeforeLeaving()
})
</script>

<template>
  <div class="page-head"><div><h2>题目拉取</h2><span class="muted">Codeforces / PTA → 智能体整理 → 人工审核 → 测试用例生成 → 导入</span></div><span :class="['agent-status', aiConfigured ? 'configured' : 'missing']">{{ aiConfigured ? '智能体已配置' : '智能体未配置' }}</span></div>
  <div v-if="!aiConfigured" class="error import-config-warning">题目拉取需要先配置 DeepSeek。配置加密保存，重启后仍然有效。 <RouterLink to="/admin/ai-config">前往配置</RouterLink></div>
  <p v-if="error" class="error">{{ error }}</p>
  <p v-if="dataNotice && !published" class="card" role="status">{{ dataNotice }}</p>
  <div v-if="!published && session.ready" class="card import-draft-bar">
    <div role="status"><strong>{{ session.restored ? '已恢复上次暂存' : '自动暂存已开启' }}</strong><p class="muted">{{ session.saving ? '正在暂存…' : savedTime ? `已暂存于 ${savedTime}。` : '' }} 题面、当前步骤和已选 ZIP 会在此浏览器按账号保留 24 小时，切换页面或刷新后可继续。</p></div>
    <div class="toolbar"><button class="button secondary" type="button" :disabled="busy || session.saving" @click="session.saveNow">暂存草稿</button><button class="button secondary" type="button" :disabled="busy" @click="resetFlow">清除暂存</button></div>
  </div>
  <p v-if="session.storageError" class="error" role="alert">{{ session.storageError }}</p>
  <p v-if="!session.ready" class="card" role="status">正在恢复暂存…</p>

  <template v-if="!published && session.ready">
    <div class="stepper four import-stepper">
      <button v-for="item in [{n:1,t:'题目来源'},{n:2,t:'题面审核'},{n:3,t:'参考程序'},{n:4,t:'测试数据 / 导入'}]" :key="item.n" type="button" class="step" :class="{ active: step === item.n, done: item.n < step }" :disabled="item.n > maxStep" @click="goStep(item.n)"><strong>{{ item.n }}.</strong> {{ item.t }}</button>
    </div>

    <section v-if="step === 1" class="card form-stack import-step-card">
      <div><h2>第 1 步：题目来源</h2><p class="muted">填写公开题号或 URL；服务器无法访问时，可直接粘贴浏览器中的题面 HTML 或文字。</p></div>
      <div class="source-choice two"><label><input v-model="source.sourceType" type="radio" value="codeforces" />Codeforces</label><label><input v-model="source.sourceType" type="radio" value="pta" />PTA（拼题 A）</label></div>
      <div class="field"><label>{{ source.sourceType === 'codeforces' ? 'Codeforces 题号或 URL' : 'PTA 题目 URL 或题集ID/题目ID' }}</label><input v-model.trim="source.sourceRef" :placeholder="sourceHint" /><small class="muted">留空时必须在下面粘贴原始题面。</small></div>
      <div class="field"><label>原始题面（可选）</label><textarea v-model="source.rawContent" rows="12" placeholder="填写后优先使用粘贴内容，不再请求远程网站。"></textarea></div>
      <div class="field"><label>管理员补充要求（可选）</label><textarea v-model="source.extraPrompt" rows="3" placeholder="例如：保留原题 LaTeX，题面使用中文说明。"></textarea></div>
      <div class="toolbar"><button class="button" :disabled="busy || !aiConfigured || (!source.sourceRef && !source.rawContent)" @click="preview">{{ busy ? '正在拉取并解析…' : '拉取并解析，进入下一步' }}</button></div>
    </section>

    <section v-else-if="step === 2 && draft" class="form-stack">
      <div class="card import-step-card form-stack">
        <div><h2>第 2 步：题面审核</h2><p class="muted">先核对题面、输入输出、数据范围和样例；此步骤不会生成或上传隐藏测试。</p></div>
        <div class="grid two"><div class="field"><label>标题</label><input v-model="draft.title" /></div><div class="field"><label>来源</label><input v-model="draft.source" /></div></div>
        <div class="field"><label>原题链接</label><input v-model="draft.sourceUrl" type="url" /></div>
        <div class="grid three"><div class="field"><label>时间限制（秒）</label><input v-model.number="draft.timeLimit" type="number" min="0.001" max="300" step="0.001" /></div><div class="field"><label>内存限制（MiB）</label><input v-model.number="draft.memoryLimit" type="number" min="1" max="4096" /></div><div class="field"><label>评测类型</label><select v-model.number="draft.spj"><option :value="0">普通标准输入输出</option><option :value="1">Special Judge</option><option :value="2">选择 / 填空（RTJ）</option><option :value="3">交互题</option></select></div></div>
        <div class="field"><label>题目描述</label><textarea v-model="draft.description" rows="12"></textarea></div>
        <div class="grid two"><div class="field"><label>输入说明</label><textarea v-model="draft.input"></textarea></div><div class="field"><label>输出说明</label><textarea v-model="draft.output"></textarea></div></div>
        <div class="field"><label>数据范围描述</label><textarea v-model="draft.constraints" placeholder="例如：1 ≤ n ≤ 2×10^5"></textarea></div>
        <div class="field"><label>特殊性质（每行一条）</label><textarea v-model="specialPropertiesText" placeholder="例如：数组有序&#10;图是一棵树"></textarea></div>
        <div class="grid two"><div class="field"><label>样例输入</label><textarea v-model="draft.sampleInput"></textarea></div><div class="field"><label>样例输出</label><textarea v-model="draft.sampleOutput"></textarea></div></div>
        <div class="field"><label>提示</label><textarea v-model="draft.hint"></textarea></div>
      </div>
      <div v-if="draft.warnings?.length" class="card"><h2>需要人工确认</h2><ul class="notice-list"><li v-for="warning in draft.warnings" :key="warning">{{ warning }}</li></ul></div>
      <div class="toolbar"><button class="button secondary" @click="step = 1">上一步</button><button class="button" :disabled="busy" @click="generateArtifacts">{{ busy ? '正在生成…' : '生成参考程序并进入下一步' }}</button></div>
    </section>

    <section v-else-if="step === 3 && draft" class="card form-stack import-step-card">
      <div><h2>第 3 步：参考程序与测试数据生成</h2><p class="muted">可使用下方国内模型协作生成并执行验证，也可下载交接 JSON 使用外部工具。</p></div>
      <div class="field"><label>建议测试组数</label><input v-model.number="draft.testGroupCount" type="number" min="1" max="20" /><small class="muted">仅作为交给 Judgedata 的建议，不代表官方测试组数。</small></div>
      <div class="field"><label>C++17 参考程序</label><textarea v-model="draft.referenceSolutionCpp" class="code" spellcheck="false"></textarea></div>
      <div class="grid two"><div class="field"><label>可选校验器 / Validator</label><textarea v-model="draft.validatorCpp" class="code" spellcheck="false"></textarea></div><div class="field"><label>可选生成器 / Generator</label><textarea v-model="draft.generatorCpp" class="code" spellcheck="false"></textarea></div></div>
      <div class="import-handoff-note">JudgeData 当前页面需要手动配置：复制参考程序、数据范围和建议测试组数。交接 JSON 用于结构化交接和存档，不应整段粘贴进单个输入框。</div>
      <div class="toolbar"><button class="button secondary" @click="step = 2">上一步</button><button class="button secondary" @click="handoff">下载 Judgedata 交接 JSON</button><button class="button" @click="openTestDataStep">下一步：上传测试数据</button></div>
    </section>

    <section v-else-if="step === 4 && draft" class="form-stack">
      <div class="card form-stack import-step-card">
        <div><h2>第 4 步：测试数据与导入</h2><p class="muted">采用上一步生成的数据包，或上传已验证的 ZIP。系统接收成对的 .in/.out 文件，并兼容 .ans。</p></div>
        <p v-if="testZip" class="success">已选择：{{ testZip.name }}（{{ Math.ceil(testZip.size / 1024) }} KiB）</p>
        <div class="field"><label>Judgedata 测试数据 ZIP</label><input type="file" accept=".zip,application/zip" @change="fileChanged" /><small class="muted">压缩包最大 64 MiB，解压后最大 256 MiB；上传前校验路径、文件名和输入输出配对。</small></div>
        <div class="grid two"><div class="field"><label>手工测试输入（可选）</label><textarea v-model="draft.testInput" placeholder="没有 ZIP 时，可粘贴有权使用的测试数据。"></textarea></div><div class="field"><label>手工测试输出（可选）</label><textarea v-model="draft.testOutput" placeholder="与测试输入一一对应。"></textarea></div></div>
        <div class="error import-data-warning">Codeforces/PTA 的官方隐藏测试不会被自动抓取，请使用 Judgedata 生成数据或上传你有权使用的数据。</div>
        <label><input v-model="draft.hidden" type="checkbox" style="width:auto" /> 先发布为隐藏题目</label>
        <label class="import-confirm"><input v-model="confirmed" type="checkbox" /> 我已检查题面、权限和测试数据，确认导入</label>
        <div class="toolbar"><button class="button secondary" @click="step = 3">上一步</button><button class="button" :disabled="busy || !confirmed" @click="publish">{{ busy ? '正在导入…' : '确认导入题目' }}</button></div>
      </div>
      <div v-if="draft.warnings?.length" class="card"><h2>导入提醒</h2><ul class="notice-list"><li v-for="warning in draft.warnings" :key="warning">{{ warning }}</li></ul></div>
    </section>
    <TestDataGenerator v-if="generationDraft && step >= 2" :key="generatorKey" :draft="generationDraft" scope="problem-import" :disabled="busy" allow-apply apply-label="采用测试数据并进入导入" style="margin-top:20px" @generated="useGeneratedData" />
    <section v-else-if="step === 1" class="card" style="margin-top:20px"><h2>测试用例生成</h2><p class="muted">拉取并核对题面后，可在页面底部直接生成、验证并采用测试数据。</p></section>
  </template>

  <section v-else-if="published" class="card import-success"><span>✓</span><h2>题目导入成功</h2><p>题目编号为 {{ published.problemId }}，已导入 {{ published.testCaseCount }} 组测试数据。</p><div class="toolbar"><RouterLink class="button" :to="`/problems/${published.problemId}`">查看题目</RouterLink><button class="button secondary" @click="resetFlow">继续拉取</button></div></section>
</template>

<style scoped>
.import-draft-bar { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:18px }
.import-draft-bar p { margin:6px 0 0 }
.import-draft-bar .toolbar { flex-shrink:0; margin:0 }
@media (max-width: 800px) { .import-draft-bar { align-items:flex-start; flex-direction:column } }
</style>
