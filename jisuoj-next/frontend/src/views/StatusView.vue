<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import Pager from '../components/Pager.vue'
import ResultCode from '../components/ResultCode.vue'
import { api, errorMessage, type PageResponse } from '../services/api'
import type { Submission } from '../types/domain'

const route = useRoute()
const rows = ref<Submission[]>([]); const total = ref(0); const loading = ref(false); const error = ref('')
const query = reactive({ page: 1, size: 20, problemId: '', userId: '', result: '', language: '' })
let timer = 0

async function load(silent = false) {
  if (!silent) loading.value = true
  try { const { data } = await api.get<PageResponse<Submission>>('/submissions', { params: query }); rows.value = data.items; total.value = data.total; error.value = '' }
  catch (e) { if (!silent) error.value = errorMessage(e) } finally { loading.value = false }
}
function search() { query.page = 1; load() }
onMounted(() => { load(); timer = window.setInterval(() => load(true), 5000); if (route.query.solutionId) error.value = `提交 #${route.query.solutionId} 已进入评测队列` })
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <div class="page-head"><h1>评测状态</h1><span class="muted">每 5 秒自动刷新</span></div>
  <form class="toolbar" @submit.prevent="search">
    <div class="field"><input v-model="query.problemId" placeholder="题目编号" /></div><div class="field"><input v-model="query.userId" placeholder="用户" /></div>
    <div class="field"><select v-model="query.result"><option value="">全部结果</option><option value="4">AC</option><option value="6">WA</option><option value="7">TLE</option><option value="8">MLE</option><option value="11">CE</option><option value="10">RE</option></select></div>
    <button class="button" type="submit">查找</button>
  </form>
  <p v-if="error" :class="route.query.solutionId ? 'card success' : 'error'">{{ error }}</p>
  <div class="table-wrap"><table>
    <thead><tr><th>提交编号</th><th>用户</th><th>题目编号</th><th>结果</th><th class="number">内存</th><th class="number">时间</th><th>语言</th><th class="number">代码长度</th><th>提交时间</th><th>判题机</th></tr></thead>
    <tbody>
      <tr v-if="loading"><td colspan="10" class="loading">正在加载…</td></tr>
      <tr v-for="s in rows" :key="s.id"><td>{{ s.id }}</td><td><RouterLink :to="`/users/${s.userId}`">{{ s.userId }}</RouterLink></td><td><RouterLink :to="`/problems/${s.problemId}`">{{ s.problemId }}</RouterLink></td><td><ResultCode :result="s.result" /></td><td class="number">{{ s.memory ? `${s.memory} KiB` : '—' }}</td><td class="number">{{ s.time }} ms</td><td>{{ s.language }}</td><td class="number">{{ s.codeLength }} B</td><td>{{ s.submittedAt?.replace('T',' ') }}</td><td>{{ s.judger || '—' }}</td></tr>
    </tbody>
  </table></div>
  <Pager :page="query.page" :size="query.size" :total="total" @change="query.page=$event;load()" />
</template>
