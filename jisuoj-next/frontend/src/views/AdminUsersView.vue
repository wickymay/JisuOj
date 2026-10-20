<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Pager from '../components/Pager.vue'
import { api, ensureCsrf, errorMessage, type PageResponse } from '../services/api'

interface UserRow {userId:string;nick:string;email:string;school:string;groupName:string;solved:number;submit:number;disabled:boolean;registeredAt:string;lastActiveAt:string;expiresAt:string}
const query=reactive({page:1,size:30,keyword:''});const rows=ref<UserRow[]>([]);const total=ref(0);const error=ref('');const selected=ref<UserRow|null>(null);const roles=ref<string[]>([]);const role=ref('problem_editor')
const roleNames:Record<string,string>={super_administrator:'超级管理员',administrator:'普通管理员',problem_editor:'题目编辑',contest_creator:'竞赛创建',source_browser:'源码查看',user_adder:'用户管理',password_setter:'密码管理',balloon:'气球派发',vip:'VIP比赛'}
const roleOptions=computed(()=>Object.entries(roleNames).filter(([value])=>!['super_administrator','administrator'].includes(value)))
const canRemoveRole=(value:string)=>!['super_administrator','administrator'].includes(value)
async function load(){try{const {data}=await api.get<PageResponse<UserRow>>('/admin/users',{params:query});rows.value=data.items;total.value=data.total}catch(e){error.value=errorMessage(e)}}
async function select(user:UserRow){selected.value=user;try{roles.value=(await api.get<string[]>(`/admin/users/${user.userId}/roles`)).data}catch(e){error.value=errorMessage(e)}}
async function toggle(user:UserRow){try{await ensureCsrf();await api.put(`/admin/users/${user.userId}/state`,{disabled:!user.disabled});user.disabled=!user.disabled}catch(e){error.value=errorMessage(e)}}
async function addRole(){if(!selected.value)return;try{await ensureCsrf();await api.post(`/admin/users/${selected.value.userId}/roles`,{role:role.value});await select(selected.value)}catch(e){error.value=errorMessage(e)}}
async function removeRole(value:string){if(!selected.value)return;try{await ensureCsrf();await api.delete(`/admin/users/${selected.value.userId}/roles/${value}`);await select(selected.value)}catch(e){error.value=errorMessage(e)}}
function search(){query.page=1;load()}onMounted(load)
</script>

<template>
  <div class="page-head"><div><RouterLink to="/admin">← 管理中心</RouterLink><h1 style="margin-top:12px">用户管理</h1></div><span class="muted">共 {{ total }} 个账号</span></div><form class="toolbar" @submit.prevent="search"><div class="field"><input v-model="query.keyword" placeholder="用户名、昵称或学校" /></div><button class="button">查找</button></form><p v-if="error" class="error">{{ error }}</p>
  <div class="grid two"><div><div class="table-wrap"><table><thead><tr><th>用户</th><th>昵称</th><th>学校</th><th class="number">解题</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="user in rows" :key="user.userId"><td><button class="link-button" @click="select(user)">{{ user.userId }}</button></td><td>{{ user.nick }}</td><td>{{ user.school||'—' }}</td><td class="number">{{ user.solved }}</td><td :class="user.disabled?'result-code rejected':'success'">{{ user.disabled?'停用':'正常' }}</td><td><button class="button secondary" @click="toggle(user)">{{ user.disabled?'启用':'停用' }}</button></td></tr></tbody></table></div><Pager :page="query.page" :size="query.size" :total="total" @change="query.page=$event;load()" /></div>
    <aside class="card"><template v-if="selected"><h2>{{ selected.nick || selected.userId }} 的角色</h2><p class="muted">@{{ selected.userId }}</p><div class="form-stack"><div v-for="item in roles" :key="item" class="page-head" style="margin:0;padding:8px 0;border-bottom:1px solid var(--line)"><span>{{ roleNames[item]||item }}</span><button v-if="canRemoveRole(item)" class="button danger" @click="removeRole(item)">移除</button><span v-else class="muted">受保护</span></div><div class="toolbar"><select v-model="role"><option v-for="([value,label]) in roleOptions" :key="value" :value="value">{{ label }}</option></select><button class="button" @click="addRole">添加角色</button></div></div></template><div v-else class="empty">选择用户管理角色</div></aside>
  </div>
</template>
