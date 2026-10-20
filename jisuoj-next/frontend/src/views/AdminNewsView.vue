<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Pager from '../components/Pager.vue'
import { api, ensureCsrf, errorMessage, type PageResponse } from '../services/api'
import type { NewsSummary } from '../types/domain'

const rows = ref<NewsSummary[]>([])
const page = ref(1)
const total = ref(0)
const error = ref('')
const busy = ref(false)

async function load() {
  try {
    const { data } = await api.get<PageResponse<NewsSummary>>('/news', { params: { page: page.value, size: 20 } })
    rows.value = data.items
    total.value = data.total
  } catch (e) { error.value = errorMessage(e) }
}
async function hide(id: number) {
  if (!window.confirm('确定隐藏这条公告吗？')) return
  busy.value = true
  try { await ensureCsrf(); await api.delete(`/admin/news/${id}`); await load() }
  catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><h2>公告列表</h2><span class="muted">管理平台公告和首页动态</span></div><RouterLink class="button" to="/admin/news/new">发布公告</RouterLink></div>
  <p v-if="error" class="error">{{ error }}</p>
  <div class="table-wrap"><table><thead><tr><th>编号</th><th>标题</th><th>作者</th><th>发布时间</th><th class="number">重要性</th><th>操作</th></tr></thead><tbody><tr v-if="!rows.length"><td colspan="6" class="loading">暂无公告</td></tr><tr v-for="row in rows" :key="row.id"><td>{{ row.id }}</td><td><RouterLink :to="`/news/${row.id}`">{{ row.title }}</RouterLink></td><td>{{ row.author }}</td><td>{{ row.publishedAt?.replace('T', ' ') }}</td><td class="number">{{ row.importance }}</td><td><RouterLink class="button secondary admin-table-button" :to="`/admin/news/${row.id}`">编辑</RouterLink><button class="button danger admin-table-button" :disabled="busy" @click="hide(row.id)">隐藏</button></td></tr></tbody></table></div>
  <Pager :page="page" :size="20" :total="total" @change="page = $event; load()" />
</template>
