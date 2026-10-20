<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth=useAuthStore();const route=useRoute();const router=useRouter();const mode=ref<'login'|'register'>('login')
const form=reactive({userId:'',password:'',nick:'',email:''})
async function submit(){try{if(mode.value==='login')await auth.login(form.userId,form.password);else await auth.register(form.userId,form.password,form.nick,form.email);const redirect=typeof route.query.redirect==='string'&&route.query.redirect.startsWith('/')&&!route.query.redirect.startsWith('//')?route.query.redirect:'/';await router.push(redirect)}catch{/* store owns message */}}
</script>

<template>
  <section class="card auth-card">
    <div class="tabs"><button :class="{active:mode==='login'}" @click="mode='login'">登录</button><button :class="{active:mode==='register'}" @click="mode='register'">注册</button></div>
    <form class="form-stack" @submit.prevent="submit">
      <div class="field"><label>用户名</label><input v-model.trim="form.userId" autocomplete="username" required /></div>
      <div class="field"><label>密码</label><input v-model="form.password" type="password" :autocomplete="mode==='login'?'current-password':'new-password'" required /></div>
      <template v-if="mode==='register'"><div class="field"><label>昵称</label><input v-model.trim="form.nick" maxlength="20" /></div><div class="field"><label>邮箱</label><input v-model.trim="form.email" type="email" /></div></template>
      <p v-if="auth.error" class="error">{{ auth.error }}</p>
      <button class="button" :disabled="auth.busy">{{ auth.busy?'请稍候…':mode==='login'?'登录':'创建账号' }}</button>
    </form>
  </section>
</template>
