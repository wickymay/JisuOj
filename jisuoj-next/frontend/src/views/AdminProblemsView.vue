<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Pager from '../components/Pager.vue'
import { api, ensureCsrf, errorMessage, type PageResponse } from '../services/api'
import type { AdminProblemSummary } from '../types/domain'

const rows = ref<AdminProblemSummary[]>([])
const page = ref(1)
const total = ref(0)
const keyword = ref('')
const appliedKeyword = ref('')
const busy = ref(false)
const loading = ref(false)
const error = ref('')

async function load(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get<PageResponse<AdminProblemSummary>>('/admin/problems', {
      params: { page: targetPage, size: 20, keyword: appliedKeyword.value },
    })
    rows.value = data.items
    total.value = data.total
    page.value = data.page
  } catch (e) { error.value = errorMessage(e) }
  finally { loading.value = false }
}

function search() {
  appliedKeyword.value = keyword.value.trim()
  void load(1)
}

async function setHidden(row: AdminProblemSummary, hidden: boolean) {
  busy.value = true
  error.value = ''
  try {
    await ensureCsrf()
    if (hidden) await api.delete(`/admin/problems/${row.id}`)
    else await api.post(`/admin/problems/${row.id}/restore`)
    await load()
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}

async function remove(row: AdminProblemSummary) {
  if (!window.confirm(`确定彻底删除题目 #${row.id}「${row.title}」吗？题目记录和测试数据会被删除，无法恢复。`)) return
  busy.value = true
  error.value = ''
  try {
    await ensureCsrf()
    await api.delete(`/admin/problems/${row.id}/permanent`)
    await load(rows.value.length === 1 && page.value > 1 ? page.value - 1 : page.value)
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}

onMounted(() => { void load() })
</script>

<template>
  <div class="page-head"><div><h2>题目管理</h2><span class="muted">包含隐藏题目；已隐藏且没有关联记录的题目可以彻底删除</span></div><RouterLink class="button" to="/admin/problems/new">新增题目</RouterLink></div>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <form class="toolbar" @submit.prevent="search"><input v-model="keyword" placeholder="搜索题号或标题" aria-label="搜索题号或标题" /><button class="button secondary" type="submit" :disabled="loading">搜索</button></form>
  <div class="table-wrap"><table><thead><tr><th>题号</th><th>标题</th><th>来源</th><th>状态</th><th>创建时间</th><th class="number">提交</th><th>操作</th></tr></thead>
    <tbody><tr v-if="loading"><td colspan="7" class="loading">正在加载…</td></tr><template v-else><tr v-for="row in rows" :key="row.id"><td>{{ row.id }}</td><td>{{ row.title }}</td><td>{{ row.source || '—' }}</td><td>{{ row.hidden ? '已隐藏' : '公开' }}</td><td>{{ row.createdAt?.replace('T', ' ') || '—' }}</td><td class="number">{{ row.submit }}</td><td><div class="toolbar"><RouterLink class="button secondary admin-table-button" :to="`/admin/problems/${row.id}`">编辑</RouterLink><button v-if="!row.hidden" class="button secondary admin-table-button" type="button" :disabled="busy" @click="setHidden(row, true)">隐藏</button><template v-else><button class="button secondary admin-table-button" type="button" :disabled="busy" @click="setHidden(row, false)">恢复公开</button><button class="button danger admin-table-button" type="button" :disabled="busy" @click="remove(row)">彻底删除</button></template></div></td></tr><tr v-if="!rows.length"><td colspan="7" class="empty">没有找到题目。</td></tr></template></tbody>
  </table></div>
  <Pager :page="page" :size="20" :total="total" @change="load($event)" />
</template>
