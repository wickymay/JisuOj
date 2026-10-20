<script setup lang="ts">
import DOMPurify from 'dompurify'
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Pager from '../components/Pager.vue'
import { api, ensureCsrf, errorMessage, type PageResponse } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { TopicSummary } from '../types/domain'

interface Topic { id:number;title:string;author:string;problemId:number;contestId:number|null;createdAt:string;replies:{id:number;author:string;content:string;createdAt:string}[] }
const route=useRoute();const router=useRouter();const auth=useAuthStore();const rows=ref<TopicSummary[]>([]);const topic=ref<Topic|null>(null);const page=ref(1);const total=ref(0);const error=ref('');const showComposer=ref(false)
const form=reactive({title:'',content:'',problemId:'',contestId:''});const reply=ref('')
async function load(){try{if(route.params.id){topic.value=(await api.get<Topic>(`/discussions/${route.params.id}`)).data}else{const {data}=await api.get<PageResponse<TopicSummary>>('/discussions',{params:{page:page.value,size:20}});rows.value=data.items;total.value=data.total}}catch(e){error.value=errorMessage(e)}}
async function create(){try{await ensureCsrf();const {data}=await api.post('/discussions',{title:form.title,content:form.content,problemId:form.problemId?Number(form.problemId):null,contestId:form.contestId?Number(form.contestId):null});await router.push(`/discussions/${data.id}`)}catch(e){error.value=errorMessage(e)}}
async function addReply(){try{await ensureCsrf();await api.post(`/discussions/${route.params.id}/replies`,{content:reply.value});reply.value='';await load()}catch(e){error.value=errorMessage(e)}}
watch(()=>route.params.id,()=>{topic.value=null;load()});onMounted(load)
</script>

<template>
  <p v-if="error" class="error">{{ error }}</p>
  <template v-if="route.params.id && topic">
    <div class="page-head"><div><RouterLink to="/discussions">← 返回讨论</RouterLink><h1 style="margin-top:12px">{{ topic.title }}</h1></div><span class="muted">题目 {{ topic.problemId || '—' }}</span></div>
    <section class="grid">
      <article v-for="(item,index) in topic.replies" :key="item.id" class="card"><div class="page-head"><strong>#{{ index+1 }} · {{ item.author }}</strong><span class="muted">{{ item.createdAt?.replace('T',' ') }}</span></div><div class="prose" v-html="DOMPurify.sanitize(item.content)"></div></article>
    </section>
    <section v-if="auth.authenticated" class="card form-stack" style="margin-top:20px"><h2>回复</h2><textarea v-model="reply" placeholder="写下你的回复"></textarea><button class="button" @click="addReply">发表回复</button></section>
  </template>
  <template v-else>
    <div class="page-head"><h1>讨论</h1><button v-if="auth.authenticated" class="button" @click="showComposer=!showComposer">发起讨论</button></div>
    <section v-if="showComposer" class="card form-stack" style="margin-bottom:20px"><div class="field"><label>标题</label><input v-model="form.title" /></div><div class="grid two"><div class="field"><label>题目编号（可选）</label><input v-model="form.problemId" type="number" /></div><div class="field"><label>竞赛编号（可选）</label><input v-model="form.contestId" type="number" /></div></div><div class="field"><label>内容</label><textarea v-model="form.content"></textarea></div><button class="button" @click="create">发布</button></section>
    <div class="table-wrap"><table><thead><tr><th>主题</th><th>作者</th><th>题目</th><th class="number">回复</th><th>最后回复</th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td><RouterLink :to="`/discussions/${item.id}`"><strong>{{ item.title }}</strong></RouterLink></td><td>{{ item.author }}</td><td>{{ item.problemId || '—' }}</td><td class="number">{{ item.replyCount }}</td><td>{{ item.lastReplyAt?.replace('T',' ') }}</td></tr></tbody></table></div>
    <Pager :page="page" :size="20" :total="total" @change="page=$event;load()" />
  </template>
</template>
