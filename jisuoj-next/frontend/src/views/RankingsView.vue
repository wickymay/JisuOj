<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import Pager from '../components/Pager.vue'
import { api, errorMessage, type PageResponse } from '../services/api'
import type { RankRow } from '../types/domain'

const query = reactive({ page: 1, size: 50, keyword: '' }); const rows = ref<RankRow[]>([]); const total = ref(0); const error = ref('')
async function load() { try { const { data } = await api.get<PageResponse<RankRow>>('/rankings', { params: query }); rows.value=data.items; total.value=data.total } catch(e) { error.value=errorMessage(e) } }
function search(){ query.page=1;load() }
onMounted(load)
</script>

<template>
  <div class="page-head"><h1>用户排名</h1><span class="muted">按解题数排序</span></div>
  <form class="toolbar" @submit.prevent="search"><div class="field"><input v-model="query.keyword" placeholder="用户名、昵称或学校" /></div><button class="button">查找</button></form>
  <p v-if="error" class="error">{{ error }}</p>
  <div class="table-wrap"><table><thead><tr><th>排名</th><th>用户</th><th>昵称</th><th>学校</th><th>班级/小组</th><th class="number">已解决</th><th class="number">提交</th><th class="number">通过率</th></tr></thead><tbody>
    <tr v-for="row in rows" :key="row.userId"><td>{{ row.rank }}</td><td><RouterLink :to="`/users/${row.userId}`">{{ row.userId }}</RouterLink></td><td>{{ row.nick }}</td><td>{{ row.school || '—' }}</td><td>{{ row.groupName || '—' }}</td><td class="number"><strong class="success">{{ row.solved }}</strong></td><td class="number">{{ row.submit }}</td><td class="number">{{ row.ratio.toFixed(2) }}%</td></tr>
  </tbody></table></div>
  <Pager :page="query.page" :size="query.size" :total="total" @change="query.page=$event;load()" />
</template>
