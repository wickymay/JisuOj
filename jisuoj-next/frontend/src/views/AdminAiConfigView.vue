<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, ensureCsrf, errorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'

interface AiConfigStatus { configured: boolean; baseUrl: string; model: string; configuredAt: string | null }

const status = ref<AiConfigStatus>({ configured: false, baseUrl: '', model: '', configuredAt: null })
const auth = useAuthStore()
const form = reactive({
  baseUrl: auth.isSuperAdmin ? 'https://api.deepseek.com' : '',
  model: auth.isSuperAdmin ? 'deepseek-v4-flash' : '',
  apiKey: '',
})
const busy = ref(false)
const error = ref('')
const notice = ref('')
const revealKey = ref(false)

function formatDate(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}
async function load() {
  try {
    status.value = (await api.get<AiConfigStatus>('/admin/ai-config')).data
    if (status.value.baseUrl) form.baseUrl = status.value.baseUrl
    if (status.value.model) form.model = status.value.model
  } catch (e) { error.value = errorMessage(e) }
}
async function save() {
  busy.value = true; error.value = ''; notice.value = ''
  try {
    await ensureCsrf()
    status.value = (await api.put<AiConfigStatus>('/admin/ai-config', form)).data
    form.apiKey = ''
    revealKey.value = false
    notice.value = '智能体配置已加密保存，项目重启后仍然有效。'
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
async function clearConfig() {
  if (!window.confirm('确定清除已持久化的智能体配置吗？清除后题目拉取将不再可用。')) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    await ensureCsrf()
    await api.delete('/admin/ai-config')
    status.value = { configured: false, baseUrl: '', model: '', configuredAt: null }
    form.apiKey = ''
    notice.value = '已清除持久化的智能体配置。'
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><h2>智能体配置</h2><span class="muted">题目拉取全流程共用</span></div><span :class="['agent-status', status.configured ? 'configured' : 'missing']">{{ status.configured ? '已配置' : '未配置' }}</span></div>
  <p v-if="error" class="error">{{ error }}</p>
  <p v-if="notice" class="card success agent-config-notice">{{ notice }}</p>

  <section class="card agent-config-card form-stack">
    <div class="agent-config-lifetime"><strong>长期有效</strong><span>配置持久化保存，API Key 使用 AES-GCM 加密且不会回显。后端容器重启后无需重新填写。</span></div>
    <div v-if="!auth.isSuperAdmin" class="agent-config-readonly"><strong>只读模式</strong><span>普通管理员可以查看当前配置状态，但只有超级管理员能修改接口、模型和 API Key。</span></div>
    <div class="field"><label>Chat Completions 接口</label><input v-model.trim="form.baseUrl" :disabled="!auth.isSuperAdmin" type="url" maxlength="500" placeholder="https://api.deepseek.com 或 https://api.openai.com/v1" /><small class="muted">可以填写服务根地址，也可以填写完整的 /chat/completions 地址。</small></div>
    <div class="field"><label>模型名称</label><input v-model.trim="form.model" :disabled="!auth.isSuperAdmin" maxlength="200" placeholder="deepseek-v4-flash" /></div>
    <div class="field"><label>API Key</label><div class="agent-key-field"><input v-model="form.apiKey" :disabled="!auth.isSuperAdmin" :type="revealKey ? 'text' : 'password'" maxlength="8000" autocomplete="new-password" :placeholder="status.configured ? '已安全保存，不回显明文' : (auth.isSuperAdmin ? '首次配置必须填写' : '尚未配置')" /><button v-if="auth.isSuperAdmin" class="button secondary" type="button" @click="revealKey = !revealKey">{{ revealKey ? '隐藏' : '显示' }}</button></div><small class="muted">API Key 不会向任何管理员回显明文。</small></div>
    <div v-if="status.configured" class="agent-current-config"><div><span>当前接口</span><strong>{{ status.baseUrl }}</strong></div><div><span>当前模型</span><strong>{{ status.model }}</strong></div><div><span>配置时间</span><strong>{{ formatDate(status.configuredAt) }}</strong></div></div>
    <div class="toolbar"><button v-if="auth.isSuperAdmin" class="button" :disabled="busy || !form.baseUrl || !form.model || (!status.configured && !form.apiKey)" @click="save">{{ busy ? '保存中…' : '保存配置' }}</button><button v-if="auth.isSuperAdmin && status.configured" class="button danger" :disabled="busy" @click="clearConfig">清除配置</button><RouterLink class="button secondary" to="/admin/problem-import">进入题目拉取</RouterLink></div>
  </section>
</template>
