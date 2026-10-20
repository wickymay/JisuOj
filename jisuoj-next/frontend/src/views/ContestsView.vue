<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Pager from '../components/Pager.vue'
import { api, errorMessage, type PageResponse } from '../services/api'
import type { ContestSummary } from '../types/domain'

const rows=ref<ContestSummary[]>([]); const page=ref(1); const total=ref(0); const error=ref('')
const statusName:Record<string,string>={UPCOMING:'未开始',RUNNING:'进行中',ENDED:'已结束'}
async function load(){try{const {data}=await api.get<PageResponse<ContestSummary>>('/contests',{params:{page:page.value,size:20}});rows.value=data.items;total.value=data.total}catch(e){error.value=errorMessage(e)}}
onMounted(load)
</script>

<template>
  <div class="page-head"><h1>竞赛与作业</h1><span class="muted">共 {{ total }} 场</span></div>
  <p v-if="error" class="error">{{ error }}</p>
  <div class="table-wrap"><table><thead><tr><th>编号</th><th>名称</th><th>状态</th><th>开始时间</th><th>结束时间</th><th>权限</th><th>创建者</th></tr></thead><tbody>
    <tr v-for="c in rows" :key="c.id"><td>{{ c.id }}</td><td><RouterLink :to="`/contests/${c.id}`"><strong>{{ c.title }}</strong></RouterLink></td><td><span :class="c.status==='RUNNING'?'success':'muted'">{{ statusName[c.status] || c.status }}</span></td><td>{{ c.startTime?.replace('T',' ') }}</td><td>{{ c.endTime?.replace('T',' ') }}</td><td>{{ c.privateContest ? '私有' : '公开' }}</td><td>{{ c.creator }}</td></tr>
  </tbody></table></div>
  <Pager :page="page" :size="20" :total="total" @change="page=$event;load()" />
</template>
