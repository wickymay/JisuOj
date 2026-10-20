<script setup lang="ts">
import DOMPurify from 'dompurify'
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, ensureCsrf, errorMessage } from '../services/api'
import type { ContestDetail, Standing } from '../types/domain'

const route=useRoute();const contest=ref<ContestDetail|null>(null);const standings=ref<Standing[]>([]);const tab=ref<'problems'|'standings'>('problems');const password=ref('');const error=ref('');const privateBlocked=ref(false)
async function load(){try{privateBlocked.value=false;contest.value=(await api.get<ContestDetail>(`/contests/${route.params.id}`)).data;standings.value=(await api.get<Standing[]>(`/contests/${route.params.id}/standings`)).data;error.value=''}catch(e){privateBlocked.value=(e as {response?:{status?:number}}).response?.status===403;error.value=errorMessage(e)}}
async function join(){try{await ensureCsrf();await api.post(`/contests/${route.params.id}/join`,{password:password.value});error.value='';privateBlocked.value=false;await load()}catch(e){error.value=errorMessage(e)}}
onMounted(load)
</script>

<template>
  <p v-if="error" class="error">{{ error }}</p>
  <section v-if="!contest && privateBlocked" class="card form-stack" style="max-width:520px;margin:30px auto"><h2>加入私有竞赛</h2><p class="muted">请输入竞赛密码后查看题目与榜单。</p><div class="field"><input v-model="password" type="password" placeholder="竞赛密码" /></div><button class="button" @click="join">加入竞赛</button></section>
  <template v-if="contest">
    <div class="page-head"><h1>{{ contest.title }}</h1><span class="tag">{{ contest.status }}</span></div>
    <div class="problem-meta"><span class="tag">{{ contest.startTime.replace('T',' ') }}</span><span class="tag">至 {{ contest.endTime.replace('T',' ') }}</span><span class="tag">{{ contest.privateContest?'私有竞赛':'公开竞赛' }}</span></div>
    <div v-if="contest.privateContest && !contest.problems.length" class="card toolbar"><div class="field"><input v-model="password" type="password" placeholder="输入竞赛密码" /></div><button class="button" @click="join">加入竞赛</button></div>
    <article v-if="contest.description" class="card prose" style="margin-bottom:18px" v-html="DOMPurify.sanitize(contest.description)"></article>
    <div class="tabs"><button :class="{active:tab==='problems'}" @click="tab='problems'">题目</button><button :class="{active:tab==='standings'}" @click="tab='standings'">排名</button></div>
    <div v-if="tab==='problems'" class="table-wrap"><table><thead><tr><th>编号</th><th>题目</th><th class="number">通过</th><th class="number">提交</th></tr></thead><tbody><tr v-for="p in contest.problems" :key="p.id"><td>{{ p.label }}</td><td><RouterLink :to="{path:`/problems/${p.id}`,query:{contestId:contest.id}}">{{ p.title }}</RouterLink></td><td class="number">{{ p.accepted }}</td><td class="number">{{ p.submit }}</td></tr></tbody></table></div>
    <div v-else class="table-wrap"><table><thead><tr><th>排名</th><th>用户</th><th class="number">解出</th><th class="number">罚时</th><th v-for="p in contest.problems" :key="p.id">{{ p.label }}</th></tr></thead><tbody><tr v-for="s in standings" :key="s.userId"><td>{{ s.rank }}</td><td><RouterLink :to="`/users/${s.userId}`">{{ s.nick || s.userId }}</RouterLink></td><td class="number success">{{ s.solved }}</td><td class="number">{{ s.penalty }}</td><td v-for="p in contest.problems" :key="p.id"><span v-if="s.problems[p.number]?.accepted" class="success">+{{ s.problems[p.number].attempts || '' }}</span><span v-else-if="s.problems[p.number]?.attempts" class="result-code rejected">-{{ s.problems[p.number].attempts }}</span><span v-else>—</span></td></tr></tbody></table></div>
  </template>
</template>
