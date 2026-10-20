import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: HomeView, meta: { title: '主页' } },
    { path: '/problems', component: () => import('./views/ProblemsView.vue'), meta: { title: '问题' } },
    { path: '/problems/:id', component: () => import('./views/ProblemView.vue'), meta: { title: '题目' } },
    { path: '/ask-ai', component: () => import('./views/AskAiView.vue'), meta: { title: '问 AI' } },
    { path: '/status', component: () => import('./views/StatusView.vue'), meta: { title: '状态' } },
    { path: '/rankings', component: () => import('./views/RankingsView.vue'), meta: { title: '排名' } },
    { path: '/contests', component: () => import('./views/ContestsView.vue'), meta: { title: '竞赛与作业' } },
    { path: '/contests/:id', component: () => import('./views/ContestView.vue'), meta: { title: '竞赛' } },
    { path: '/discussions', component: () => import('./views/DiscussionsView.vue'), meta: { title: '讨论' } },
    { path: '/discussions/:id', component: () => import('./views/DiscussionsView.vue'), meta: { title: '讨论详情' } },
    { path: '/news/:id', component: () => import('./views/NewsView.vue'), meta: { title: '公告' } },
    { path: '/todo', component: () => import('./views/TodoView.vue'), meta: { title: '我的待办' } },
    { path: '/login', component: () => import('./views/AuthView.vue'), meta: { title: '登录' } },
    { path: '/users/:id', component: () => import('./views/ProfileView.vue'), meta: { title: '用户' } },
    { path: '/mail', component: () => import('./views/MailView.vue'), meta: { title: '站内信' } },
    { path: '/administrators', redirect: '/other/administrators' },
    {
      path: '/other', component: () => import('./layouts/OtherLayout.vue'), meta: { title: '其他' },
      children: [
        { path: '', redirect: '/other/administrators' },
        { path: 'administrators', component: () => import('./views/AdministratorsView.vue'), meta: { title: '管理员列表' } },
      ],
    },
    {
      path: '/admin', component: () => import('./layouts/AdminLayout.vue'), meta: { title: '管理' },
      children: [
        { path: '', component: () => import('./views/AdminView.vue'), meta: { title: '管理首页' } },
        { path: 'ai-config', component: () => import('./views/AdminAiConfigView.vue'), meta: { title: '智能体配置' } },
        { path: 'problem-import', component: () => import('./views/ProblemImportView.vue'), meta: { title: '拉取题目' } },
        { path: 'problems', component: () => import('./views/AdminProblemsView.vue'), meta: { title: '题目管理' } },
        { path: 'problems/new', component: () => import('./views/AdminProblemEditorView.vue'), meta: { title: '新建题目' } },
        { path: 'problems/:id', component: () => import('./views/AdminProblemEditorView.vue'), meta: { title: '编辑题目' } },
        { path: 'contests/new', component: () => import('./views/AdminContestEditorView.vue'), meta: { title: '新建竞赛' } },
        { path: 'contests/:id', component: () => import('./views/AdminContestEditorView.vue'), meta: { title: '编辑竞赛' } },
        { path: 'news', component: () => import('./views/AdminNewsView.vue'), meta: { title: '公告列表' } },
        { path: 'news/new', component: () => import('./views/AdminNewsEditorView.vue'), meta: { title: '新建公告' } },
        { path: 'news/:id', component: () => import('./views/AdminNewsEditorView.vue'), meta: { title: '编辑公告' } },
        { path: 'users', component: () => import('./views/AdminUsersView.vue'), meta: { title: '用户管理' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title || 'JISUOJ')} - JISUOJ`
})

export default router
