<script setup lang="ts">
import { onMounted } from 'vue'
import AppShell from './components/AppShell.vue'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
onMounted(() => auth.initialize())
</script>

<template>
  <AppShell />
</template>
