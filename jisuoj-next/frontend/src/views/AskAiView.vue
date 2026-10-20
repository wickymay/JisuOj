<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ensureCsrf, errorMessage, type PageResponse } from '../services/api'
import { takeAiTeachingHandoff } from '../services/aiTeachingHandoff'
import { useAuthStore } from '../stores/auth'
import type { ProblemSummary } from '../types/domain'
import '../styles/ai-teaching.css'

type Role = 'user' | 'assistant'
type KnowledgeSource = { number: number; chunkId: string; bookTitle: string; chapter: string; section: string; pageStart: number; pageEnd: number }
type TeachingSkillUse = { status: string; version: string; mode: string; topics: { id: string; title: string }[] }
type TeachingSkillStatus = { status: string; version: string; topics: number; sourceBooks: number }
type ChatMessage = { role: Role; content: string; sources?: KnowledgeSource[]; knowledgeStatus?: string; teachingSkill?: TeachingSkillUse }
type CodeAttachment = { language: string; source: string }
type Conversation = {
  id: string
  problemId: number | null
  problemTitle: string | null
  codeAttachment: CodeAttachment | null
  includeRecentSubmissions: boolean
  messages: ChatMessage[]
  updatedAt: number
}

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const conversations = ref<Conversation[]>([])
const activeId = ref<string | null>(null)
const active = computed(() => conversations.value.find(item => item.id === activeId.value) ?? null)
const draftProblem = ref<{ id: number; title: string } | null>(null)
const draftCode = ref<CodeAttachment | null>(null)
const draftRecent = ref(false)
const attachment = computed(() => active.value
  ? (active.value.problemId === null ? null : { id: active.value.problemId, title: active.value.problemTitle || '' })
  : draftProblem.value)
const codeAttachment = computed(() => active.value ? active.value.codeAttachment : draftCode.value)
const codeTooLong = computed(() => (codeAttachment.value?.source.length ?? 0) > 16_000)
const includeRecent = computed({
  get: () => active.value ? active.value.includeRecentSubmissions : draftRecent.value,
  set: (enabled: boolean) => {
    if (!attachment.value) return
    if (active.value) {
      active.value.includeRecentSubmissions = enabled
      persist()
    } else draftRecent.value = enabled
  },
})
const pickerOpen = ref(false)
const searchKeyword = ref('')
const searchResults = ref<ProblemSummary[]>([])
const searching = ref(false)
const searchError = ref('')
const configured = ref<boolean | null>(null)
const knowledgeBase = ref<{ status: string; chunks: number } | null>(null)
const teachingSkill = ref<TeachingSkillStatus | null>(null)
const statusError = ref('')
const draft = ref('')
const pendingMessage = ref('')
const asking = ref(false)
const askError = ref('')
const storageWarning = ref('')
const handoffError = ref('')
const messageList = ref<HTMLElement | null>(null)

function storageKey(userId: string) { return `jisuoj-ai-teaching:${userId}` }

function restore(userId: string): Conversation[] {
  try {
    const raw: unknown = JSON.parse(localStorage.getItem(storageKey(userId)) || '[]')
    if (!Array.isArray(raw)) return []
    return raw.flatMap((entry: unknown): Conversation[] => {
      if (!entry || typeof entry !== 'object') return []
      const item = entry as Record<string, unknown>
      if (typeof item.id !== 'string') return []
      if (item.problemId !== null && (!Number.isInteger(item.problemId) || Number(item.problemId) < 1 || typeof item.problemTitle !== 'string')) return []
      if (item.problemId === null && item.problemTitle !== null) return []
      const messages: ChatMessage[] = Array.isArray(item.messages)
        ? item.messages.filter((turn: unknown): turn is ChatMessage => {
          if (!turn || typeof turn !== 'object') return false
          const value = turn as Record<string, unknown>
          return (value.role === 'user' || value.role === 'assistant') && typeof value.content === 'string'
        })
        : []
      const code = item.codeAttachment
      const codeAttachment: CodeAttachment | null = code && typeof code === 'object' &&
        typeof (code as Record<string, unknown>).language === 'string' &&
        typeof (code as Record<string, unknown>).source === 'string'
        ? { language: (code as CodeAttachment).language, source: (code as CodeAttachment).source }
        : null
      return [{
        id: item.id, problemId: item.problemId === null ? null : Number(item.problemId),
        problemTitle: item.problemId === null ? null : String(item.problemTitle),
        codeAttachment,
        includeRecentSubmissions: item.problemId !== null && item.includeRecentSubmissions === true,
        messages, updatedAt: typeof item.updatedAt === 'number' ? item.updatedAt : Date.now(),
      }]
    })
  } catch { return [] }
}

function persist() {
  if (!auth.user) return
  try {
    localStorage.setItem(storageKey(auth.user.userId), JSON.stringify(conversations.value))
    storageWarning.value = ''
  } catch { storageWarning.value = '浏览器未能保存对话；离开页面前请复制需要保留的内容。' }
}

async function loadStatus() {
  statusError.value = ''
  try {
    const { data } = await api.get<{ configured: boolean; knowledgeBase?: { status: string; chunks: number }; teachingSkill?: TeachingSkillStatus }>('/ai/teach/status')
    configured.value = data.configured
    knowledgeBase.value = data.knowledgeBase ?? null
    teachingSkill.value = data.teachingSkill ?? null
  }
  catch (error) { statusError.value = errorMessage(error); configured.value = null }
}

async function searchProblems() {
  searching.value = true
  searchError.value = ''
  try {
    const { data } = await api.get<PageResponse<ProblemSummary>>('/problems', {
      params: { page: 1, size: 12, keyword: searchKeyword.value.trim() },
    })
    searchResults.value = data.items
  } catch (error) { searchError.value = errorMessage(error) }
  finally { searching.value = false }
}

watch(() => auth.user?.userId, userId => {
  conversations.value = userId ? restore(userId) : []
  activeId.value = conversations.value[0]?.id ?? null
  draftProblem.value = null
  draftCode.value = null
  draftRecent.value = false
  pickerOpen.value = false
  configured.value = null
  knowledgeBase.value = null
  teachingSkill.value = null
  if (userId) {
    void loadStatus()
  }
}, { immediate: true })

watch([() => auth.user?.userId, () => route.query.handoff], ([userId, token]) => {
  if (!userId || typeof token !== 'string') return
  const context = takeAiTeachingHandoff(token, userId)
  if (context) {
    activeId.value = null
    draftProblem.value = { id: context.problemId, title: context.problemTitle }
    draftCode.value = { language: context.sourceLanguage, source: context.sourceCode }
    draftRecent.value = false
    pickerOpen.value = false
    draft.value = ''
    askError.value = ''
    handoffError.value = ''
  } else handoffError.value = '题目与代码的临时附件已失效，请返回题目页重新点击“问 AI”。'
  void router.replace({ path: '/ask-ai' })
}, { immediate: true })

watch([() => active.value?.messages.length, pendingMessage], async () => {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
})

function startNew() {
  if (asking.value) return
  activeId.value = null
  draftProblem.value = null
  draftCode.value = null
  draftRecent.value = false
  pickerOpen.value = false
  draft.value = ''
  askError.value = ''
}

function togglePicker() {
  if (asking.value) return
  pickerOpen.value = !pickerOpen.value
  if (pickerOpen.value && !searchResults.value.length) void searchProblems()
}

function selectProblem(problem: ProblemSummary) {
  if (asking.value) return
  if (active.value) {
    active.value.problemId = problem.id
    active.value.problemTitle = problem.title
    active.value.includeRecentSubmissions = false
    active.value.updatedAt = Date.now()
    persist()
  } else {
    draftProblem.value = { id: problem.id, title: problem.title }
    draftRecent.value = false
  }
  pickerOpen.value = false
  askError.value = ''
}

function removeProblem() {
  if (asking.value) return
  if (active.value) {
    active.value.problemId = null
    active.value.problemTitle = null
    active.value.includeRecentSubmissions = false
    persist()
  } else {
    draftProblem.value = null
    draftRecent.value = false
  }
  pickerOpen.value = false
}

function updateCode(event: Event) {
  const source = (event.target as HTMLTextAreaElement).value
  if (active.value?.codeAttachment) active.value.codeAttachment.source = source
  else if (draftCode.value) draftCode.value.source = source
}

function removeCode() {
  if (asking.value) return
  if (active.value) {
    active.value.codeAttachment = null
    persist()
  } else draftCode.value = null
}

function openConversation(conversation: Conversation) {
  if (asking.value) return
  activeId.value = conversation.id
  draftProblem.value = null
  draftCode.value = null
  draftRecent.value = false
  pickerOpen.value = false
  draft.value = ''
  askError.value = ''
}

function compactHistory(messages: ChatMessage[]): ChatMessage[] {
  const first = messages[0]
  const recent = messages.slice(1)
  const length = () => first.content.length + recent.reduce((sum, turn) => sum + turn.content.length, 0)
  while (recent.length > 14 || (recent.length > 0 && length() > 19_000)) recent.splice(0, 2)
  return [first, ...recent]
}

async function send() {
  const wasNew = !active.value
  const conversation: Conversation = active.value ?? {
    id: crypto.randomUUID(),
    problemId: draftProblem.value?.id ?? null,
    problemTitle: draftProblem.value?.title ?? null,
    codeAttachment: draftCode.value,
    includeRecentSubmissions: draftRecent.value,
    messages: [],
    updatedAt: Date.now(),
  }
  const question = draft.value.trim()
  if (!question || asking.value || configured.value !== true) return
  if (codeTooLong.value) {
    askError.value = '代码附件超过 16000 字，请精简代码或移除附件'
    return
  }
  const outgoing = compactHistory([...conversation.messages, { role: 'user', content: question }])
    .map(({ role, content }) => ({ role, content }))
  asking.value = true
  askError.value = ''
  pendingMessage.value = question
  draft.value = ''
  try {
    await ensureCsrf()
    const { data } = await api.post<{ content: string; sources?: KnowledgeSource[]; knowledgeStatus?: string; teachingSkill?: TeachingSkillUse }>('/ai/teach', {
      problemId: conversation.problemId, messages: outgoing,
      sourceCode: conversation.codeAttachment?.source ?? null,
      sourceLanguage: conversation.codeAttachment?.language ?? null,
      includeRecentSubmissions: conversation.includeRecentSubmissions,
    }, { timeout: 105_000 })
    conversation.messages.push({ role: 'user', content: question }, { role: 'assistant', content: data.content,
      sources: data.sources ?? [], knowledgeStatus: data.knowledgeStatus, teachingSkill: data.teachingSkill })
    conversation.updatedAt = Date.now()
    conversations.value = [conversation, ...conversations.value.filter(item => item.id !== conversation.id)]
    if (wasNew) {
      activeId.value = conversation.id
      draftProblem.value = null
      draftCode.value = null
      draftRecent.value = false
    }
    persist()
  } catch (error) {
    askError.value = errorMessage(error)
    draft.value = question
  } finally {
    pendingMessage.value = ''
    asking.value = false
  }
}

function formatTime(value: number) {
  return new Date(value).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}
</script>

<template>
  <div v-if="!auth.ready" class="card ai-access-state">正在检查登录状态…</div>
  <div v-else-if="!auth.authenticated" class="card ai-access-state">
    <span class="ai-eyebrow">AI LEARNING</span>
    <h1>问 AI</h1>
    <p>登录后可以直接提问编程问题，也可以附上一道题目，让 AI 结合题面讲解。</p>
    <RouterLink class="button" :to="{ path: '/login', query: { redirect: route.fullPath } }">登录后开始</RouterLink>
  </div>
  <div v-else class="ai-teaching-page">
    <aside class="ai-conversations" aria-label="AI 对话">
      <div class="ai-rail-head"><span class="ai-eyebrow">LEARNING DESK</span><h1>问 AI</h1><p>编程学习与题目讲解。</p><small v-if="knowledgeBase?.status === 'ready'" class="ai-knowledge-ready">知识库已连接 · {{ knowledgeBase.chunks }} 个片段</small><small v-if="teachingSkill?.status === 'ready'" class="ai-knowledge-ready">教学方法已启用 · {{ teachingSkill.topics }} 个专题</small></div>
      <button class="button ai-new-chat" type="button" :disabled="asking" @click="startNew">＋ 新对话</button>
      <div class="ai-chat-list">
        <span class="ai-list-label">最近对话</span>
        <p v-if="!conversations.length" class="ai-list-empty">还没有对话，直接提问即可开始。</p>
        <button v-for="conversation in conversations" :key="conversation.id" class="ai-chat-item" :class="{ active: activeId === conversation.id }" type="button" :disabled="asking" @click="openConversation(conversation)">
          <strong>{{ conversation.problemId ? '#' + conversation.problemId + ' · ' + conversation.problemTitle : (conversation.messages[0]?.content || '新对话') }}</strong>
          <span>{{ conversation.problemId ? (conversation.messages[0]?.content || '尚未提问') : '未附加题目' }}</span>
          <small>{{ formatTime(conversation.updatedAt) }}</small>
        </button>
      </div>
      <p class="ai-storage-note">对话和代码附件保存在此浏览器中。提问时发送近期对话、当前附件；最近提交记录仅在勾选后发送。</p>
      <p v-if="storageWarning" class="ai-storage-warning" role="alert">{{ storageWarning }}</p>
    </aside>

    <section class="ai-workspace" aria-label="AI 编程学习">
      <header class="ai-workspace-head"><div><span class="ai-eyebrow">{{ attachment ? 'PROBLEM ATTACHED' : 'AI LEARNING' }}</span><h2>{{ attachment ? '#' + attachment.id + ' · ' + attachment.title : (active ? '编程学习对话' : '新对话') }}</h2></div><RouterLink v-if="attachment" class="ai-problem-link" :to="'/problems/' + attachment.id">查看题目 ↗</RouterLink><span v-else class="ai-head-mark" aria-hidden="true">✦</span></header>
      <div ref="messageList" class="ai-message-list" aria-live="polite">
        <div v-if="!active?.messages.length && !pendingMessage" class="ai-welcome"><span class="ai-welcome-mark" aria-hidden="true">✦</span><h3>从你的疑问开始</h3><p>{{ codeAttachment ? '题面和当前代码已带入。写下遇到的问题，发送时 AI 会一起参考。' : '直接询问编程、算法或代码问题。遇到站内题目时，可在下方附加题目。' }}</p></div>
        <article v-for="(message, index) in (active?.messages || [])" :key="index" class="ai-message" :class="message.role">
          <span class="ai-message-role">{{ message.role === 'user' ? '你' : 'AI 助教' }}</span>
          <div class="ai-message-content">{{ message.content }}</div>
          <p v-if="message.role === 'assistant' && message.teachingSkill?.status === 'used'" class="ai-knowledge-note">{{ message.teachingSkill.mode === 'full_solution' ? '完整讲解' : '逐步引导' }} · {{ message.teachingSkill.topics.map(topic => topic.title).join('、') }}</p>
          <details v-if="message.role === 'assistant' && message.sources?.length" class="ai-knowledge-sources">
            <summary>检索参考 · {{ message.sources.length }} 条</summary>
            <ol><li v-for="source in message.sources" :key="source.chunkId"><strong>[资料{{ source.number }}] {{ source.bookTitle }}</strong><span>{{ source.section || source.chapter }} · PDF 第 {{ source.pageStart }}{{ source.pageEnd !== source.pageStart ? '—' + source.pageEnd : '' }} 页</span></li></ol>
            <small>资料经自动筛选，关键结论可对照原书复核。</small>
          </details>
          <p v-if="message.role === 'assistant' && message.knowledgeStatus === 'unavailable'" class="ai-knowledge-note">本次知识库暂不可用，回答未引用本地资料。</p>
        </article>
        <article v-if="pendingMessage" class="ai-message user"><span class="ai-message-role">你</span><div class="ai-message-content">{{ pendingMessage }}</div></article>
        <article v-if="asking" class="ai-message assistant"><span class="ai-message-role">AI 助教</span><div class="ai-message-content">{{ attachment ? '正在结合题面思考…' : '正在思考…' }}</div></article>
      </div>
      <div class="ai-composer-wrap">
        <p v-if="handoffError" class="error" role="alert">{{ handoffError }}</p>
        <div class="ai-attachment-row">
          <template v-if="attachment"><span class="ai-attachment-chip">题目附件 · #{{ attachment.id }} {{ attachment.title }}</span><button class="link-button" type="button" :disabled="asking" @click="togglePicker">更换</button><button class="link-button" type="button" :disabled="asking" @click="removeProblem">移除</button></template>
          <button v-else class="ai-attach-button" type="button" :disabled="asking" @click="togglePicker">＋ 附加题目（可选）</button>
        </div>
        <div v-if="codeAttachment" class="ai-code-attachment">
          <div class="ai-code-attachment-head"><strong>当前代码 · {{ codeAttachment.language }}</strong><span>{{ codeAttachment.source.length }} 字</span><button class="link-button" type="button" :disabled="asking" @click="removeCode">移除代码</button></div>
          <details class="ai-code-detail"><summary>查看或修改将发送的代码</summary><textarea :value="codeAttachment.source" :disabled="asking" spellcheck="false" aria-label="将发送给 AI 的代码" @input="updateCode" @change="persist"></textarea></details>
        </div>
        <label v-if="attachment" class="ai-recent-option"><input v-model="includeRecent" type="checkbox" :disabled="asking" /><span>附上我最近 5 次本题提交<small>仅评测结果、语言和耗时/内存；不含历史源码，默认关闭</small></span></label>
        <div v-if="pickerOpen" class="ai-attachment-picker">
          <p>按题号或标题查找。选中后，提问时会附上题面文字、样例与限制；图片暂不能识别。</p>
          <form class="ai-search" @submit.prevent="searchProblems"><input v-model="searchKeyword" type="search" placeholder="例如：1000 或题目标题" aria-label="搜索题号或标题" maxlength="100" /><button class="button" type="submit" :disabled="searching">{{ searching ? '检索中…' : '检索题目' }}</button></form>
          <p v-if="searchError" class="error" role="alert">{{ searchError }}</p>
          <div v-else-if="searching" class="ai-search-state">正在查找题目…</div>
          <div v-else-if="!searchResults.length" class="ai-search-state">没有找到可用题目，试试题号或其他关键词。</div>
          <div v-else class="ai-problem-results">
            <button v-for="problem in searchResults" :key="problem.id" class="ai-problem-result" type="button" :disabled="asking" @click="selectProblem(problem)"><span class="ai-problem-id">#{{ problem.id }}</span><strong>{{ problem.title }}</strong><span class="ai-problem-arrow" aria-hidden="true">↗</span></button>
          </div>
        </div>
        <p v-if="codeTooLong" class="error" role="alert">代码附件超过 16000 字，请在上方精简，或移除代码附件。</p>
        <p v-if="configured === false" class="error" role="alert">AI 教学尚未配置，请联系管理员。<button class="link-button" type="button" @click="loadStatus">重新检查</button></p>
        <p v-if="statusError" class="error" role="alert">{{ statusError }} <button class="link-button" type="button" @click="loadStatus">重新检查</button></p>
        <p v-if="askError" class="error" role="alert">{{ askError }}</p>
        <form class="ai-composer" @submit.prevent="send"><label for="ai-question" class="visually-hidden">向 AI 提问</label><textarea id="ai-question" v-model="draft" :disabled="asking || configured !== true" maxlength="2000" rows="3" :placeholder="attachment ? '结合这道题，写下你的问题…' : '直接输入编程或算法问题…'" @keydown.ctrl.enter.prevent="send" @keydown.meta.enter.prevent="send"></textarea><div class="ai-composer-bottom"><small>{{ draft.length }} / 2000 · 题目附件可选</small><button class="button" type="submit" :disabled="asking || configured !== true || codeTooLong || !draft.trim()">{{ asking ? '回答中…' : '发送问题 ↗' }}</button></div></form>
        <p class="ai-answer-note">发送时，题面、当前代码和已勾选的提交摘要会提供给配置的 AI 服务。AI 讲解仅供学习参考。</p>
      </div>
    </section>
  </div>
</template>
