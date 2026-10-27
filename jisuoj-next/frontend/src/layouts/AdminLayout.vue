<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAuthStore } from '../stores/auth'

interface MenuItem { to: string; label: string; icon: string; roles?: string[] }
interface MenuGroup { label: string; icon: string; items: MenuItem[] }

const auth = useAuthStore()
const menuOpen = ref(false)
const fullAdmin = computed(() => !!auth.user?.roles.some((role) => ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN'].includes(role)))

const groups = computed<MenuGroup[]>(() => {
  const all = (roles?: string[]) => !roles || fullAdmin.value || roles.some((role) => auth.user?.roles.includes(role))
  const source: MenuGroup[] = [
    { label: '公告管理', icon: '▣', items: [
      { to: '/admin/news', label: '公告列表', icon: '☷', roles: ['ROLE_ADMIN'] },
      { to: '/admin/news/new', label: '发布公告', icon: '+', roles: ['ROLE_ADMIN'] },
    ] },
    { label: '用户管理', icon: '♙', items: [
      { to: '/admin/users', label: '用户与权限', icon: '☷', roles: ['ROLE_ADMIN', 'ROLE_USER_MANAGER', 'ROLE_PASSWORD_SETTER'] },
      { to: '/other/administrators', label: '管理员列表', icon: '♛', roles: ['ROLE_ADMIN'] },
    ] },
    { label: '题目管理', icon: '?', items: [
      { to: '/problems', label: '题目列表', icon: '☷', roles: ['ROLE_ADMIN', 'ROLE_PROBLEM_EDITOR', 'ROLE_CONTEST_CREATOR'] },
      { to: '/admin/problems', label: '管理题目', icon: '▤', roles: ['ROLE_ADMIN', 'ROLE_PROBLEM_EDITOR'] },
      { to: '/admin/problems/new', label: '新增题目', icon: '+', roles: ['ROLE_ADMIN', 'ROLE_PROBLEM_EDITOR'] },
      { to: '/admin/problem-import', label: '拉取 / 导入题目', icon: '↥', roles: ['ROLE_ADMIN', 'ROLE_PROBLEM_EDITOR'] },
      { to: '/admin/test-data', label: '生成测试数据', icon: '◇', roles: ['ROLE_ADMIN', 'ROLE_PROBLEM_EDITOR'] },
    ] },
    { label: '竞赛管理', icon: '⚑', items: [
      { to: '/contests', label: '竞赛列表', icon: '☷', roles: ['ROLE_ADMIN', 'ROLE_CONTEST_CREATOR'] },
      { to: '/admin/contests/new', label: '新增竞赛', icon: '+', roles: ['ROLE_ADMIN', 'ROLE_CONTEST_CREATOR'] },
    ] },
    { label: '系统功能', icon: '⚙', items: [
      { to: '/admin/ai-config', label: '智能体配置', icon: '◇', roles: ['ROLE_ADMIN'] },
      { to: '/status', label: '评测状态', icon: '◉', roles: ['ROLE_ADMIN'] },
      { to: '/rankings', label: '排行榜', icon: '▥', roles: ['ROLE_ADMIN'] },
    ] },
  ]
  return source.map((group) => ({ ...group, items: group.items.filter((item) => all(item.roles)) })).filter((group) => group.items.length)
})

const allowed = computed(() => auth.isAdmin)
</script>

<template>
  <div class="admin-layout">
    <button class="admin-menu-toggle" type="button" @click="menuOpen = !menuOpen">☰ <span>管理菜单</span></button>
    <div v-if="menuOpen" class="admin-menu-backdrop" @click="menuOpen = false"></div>
    <aside class="admin-sidebar" :class="{ open: menuOpen }">
      <div class="admin-sidebar-brand"><span class="admin-sidebar-mark">J</span><div><strong>管理中心</strong><small>JISUOJ ADMIN</small></div></div>
      <div class="admin-sidebar-top">
        <RouterLink class="admin-sidebar-home" to="/admin" exact-active-class="active" @click="menuOpen = false"><span>★</span>管理首页</RouterLink>
        <RouterLink class="admin-sidebar-home" to="/status" active-class="active" @click="menuOpen = false"><span>◉</span>查看评测</RouterLink>
      </div>
      <nav class="admin-sidebar-nav">
        <section v-for="group in groups" :key="group.label" class="admin-menu-group">
          <h3><span>{{ group.icon }}</span>{{ group.label }}</h3>
          <RouterLink v-for="item in group.items" :key="item.to" :to="item.to" active-class="active" @click="menuOpen = false"><span>{{ item.icon }}</span>{{ item.label }}</RouterLink>
        </section>
      </nav>
      <div v-if="auth.isAdmin" class="admin-sidebar-links">
        <a href="https://github.com/zhblue/hustoj/" target="_blank" rel="noreferrer">⌘ HUSTOJ 项目</a>
        <a href="http://hustoj.com" target="_blank" rel="noreferrer">? 管理帮助</a>
      </div>
    </aside>

    <main class="admin-main">
      <div class="admin-main-header"><div><span class="admin-kicker">ADMINISTRATION</span><h1>管理中心</h1></div><RouterLink v-if="auth.user" class="admin-current-user" :to="`/users/${auth.user.userId}`">{{ auth.user.nick || auth.user.userId }}</RouterLink></div>
      <p v-if="auth.ready && !allowed" class="error">当前账号没有管理权限。</p>
      <div v-else-if="!auth.ready" class="card admin-loading">正在检查管理员权限…</div>
      <RouterView v-else />
    </main>
  </div>
</template>
