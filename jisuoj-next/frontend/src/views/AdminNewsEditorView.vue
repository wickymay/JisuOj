<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ensureCsrf, errorMessage } from '../services/api'

interface NewsDetail {id:number;title:string;content:string;author:string;publishedAt:string;importance:number;menu:boolean;hidden:boolean}
const route=useRoute();const router=useRouter();const id=computed(()=>route.params.id?Number(route.params.id):null);const jumpId=ref('');const error=ref('');const busy=ref(false)
const form=reactive({title:'',content:'',importance:0,menu:false,hidden:false})
async function load(){if(!id.value)return;try{Object.assign(form,(await api.get<NewsDetail>(`/news/${id.value}`)).data)}catch(e){error.value=errorMessage(e)}}
async function save(){busy.value=true;error.value='';try{await ensureCsrf();if(id.value)await api.put(`/admin/news/${id.value}`,form);else{const {data}=await api.post('/admin/news',form);await router.replace(`/admin/news/${data.id}`)}}catch(e){error.value=errorMessage(e)}finally{busy.value=false}}
async function hide(){if(!id.value)return;await ensureCsrf();await api.delete(`/admin/news/${id.value}`);form.hidden=true}
function jump(){if(Number(jumpId.value)>0)router.push(`/admin/news/${jumpId.value}`)}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><RouterLink to="/admin">← 管理中心</RouterLink><h1 style="margin-top:12px">{{ id?`编辑公告 #${id}`:'新建公告' }}</h1></div><form class="toolbar" @submit.prevent="jump"><input v-model="jumpId" type="number" placeholder="打开公告编号" /><button class="button secondary">打开</button></form></div><p v-if="error" class="error">{{ error }}</p>
  <section class="card form-stack"><div class="field"><label>标题</label><input v-model="form.title" /></div><div class="field"><label>正文（HTML）</label><textarea v-model="form.content" rows="18"></textarea></div><div class="grid two"><div class="field"><label>重要程度（0–9）</label><input v-model.number="form.importance" type="number" min="0" max="9" /></div><div style="display:flex;gap:18px;align-items:end;padding-bottom:10px"><label><input v-model="form.menu" type="checkbox" style="width:auto" /> 显示在菜单</label><label><input v-model="form.hidden" type="checkbox" style="width:auto" /> 隐藏</label></div></div><div class="toolbar"><button class="button" :disabled="busy" @click="save">{{ busy?'保存中…':'保存' }}</button><button v-if="id&&!form.hidden" class="button danger" @click="hide">隐藏公告</button></div></section>
</template>
