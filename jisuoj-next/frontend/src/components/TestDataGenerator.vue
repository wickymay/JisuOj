<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { isAxiosError } from 'axios'
import { api, ensureCsrf, errorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { ProblemDraft } from '../types/domain'
import type { GeneratedData, GenerationJob, TestDataCapabilities } from '../types/testData'
import { testDataFingerprint } from '../utils/testDataHandoff'

const props = defineProps<{ draft: ProblemDraft; allowApply?: boolean; applyLabel?: string; disabled?: boolean; scope?: string }>()
const emit = defineEmits<{ generated: [file: File, result: GeneratedData] }>()
const auth = useAuthStore()
const capabilities = ref<TestDataCapabilities | null>(null)
const job = ref<GenerationJob | null>(null)
const count = ref(10), seed = ref(20261026), useKimi = ref(false), extraPrompt = ref('')
const error = ref(''), busy = ref(false), transferring = ref(false), signature = ref('')
const required = ['qwen', 'deepseek', 'glm']
const missing = computed(() => required.filter(p => !capabilities.value?.providers[p]))
const running = computed(() => busy.value || job.value?.state === 'QUEUED' || job.value?.state === 'RUNNING')
const fingerprint = () => testDataFingerprint(props.draft)
const stale = computed(() => !!signature.value && signature.value !== fingerprint())
const cacheKey = `jisuoj-testdata-job:${auth.user?.userId || ''}:${props.scope || 'standalone'}`
let timer: ReturnType<typeof setTimeout> | undefined
let alive = true
let cacheReady = false

function saveCache() {
  try {
    sessionStorage.setItem(cacheKey, JSON.stringify({ id: job.value?.id, signature: signature.value,
      count: count.value, seed: seed.value, useKimi: useKimi.value, extraPrompt: extraPrompt.value }))
  } catch { /* Browser storage must not interrupt an active generation task. */ }
}
watch([count, seed, useKimi, extraPrompt], () => { if (cacheReady) saveCache() })

async function loadCapabilities() {
  try { capabilities.value = (await api.get<TestDataCapabilities>('/admin/test-data/capabilities')).data }
  catch (e) { error.value = errorMessage(e) }
}
async function poll(id: string) {
  if (!alive) return
  try {
    const { data } = await api.get<GenerationJob>(`/admin/test-data/jobs/${id}`)
    if (!alive) return
    job.value = data; error.value = ''
    if (data.state === 'QUEUED' || data.state === 'RUNNING') timer = setTimeout(() => poll(id), 2500)
    else saveCache()
  } catch (e) {
    if (!alive) return
    if (isAxiosError(e) && e.response?.status === 404) {
      job.value = null; signature.value = ''; saveCache()
      error.value = '之前的生成任务已过期或服务已重启，请重新生成。已采用的 ZIP 仍可随草稿导入。'
    } else error.value = `${errorMessage(e)}；可点击“刷新任务”继续查询。`
  }
}
async function start() {
  if (props.disabled || running.value || transferring.value) return
  busy.value = true; error.value = ''
  if (timer) clearTimeout(timer)
  try {
    await ensureCsrf()
    const snapshot = JSON.parse(JSON.stringify(props.draft)) as ProblemDraft
    signature.value = fingerprint()
    job.value = (await api.post<GenerationJob>('/admin/test-data/jobs',
      { draft: snapshot, caseCount: count.value, seed: seed.value, useKimi: useKimi.value, extraPrompt: extraPrompt.value })).data
    saveCache()
    if (alive) timer = setTimeout(() => poll(job.value!.id), 1500)
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
async function getZip(): Promise<File> {
  const { data } = await api.get<Blob>(`/admin/test-data/jobs/${job.value!.id}/zip`, { responseType: 'blob', timeout: 60000 })
  return new File([data], `test-data-${job.value!.id}.zip`, { type: 'application/zip' })
}
async function download(apply: boolean) {
  if (props.disabled || (apply && stale.value)) return
  transferring.value = true; error.value = ''
  try {
    const file = await getZip()
    if (apply) {
      if (props.disabled || stale.value) throw new Error('题面已修改，请重新生成后再应用。')
      emit('generated', file, job.value!.result!)
      await nextTick()
      signature.value = fingerprint()
      saveCache()
    }
    else {
      const url = URL.createObjectURL(file), link = document.createElement('a')
      link.href = url; link.download = file.name; link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    }
  } catch (e) { error.value = errorMessage(e) }
  finally { transferring.value = false }
}
function refresh() { if (timer) clearTimeout(timer); if (job.value) void poll(job.value.id) }
onMounted(async () => {
  await loadCapabilities()
  if (!alive) return
  try {
    const cached = JSON.parse(sessionStorage.getItem(cacheKey) || 'null')
    if (cached) {
      if (Number.isInteger(cached.count) && cached.count >= 1 && cached.count <= 20) count.value = cached.count
      if (Number.isInteger(cached.seed) && cached.seed >= 0 && cached.seed <= 2147483647) seed.value = cached.seed
      useKimi.value = cached.useKimi === true
      if (typeof cached.extraPrompt === 'string') extraPrompt.value = cached.extraPrompt.slice(0, 4000)
      if (typeof cached.id === 'string' && typeof cached.signature === 'string') {
        signature.value = cached.signature
        await poll(cached.id)
      }
    }
  } catch { /* An unreadable cache should leave a fresh, usable form. */ }
  finally { cacheReady = true }
})
onUnmounted(() => { alive = false; if (timer) clearTimeout(timer) })
</script>

<template>
  <section class="card form-stack test-data-generator">
    <div class="page-head"><div><h2>智能生成测试数据</h2><span class="muted">Qwen 生成 → DeepSeek 标准解 → GLM 独立解与校验 → 隔离执行</span></div><RouterLink to="/admin/ai-config">模型配置</RouterLink></div>
    <p v-if="capabilities && missing.length" class="error">请先配置：{{ missing.join('、') }}。</p>
    <p v-if="capabilities && !capabilities.runnerConfigured" class="error">隔离执行器尚未配置。请按部署文档启用执行器后生成。</p>
    <div class="grid two"><div class="field"><label>生成组数</label><input v-model.number="count" type="number" min="1" max="20" :disabled="running" /><small class="muted">额外加入题面样例与最多 3 组反例；自动去除重复输入。</small></div><div class="field"><label>随机种子</label><input v-model.number="seed" type="number" min="0" max="2147483647" :disabled="running" /></div></div>
    <div class="field"><label>生成要求（可选）</label><textarea v-model="extraPrompt" maxlength="4000" rows="2" :disabled="running" placeholder="例如：重点覆盖重复元素、溢出和退化结构。"></textarea></div>
    <label><input v-model="useKimi" type="checkbox" style="width:auto" :disabled="running || !capabilities?.providers.kimi" /> 加入 Kimi 复核代码与覆盖计划{{ capabilities?.providers.kimi ? '' : '（需先配置）' }}</label>
    <div class="toolbar"><button class="button" :disabled="disabled || running || transferring || !capabilities?.runnerConfigured || !!missing.length || !draft.title || !draft.description || !draft.input || !draft.output || !draft.constraints || draft.spj !== 0 || count < 1 || count > 20 || !Number.isInteger(count) || !Number.isInteger(seed) || seed < 0 || seed > 2147483647" @click="start">{{ running ? '正在生成与验证…' : '开始生成' }}</button><button v-if="job" class="button secondary" :disabled="busy" @click="refresh">刷新任务</button></div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-if="job" aria-live="polite">
      <ol class="notice-list"><li v-for="(event, i) in job.events" :key="i">{{ event.message }}</li></ol>
      <p v-if="job.state === 'FAILED'" class="error" style="white-space:pre-wrap">{{ job.error }}</p>
      <template v-if="job.state === 'SUCCEEDED' && job.result">
        <p><strong>{{ job.result.cases.length }} 组测试通过输入校验、样例核对与双解对拍。</strong>典型错误解被 {{ job.result.mutantRejected }} 组数据检出。</p>
        <ul class="notice-list"><li v-for="(note, i) in job.result.notes" :key="i">{{ note }}</li></ul>
        <p v-if="stale" class="error">题面或参考程序已修改；请重新生成后再应用。</p>
        <div class="toolbar"><button class="button secondary" :disabled="disabled || transferring" @click="download(false)">下载测试数据 ZIP</button><button v-if="allowApply" class="button" :disabled="disabled || transferring || stale" @click="download(true)">{{ applyLabel || '采用此测试数据' }}</button></div>
        <details><summary>查看测试数据预览</summary><details v-for="item in job.result.cases" :key="item.name"><summary>{{ item.name }} · {{ item.label }} · 输入 {{ item.inputBytes }} B / 输出 {{ item.outputBytes }} B</summary><div class="grid two"><div><strong>输入预览</strong><pre>{{ item.inputPreview }}</pre></div><div><strong>输出预览</strong><pre>{{ item.outputPreview }}</pre></div></div></details></details>
        <details><summary>审阅生成的程序</summary><h3>DeepSeek 标准解</h3><pre>{{ job.result.referenceSolutionCpp }}</pre><h3>GLM 独立解</h3><pre>{{ job.result.independentSolutionCpp }}</pre><h3>输入校验器</h3><pre>{{ job.result.validatorCpp }}</pre><h3>数据生成器</h3><pre>{{ job.result.generatorCpp }}</pre><h3>典型错误解</h3><pre>{{ job.result.wrongSolutionCpp || '未提供' }}</pre></details>
      </template>
    </div>
    <small class="muted">结果保留 30 分钟；后端重启后任务会清空。ZIP 含 .in/.out、程序与生成记录，正式导入前请人工审阅。</small>
  </section>
</template>

<style scoped>
.test-data-generator pre { white-space:pre-wrap; overflow-wrap:anywhere; max-height:28rem; overflow:auto; background:var(--bg-soft, #f4f6f8); color:var(--text, inherit); padding:12px; border-radius:8px }
.test-data-generator details { margin-top:12px }
.test-data-generator summary { cursor:pointer; padding:8px 0 }
</style>
