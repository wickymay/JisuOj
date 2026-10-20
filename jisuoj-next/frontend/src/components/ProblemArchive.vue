<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { ProblemSummary } from '../types/domain'
import type { ArchiveScene } from './archive/ArchiveScene'
import { clampIndex } from './archive/archive-motion'

const props = defineProps<{ rows: ProblemSummary[]; selectedIndex: number; loading: boolean; page: number; pages: number; total: number; offset: number }>()
const emit = defineEmits<{ select: [index: number]; page: [direction: number] }>()
const router = useRouter()
const stage = ref<HTMLElement | null>(null)
const canvasHost = ref<HTMLElement | null>(null)
const motionEnabled = ref(true)
const reducedMotion = ref(false)
const sceneState = ref<'loading' | 'ready' | 'fallback'>('loading')
const animate = computed(() => motionEnabled.value && !reducedMotion.value)
const selected = computed(() => props.rows[props.selectedIndex])
const atStart = computed(() => props.page === 1 && props.selectedIndex === 0)
const atEnd = computed(() => props.page === props.pages && props.selectedIndex >= props.rows.length - 1)
let scene: ArchiveScene | undefined
let disposed = false
let motionQuery: MediaQueryList | undefined
let lastWheel = 0
let lastStep = 0
let wheelDelta = 0
let touchStart: { x: number; y: number } | undefined
let ignoreClickUntil = 0

function select(index: number) {
  if (!props.loading && props.rows.length) emit('select', clampIndex(index, props.rows.length))
}
function step(direction: number, crossPage = true) {
  if (props.loading || !props.rows.length) return
  const next = props.selectedIndex + direction
  if (next >= 0 && next < props.rows.length) select(next)
  else if (crossPage && ((direction < 0 && props.page > 1) || (direction > 0 && props.page < props.pages))) emit('page', direction)
}
function onWheel(event: WheelEvent) {
  if (event.ctrlKey || event.metaKey || !props.rows.length) return
  event.preventDefault()
  if (props.loading) return
  const now = performance.now(), newGesture = now - lastWheel > 260
  const raw = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
  if (newGesture || Math.sign(raw) !== Math.sign(wheelDelta)) wheelDelta = 0
  wheelDelta += raw * (event.deltaMode === 1 ? 30 : event.deltaMode === 2 ? 200 : 1)
  if (Math.abs(wheelDelta) >= 28 && now - lastStep > 125) {
    step(Math.sign(wheelDelta), newGesture); wheelDelta = 0; lastStep = now
  }
  lastWheel = now
}
function onKey(event: KeyboardEvent) {
  if (event.target !== stage.value || !props.rows.length) return
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End', 'PageUp', 'PageDown', 'Enter'].includes(event.key)) return
  event.preventDefault()
  if (['ArrowRight', 'ArrowDown'].includes(event.key)) step(1)
  if (['ArrowLeft', 'ArrowUp'].includes(event.key)) step(-1)
  if (event.key === 'Home') select(0)
  if (event.key === 'End') select(props.rows.length - 1)
  if (event.key === 'PageUp' && props.page > 1 && !props.loading) emit('page', -1)
  if (event.key === 'PageDown' && props.page < props.pages && !props.loading) emit('page', 1)
  if (event.key === 'Enter' && selected.value && !props.loading) void router.push(`/problems/${selected.value.id}`)
}
function onClick(event: MouseEvent) {
  if (props.loading || performance.now() < ignoreClickUntil || (event.target as HTMLElement).closest('button, a')) return
  stage.value?.focus({ preventScroll: true }); scene?.pick(event.clientX, event.clientY)
}
function onTouchStart(event: TouchEvent) {
  const touch = event.touches[0]
  touchStart = event.touches.length === 1 && touch ? { x: touch.clientX, y: touch.clientY } : undefined
}
function onTouchEnd(event: TouchEvent) {
  const touch = event.changedTouches[0]
  if (!touchStart || !touch) return
  const dx = touch.clientX - touchStart.x, dy = touch.clientY - touchStart.y
  touchStart = undefined
  if (Math.max(Math.abs(dx), Math.abs(dy)) > 36) {
    step(Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? 1 : -1) : (dy < 0 ? 1 : -1))
    ignoreClickUntil = performance.now() + 400
  }
}
function syncMotion(event: MediaQueryListEvent) { reducedMotion.value = event.matches }
function fallback() { sceneState.value = 'fallback'; scene?.dispose(); scene = undefined }
watch(() => props.rows, () => { scene?.setRows(props.rows, props.selectedIndex); lastWheel = performance.now(); wheelDelta = 0 })
watch(() => props.selectedIndex, (index) => scene?.setSelection(index, props.rows[index]))
watch(animate, (enabled) => scene?.setMotion(enabled))
onMounted(async () => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  motionQuery.addEventListener('change', syncMotion)
  stage.value?.addEventListener('wheel', onWheel, { passive: false })
  try {
    const { ArchiveScene } = await import('./archive/ArchiveScene')
    if (disposed || !canvasHost.value) return
    scene = new ArchiveScene(canvasHost.value, { select, unavailable: fallback })
    scene.setRows(props.rows, props.selectedIndex); scene.setMotion(animate.value)
    sceneState.value = 'ready'
  } catch { if (!disposed) fallback() }
})
onBeforeUnmount(() => {
  disposed = true; scene?.dispose()
  motionQuery?.removeEventListener('change', syncMotion)
  stage.value?.removeEventListener('wheel', onWheel)
})
</script>

<template>
  <section class="archive-browser" :class="{ 'motion-paused': !animate }">
    <header class="archive-browser-top">
      <div class="archive-terminal-brand"><svg viewBox="0 0 40 40" fill="none" aria-hidden="true"><path d="m20 2 16 9v18L20 38 4 29V11Z M20 10v20M11 15l9-5 9 5M11 25l9 5 9-5M11 15v10M29 15v10" /></svg><div><strong>题目档案库</strong><span class="archive-mono">JISU / KNOWLEDGE REPOSITORY</span></div></div>
      <button type="button" class="archive-motion-toggle" :aria-pressed="!motionEnabled" :disabled="reducedMotion" @click="motionEnabled = !motionEnabled"><i :class="{ paused: !animate }"></i>{{ !animate ? '静态模式' : '动态模式' }}</button>
    </header>
    <div ref="stage" class="archive-stage" tabindex="0" role="region" aria-label="三维题目档案，滚轮或方向键切换，回车打开" @keydown="onKey" @click="onClick" @pointermove="$event.pointerType === 'mouse' && scene?.point($event.clientX, $event.clientY)" @pointerleave="scene?.resetPoint()" @touchstart.passive="onTouchStart" @touchend.passive="onTouchEnd" @touchcancel="touchStart = undefined">
      <div ref="canvasHost" class="archive-canvas" :class="{ 'is-loading': loading, 'is-hidden': sceneState === 'fallback' }"></div>
      <div class="archive-scene-caption" aria-hidden="true"><span>ARCHIVE ARRAY</span><small>01 — {{ String(pages).padStart(2, '0') }} / 题目存储阵列</small></div>
      <div v-if="loading && !rows.length" class="archive-stage-loading" role="status"><i></i><p>正在读取档案…</p></div>
      <slot v-else-if="!rows.length" name="empty"></slot>
      <div v-else-if="sceneState === 'fallback'" class="archive-fallback"><span>当前设备使用轻量档案视图</span><button v-for="(problem, index) in rows" :key="problem.id" :class="{ selected: index === selectedIndex }" :aria-pressed="index === selectedIndex" :disabled="loading" @click="select(index)"><small>#{{ problem.id }}</small><strong>{{ problem.title }}</strong><span>{{ problem.acceptanceRate.toFixed(1) }}% 通过率</span></button></div>
      <div v-else-if="selected" class="archive-floating-id" aria-hidden="true"><span>SELECTED RECORD</span><strong :key="selected.id">{{ selected.id }}</strong><small><i></i>{{ selected.userStatus === 'AC' ? 'VERIFIED / 已通过' : 'READY TO ACCESS / 可访问' }}</small></div>
      <div class="archive-scene-coordinate archive-mono" aria-hidden="true"><span>J / OJ — {{ String(page).padStart(2, '0') }}</span><span>{{ sceneState === 'loading' ? 'INITIALIZING 3D' : sceneState === 'fallback' ? 'COMPATIBILITY VIEW' : 'SPATIAL INDEX ONLINE' }}</span></div>
    </div>
    <div class="archive-navigation">
      <div class="archive-selection-label" aria-live="polite" aria-atomic="true"><span>{{ selected ? `当前选中 #${selected.id}` : '等待选择' }}</span><span class="archive-mono">{{ rows.length ? String(offset + selectedIndex + 1).padStart(3, '0') : '000' }} <i>/ {{ String(total).padStart(3, '0') }}</i></span></div>
      <div class="archive-index-strip" role="group" aria-label="本页题目索引"><button v-for="(problem, index) in rows" :key="problem.id" type="button" :class="{ active: selectedIndex === index, solved: problem.userStatus === 'AC' }" :aria-label="`选择题目 ${problem.id}：${problem.title}`" :aria-pressed="selectedIndex === index" :disabled="loading" @click="select(index)"><i></i></button></div>
      <div class="archive-controls"><span class="archive-scroll-hint">滚轮 / 滑动检索 <b>·</b> 方向键切换 <b>·</b> ENTER 访问</span><div><button type="button" aria-label="上一道题" :disabled="loading || !rows.length || atStart" @click="step(-1)">←</button><button type="button" aria-label="下一道题" :disabled="loading || !rows.length || atEnd" @click="step(1)">→</button></div></div>
    </div>
    <footer class="archive-page-navigation"><span>分区 <strong class="archive-mono">{{ String(page).padStart(2, '0') }} / {{ String(pages).padStart(2, '0') }}</strong></span><div><button type="button" :disabled="page <= 1 || loading" @click="emit('page', -1)">上一组</button><button type="button" :disabled="page >= pages || loading" @click="emit('page', 1)">下一组 ↗</button></div></footer>
  </section>
</template>
