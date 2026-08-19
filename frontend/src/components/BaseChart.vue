<script setup lang="ts">
import * as echarts from 'echarts'
import { nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * ECharts 统一封装。
 * gauge 不用 ECharts 内部过渡（会直接跳到终值），改为逐帧把指针从 0 播到当前值。
 */
const props = defineProps<{
  option: echarts.EChartsOption
  height?: string
  /** 变化时强制从 0 再播一次（切 tab / 刷新） */
  playKey?: number | string
}>()

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null
let visibilityObserver: IntersectionObserver | null = null
let rafId = 0
let sizeWaitTimer: ReturnType<typeof setInterval> | null = null
let visible = false
let lastGaugeSig = ''
let playing = false
let playGen = 0

const GAUGE_ANIM_MS = 2400
const DARK_TEXT = '#94a3c7'

const baseOption: echarts.EChartsOption = {
  color: ['#22d3ee', '#6366f1', '#34d399', '#fbbf24', '#f87171', '#60a5fa', '#a78bfa', '#f472b6'],
  textStyle: { color: DARK_TEXT },
  tooltip: {
    backgroundColor: 'rgba(8,16,38,0.92)',
    borderColor: 'rgba(34,211,238,0.4)',
    textStyle: { color: '#e6f1ff' }
  },
  grid: { left: 12, right: 16, top: 30, bottom: 8, containLabel: true }
}

function hasSize() {
  return !!el.value && el.value.clientWidth >= 8 && el.value.clientHeight >= 8
}

function ensureChart() {
  if (!el.value) return null
  if (!chart) chart = echarts.init(el.value, undefined, { renderer: 'canvas' })
  return chart
}

function cancelPlay() {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = 0
  }
  if (sizeWaitTimer) {
    clearInterval(sizeWaitTimer)
    sizeWaitTimer = null
  }
  playing = false
}

function hasGaugeSeries(option: echarts.EChartsOption): boolean {
  const series = (option as any).series
  return Array.isArray(series) && series.some((s: any) => s?.type === 'gauge')
}

function gaugeSignature(option: echarts.EChartsOption): string {
  const series = (option as any).series
  if (!Array.isArray(series)) return ''
  return series
    .filter((s: any) => s?.type === 'gauge')
    .map((s: any) =>
      (Array.isArray(s.data) ? s.data : [])
        .map((d: any) => `${d?.name ?? ''}:${Number(d?.value ?? 0)}`)
        .join(',')
    )
    .join('|')
}

function gaugeValues(option: echarts.EChartsOption): number[][] {
  const series = (option as any).series
  if (!Array.isArray(series)) return []
  return series.map((s: any) =>
    s?.type === 'gauge' && Array.isArray(s.data) ? s.data.map((d: any) => Number(d?.value ?? 0)) : []
  )
}

function withGaugeValues(option: echarts.EChartsOption, values: number[][]): echarts.EChartsOption {
  const series = ((option as any).series || []).map((s: any, i: number) => {
    if (s?.type !== 'gauge' || !Array.isArray(s.data)) return s
    const row = values[i] || []
    return {
      ...s,
      data: s.data.map((d: any, j: number) => ({ ...d, value: row[j] ?? 0 }))
    }
  })
  return { ...option, series } as echarts.EChartsOption
}

function zeroValues(option: echarts.EChartsOption): number[][] {
  return gaugeValues(option).map((row) => row.map(() => 0))
}

function easeInOutCubic(t: number) {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
}

function render() {
  const inst = ensureChart()
  if (!inst) return
  inst.setOption(baseOption, false)
  inst.setOption(props.option, true)
}

function waitForSize(): Promise<boolean> {
  if (hasSize()) return Promise.resolve(true)
  return new Promise((resolve) => {
    if (sizeWaitTimer) clearInterval(sizeWaitTimer)
    const start = Date.now()
    sizeWaitTimer = setInterval(() => {
      if (hasSize()) {
        clearInterval(sizeWaitTimer!)
        sizeWaitTimer = null
        resolve(true)
      } else if (Date.now() - start > 4000) {
        clearInterval(sizeWaitTimer!)
        sizeWaitTimer = null
        resolve(false)
      }
    }, 50)
  })
}

function paintGauge(option: echarts.EChartsOption, notMerge: boolean) {
  const inst = ensureChart()
  if (!inst) return
  inst.setOption(
    {
      ...option,
      animation: false,
      animationDuration: 0,
      animationDurationUpdate: 0
    },
    notMerge
  )
}

function playGaugeFromZero() {
  if (!hasGaugeSeries(props.option)) {
    render()
    return
  }
  const gen = ++playGen
  cancelPlay()
  playing = true
  const optionSnapshot = props.option
  lastGaugeSig = gaugeSignature(optionSnapshot)

  void (async () => {
    const ready = await waitForSize()
    if (gen !== playGen || !ready || !el.value) {
      if (gen === playGen) playing = false
      return
    }
    const inst = ensureChart()
    if (!inst) {
      if (gen === playGen) playing = false
      return
    }
    inst.resize()

    const targets = gaugeValues(optionSnapshot)
    inst.setOption(baseOption, false)
    paintGauge(withGaugeValues(optionSnapshot, zeroValues(optionSnapshot)), true)
    inst.resize()

    await nextTick()
    await new Promise<void>((r) => requestAnimationFrame(() => requestAnimationFrame(() => r())))
    if (gen !== playGen) return

    const start = performance.now()
    const tick = (now: number) => {
      if (gen !== playGen || !chart) return
      const t = Math.min(1, (now - start) / GAUGE_ANIM_MS)
      const e = easeInOutCubic(t)
      const frame = targets.map((row) => row.map((v) => Math.round(v * e * 10) / 10))
      paintGauge(withGaugeValues(optionSnapshot, frame), false)
      if (t < 1) {
        rafId = requestAnimationFrame(tick)
      } else {
        rafId = 0
        playing = false
        const latest = gaugeSignature(props.option)
        if (latest !== lastGaugeSig) playGaugeFromZero()
        else paintGauge(props.option, false)
      }
    }
    rafId = requestAnimationFrame(tick)
  })()
}

onMounted(() => {
  if (!el.value) return
  if (!hasGaugeSeries(props.option)) render()

  resizeObserver = new ResizeObserver(() => {
    if (!hasSize()) return
    chart?.resize()
    if (hasGaugeSeries(props.option) && !playing && !chart) playGaugeFromZero()
  })
  resizeObserver.observe(el.value)

  visibilityObserver = new IntersectionObserver(
    (entries) => {
      const entry = entries[0]
      const nowVisible = !!entry?.isIntersecting && (entry.intersectionRatio ?? 0) > 0.02
      if (nowVisible && !visible) {
        visible = true
        if (hasGaugeSeries(props.option)) playGaugeFromZero()
        else chart?.resize()
      } else if (!nowVisible) {
        visible = false
        playGen += 1
        cancelPlay()
      }
    },
    { threshold: [0, 0.02, 0.2, 1] }
  )
  visibilityObserver.observe(el.value)

  window.setTimeout(() => {
    if (!visible && hasGaugeSeries(props.option) && hasSize()) {
      visible = true
      playGaugeFromZero()
    }
  }, 320)
})

onActivated(() => {
  chart?.resize()
  if (hasGaugeSeries(props.option)) playGaugeFromZero()
})

onBeforeUnmount(() => {
  playGen += 1
  cancelPlay()
  resizeObserver?.disconnect()
  visibilityObserver?.disconnect()
  resizeObserver = null
  visibilityObserver = null
  chart?.dispose()
  chart = null
})

watch(
  () => props.option,
  (newOpt) => {
    if (!hasGaugeSeries(newOpt)) {
      render()
      return
    }
    const sig = gaugeSignature(newOpt)
    if (sig === lastGaugeSig) return
    playGaugeFromZero()
  },
  { deep: true }
)

watch(
  () => props.playKey,
  () => {
    if (hasGaugeSeries(props.option)) playGaugeFromZero()
  }
)
</script>

<template>
  <div ref="el" :style="{ width: '100%', height: height || '100%' }" />
</template>
