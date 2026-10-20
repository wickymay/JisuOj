<script setup lang="ts">
import DOMPurify from 'dompurify'
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage } from '../services/api'

interface NewsDetail {
  id: number
  title: string
  content: string
  author: string
  publishedAt: string
}

const route = useRoute()
const news = ref<NewsDetail | null>(null)
const error = ref('')

onMounted(async () => {
  try {
    news.value = (await api.get<NewsDetail>(`/news/${route.params.id}`)).data
  } catch (cause) {
    error.value = errorMessage(cause)
  }
})
</script>

<template>
  <p v-if="error" class="error">{{ error }}</p>
  <article v-if="news" class="card news-detail">
    <div class="page-head">
      <div><RouterLink to="/">← 返回主页</RouterLink><h1 style="margin-top:12px">{{ news.title }}</h1></div>
      <span class="muted">{{ news.author }} · {{ news.publishedAt?.replace('T', ' ') }}</span>
    </div>
    <div class="prose" v-html="DOMPurify.sanitize(news.content || '')"></div>
  </article>
</template>
