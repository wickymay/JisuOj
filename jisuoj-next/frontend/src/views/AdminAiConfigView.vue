<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ensureCsrf, errorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'
interface Status { configured: boolean; baseUrl: string; model: string; configuredAt: string | null }
interface Provider { provider: string; label: string; defaultBaseUrl: string; defaultModel: string; status: Status }
interface Editor extends Provider { baseUrl: string; model: string; apiKey: string; busy: boolean; reveal: boolean }
const auth = useAuthStore()
const providers = ref<Editor[]>([])
const error = ref('')
const notice = ref('')
const roles: Record<string, string> = { deepseek: '题目拉取、AI 教学；测试数据标准解', qwen: '覆盖计划与数据生成器', glm: '独立解法、输入校验与反例攻击', kimi: '可选的代码与覆盖复核' }
async function load() {
  try {
    const { data } = await api.get<Provider[]>('/admin/ai-config/providers')
    providers.value = data.map(p => ({ ...p, baseUrl: p.status.baseUrl || p.defaultBaseUrl, model: p.status.model || p.defaultModel, apiKey: '', busy: false, reveal: false }))
  } catch (e) { error.value = errorMessage(e) }
}
async function save(p: Editor) {
  p.busy = true; error.value = ''; notice.value = ''
  try {
    await ensureCsrf()
    p.status = (await api.put<Status>(`/admin/ai-config/${p.provider}`, { baseUrl: p.baseUrl, model: p.model, apiKey: p.apiKey })).data
    p.apiKey = ''; p.reveal = false; notice.value = `${p.label} 配置已加密保存，重启后仍然有效。`
  } catch (e) { error.value = errorMessage(e) }
  finally { p.busy = false }
}
async function clear(p: Editor) {
  if (!window.confirm(`确定清除 ${p.label} 配置吗？使用它的功能将暂停。`)) return
  p.busy = true; error.value = ''; notice.value = ''
  try {
    await ensureCsrf(); await api.delete(`/admin/ai-config/${p.provider}`)
    p.status = { configured: false, baseUrl: '', model: '', configuredAt: null }
    p.apiKey = ''; p.reveal = false; notice.value = `已清除 ${p.label} 配置。`
  } catch (e) { error.value = errorMessage(e) }
  finally { p.busy = false }
}
onMounted(load)
</script>
<template>
  <div class="page-head"><div><h2>智能体配置</h2><span class="muted">DeepSeek / Qwen / GLM / Kimi 统一管理</span></div><RouterLink class="button secondary" to="/admin/test-data">生成测试数据</RouterLink></div>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="notice" class="card success" role="status">{{ notice }}</p>
  <div class="card agent-config-lifetime"><strong>长期有效</strong><span>四家服务独立保存接口、模型与 API Key。密钥加密存储且不回显；原有 DeepSeek 配置自动沿用。模型名称可按账户实际权限修改。</span></div>
  <p v-if="!auth.isSuperAdmin" class="muted">当前为只读模式，只有超级管理员可以修改配置。</p>
  <section v-for="p in providers" :key="p.provider" class="card agent-config-card form-stack" style="margin-top:20px">
    <div class="page-head"><div><h2>{{ p.label }}</h2><span class="muted">{{ roles[p.provider] }}</span></div><span :class="['agent-status', p.status.configured ? 'configured' : 'missing']">{{ p.status.configured ? '已配置' : '未配置' }}</span></div>
    <div class="field"><label :for="`${p.provider}-url`">Chat Completions 接口</label><input :id="`${p.provider}-url`" v-model.trim="p.baseUrl" :disabled="!auth.isSuperAdmin || p.busy" type="url" maxlength="500" :placeholder="p.defaultBaseUrl" /><small class="muted">可填写根地址或完整的 /chat/completions 地址；更换地址时需要重新填写密钥。</small></div>
    <div class="field"><label :for="`${p.provider}-model`">模型名称</label><input :id="`${p.provider}-model`" v-model.trim="p.model" :disabled="!auth.isSuperAdmin || p.busy" maxlength="200" :placeholder="p.defaultModel" /></div>
    <div class="field"><label :for="`${p.provider}-key`">API Key</label><div class="agent-key-field"><input :id="`${p.provider}-key`" v-model="p.apiKey" :disabled="!auth.isSuperAdmin || p.busy" :type="p.reveal ? 'text' : 'password'" maxlength="8000" autocomplete="new-password" :placeholder="p.status.configured ? '留空保留已保存的密钥' : '首次配置必须填写'" /><button v-if="auth.isSuperAdmin" class="button secondary" type="button" @click="p.reveal = !p.reveal">{{ p.reveal ? '隐藏' : '显示' }}</button></div></div>
    <small v-if="p.status.configuredAt" class="muted">保存时间：{{ new Date(p.status.configuredAt).toLocaleString() }}</small>
    <div v-if="auth.isSuperAdmin" class="toolbar"><button class="button" :disabled="p.busy || !p.baseUrl || !p.model || (!p.status.configured && !p.apiKey)" @click="save(p)">{{ p.busy ? '处理中…' : `保存 ${p.label}` }}</button><button v-if="p.status.configured" class="button danger" :disabled="p.busy" @click="clear(p)">清除配置</button></div>
  </section>
</template>
