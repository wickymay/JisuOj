<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, ensureCsrf, errorMessage } from '../services/api'

interface Mail {id:number;toUser:string;fromUser:string;title:string;content:string;unread:boolean;sentAt:string;sentByMe:boolean}
const box=ref<'inbox'|'sent'>('inbox');const messages=ref<Mail[]>([]);const selected=ref<Mail|null>(null);const composing=ref(false);const error=ref('');const form=reactive({toUser:'',title:'',content:''})
async function load(){try{messages.value=(await api.get<Mail[]>('/mail',{params:{box:box.value}})).data}catch(e){error.value=errorMessage(e)}}
async function open(mail:Mail){selected.value=mail;if(mail.unread&&!mail.sentByMe){await ensureCsrf();await api.post(`/mail/${mail.id}/read`);mail.unread=false}}
async function send(){try{await ensureCsrf();await api.post('/mail',{...form,replyTo:null});composing.value=false;box.value='sent';await load()}catch(e){error.value=errorMessage(e)}}
async function remove(id:number){await ensureCsrf();await api.delete(`/mail/${id}`);selected.value=null;await load()}
onMounted(load)
</script>

<template>
  <div class="page-head"><h1>站内信</h1><button class="button" @click="composing=!composing">写信</button></div>
  <p v-if="error" class="error">{{ error }}</p>
  <section v-if="composing" class="card form-stack" style="margin-bottom:20px"><div class="field"><label>收件人</label><input v-model="form.toUser" /></div><div class="field"><label>标题</label><input v-model="form.title" /></div><div class="field"><label>正文</label><textarea v-model="form.content"></textarea></div><button class="button" @click="send">发送</button></section>
  <div class="tabs"><button :class="{active:box==='inbox'}" @click="box='inbox';load()">收件箱</button><button :class="{active:box==='sent'}" @click="box='sent';load()">已发送</button></div>
  <div class="grid two">
    <div class="table-wrap"><table style="min-width:460px"><thead><tr><th>{{ box==='sent'?'收件人':'发件人' }}</th><th>标题</th><th>时间</th></tr></thead><tbody><tr v-for="mail in messages" :key="mail.id" @click="open(mail)" style="cursor:pointer"><td>{{ box==='sent'?mail.toUser:mail.fromUser }}</td><td><strong v-if="mail.unread">● </strong>{{ mail.title }}</td><td>{{ mail.sentAt?.replace('T',' ') }}</td></tr></tbody></table></div>
    <article class="card"><template v-if="selected"><div class="page-head"><h2>{{ selected.title }}</h2><button class="button danger" @click="remove(selected.id)">删除</button></div><p class="muted">{{ selected.fromUser }} → {{ selected.toUser }} · {{ selected.sentAt?.replace('T',' ') }}</p><pre class="sample">{{ selected.content }}</pre></template><div v-else class="empty">选择一封邮件查看</div></article>
  </div>
</template>
