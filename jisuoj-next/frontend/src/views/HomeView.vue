<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, type PageResponse } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { ContestSummary, NewsSummary, Submission } from '../types/domain'

const auth = useAuthStore()
const news = ref<NewsSummary[]>([])
const contests = ref<ContestSummary[]>([])
const practice = ref<Submission[]>([])
const newsPage = ref(1)
const newsTotal = ref(0)
const newsLoading = ref(false)
const groupsOpen = ref(false)
const newsPages = computed(() => Math.max(1, Math.ceil(newsTotal.value / 6)))

const cleanNews = (items: NewsSummary[]) => items
  .filter((item) => item.title !== 'HelloWorld!' && !item.title.startsWith('faqs.'))
  .slice(0, 6)

async function loadNews() {
  newsLoading.value = true
  try {
    const { data } = await api.get<PageResponse<NewsSummary>>('/news', {
      params: { page: newsPage.value, size: 8 },
    })
    news.value = cleanNews(data.items)
    newsTotal.value = data.total
  } finally {
    newsLoading.value = false
  }
}

async function loadHomeData() {
  await Promise.allSettled([
    loadNews(),
    api.get<PageResponse<ContestSummary>>('/contests', { params: { page: 1, size: 5 } })
      .then(({ data }) => { contests.value = data.items }),
    auth.user
      ? api.get<PageResponse<Submission>>('/submissions', {
          params: { page: 1, size: 24, userId: auth.user.userId },
        }).then(({ data }) => {
          const seen = new Set<number>()
          practice.value = data.items.filter((item) => {
            if (seen.has(item.problemId)) return false
            seen.add(item.problemId)
            return true
          }).slice(0, 5)
        })
      : Promise.resolve(),
  ])
}

function changeNewsPage(next: number) {
  newsPage.value = next > newsPages.value ? 1 : next < 1 ? newsPages.value : next
  loadNews()
}

let homeLoaded = false
watch(() => auth.ready, (ready) => {
  if (ready && !homeLoaded) {
    homeLoaded = true
    loadHomeData()
  }
}, { immediate: true })
</script>

<template>
  <div class="jisu-home">
    <section class="jisu-hero" aria-labelledby="jisu-home-title">
      <div class="jisu-hero-particles" aria-hidden="true">
        <span v-for="item in 16" :key="item" class="jisu-hero-particle"></span>
      </div>
      <div class="jisu-hero-copy">
        <div class="jisu-eyebrow">吉林外国语大学 · PROGRAMMING PLATFORM</div>
        <h1 id="jisu-home-title">JisuOj</h1>
        <p class="jisu-subtitle">吉林外国语大学程序设计平台</p>
        <p class="jisu-description">从第一行代码开始，练习算法、参与竞赛，在真实的评测中持续进步。</p>
        <div class="jisu-hero-actions">
          <RouterLink class="jisu-primary-action" to="/problems">开始练习 <span aria-hidden="true">→</span></RouterLink>
          <button class="jisu-secondary-action" type="button" aria-controls="jisu-group-qrs" :aria-expanded="groupsOpen" @click="groupsOpen = !groupsOpen">加入群聊 <span aria-hidden="true">{{ groupsOpen ? '↑' : '↓' }}</span></button>
        </div>
        <div class="jisu-hero-note">
          <span><i aria-hidden="true">✓</i> 在线评测</span>
          <span><i aria-hidden="true">◈</i> 分层训练</span>
          <span><i aria-hidden="true">★</i> 竞赛实践</span>
        </div>
      </div>
      <div class="jisu-hero-visual" aria-hidden="true">
        <div class="jisu-emblem">
          <img class="jisu-emblem-logo" src="/image/jisu-university-logo.png" alt="吉林外国语大学校徽">
        </div>
        <span class="jisu-hero-index">01</span>
      </div>
    </section>

    <Teleport to="body">
      <div v-if="groupsOpen" class="jisu-group-overlay" @click.self="groupsOpen = false">
        <section id="jisu-group-qrs" class="jisu-group-dialog" role="dialog" aria-modal="true" aria-labelledby="jisu-group-title">
          <header><div><span class="jisu-section-kicker">GET CONNECTED</span><h2 id="jisu-group-title">加入群聊</h2></div><button type="button" aria-label="关闭二维码" @click="groupsOpen = false">×</button></header>
          <div class="jisu-group-qrs">
            <article><img src="/image/qq-group.png" alt="New Coders 答疑灌水群二维码"><div><strong>加入 QQ 群</strong><small>New Coders 答疑灌水群</small></div></article>
            <article><img src="/image/wechat-group.png" alt="吉外程序设计社团 C++ 群二维码"><div><strong>加入微信群</strong><small>吉外程序设计社团（C++）</small></div></article>
          </div>
        </section>
      </div>
    </Teleport>

    <section class="jisu-entry-grid" aria-label="平台入口">
      <RouterLink class="jisu-entry-card" to="/problems">
        <span class="jisu-entry-icon" aria-hidden="true">☷</span>
        <span class="jisu-entry-content"><strong>题库练习</strong><small>按知识点和难度训练</small></span>
        <span class="jisu-entry-arrow" aria-hidden="true">›</span>
      </RouterLink>
      <RouterLink class="jisu-entry-card" to="/contests">
        <span class="jisu-entry-icon" aria-hidden="true">♛</span>
        <span class="jisu-entry-content"><strong>竞赛与作业</strong><small>参加比赛，检验学习成果</small></span>
        <span class="jisu-entry-arrow" aria-hidden="true">›</span>
      </RouterLink>
      <RouterLink class="jisu-entry-card" to="/status">
        <span class="jisu-entry-icon" aria-hidden="true">≡</span>
        <span class="jisu-entry-content"><strong>提交状态</strong><small>查看评测结果和运行记录</small></span>
        <span class="jisu-entry-arrow" aria-hidden="true">›</span>
      </RouterLink>
    </section>

    <section id="platform-news" class="jisu-home-section">
      <div class="jisu-section-heading">
        <div><span class="jisu-section-kicker">LATEST</span><h2>平台动态</h2></div>
        <button v-if="newsPages > 1" class="jisu-section-link jisu-link-button" type="button" @click="changeNewsPage(newsPage + 1)">
          {{ newsPage === newsPages ? '回到最新' : '更多动态' }} <span aria-hidden="true">→</span>
        </button>
      </div>
      <div class="jisu-news-panel">
        <div v-if="newsLoading" class="jisu-empty-state">正在加载平台动态…</div>
        <RouterLink v-for="item in news" v-else :key="item.id" class="jisu-news-row" :to="`/news/${item.id}`">
          <span class="jisu-news-title">{{ item.title }}</span>
          <time :datetime="item.publishedAt">{{ item.publishedAt?.slice(0, 10) }}</time>
        </RouterLink>
        <div v-if="!newsLoading && !news.length" class="jisu-empty-state">暂时没有新的平台动态。</div>
      </div>
      <div v-if="newsPages > 1" class="jisu-news-pagination" aria-label="平台动态分页">
        <button type="button" @click="changeNewsPage(newsPage - 1)">上一页</button>
        <span>{{ newsPage }} / {{ newsPages }}</span>
        <button type="button" @click="changeNewsPage(newsPage + 1)">下一页</button>
      </div>
    </section>

    <section class="jisu-content-grid">
      <div class="jisu-home-section">
        <div class="jisu-section-heading">
          <div><span class="jisu-section-kicker">CONTESTS</span><h2>近期比赛</h2></div>
          <RouterLink class="jisu-section-link" to="/contests">全部比赛 <span aria-hidden="true">→</span></RouterLink>
        </div>
        <div class="jisu-list-panel">
          <RouterLink v-for="contest in contests" :key="contest.id" class="jisu-list-row" :to="`/contests/${contest.id}`">
            <span class="jisu-list-main"><strong>{{ contest.title }}</strong><small>{{ contest.startTime?.replace('T', ' ').slice(0, 16) }}</small></span>
            <span class="jisu-list-arrow" aria-hidden="true">›</span>
          </RouterLink>
          <div v-if="!contests.length" class="jisu-empty-state">近期暂无比赛安排。</div>
        </div>
      </div>

      <div class="jisu-home-section">
        <div class="jisu-section-heading">
          <div><span class="jisu-section-kicker">PRACTICE</span><h2>{{ auth.user ? '继续练习' : '开始练习' }}</h2></div>
          <RouterLink class="jisu-section-link" to="/problems">进入题库 <span aria-hidden="true">→</span></RouterLink>
        </div>
        <div class="jisu-list-panel">
          <RouterLink v-for="item in practice" :key="item.problemId" class="jisu-list-row" :to="`/problems/${item.problemId}`">
            <span class="jisu-list-main"><strong>{{ item.problemId }} · {{ item.problemTitle || '题目' }}</strong><small>最近提交 {{ item.submittedAt?.replace('T', ' ').slice(0, 16) }}</small></span>
            <span class="jisu-list-arrow" aria-hidden="true">›</span>
          </RouterLink>
          <div v-if="!practice.length" class="jisu-practice-empty">
            <p>选择一道题目，开始你的下一次思考。</p>
            <RouterLink class="jisu-text-action" to="/problems">浏览题库 <span aria-hidden="true">→</span></RouterLink>
          </div>
        </div>
      </div>
    </section>

    <section class="jisu-bottom-banner">
      <div><span class="jisu-section-kicker">LEARN BY DOING</span><h2>让每一次提交，都成为进步的记录。</h2></div>
      <RouterLink class="jisu-primary-action" to="/problems">进入题库 <span aria-hidden="true">→</span></RouterLink>
    </section>
  </div>
</template>
