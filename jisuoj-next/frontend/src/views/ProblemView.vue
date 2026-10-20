<script setup lang="ts">
import DOMPurify from 'dompurify'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Pager from '../components/Pager.vue'
import ResultCode from '../components/ResultCode.vue'
import { api, ensureCsrf, errorMessage, type PageResponse } from '../services/api'
import { saveAiTeachingHandoff } from '../services/aiTeachingHandoff'
import { useAuthStore } from '../stores/auth'
import type { AcceptedUser, ProblemDetail } from '../types/domain'

const route = useRoute(); const router = useRouter(); const auth = useAuthStore()
const problem = ref<ProblemDetail | null>(null)
const language = ref(1); const source = ref('#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n\n    return 0;\n}\n')
const error = ref(''); const submitting = ref(false)
const acceptedOpen = ref(false); const acceptedLoading = ref(false); const acceptedLoaded = ref(false)
const acceptedError = ref(''); const acceptedUsers = ref<AcceptedUser[]>([])
const acceptedPage = ref(1); const acceptedSize = 20; const acceptedTotal = ref(0)
const failedAvatars = ref(new Set<string>())
const messageTarget = ref<AcceptedUser | null>(null)
const messageBody = ref(''); const messageError = ref(''); const messageSending = ref(false); const messageSent = ref(false)
const languages = ref<{ id: number; name: string }[]>([{ id: 1, name: 'C++' }, { id: 6, name: 'Python' }, { id: 3, name: 'Java' }])
const clean = (html?: string) => DOMPurify.sanitize(html || '')
const title = computed(() => problem.value ? `${problem.value.id}. ${problem.value.title}` : '正在加载…')
const messageProblemRoute = computed(() => ({
  path: `/problems/${problem.value?.id ?? route.params.id}`,
  query: route.query.contestId ? { contestId: String(route.query.contestId) } : {},
}))
const messageProblemUrl = computed(() => problem.value
  ? new URL(router.resolve(messageProblemRoute.value).href, window.location.origin).toString()
  : '')
const messagePrefix = computed(() => problem.value
  ? `题目：#${problem.value.id} ${problem.value.title}\n题目链接：${messageProblemUrl.value}\n\n`
  : '')
const messageBodyMaxLength = computed(() => Math.max(0, 1000 - messagePrefix.value.length))

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}
function userInitial(user: AcceptedUser) {
  return (user.nick || user.userId || '?').slice(0, 1).toUpperCase()
}
function avatarUrl(userId: string) {
  return `/api/users/${encodeURIComponent(userId)}/avatar`
}
function markAvatarFailed(userId: string) {
  failedAvatars.value.add(userId)
}
async function loadAcceptedUsers(page = 1) {
  if (!problem.value) return
  acceptedLoading.value = true; acceptedError.value = ''
  try {
    const { data } = await api.get<PageResponse<AcceptedUser>>(`/problems/${problem.value.id}/accepted-users`, {
      params: { page, size: acceptedSize },
    })
    acceptedUsers.value = data.items
    acceptedPage.value = data.page
    acceptedTotal.value = data.total
    acceptedLoaded.value = true
  } catch (e) { acceptedError.value = errorMessage(e) }
  finally { acceptedLoading.value = false }
}
async function toggleAcceptedUsers() {
  acceptedOpen.value = !acceptedOpen.value
  if (acceptedOpen.value && !acceptedLoaded.value) await loadAcceptedUsers()
}
function openAcceptedMessage(user: AcceptedUser) {
  messageTarget.value = user
  messageBody.value = ''
  messageError.value = ''
  messageSent.value = false
}
function closeAcceptedMessage() {
  if (messageSending.value) return
  messageTarget.value = null
}
async function sendAcceptedMessage() {
  const recipient = messageTarget.value
  const body = messageBody.value.trim()
  if (!recipient || !body) return
  if (!auth.authenticated) return
  messageSending.value = true
  messageError.value = ''
  try {
    await ensureCsrf()
    await api.post(`/users/${encodeURIComponent(recipient.userId)}/messages`, {
      content: `${messagePrefix.value}${body}`,
    })
    messageSent.value = true
  } catch (e) { messageError.value = errorMessage(e) }
  finally { messageSending.value = false }
}

async function submit() {
  if (!auth.authenticated) { await router.push('/login'); return }
  submitting.value = true; error.value = ''
  try {
    await ensureCsrf()
    const contestId = route.query.contestId ? Number(route.query.contestId) : null
    const { data } = await api.post<{ id: number }>('/submissions', { problemId: problem.value!.id, language: language.value, source: source.value, contestId })
    await router.push({ path: '/status', query: { solutionId: data.id } })
  } catch (e) { error.value = errorMessage(e) } finally { submitting.value = false }
}

async function askAi() {
  if (!problem.value) return
  try {
    const token = saveAiTeachingHandoff({
      problemId: problem.value.id,
      problemTitle: problem.value.title,
      sourceCode: source.value,
      sourceLanguage: languages.value.find(item => item.id === language.value)?.name || String(language.value),
      ownerUserId: auth.user?.userId ?? null,
    })
    await router.push({ path: '/ask-ai', query: { handoff: token } })
  } catch {
    error.value = '无法临时保存当前代码，请检查浏览器存储空间后重试'
  }
}

onMounted(async () => {
  try {
    problem.value = (await api.get<ProblemDetail>(`/problems/${route.params.id}`)).data
    const catalog = (await api.get('/judge/catalog')).data
    if (catalog.languages?.length) languages.value = catalog.languages
  } catch (e) { error.value = errorMessage(e) }
})
</script>

<template>
  <p v-if="error && !problem" class="error">{{ error }}</p>
  <div v-if="problem" class="problem-page">
    <div class="page-head"><h1>{{ title }}</h1><div class="problem-head-actions"><ResultCode v-if="problem.userStatus" :result="problem.userStatus" /><button class="button secondary accepted-toggle" :class="{ active: acceptedOpen }" type="button" :aria-expanded="acceptedOpen" aria-controls="problem-accepted-users" @click="toggleAcceptedUsers"><span aria-hidden="true">✓</span>{{ acceptedOpen ? '收起通过用户' : '查看通过用户' }}<b v-if="acceptedLoaded">{{ acceptedTotal }}</b></button><button class="button secondary problem-ask-ai" type="button" @click="askAi"><span aria-hidden="true">✦</span> 问 AI</button></div></div>
    <div class="problem-meta"><span v-if="route.query.contestId" class="tag">竞赛 #{{ route.query.contestId }}</span><span class="tag">时间 {{ problem.timeLimit }} s</span><span class="tag">内存 {{ problem.memoryLimit }} MiB</span><span class="tag">{{ problem.specialJudge ? '特殊评测' : '标准评测' }}</span><span class="tag">来源 {{ problem.source || '未知' }}</span></div>
    <div class="problem-workspace" :class="{ 'accepted-open': acceptedOpen }">
      <div class="problem-core">
        <article class="card prose problem-statement">
          <h2>题目描述</h2><div v-html="clean(problem.description)"></div>
          <h2>输入</h2><div v-html="clean(problem.input)"></div>
          <h2>输出</h2><div v-html="clean(problem.output)"></div>
          <h2>样例输入</h2><pre class="sample">{{ problem.sampleInput }}</pre>
          <h2>样例输出</h2><pre class="sample">{{ problem.sampleOutput }}</pre>
          <template v-if="problem.hint"><h2>提示</h2><div v-html="clean(problem.hint)"></div></template>
        </article>
        <section class="card form-stack problem-submit">
          <h2>提交代码</h2>
          <div class="field"><label>编程语言</label><select v-model.number="language"><option v-for="lang in languages" :key="lang.id" :value="lang.id">{{ lang.name }}</option></select></div>
          <div class="field problem-code-field"><label>源代码</label><textarea v-model="source" class="code" spellcheck="false"></textarea></div>
          <div class="problem-submit-actions">
            <p v-if="error" class="error">{{ error }}</p>
            <button class="button" :disabled="submitting" @click="submit">{{ submitting ? '提交中…' : '提交评测' }}</button>
          </div>
        </section>
      </div>
      <aside id="problem-accepted-users" class="problem-accepted-rail" :aria-hidden="!acceptedOpen">
        <section class="card problem-accepted-panel">
          <header><div><span class="problem-accepted-kicker">AC USERS</span><h2>通过用户</h2><p>{{ acceptedLoaded ? `共 ${acceptedTotal} 位用户通过` : '正在读取通过记录' }}</p></div><button class="problem-accepted-close" type="button" aria-label="关闭通过用户列表" @click="acceptedOpen = false">×</button></header>
          <p v-if="acceptedError" class="error">{{ acceptedError }}</p>
          <div v-else-if="acceptedLoading" class="problem-accepted-state">正在加载…</div>
          <div v-else-if="!acceptedUsers.length" class="problem-accepted-state"><strong>还没有用户通过</strong><span>成为第一个完成这道题的人吧。</span></div>
          <div v-else class="problem-accepted-list">
            <div v-for="(user, index) in acceptedUsers" :key="user.userId" class="problem-accepted-user">
              <span class="problem-accepted-rank">#{{ (acceptedPage - 1) * acceptedSize + index + 1 }}</span><span class="problem-accepted-avatar"><img v-if="!failedAvatars.has(user.userId)" :src="avatarUrl(user.userId)" :alt="`${user.nick || user.userId}的头像`" @error="markAvatarFailed(user.userId)"><span v-else>{{ userInitial(user) }}</span></span><RouterLink class="problem-accepted-identity" :to="`/users/${encodeURIComponent(user.userId)}`"><strong>{{ user.nick || user.userId }}</strong><small>@{{ user.userId }}</small><em v-if="user.school">{{ user.school }}</em></RouterLink><time :datetime="user.firstAcceptedAt">{{ formatDate(user.firstAcceptedAt) }}</time><button v-if="auth.user?.userId !== user.userId" class="problem-accepted-message" type="button" :aria-label="`给${user.nick || user.userId}留言`" @click="openAcceptedMessage(user)">留言</button>
            </div>
          </div>
          <Pager v-if="acceptedTotal > acceptedSize" :page="acceptedPage" :size="acceptedSize" :total="acceptedTotal" @change="loadAcceptedUsers" />
        </section>
      </aside>
    </div>
    <div v-if="messageTarget" class="accepted-message-overlay" @click.self="closeAcceptedMessage" @keydown.esc="closeAcceptedMessage">
      <section class="accepted-message-dialog" role="dialog" aria-modal="true" aria-labelledby="accepted-message-title">
        <header><div><span class="problem-accepted-kicker">LEAVE A MESSAGE</span><h2 id="accepted-message-title">给 {{ messageTarget.nick || messageTarget.userId }} 留言</h2><p>留言将显示在对方的个人主页。</p></div><button class="problem-accepted-close" type="button" aria-label="关闭留言窗口" :disabled="messageSending" @click="closeAcceptedMessage">×</button></header>
        <div v-if="messageSent" class="accepted-message-success" role="status"><span aria-hidden="true">✓</span><strong>留言已发布</strong><p>已发到 @{{ messageTarget.userId }} 的个人主页。</p><button class="button" type="button" @click="closeAcceptedMessage">完成</button></div>
        <div v-else-if="!auth.authenticated" class="accepted-message-login"><p>登录后即可给这位通过用户留言。</p><RouterLink class="button" :to="{ path: '/login', query: { redirect: route.fullPath } }">前往登录</RouterLink></div>
        <form v-else class="accepted-message-form" @submit.prevent="sendAcceptedMessage">
          <div class="accepted-message-context"><span>自动附带题目</span><RouterLink :to="messageProblemRoute">#{{ problem.id }} · {{ problem.title }}</RouterLink><small>{{ messageProblemUrl }}</small></div>
          <label for="accepted-message-body">留言内容</label><textarea id="accepted-message-body" v-model="messageBody" :maxlength="messageBodyMaxLength" rows="5" placeholder="写下你想说的话…"></textarea>
          <p v-if="messageError" class="error" role="alert">{{ messageError }}</p>
          <div class="accepted-message-actions"><small>{{ messagePrefix.length + messageBody.length }} / 1000</small><button class="button" type="submit" :disabled="messageSending || !messageBody.trim()">{{ messageSending ? '发布中…' : '发布留言' }}</button></div>
        </form>
      </section>
    </div>
  </div>
</template>
