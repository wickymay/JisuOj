<script setup lang="ts">
defineProps<{ result?: string | null }>()

function code(value?: string | null) {
  const text = (value || '等待').toUpperCase()
  const map: Record<string, string> = {
    '正确': 'AC', '答案正确': 'AC', 'ACCEPTED': 'AC',
    '答案错误': 'WA', 'WRONG ANSWER': 'WA',
    '运行超时': 'TLE', 'TIME LIMIT EXCEEDED': 'TLE',
    '内存超限': 'MLE', 'MEMORY LIMIT EXCEEDED': 'MLE',
    '编译错误': 'CE', 'COMPILE ERROR': 'CE',
    '运行错误': 'RE', 'RUNTIME ERROR': 'RE',
    '输出超限': 'OLE', 'OUTPUT LIMIT EXCEEDED': 'OLE',
    '格式错误': 'PE', 'PRESENTATION ERROR': 'PE',
    '等待': 'PENDING', '等待重判': 'PENDING', '编译中': 'JUDGING', '运行并评判': 'JUDGING',
  }
  return map[text] || (text.includes('ACCEPT') ? 'AC' : text)
}

function tone(value: string) {
  if (value === 'AC') return 'accepted'
  if (['PENDING', 'JUDGING', 'QUEUING'].includes(value)) return 'pending'
  if (value === 'CE') return 'compile'
  return 'rejected'
}
</script>

<template>
  <strong class="result-code" :class="tone(code(result))">{{ code(result) }}</strong>
</template>
