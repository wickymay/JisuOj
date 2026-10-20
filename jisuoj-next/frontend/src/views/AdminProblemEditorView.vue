<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ensureCsrf, errorMessage } from '../services/api'
import type { ProblemDetail } from '../types/domain'

const route=useRoute();const router=useRouter();const id=computed(()=>route.params.id?Number(route.params.id):null);const jumpId=ref('');const busy=ref(false);const error=ref('')
const form=reactive({title:'',description:'',input:'',output:'',sampleInput:'',sampleOutput:'',hint:'',source:'',timeLimit:1,memoryLimit:128,specialJudge:false,hidden:true})
async function load(){if(!id.value)return;try{const p=(await api.get<ProblemDetail>(`/problems/${id.value}`)).data;Object.assign(form,p)}catch(e){error.value=errorMessage(e)}}
async function save(){busy.value=true;error.value='';try{await ensureCsrf();if(id.value)await api.put(`/admin/problems/${id.value}`,form);else{const {data}=await api.post('/admin/problems',form);await router.replace(`/admin/problems/${data.id}`)}}catch(e){error.value=errorMessage(e)}finally{busy.value=false}}
async function hide(){if(!id.value)return;busy.value=true;error.value='';try{await ensureCsrf();await api.delete(`/admin/problems/${id.value}`);form.hidden=true}catch(e){error.value=errorMessage(e)}finally{busy.value=false}}
async function restore(){if(!id.value)return;busy.value=true;error.value='';try{await ensureCsrf();await api.post(`/admin/problems/${id.value}/restore`);form.hidden=false}catch(e){error.value=errorMessage(e)}finally{busy.value=false}}
async function remove(){
  if(!id.value || !window.confirm(`确定彻底删除题目 #${id.value}「${form.title}」吗？题目记录和测试数据会被删除，无法恢复。`))return
  busy.value=true;error.value=''
  try{await ensureCsrf();await api.delete(`/admin/problems/${id.value}/permanent`);await router.replace('/admin/problems')}
  catch(e){error.value=errorMessage(e)}finally{busy.value=false}
}
function jump(){if(Number(jumpId.value)>0)router.push(`/admin/problems/${jumpId.value}`)}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><RouterLink to="/admin/problems">← 题目管理</RouterLink><h1 style="margin-top:12px">{{ id?`编辑题目 #${id}`:'新建题目' }}</h1></div><form class="toolbar" @submit.prevent="jump"><input v-model="jumpId" type="number" placeholder="打开题号" /><button class="button secondary">打开</button></form></div>
  <p v-if="error" class="error">{{ error }}</p>
  <section class="card form-stack"><div class="grid two"><div class="field"><label>标题</label><input v-model="form.title" /></div><div class="field"><label>来源</label><input v-model="form.source" /></div></div><div class="grid three"><div class="field"><label>时间限制（秒）</label><input v-model.number="form.timeLimit" type="number" min="0.001" step="0.1" /></div><div class="field"><label>内存限制（MiB）</label><input v-model.number="form.memoryLimit" type="number" min="1" /></div><label style="align-self:end"><input v-model="form.specialJudge" type="checkbox" style="width:auto" /> 特殊评测</label></div><div class="field"><label>题目描述（HTML）</label><textarea v-model="form.description" rows="12"></textarea></div><div class="grid two"><div class="field"><label>输入说明</label><textarea v-model="form.input"></textarea></div><div class="field"><label>输出说明</label><textarea v-model="form.output"></textarea></div></div><div class="grid two"><div class="field"><label>样例输入</label><textarea v-model="form.sampleInput"></textarea></div><div class="field"><label>样例输出</label><textarea v-model="form.sampleOutput"></textarea></div></div><div class="field"><label>提示</label><textarea v-model="form.hint"></textarea></div><label><input v-model="form.hidden" type="checkbox" style="width:auto" /> 隐藏题目</label><div class="toolbar"><button class="button" :disabled="busy" @click="save">{{ busy?'保存中…':'保存' }}</button><button v-if="id&&!form.hidden" class="button danger" :disabled="busy" @click="hide">隐藏</button><button v-if="id&&form.hidden" class="button secondary" :disabled="busy" @click="restore">恢复公开</button><button v-if="id&&form.hidden" class="button danger" :disabled="busy" @click="remove">彻底删除</button><RouterLink v-if="id" class="button secondary" :to="`/problems/${id}`">查看题目</RouterLink></div></section>
</template>
