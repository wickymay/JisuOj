<script setup lang="ts">
const props = defineProps<{ page: number; size: number; total: number }>()
const emit = defineEmits<{ change: [page: number] }>()
const pages = () => Math.max(1, Math.ceil(props.total / props.size))
</script>

<template>
  <div class="pager">
    <button class="button secondary" :disabled="page <= 1" @click="emit('change', 1)">首页</button>
    <button class="button secondary" :disabled="page <= 1" @click="emit('change', page - 1)">上一页</button>
    <span>第 {{ page }} / {{ pages() }} 页</span>
    <button class="button secondary" :disabled="page >= pages()" @click="emit('change', page + 1)">下一页</button>
  </div>
</template>
