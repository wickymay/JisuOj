<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ensureCsrf, errorMessage } from '../services/api'
import type { ContestDetail } from '../types/domain'

const route=useRoute();const router=useRouter();const id=computed(()=>route.params.id?Number(route.params.id):null);const jumpId=ref('');const error=ref('');const busy=ref(false);const problemText=ref('')
const now=new Date();const later=new Date(now.getTime()+2*60*60*1000);const local=(d:Date)=>new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16)
const form=reactive({title:'',description:'',startTime:local(now),endTime:local(later),privateContest:false,password:'',type:0,languageMask:0,hidden:true,problemIds:[] as number[]})
async function load(){if(!id.value)return;try{const c=(await api.get<ContestDetail>(`/contests/${id.value}`)).data;Object.assign(form,c,{problemIds:c.problems.map(p=>p.id)});problemText.value=form.problemIds.join(', ')}catch(e){error.value=errorMessage(e)}}
async function save(){busy.value=true;error.value='';form.problemIds=problemText.value.split(/[\s,，]+/).map(Number).filter(n=>n>0);try{await ensureCsrf();if(id.value)await api.put(`/admin/contests/${id.value}`,form);else{const {data}=await api.post('/admin/contests',form);await router.replace(`/admin/contests/${data.id}`)}}catch(e){error.value=errorMessage(e)}finally{busy.value=false}}
async function hide(){if(!id.value)return;await ensureCsrf();await api.delete(`/admin/contests/${id.value}`);form.hidden=true}
function jump(){if(Number(jumpId.value)>0)router.push(`/admin/contests/${jumpId.value}`)}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><RouterLink to="/admin">← 管理中心</RouterLink><h1 style="margin-top:12px">{{ id?`编辑竞赛 #${id}`:'新建竞赛' }}</h1></div><form class="toolbar" @submit.prevent="jump"><input v-model="jumpId" type="number" placeholder="打开竞赛编号" /><button class="button secondary">打开</button></form></div><p v-if="error" class="error">{{ error }}</p>
  <section class="card form-stack"><div class="field"><label>名称</label><input v-model="form.title" /></div><div class="field"><label>说明（HTML）</label><textarea v-model="form.description"></textarea></div><div class="grid two"><div class="field"><label>开始时间</label><input v-model="form.startTime" type="datetime-local" /></div><div class="field"><label>结束时间</label><input v-model="form.endTime" type="datetime-local" /></div></div><div class="grid two"><div class="field"><label>题目编号（按顺序，逗号分隔）</label><input v-model="problemText" placeholder="1000, 1001, 1002" /></div><div class="field"><label>赛制</label><select v-model.number="form.type"><option :value="0">ACM</option><option :value="1">OI</option></select></div></div><div class="grid two"><label><input v-model="form.privateContest" type="checkbox" style="width:auto" /> 私有竞赛</label><label><input v-model="form.hidden" type="checkbox" style="width:auto" /> 隐藏竞赛</label></div><div v-if="form.privateContest" class="field"><label>竞赛密码</label><input v-model="form.password" maxlength="16" /></div><div class="toolbar"><button class="button" :disabled="busy" @click="save">{{ busy?'保存中…':'保存' }}</button><button v-if="id&&!form.hidden" class="button danger" @click="hide">隐藏</button><RouterLink v-if="id" class="button secondary" :to="`/contests/${id}`">查看竞赛</RouterLink></div></section>
</template>
