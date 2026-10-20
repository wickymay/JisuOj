<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ensureCsrf, errorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'

interface AdministratorRow {
  userId: string
  nick: string
  school: string
  level: 'SUPER_ADMIN' | 'ADMIN'
  registeredAt: string | null
  lastActiveAt: string | null
}

const auth = useAuthStore()
const administrators = ref<AdministratorRow[]>([])
const userId = ref('')
const busy = ref(false)
const loading = ref(true)
const error = ref('')
const notice = ref('')
const failedAvatars = ref(new Set<string>())
const superAdminCount = computed(() => administrators.value.filter((row) => row.level === 'SUPER_ADMIN').length)
const regularAdminCount = computed(() => administrators.value.filter((row) => row.level === 'ADMIN').length)

function formatDate(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function avatarUrl(userId: string) {
  return `/api/users/${encodeURIComponent(userId)}/avatar`
}

function markAvatarFailed(userId: string) {
  failedAvatars.value.add(userId)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    administrators.value = (await api.get<AdministratorRow[]>('/administrators')).data
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function addAdministrator() {
  if (!userId.value.trim()) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await ensureCsrf()
    await api.post('/administrators', { userId: userId.value.trim() })
    notice.value = `已将 ${userId.value.trim()} 添加为普通管理员。该用户重新登录后权限生效。`
    userId.value = ''
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

async function removeAdministrator(row: AdministratorRow) {
  if (!window.confirm(`确定移除管理员 ${row.nick || row.userId}（${row.userId}）吗？用户账号本身不会被删除。`)) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await ensureCsrf()
    await api.delete(`/administrators/${encodeURIComponent(row.userId)}`)
    notice.value = `已移除 ${row.userId} 的普通管理员权限。`
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="administrator-page">
    <div class="page-head">
      <div><span class="jisu-section-kicker">TEAM</span><h1>管理员列表</h1><p class="muted">查看当前负责维护网站的管理员账号。</p></div>
      <span class="administrator-count">超级管理员 {{ superAdminCount }} 位 · 普通管理员 {{ regularAdminCount }} 位</span>
    </div>

    <form v-if="auth.isSuperAdmin" class="card administrator-add" @submit.prevent="addAdministrator">
      <div><h2>添加普通管理员</h2><p class="muted">填写已经注册的用户名；添加后不会影响该用户的其他权限。</p></div>
      <div class="administrator-add-controls"><input v-model.trim="userId" maxlength="48" pattern="[A-Za-z0-9_]{3,48}" placeholder="输入用户名" aria-label="用户名"><button class="button" :disabled="busy || !userId">添加管理员</button></div>
    </form>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="card success administrator-notice">{{ notice }}</p>
    <div v-if="loading" class="card loading">正在加载管理员列表…</div>
    <div v-else class="administrator-grid">
      <article v-for="row in administrators" :key="row.userId" class="card administrator-card">
        <span class="administrator-avatar">
          <img v-if="!failedAvatars.has(row.userId)" :src="avatarUrl(row.userId)" :alt="`${row.nick || row.userId}的头像`" @error="markAvatarFailed(row.userId)">
          <span v-else>{{ (row.nick || row.userId).slice(0, 1).toUpperCase() }}</span>
        </span>
        <div class="administrator-identity"><RouterLink class="administrator-name" :to="`/users/${row.userId}`">{{ row.nick || row.userId }}</RouterLink><p class="muted">@{{ row.userId }}</p></div>
        <dl><div><dt>学校</dt><dd>{{ row.school || '—' }}</dd></div><div><dt>最近活跃</dt><dd>{{ formatDate(row.lastActiveAt) }}</dd></div></dl>
        <span :class="['administrator-level', row.level === 'SUPER_ADMIN' ? 'super' : 'regular']">{{ row.level === 'SUPER_ADMIN' ? '超级管理员' : '普通管理员' }}</span>
        <button v-if="auth.isSuperAdmin && row.level === 'ADMIN'" class="button danger" :disabled="busy" @click="removeAdministrator(row)">移除管理员权限</button>
      </article>
      <div v-if="!administrators.length" class="card empty">暂无管理员。</div>
    </div>
  </section>
</template>
