<script setup lang="ts">
import { ref } from 'vue'

const menuOpen = ref(false)
</script>

<template>
  <div class="other-layout">
    <button class="other-menu-toggle" type="button" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">☰ 其他功能</button>
    <aside class="other-sidebar" :class="{ open: menuOpen }">
      <div class="other-sidebar-title"><span>•••</span><div><strong>其他</strong><small>MORE TOOLS</small></div></div>
      <nav>
        <h2>站点信息</h2>
        <RouterLink to="/other/administrators" active-class="active" @click="menuOpen = false"><span aria-hidden="true">♙</span>管理员列表</RouterLink>
      </nav>
    </aside>
    <main class="other-main"><RouterView /></main>
  </div>
</template>
