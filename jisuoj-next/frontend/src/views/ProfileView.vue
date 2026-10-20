<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, ensureCsrf, errorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'

interface ResultStat { resultId: number; count: number }
interface DailyStat { day: string; submissions: number; accepted: number }
interface ContestStat { contestId: number; title: string; startTime: string; tried: number; accepted: number }
interface Profile {
  userId: string; nick: string; school: string; groupName: string; email: string | null
  solved: number; submit: number; registeredAt: string; lastActiveAt: string
  followerCount: number; followingCount: number; followedByMe: boolean
  solvedProblems: number[]; attemptedProblems: number[]; rank: number; starred: boolean
  coinEarned: number; coinBonus: number; coinSpent: number
  resultStats: ResultStat[]; dailyStats: DailyStat[]; contestStats: ContestStat[]
}
interface Message { id: number; profileUserId: string; authorId: string; content: string; createdAt: string }
interface MessagePart { text: string; to?: string }

const route = useRoute()
const auth = useAuthStore()
const profile = ref<Profile | null>(null)
const messages = ref<Message[]>([])
const error = ref('')
const messageDraft = ref('')
const editing = ref(false)
const saving = ref(false)
const sending = ref(false)
const editForm = reactive({ nick: '', school: '', groupName: '', email: '' })

const userId = computed(() => String(route.params.id || ''))
const isSelf = computed(() => auth.user?.userId === profile.value?.userId)
const levelSteps = [
  { min: 0, name: '萌新', next: 10 }, { min: 10, name: '小小牛', next: 20 },
  { min: 20, name: '小牛', next: 30 }, { min: 30, name: '小犇', next: 50 },
  { min: 50, name: '中牛', next: 80 }, { min: 80, name: '中犇', next: 100 },
  { min: 100, name: '大牛', next: 200 }, { min: 200, name: '大犇', next: 300 },
  { min: 300, name: '神牛', next: 500 }, { min: 500, name: '神犇', next: 800 },
  { min: 800, name: '神犇', next: null },
]
const levelInfo = computed(() => {
  const solved = profile.value?.solved || 0
  return [...levelSteps].reverse().find((step) => solved >= step.min) || levelSteps[0]
})
const levelProgress = computed(() => {
  const solved = profile.value?.solved || 0
  if (!levelInfo.value.next) return 100
  return Math.min(100, Math.round(((solved - levelInfo.value.min) / (levelInfo.value.next - levelInfo.value.min)) * 100))
})
const avatarInitial = computed(() => (profile.value?.nick || profile.value?.userId || '?').slice(0, 1).toUpperCase())
const avatarUrl = computed(() => {
  const email = profile.value?.email?.trim() || ''
  const qq = email.match(/^([0-9]{5,12})@qq\.com$/i)
  return qq ? `https://q1.qlogo.cn/g?b=qq&nk=${qq[1]}&s=5` : ''
})
const totalResults = computed(() => (profile.value?.resultStats || []).reduce((sum, item) => sum + item.count, 0))
const maxResultCount = computed(() => Math.max(1, ...(profile.value?.resultStats || []).map((item) => item.count)))
const recentDays = computed(() => (profile.value?.dailyStats || []).slice(-70))
const maxDailyCount = computed(() => Math.max(1, ...recentDays.value.map((item) => item.submissions)))
const coinBalance = computed(() => {
  const item = profile.value
  return item ? item.coinEarned + item.coinBonus - item.coinSpent : 0
})

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}
function messageParts(content: string): MessagePart[] {
  return content.split(/(https?:\/\/[^\s]+)/g).map((part) => {
    if (!/^https?:\/\//i.test(part)) return { text: part }
    try {
      const url = new URL(part)
      const problemId = url.pathname.match(/\/problems\/(\d+)\/?$/)?.[1]
      if (url.origin === window.location.origin && problemId) {
        return { text: `打开题目 #${problemId}`, to: `/problems/${problemId}${url.search}${url.hash}` }
      }
    } catch { /* Keep malformed and external links as plain text. */ }
    return { text: part }
  })
}
function resultName(id: number) {
  return ({ 4: 'AC', 5: 'PE', 6: 'WA', 7: 'TLE', 8: 'MLE', 9: 'OLE', 10: 'RE', 11: 'CE', 12: 'CO' } as Record<number, string>)[id] || `结果 ${id}`
}
function resultTone(id: number) {
  return id === 4 ? 'accepted' : id === 11 ? 'compile' : 'rejected'
}
function syncEditForm() {
  const item = profile.value
  if (!item) return
  editForm.nick = item.nick || ''
  editForm.school = item.school || ''
  editForm.groupName = item.groupName || ''
  editForm.email = item.email || ''
}
async function loadMessages() {
  try {
    messages.value = (await api.get<Message[]>(`/users/${userId.value}/messages`)).data
  } catch (e) {
    error.value = errorMessage(e)
  }
}
async function load() {
  error.value = ''
  profile.value = null
  try {
    profile.value = (await api.get<Profile>(`/users/${userId.value}`)).data
    syncEditForm()
    editing.value = route.query.edit === '1' && isSelf.value
    await loadMessages()
  } catch (e) {
    error.value = errorMessage(e)
  }
}
async function follow() {
  if (!profile.value) return
  try {
    await ensureCsrf()
    if (profile.value.followedByMe) await api.delete(`/users/${profile.value.userId}/follow`)
    else await api.post(`/users/${profile.value.userId}/follow`)
    await load()
  } catch (e) { error.value = errorMessage(e) }
}
async function saveProfile() {
  saving.value = true
  error.value = ''
  try {
    await ensureCsrf()
    await api.put('/users/me', editForm)
    editing.value = false
    await load()
    if (auth.user) auth.user.nick = editForm.nick
  } catch (e) { error.value = errorMessage(e) }
  finally { saving.value = false }
}
async function addMessage() {
  if (!messageDraft.value.trim()) return
  sending.value = true
  try {
    await ensureCsrf()
    await api.post(`/users/${userId.value}/messages`, { content: messageDraft.value.trim() })
    messageDraft.value = ''
    await loadMessages()
  } catch (e) { error.value = errorMessage(e) }
  finally { sending.value = false }
}
async function deleteMessage(id: number) {
  try {
    await ensureCsrf()
    await api.delete(`/users/messages/${id}`)
    await loadMessages()
  } catch (e) { error.value = errorMessage(e) }
}

watch(userId, load)
watch(() => route.query.edit, (value) => {
  if (value === '1' && isSelf.value) editing.value = true
})
watch(() => auth.user?.userId, () => {
  if (route.query.edit === '1' && isSelf.value) editing.value = true
})
onMounted(load)
</script>

<template>
  <p v-if="error" class="error">{{ error }}</p>
  <div v-else-if="!profile" class="card profile-loading">正在加载个人资料…</div>
  <div v-else class="profile-page">
    <div class="profile-layout">
      <aside class="profile-sidebar">
        <section class="card profile-card">
          <div class="profile-avatar-wrap">
            <img v-if="avatarUrl" class="profile-avatar" :src="avatarUrl" :alt="`${profile.nick || profile.userId}的头像`" />
            <div v-else class="profile-avatar profile-avatar-fallback">{{ avatarInitial }}</div>
          </div>
          <h1>{{ profile.nick || profile.userId }}</h1>
          <p class="profile-id">@{{ profile.userId }}</p>
          <div class="profile-meta"><span v-if="profile.school">⌂ {{ profile.school }}</span><span v-if="profile.groupName">▣ {{ profile.groupName }}</span></div>
          <div v-if="isSelf" class="profile-actions"><button class="button secondary" @click="editing = !editing">{{ editing ? '取消编辑' : '修改资料' }}</button></div>
          <div v-else-if="auth.authenticated" class="profile-actions"><button class="button" @click="follow">{{ profile.followedByMe ? '取消关注' : '关注' }}</button></div>

          <div class="profile-level"><div class="profile-level-head"><span>等级 <strong>{{ levelInfo.name }}</strong></span><span v-if="levelInfo.next">距下一等级 {{ Math.max(0, levelInfo.next - profile.solved) }} 题</span><span v-else>已达最高等级</span></div><div class="profile-progress"><i :style="{ width: `${levelProgress}%` }"></i></div></div>
          <div class="profile-stat-grid"><div><strong>{{ profile.solved }}</strong><span>通过题目</span></div><div><strong>#{{ profile.rank }}</strong><span>当前排名</span></div></div>
          <div class="profile-social-grid"><div><strong>{{ profile.followerCount }}</strong><span>关注者</span></div><div><strong>{{ profile.followingCount }}</strong><span>正在关注</span></div><div><strong>{{ messages.length }}</strong><span>留言</span></div></div>
          <a v-if="profile.email" class="profile-email" :href="`mailto:${profile.email}`">✉ {{ profile.email }}</a>
          <div v-if="profile.coinEarned || profile.coinBonus || profile.coinSpent" class="profile-coins"><span>积分余额</span><strong>{{ coinBalance }}</strong><small>获得 {{ profile.coinEarned }} · 奖励 {{ profile.coinBonus }} · 消耗 {{ profile.coinSpent }}</small></div>
          <p class="profile-last-active">注册于 {{ formatDate(profile.registeredAt) }}<br />最后活跃 {{ formatDate(profile.lastActiveAt) }}</p>
        </section>

        <section class="card profile-message-panel">
          <div class="profile-section-heading"><h2>留言板</h2><span>{{ messages.length }}</span></div>
          <div v-if="auth.authenticated && !isSelf" class="message-composer"><textarea v-model="messageDraft" maxlength="1000" rows="3" :placeholder="`给 ${profile.nick || profile.userId} 留言`"></textarea><button class="button" :disabled="sending || !messageDraft.trim()" @click="addMessage">{{ sending ? '发布中…' : '发布留言' }}</button></div>
          <p v-else-if="!auth.authenticated" class="muted">登录后可以给该用户留言。</p>
          <div v-if="!messages.length" class="muted message-empty">还没有留言。</div>
          <article v-for="message in messages" :key="message.id" class="profile-message"><div class="profile-message-head"><RouterLink :to="`/users/${message.authorId}`">{{ message.authorId }}</RouterLink><time>{{ formatDate(message.createdAt) }}</time><button v-if="auth.isAdmin || auth.user?.userId === message.authorId || isSelf" class="link-button danger-link" @click="deleteMessage(message.id)">删除</button></div><p><template v-for="(part, index) in messageParts(message.content)" :key="index"><RouterLink v-if="part.to" :to="part.to">{{ part.text }}</RouterLink><span v-else>{{ part.text }}</span></template></p></article>
        </section>
      </aside>

      <main class="profile-main">
        <section v-if="editing" class="card profile-section form-stack"><div class="profile-section-heading"><h2>修改个人资料</h2></div><div class="profile-edit-grid"><div class="field"><label>昵称</label><input v-model="editForm.nick" maxlength="20" /></div><div class="field"><label>邮箱</label><input v-model="editForm.email" type="email" maxlength="100" /></div><div class="field"><label>学校</label><input v-model="editForm.school" maxlength="20" /></div><div class="field"><label>班级 / 小组</label><input v-model="editForm.groupName" maxlength="16" /></div></div><button class="button" :disabled="saving" @click="saveProfile">{{ saving ? '保存中…' : '保存资料' }}</button></section>

        <section class="card profile-section profile-overview"><div><span class="profile-kicker">USER PROFILE</span><h2>{{ profile.nick || profile.userId }} 的做题轨迹</h2><p>共提交 {{ profile.submit }} 个题目，解决 {{ profile.solved }} 个题目。</p></div><RouterLink class="button secondary" :to="{ path: '/status', query: { userId: profile.userId } }">查看提交记录</RouterLink></section>

        <section class="card profile-section"><div class="profile-section-heading"><h2>提交统计</h2><span>最近一年</span></div><div v-if="recentDays.length" class="submission-chart"><div v-for="item in recentDays" :key="item.day" class="submission-day" :title="`${item.day}：${item.submissions} 次提交，${item.accepted} 次 AC`"><i :style="{ height: `${Math.max(8, Math.round(item.submissions / maxDailyCount * 100))}%` }" :class="{ 'has-ac': item.accepted > 0 }"></i><small>{{ item.day.slice(5) }}</small></div></div><div v-else class="profile-empty">暂无提交统计</div></section>

        <section class="card profile-section"><div class="profile-section-heading"><h2>结果统计</h2><span>{{ totalResults }} 次有效提交</span></div><div v-if="profile.resultStats.length" class="result-bars"><div v-for="item in profile.resultStats" :key="item.resultId" class="result-bar-row"><span :class="['result-pill', resultTone(item.resultId)]">{{ resultName(item.resultId) }}</span><div class="result-bar"><i :class="resultTone(item.resultId)" :style="{ width: `${item.count / maxResultCount * 100}%` }"></i></div><strong>{{ item.count }}</strong></div></div><div v-else class="profile-empty">暂无结果统计</div></section>

        <section class="card profile-section"><div class="profile-section-heading"><h2>竞赛记录</h2><span>{{ profile.contestStats.length }} 场</span></div><div v-if="profile.contestStats.length" class="table-wrap"><table><thead><tr><th>竞赛</th><th>开始时间</th><th class="number">尝试</th><th class="number">AC</th></tr></thead><tbody><tr v-for="contest in profile.contestStats" :key="contest.contestId"><td><RouterLink :to="`/contests/${contest.contestId}`">{{ contest.title || `竞赛 #${contest.contestId}` }}</RouterLink></td><td>{{ formatDate(contest.startTime) }}</td><td class="number">{{ contest.tried }}</td><td class="number success">{{ contest.accepted }}</td></tr></tbody></table></div><div v-else class="profile-empty">还没有参加过竞赛</div></section>

        <section class="card profile-section"><div class="profile-section-heading"><h2>通过的题目</h2><span class="success">{{ profile.solvedProblems.length }} AC</span></div><div class="problem-labels"><RouterLink v-for="id in profile.solvedProblems" :key="id" class="profile-problem-label accepted" :to="`/problems/${id}`">{{ id }}</RouterLink><span v-if="!profile.solvedProblems.length" class="profile-empty">暂无</span></div></section>
        <section class="card profile-section"><div class="profile-section-heading"><h2>未通过的题目</h2><span>{{ profile.attemptedProblems.length }}</span></div><div class="problem-labels"><RouterLink v-for="id in profile.attemptedProblems" :key="id" class="profile-problem-label attempted" :to="`/problems/${id}`">{{ id }}</RouterLink><span v-if="!profile.attemptedProblems.length" class="profile-empty">暂无</span></div></section>
      </main>
    </div>
  </div>
</template>
