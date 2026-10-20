<template>
  <section class="admin-dashboard">
    <div class="admin-dashboard-intro"><span class="admin-kicker">CONTROL PANEL</span><h2>欢迎回来</h2><p>从左侧选择要管理的模块。</p></div>
    <div class="admin-dashboard-grid">
      <RouterLink class="admin-dashboard-card" to="/admin/users"><span class="admin-dashboard-icon">♙</span><div><strong>用户与权限</strong><small>账号、状态和角色权限</small></div><b>›</b></RouterLink>
      <RouterLink class="admin-dashboard-card" to="/admin/problems"><span class="admin-dashboard-icon">?</span><div><strong>题目管理</strong><small>题库浏览、创建和导入</small></div><b>›</b></RouterLink>
      <RouterLink class="admin-dashboard-card" to="/contests"><span class="admin-dashboard-icon">⚑</span><div><strong>竞赛管理</strong><small>竞赛列表、时间和题目编排</small></div><b>›</b></RouterLink>
      <RouterLink class="admin-dashboard-card" to="/admin/news"><span class="admin-dashboard-icon">▣</span><div><strong>公告管理</strong><small>发布和维护平台动态</small></div><b>›</b></RouterLink>
      <RouterLink class="admin-dashboard-card" to="/status"><span class="admin-dashboard-icon">◉</span><div><strong>评测监控</strong><small>查看提交和评测结果</small></div><b>›</b></RouterLink>
      <RouterLink class="admin-dashboard-card" to="/admin/ai-config"><span class="admin-dashboard-icon">◇</span><div><strong>智能体配置</strong><small>持久化的接口、模型和 API Key</small></div><b>›</b></RouterLink>
      <RouterLink class="admin-dashboard-card" to="/admin/problem-import"><span class="admin-dashboard-icon">↥</span><div><strong>智能拉题</strong><small>从外部来源整理题面</small></div><b>›</b></RouterLink>
    </div>
  </section>
</template>
