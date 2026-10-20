<script setup lang="ts">
import DOMPurify from 'dompurify'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import Pager from '../components/Pager.vue'
import ProblemArchive from '../components/ProblemArchive.vue'
import ResultCode from '../components/ResultCode.vue'
import { api, errorMessage, type PageResponse } from '../services/api'
import type { ProblemDetail, ProblemSummary } from '../types/domain'
import '../styles/problem-archive.css'
import '../styles/problem-archive-dark.css'
import '../styles/problem-archive-classic.css'

const rows = ref<ProblemSummary[]>([])
const previewFixture = import.meta.env.DEV && import.meta.env.VITE_DEV_API_TARGET === 'http://127.0.0.1:8082'
const sources = ref<string[]>([])
const query = reactive({ page: 1, size: 20, keyword: '', source: '' })
const appliedFilters = reactive({ keyword: '', source: '' })
const total = ref(0)
const loading = ref(true)
const error = ref('')
const view = ref<'archive' | 'list'>('archive')
const selectedIndex = ref(0)
const detail = ref<ProblemDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const detailCache = new Map<number, ProblemDetail>()
let listController: AbortController | undefined
let detailController: AbortController | undefined
let detailTimer: ReturnType<typeof setTimeout> | undefined
const sourcesController = new AbortController()

const selected = computed(() => rows.value[selectedIndex.value] ?? null)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / query.size)))
const archiveNumber = computed(() => (query.page - 1) * query.size + selectedIndex.value + 1)
const description = computed(() => {
  if (!detail.value?.description) return ''
  const fragment = DOMPurify.sanitize(detail.value.description, { RETURN_DOM_FRAGMENT: true })
  fragment.querySelectorAll('p,div,br,li,h1,h2,h3,pre').forEach((node) => node.append(' '))
  return (fragment.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 360)
})
const acceptance = computed(() => Math.max(0, Math.min(100, selected.value?.acceptanceRate || 0)))
const statusText = computed(() => selected.value?.userStatus === 'AC' ? '已通过' : selected.value?.userStatus === 'TRIED' ? '已尝试' : '未尝试')

async function load(page = query.page, selectLast = false) {
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get<PageResponse<ProblemSummary>>('/problems', {
      params: { page, size: query.size, ...appliedFilters }, signal: controller.signal,
    })
    if (controller.signal.aborted) return
    rows.value = data.items
    total.value = data.total
    query.page = data.page
    selectedIndex.value = selectLast ? Math.max(0, data.items.length - 1) : 0
  } catch (e) {
    if (!controller.signal.aborted) error.value = errorMessage(e)
  } finally {
    if (listController === controller) loading.value = false
  }
}
function search() {
  appliedFilters.keyword = query.keyword.trim()
  appliedFilters.source = query.source
  void load(1)
}
function resetSearch() { query.keyword = ''; query.source = ''; search() }
function turnPage(direction: number) {
  const page = query.page + direction
  if (!loading.value && page >= 1 && page <= pageCount.value) void load(page, direction < 0)
}
async function loadDetail(id: number) {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  detailLoading.value = true
  detailError.value = ''
  try {
    const { data } = await api.get<ProblemDetail>(`/problems/${id}`, { signal: controller.signal })
    if (controller.signal.aborted || selected.value?.id !== id) return
    detailCache.set(id, data)
    if (detailCache.size > 60) detailCache.delete(detailCache.keys().next().value!)
    detail.value = data
  } catch (e) {
    if (!controller.signal.aborted && selected.value?.id === id) detailError.value = errorMessage(e)
  } finally {
    if (detailController === controller) detailLoading.value = false
  }
}
watch(() => selected.value?.id, (id) => {
  clearTimeout(detailTimer)
  detailController?.abort()
  detailController = undefined
  detail.value = id ? detailCache.get(id) ?? null : null
  detailError.value = ''
  detailLoading.value = !!id && !detail.value
  if (id && !detail.value) detailTimer = setTimeout(() => void loadDetail(id), 180)
})
onMounted(() => {
  void load()
  void api.get<string[]>('/problems/sources', { signal: sourcesController.signal })
    .then(({ data }) => { sources.value = data })
    .catch(() => { /* Keep the default source option when the catalog is unavailable. */ })
})
onBeforeUnmount(() => {
  clearTimeout(detailTimer)
  listController?.abort(); detailController?.abort(); sourcesController.abort()
})
</script>

<template>
  <section class="archive-page" aria-label="问题资料库">
    <span v-if="previewFixture" class="archive-preview-badge">本地演示 · 虚拟题目</span>
    <header class="archive-page-header">
      <div class="archive-header-tools">
        <span class="archive-total"><strong>{{ total.toLocaleString() }}</strong> 份题目档案</span>
        <div class="archive-view-switch" role="group" aria-label="题目浏览方式">
          <button type="button" :class="{ active: view === 'archive' }" :aria-pressed="view === 'archive'" @click="view = 'archive'"><svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m10 2 7 4v8l-7 4-7-4V6l7-4Z M3 6l7 4 7-4 M10 10v8" /></svg>空间检索</button>
          <button type="button" :class="{ active: view === 'list' }" :aria-pressed="view === 'list'" @click="view = 'list'"><svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M7 5h10M7 10h10M7 15h10M3 5h1M3 10h1M3 15h1" /></svg>列表</button>
        </div>
      </div>
    </header>
    <form class="archive-search" role="search" @submit.prevent="search">
      <label class="archive-search-input"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4.5 4.5" /></svg><input v-model="query.keyword" placeholder="输入题号或标题，定位你的下一份挑战" aria-label="题号或标题" /><kbd aria-hidden="true">↵</kbd></label>
      <label class="archive-source"><span>来源</span><select v-model="query.source" aria-label="题目来源"><option value="">全部来源</option><option v-for="s in sources" :key="s">{{ s }}</option></select></label>
      <button class="archive-search-button" type="submit" :disabled="loading">检索档案 <span aria-hidden="true">↗</span></button>
    </form>
    <div v-if="error" class="archive-load-error" role="alert"><span>{{ error }}</span><button type="button" @click="load()">重新加载</button></div>
    <div v-if="view === 'archive'" class="archive-workspace" :aria-busy="loading">
      <ProblemArchive :rows="rows" :selected-index="selectedIndex" :loading="loading" :page="query.page" :pages="pageCount" :total="total" :offset="(query.page - 1) * query.size" @select="selectedIndex = $event" @page="turnPage">
        <template #empty><div class="archive-empty"><span aria-hidden="true">∅</span><h2>{{ error ? '资料库暂时不可用' : '没有找到匹配的档案' }}</h2><p>{{ error ? '请重新加载，稍后继续探索。' : '尝试其他题号、标题或来源。' }}</p><button type="button" @click="error ? load() : resetSearch()">{{ error ? '重试' : '查看全部题目' }}</button></div></template>
      </ProblemArchive>
      <aside class="archive-inspector" aria-label="选中题目信息" :aria-busy="detailLoading">
        <header class="archive-inspector-top"><span><i aria-hidden="true"></i> 档案详情</span><span class="archive-mono">{{ selected ? String(archiveNumber).padStart(3, '0') : '—' }} / {{ String(total).padStart(3, '0') }}</span></header>
        <div v-if="selected" :key="selected.id" class="archive-inspector-body">
          <div class="archive-dossier-meta"><span class="archive-mono">PROBLEM / {{ selected.id }}</span><span class="archive-user-status" :class="{ solved: selected.userStatus === 'AC' }">{{ statusText }}</span></div>
          <div class="archive-dossier-id" aria-hidden="true"><small>No.</small>{{ selected.id }}</div>
          <h2>{{ selected.title }}</h2>
          <div class="archive-origin"><span>来源</span><strong>{{ selected.source || '本站题库' }}</strong></div>
          <div class="archive-description">
            <h3><span>01</span> 题目概览</h3>
            <div v-if="detailLoading" class="archive-skeleton" role="status" aria-label="正在加载题目简介"><i></i><i></i><i></i></div>
            <div v-else-if="detailError" class="archive-detail-error"><p>{{ detailError }}</p><button type="button" @click="loadDetail(selected.id)">重试简介</button></div>
            <p v-else>{{ description || '这份题目暂未提供文字简介，打开题目查看完整内容。' }}</p>
          </div>
          <dl class="archive-limits"><div><dt>时间限制</dt><dd>{{ detail ? `${detail.timeLimit} s` : '—' }}</dd></div><div><dt>内存限制</dt><dd>{{ detail ? `${detail.memoryLimit} MiB` : '—' }}</dd></div><div><dt>评测方式</dt><dd>{{ detail ? (detail.specialJudge ? '特殊评测' : '标准评测') : '—' }}</dd></div></dl>
          <div class="archive-statistics">
            <h3><span>02</span> 提交数据</h3>
            <div class="archive-stat-numbers"><div><strong>{{ selected.accepted.toLocaleString() }}</strong><span>通过提交</span></div><div><strong>{{ selected.submit.toLocaleString() }}</strong><span>总提交</span></div><div><strong>{{ acceptance.toFixed(1) }}<small>%</small></strong><span>通过率</span></div></div>
            <div class="archive-rate-track" role="meter" aria-label="提交通过率" :aria-valuenow="acceptance" aria-valuemin="0" aria-valuemax="100"><i :style="{ width: `${acceptance}%` }"></i></div>
          </div>
          <RouterLink class="archive-open-problem" :to="`/problems/${selected.id}`"><span>打开题目<span class="archive-mono">OPEN FILE</span></span><span aria-hidden="true">↗</span></RouterLink>
          <p class="archive-inspector-footnote"><span aria-hidden="true">⌘</span> 每一次探索，都从一个问题开始。</p>
        </div>
        <div v-else class="archive-inspector-placeholder"><svg viewBox="0 0 64 64" fill="none" aria-hidden="true"><path d="M18 9h22l10 10v36H18zM40 9v12h10M26 31h16M26 39h16M26 47h9" /></svg><p>{{ loading ? '正在连接题目资料库…' : '选择一份档案，查看详细信息' }}</p></div>
      </aside>
    </div>
    <template v-else>
      <div class="table-wrap archive-table" :aria-busy="loading">
        <table><thead><tr><th>状态</th><th>题目编号</th><th>标题</th><th>来源</th><th class="number">通过</th><th class="number">提交</th><th class="number">通过率</th></tr></thead>
          <tbody><tr v-if="loading"><td colspan="7" class="loading">正在加载…</td></tr><template v-else><tr v-for="p in rows" :key="p.id"><td><ResultCode v-if="p.userStatus" :result="p.userStatus" /><span v-else class="muted">—</span></td><td><RouterLink :to="`/problems/${p.id}`">{{ p.id }}</RouterLink></td><td><RouterLink :to="`/problems/${p.id}`"><strong>{{ p.title }}</strong></RouterLink></td><td>{{ p.source || '—' }}</td><td class="number">{{ p.accepted }}</td><td class="number">{{ p.submit }}</td><td class="number">{{ p.acceptanceRate.toFixed(2) }}%</td></tr><tr v-if="!rows.length"><td colspan="7" class="empty">没有找到匹配的题目。</td></tr></template></tbody>
        </table>
      </div>
      <Pager :page="query.page" :size="query.size" :total="total" @change="load($event)" />
    </template>
  </section>
</template>
