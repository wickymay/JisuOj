<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ensureCsrf, errorMessage } from '../services/api'
import type { ProblemDetail } from '../types/domain'
import type { GeneratedData } from '../types/testData'
import TestDataGenerator from '../components/TestDataGenerator.vue'
import { descriptionWithConstraints, manualProblemDraft, problemPublishForm, testDataFingerprint, withGeneratedArtifacts } from '../utils/testDataHandoff'

const route=useRoute();const router=useRouter();const id=computed(()=>route.params.id?Number(route.params.id):null);const jumpId=ref('');const busy=ref(false);const error=ref('')
const form=reactive({title:'',description:'',input:'',output:'',sampleInput:'',sampleOutput:'',hint:'',source:'',timeLimit:1,memoryLimit:128,specialJudge:false,hidden:true})
const constraints = ref(''), referenceSolutionCpp = ref(''), notice = ref('')
const testZip = ref<File | null>(null), generatedResult = ref<GeneratedData | null>(null)
const selectedFingerprint = ref('')
const generationDraft = computed(() => manualProblemDraft(form, constraints.value, referenceSolutionCpp.value))
watch(() => testDataFingerprint(generationDraft.value), fingerprint => {
  if (testZip.value && selectedFingerprint.value && fingerprint !== selectedFingerprint.value) {
    clearGeneratedData()
    notice.value = '题面或约束已修改，已取消采用旧测试数据，请重新生成。'
  }
}, { flush: 'sync' })
function clearGeneratedData() {
  testZip.value = null; generatedResult.value = null; selectedFingerprint.value = ''
}
function useGeneratedData(file: File, result: GeneratedData) {
  if (busy.value || id.value) return
  selectedFingerprint.value = testDataFingerprint(generationDraft.value)
  generatedResult.value = result; testZip.value = file
  notice.value = `已采用 ${result.cases.length} 组测试数据，点击“保存题目与测试数据”一起保存。`
}
async function load(){if(!id.value)return;try{const p=(await api.get<ProblemDetail>(`/problems/${id.value}`)).data;Object.assign(form,p)}catch(e){error.value=errorMessage(e)}}
async function save() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    await ensureCsrf()
    if (id.value) {
      await api.put(`/admin/problems/${id.value}`, form)
      notice.value = '题目已保存。'
    } else {
      let problemId: number
      if (testZip.value && generatedResult.value) {
        if (selectedFingerprint.value !== testDataFingerprint(generationDraft.value)) throw new Error('测试数据与题面不匹配，请重新生成。')
        const payload = problemPublishForm(withGeneratedArtifacts(generationDraft.value, generatedResult.value), testZip.value)
        const { data } = await api.post<{ problemId: number; testCaseCount: number }>('/admin/problem-imports/publish', payload)
        problemId = data.problemId
        notice.value = `题目已新增，并保存 ${data.testCaseCount} 组测试数据。`
      } else {
        const { data } = await api.post<{ id: number }>('/admin/problems', {
          ...form, description: descriptionWithConstraints(form.description, constraints.value),
        })
        problemId = data.id
        notice.value = '题目已新增。'
      }
      clearGeneratedData()
      await router.replace(`/admin/problems/${problemId}`)
      await load()
    }
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
async function hide(){if(!id.value)return;busy.value=true;error.value='';try{await ensureCsrf();await api.delete(`/admin/problems/${id.value}`);form.hidden=true}catch(e){error.value=errorMessage(e)}finally{busy.value=false}}
async function restore(){if(!id.value)return;busy.value=true;error.value='';try{await ensureCsrf();await api.post(`/admin/problems/${id.value}/restore`);form.hidden=false}catch(e){error.value=errorMessage(e)}finally{busy.value=false}}
async function remove(){
  if(!id.value || !window.confirm(`确定彻底删除题目 #${id.value}「${form.title}」吗？题目记录和测试数据会被删除，无法恢复。`))return
  busy.value=true;error.value=''
  try{await ensureCsrf();await api.delete(`/admin/problems/${id.value}/permanent`);await router.replace('/admin/problems')}
  catch(e){error.value=errorMessage(e)}finally{busy.value=false}
}
function jump(){if(Number(jumpId.value)>0)router.push(`/admin/problems/${jumpId.value}`)}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><RouterLink to="/admin/problems">← 题目管理</RouterLink><h1 style="margin-top:12px">{{ id?`编辑题目 #${id}`:'新建题目' }}</h1></div><form class="toolbar" @submit.prevent="jump"><input v-model="jumpId" type="number" placeholder="打开题号" /><button class="button secondary">打开</button></form></div>
  <p v-if="error" class="error">{{ error }}</p>
  <p v-if="notice" class="card" role="status">{{ notice }}</p>
  <fieldset class="problem-editor-fields" :disabled="busy">
    <section class="card form-stack">
      <div class="grid two"><div class="field"><label>标题</label><input v-model="form.title" /></div><div class="field"><label>来源</label><input v-model="form.source" /></div></div>
      <div class="grid three"><div class="field"><label>时间限制（秒）</label><input v-model.number="form.timeLimit" type="number" min="0.001" step="0.1" /></div><div class="field"><label>内存限制（MiB）</label><input v-model.number="form.memoryLimit" type="number" min="1" /></div><label style="align-self:end"><input v-model="form.specialJudge" type="checkbox" style="width:auto" /> 特殊评测</label></div>
      <div class="field"><label>题目描述（HTML）</label><textarea v-model="form.description" rows="12"></textarea></div>
      <div class="grid two"><div class="field"><label>输入说明</label><textarea v-model="form.input"></textarea></div><div class="field"><label>输出说明</label><textarea v-model="form.output"></textarea></div></div>
      <div class="grid two"><div class="field"><label>样例输入</label><textarea v-model="form.sampleInput"></textarea></div><div class="field"><label>样例输出</label><textarea v-model="form.sampleOutput"></textarea></div></div>
      <div class="field"><label>提示</label><textarea v-model="form.hint"></textarea></div>
      <label><input v-model="form.hidden" type="checkbox" style="width:auto" /> 隐藏题目</label>
      <div class="toolbar"><button class="button" :disabled="busy" @click="save">{{ busy ? '保存中…' : (!id && testZip ? '保存题目与测试数据' : '保存') }}</button><button v-if="id&&!form.hidden" class="button danger" :disabled="busy" @click="hide">隐藏</button><button v-if="id&&form.hidden" class="button secondary" :disabled="busy" @click="restore">恢复公开</button><button v-if="id&&form.hidden" class="button danger" :disabled="busy" @click="remove">彻底删除</button><RouterLink v-if="id" class="button secondary" :to="`/problems/${id}`">查看题目</RouterLink></div>
    </section>
    <template v-if="!id">
      <section class="card form-stack" style="margin-top:20px">
        <h2>测试用例生成</h2>
        <p class="muted">填写完整题面与数据范围后，在下方生成并采用测试数据，随后随题目一起保存。</p>
        <div class="field"><label>数据范围与结构约束</label><textarea v-model="constraints" placeholder="例如：1 ≤ n ≤ 2×10^5；所有元素均为正整数。"></textarea></div>
        <details><summary>已有 C++17 参考程序（可选）</summary><textarea v-model="referenceSolutionCpp" rows="8" class="code" spellcheck="false"></textarea></details>
        <p v-if="form.specialJudge" class="error">当前生成流程仅支持普通标准输入输出题。</p>
        <div v-if="testZip && generatedResult" class="toolbar"><span>已采用 {{ generatedResult.cases.length }} 组：{{ testZip.name }}</span><button class="button secondary" type="button" @click="clearGeneratedData">取消采用</button><button class="button" type="button" @click="save">保存题目与测试数据</button></div>
      </section>
      <TestDataGenerator :draft="generationDraft" scope="new-problem" :disabled="busy" allow-apply apply-label="采用测试数据，随题目保存" style="margin-top:20px" @generated="useGeneratedData" />
    </template>
  </fieldset>
</template>

<style scoped>
.problem-editor-fields { border:0; margin:0; padding:0; min-width:0 }
</style>
