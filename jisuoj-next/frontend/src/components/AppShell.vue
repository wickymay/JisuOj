<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const menuOpen = ref(false)
const accountMenuOpen = ref(false)
const accountMenu = ref<HTMLElement | null>(null)
type Theme = 'dark' | 'light' | 'classic'
const theme = ref<Theme>('dark')
const themeMenuOpen = ref(false)
const nav = [
  ['/', '⌂', '主页'], ['/problems', '☷', '问题'], ['/ask-ai', '✦', '问 AI'], ['/contests', '♛', '竞赛&作业'],
  ['/status', '☷', '状态'], ['/rankings', '▥', '排名'], ['/discussions', '●', '讨论'],
  ['/other', '•••', '其他'],
]
const accountName = computed(() => auth.user?.nick || auth.user?.userId || '')
const accountInitial = computed(() => accountName.value.slice(0, 1).toUpperCase())
const themeOptions: { value: Theme; label: string; icon: string }[] = [
  { value: 'light', label: '浅色风格', icon: '◐' },
  { value: 'dark', label: '深色风格', icon: '◑' },
  { value: 'classic', label: '经典风格', icon: '◉' },
]
const currentTheme = computed(() => themeOptions.find(option => option.value === theme.value)?.label ?? '深色风格')
const themeIcon = computed(() => theme.value === 'classic' ? '◉' : theme.value === 'dark' ? '☾' : '☀')

function applyTheme(next: Theme) {
  theme.value = next
  document.documentElement.dataset.theme = next
  document.documentElement.style.colorScheme = next === 'light' ? 'light' : 'dark'
  const browserColor = next === 'light' ? '#e9e5df' : next === 'classic' ? '#08111f' : '#151515'
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', browserColor)
  try { localStorage.setItem('jisuoj-theme', next) } catch { /* Storage may be disabled. */ }
}

function initializeTheme() {
  let saved: string | null = null
  try { saved = localStorage.getItem('jisuoj-theme') } catch { /* Use the system preference. */ }
  const next = saved === 'light' || saved === 'dark' || saved === 'classic'
    ? saved
    : (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark')
  applyTheme(next)
}

function selectTheme(next: Theme) {
  applyTheme(next)
  themeMenuOpen.value = false
  closeAccountMenu()
}

function closeAccountMenu() {
  accountMenuOpen.value = false
}

function handleDocumentClick(event: MouseEvent) {
  if (accountMenu.value && !accountMenu.value.contains(event.target as Node)) closeAccountMenu()
  const target = event.target
  if (!(target instanceof Element) || !target.closest('.theme-picker')) themeMenuOpen.value = false
}

function toggleAccountMenu() {
  accountMenuOpen.value = !accountMenuOpen.value
  themeMenuOpen.value = false
}

function toggleThemeMenu() {
  themeMenuOpen.value = !themeMenuOpen.value
  accountMenuOpen.value = false
}

async function logout() {
  closeAccountMenu()
  await auth.logout()
  await router.push('/')
}

onMounted(() => {
  initializeTheme()
  document.addEventListener('click', handleDocumentClick)
})
onBeforeUnmount(() => document.removeEventListener('click', handleDocumentClick))
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/">JISUOJ</RouterLink>
      <button class="menu-toggle" type="button" aria-label="展开菜单" @click="menuOpen = !menuOpen">☰</button>
      <nav class="nav" :class="{ open: menuOpen }" @click="menuOpen = false">
        <RouterLink v-for="item in nav" :key="item[0]" :to="item[0]">
          <span aria-hidden="true">{{ item[1] }}</span>{{ item[2] }}
        </RouterLink>
      </nav>
      <div class="account">
        <template v-if="auth.user">
          <div ref="accountMenu" class="account-menu">
            <button
              class="account-trigger"
              type="button"
              aria-haspopup="menu"
              :aria-expanded="accountMenuOpen"
              @click.stop="toggleAccountMenu"
            >
              <span class="account-avatar" aria-hidden="true">{{ accountInitial }}</span>
              <span class="account-name">{{ accountName }}</span>
              <span class="account-caret" aria-hidden="true">⌄</span>
            </button>
            <div v-if="accountMenuOpen" class="account-dropdown" role="menu">
              <RouterLink class="account-dropdown-item" role="menuitem" :to="`/users/${auth.user.userId}`" @click="closeAccountMenu">◎ 个人主页</RouterLink>
              <RouterLink class="account-dropdown-item" role="menuitem" :to="{ path: `/users/${auth.user.userId}`, query: { edit: '1' } }" @click="closeAccountMenu">✎ 修改资料</RouterLink>
              <RouterLink class="account-dropdown-item" role="menuitem" to="/todo" @click="closeAccountMenu">✓ 我的待办</RouterLink>
              <RouterLink class="account-dropdown-item" role="menuitem" to="/mail" @click="closeAccountMenu">✉ 站内信</RouterLink>
              <RouterLink v-if="auth.isAdmin" class="account-dropdown-item" role="menuitem" to="/admin" @click="closeAccountMenu">⚙ 管理中心</RouterLink>
              <div class="account-dropdown-theme">
                <span class="account-dropdown-label">界面风格</span>
                <button
                  v-for="option in themeOptions"
                  :key="option.value"
                  class="account-dropdown-item theme-option"
                  :class="{ active: theme === option.value }"
                  role="menuitemradio"
                  :aria-checked="theme === option.value"
                  type="button"
                  @click="selectTheme(option.value)"
                ><span class="theme-option-icon" aria-hidden="true">{{ option.icon }}</span>{{ option.label }}<span v-if="theme === option.value" class="theme-option-check" aria-hidden="true">✓</span></button>
              </div>
              <div class="account-dropdown-divider" role="separator"></div>
              <button class="account-dropdown-item account-dropdown-logout" role="menuitem" type="button" @click="logout">↪ 退出登录</button>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="account-menu theme-picker">
            <button class="account-trigger guest-theme-toggle" type="button" aria-haspopup="menu" :aria-expanded="themeMenuOpen" :aria-label="`选择主题，当前${currentTheme}`" :title="`当前：${currentTheme}`" @click.stop="toggleThemeMenu"><span aria-hidden="true">{{ themeIcon }}</span></button>
            <div v-if="themeMenuOpen" class="account-dropdown theme-dropdown" role="menu" aria-label="界面风格">
              <span class="account-dropdown-label">界面风格</span>
              <button
                v-for="option in themeOptions"
                :key="option.value"
                class="account-dropdown-item theme-option"
                :class="{ active: theme === option.value }"
                role="menuitemradio"
                :aria-checked="theme === option.value"
                type="button"
                @click="selectTheme(option.value)"
              ><span class="theme-option-icon" aria-hidden="true">{{ option.icon }}</span>{{ option.label }}<span v-if="theme === option.value" class="theme-option-check" aria-hidden="true">✓</span></button>
            </div>
          </div>
          <RouterLink to="/login">登录 / 注册</RouterLink>
        </template>
      </div>
    </header>

    <main class="main-content">
      <RouterView />
    </main>

    <footer>JISUOJ · Vue 3 + Spring Boot 重构版</footer>
  </div>
</template>
