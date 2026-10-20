<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, errorMessage, type PageResponse } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { ContestSummary, ProblemDetail } from '../types/domain'

const auth = useAuthStore()
const profile = ref<{ attemptedProblems: number[] } | null>(null)
const problems = ref<ProblemDetail[]>([])
const contests = ref<ContestSummary[]>([])
const loading = ref(false)
const error = ref('')

const pendingCount = computed(() => problems.value.length)
const runningContests = computed(() => contests.value.filter((contest) => contest.status === 'RUNNING'))

async function load() {
  if (!auth.user) return
  loading.value = true
  error.value = ''
  try {
    const [profileResponse, contestResponse] = await Promise.all([
      api.get<{ attemptedProblems: number[] }>(`/users/${encodeURIComponent(auth.user.userId)}`),
      api.get<PageResponse<ContestSummary>>('/contests', { params: { page: 1, size: 100 } }),
    ])
    profile.value = profileResponse.data
    contests.value = contestResponse.data.items
    const ids = profile.value.attemptedProblems || []
    const details = await Promise.all(ids.map(async (id) => {
      try {
        return (await api.get<ProblemDetail>(`/problems/${id}`)).data
      } catch {
        return null
      }
    }))
    problems.value = details.filter((problem): problem is ProblemDetail => !!problem)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

watch(() => [auth.ready, auth.user?.userId], () => {
  if (auth.ready && auth.user) load()
}, { immediate: true })
</script>

<template>
  <div v-if="!auth.ready" class="card loading">正在检查登录状态…</div>
  <section v-else-if="!auth.user" class="card todo-login">
    <h1>我的待办</h1>
    <p class="muted">登录后可以查看做过但尚未通过的题目和正在进行的竞赛。</p>
    <RouterLink class="button" to="/login">前往登录</RouterLink>
  </section>
  <template v-else>
    <div class="page-head">
      <div><span class="todo-kicker">PERSONAL DASHBOARD</span><h1>我的待办</h1></div>
      <button class="button secondary" :disabled="loading" type="button" @click="load">{{ loading ? '刷新中…' : '刷新' }}</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="grid two todo-grid">
      <section class="card todo-panel">
        <div class="todo-panel-heading"><div><h2>待完成题目</h2><p class="muted">你尝试过但还没有通过的题目</p></div><strong>{{ pendingCount }}</strong></div>
        <div v-if="loading" class="empty">正在加载题目…</div>
        <div v-else-if="!problems.length" class="empty">太棒了，暂时没有待完成题目。</div>
        <div v-else class="todo-problem-list">
          <RouterLink v-for="problem in problems" :key="problem.id" class="todo-problem" :to="`/problems/${problem.id}`">
            <span class="todo-problem-id">#{{ problem.id }}</span>
            <span class="todo-problem-title">{{ problem.title || `题目 #${problem.id}` }}</span>
            <span class="todo-problem-stats">AC {{ problem.accepted }} · 提交 {{ problem.submit }}</span>
          </RouterLink>
        </div>
      </section>

      <section class="card todo-panel">
        <div class="todo-panel-heading"><div><h2>进行中的竞赛</h2><p class="muted">现在可以参加的竞赛</p></div><strong>{{ runningContests.length }}</strong></div>
        <div v-if="loading" class="empty">正在加载竞赛…</div>
        <div v-else-if="!runningContests.length" class="empty">当前没有进行中的竞赛。</div>
        <div v-else class="todo-contest-list">
          <RouterLink v-for="contest in runningContests" :key="contest.id" class="todo-contest" :to="`/contests/${contest.id}`">
            <span class="todo-contest-status">进行中</span>
            <span><strong>{{ contest.title || `竞赛 #${contest.id}` }}</strong><small>{{ contest.privateContest ? '私有竞赛' : '公开竞赛' }} · {{ contest.creator }}</small></span>
            <span aria-hidden="true">→</span>
          </RouterLink>
        </div>
      </section>
    </div>
  </template>
</template>
