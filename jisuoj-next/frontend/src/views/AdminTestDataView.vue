<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage } from '../services/api'
import type { ProblemDetail, ProblemDraft } from '../types/domain'
import TestDataGenerator from '../components/TestDataGenerator.vue'
const route = useRoute()
const problemId = ref(''), error = ref(''), loading = ref(false)
const draft = reactive<ProblemDraft>({ title: '', description: '', input: '', output: '', constraints: '', specialProperties: [],
  sampleInput: '', sampleOutput: '', hint: '', timeLimit: 1, memoryLimit: 128, source: '', sourceUrl: '', sourceType: 'manual',
  sourceRef: '', problemType: 'standard', spj: 0, testGroupCount: 10, testInput: '', testOutput: '',
  referenceSolutionCpp: '', validatorCpp: '', generatorCpp: '', warnings: [], hidden: true })
async function loadProblem() {
  if (!/^\d+$/.test(problemId.value) || Number(problemId.value) < 1) { error.value = '请输入有效题号'; return }
  loading.value = true; error.value = ''
  try {
    const p = (await api.get<ProblemDetail>(`/problems/${problemId.value}`)).data
    Object.assign(draft, { title: p.title, description: p.description, input: p.input, output: p.output,
      sampleInput: p.sampleInput, sampleOutput: p.sampleOutput, timeLimit: p.timeLimit, memoryLimit: p.memoryLimit,
      hint: p.hint, constraints: '', referenceSolutionCpp: '', spj: p.specialJudge ? 1 : 0 })
  } catch (e) { error.value = errorMessage(e) }
  finally { loading.value = false }
}
onMounted(() => { if (typeof route.query.problemId === 'string') { problemId.value = route.query.problemId; void loadProblem() } })
</script>
<template>
  <div class="page-head"><div><h2>测试数据生成</h2><span class="muted">填写题面或载入站内题目，生成经过执行验证的数据包。</span></div><RouterLink to="/admin/ai-config">配置国内模型</RouterLink></div>
  <p v-if="error" class="error">{{ error }}</p>
  <section class="card form-stack" style="margin-bottom:20px">
    <form class="toolbar" @submit.prevent="loadProblem"><input v-model="problemId" type="number" min="1" placeholder="可选：载入站内题号" /><button class="button secondary" :disabled="loading">{{ loading ? '载入中…' : '载入题目' }}</button></form>
    <div class="field"><label>题目标题</label><input v-model="draft.title" maxlength="200" /></div>
    <div class="field"><label>题目描述</label><textarea v-model="draft.description" rows="8"></textarea></div>
    <div class="grid two"><div class="field"><label>输入说明</label><textarea v-model="draft.input"></textarea></div><div class="field"><label>输出说明</label><textarea v-model="draft.output"></textarea></div></div>
    <div class="field"><label>数据范围与结构约束</label><textarea v-model="draft.constraints" placeholder="请明确所有数值范围和结构性质；载入题目后请从题面补充。"></textarea></div>
    <div class="grid two"><div class="field"><label>样例输入</label><textarea v-model="draft.sampleInput"></textarea></div><div class="field"><label>样例输出</label><textarea v-model="draft.sampleOutput"></textarea></div></div>
    <div class="grid two"><div class="field"><label>时间限制（秒）</label><input v-model.number="draft.timeLimit" type="number" min="0.001" max="300" step="0.1" /></div><div class="field"><label>内存限制（MiB）</label><input v-model.number="draft.memoryLimit" type="number" min="1" max="4096" /></div></div>
    <details><summary>已有参考程序（可选）</summary><textarea v-model="draft.referenceSolutionCpp" rows="10" class="code" spellcheck="false"></textarea></details>
    <p v-if="draft.spj" class="error">当前题目使用特殊判题，暂不支持自动生成；请使用人工验证的数据。</p>
  </section>
  <TestDataGenerator :draft="draft" />
</template>
