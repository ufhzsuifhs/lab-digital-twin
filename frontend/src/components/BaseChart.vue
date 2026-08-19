<script setup lang="ts">
import * as echarts from 'echarts'
import { onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * ECharts 统一封装：暗色主题、自适应缩放、loading 由父组件控制。
 * gauge 在刷新 / 切页进入 / 轮播重新可见时，从 0 慢速播到当前值。
 */
const props = defineProps<{
  option: echarts.EChartsOption
  height?: string
}>()

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null
let visibilityObserver: IntersectionObserver | null = null
let gaugeAnimTimer: ReturnType<typeof setTimeout> | null = null
let introPlaying = false
let visible = false
let lastGaugeSig = ''

const GAUGE_ANIM_MS = 2400
const DARK_TEXT = '#94a3c7'

/** 暗色科技默认样式，可被 option 覆盖 */
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
  if (!chart) chart = echarts.init(el.value)
  return chart
}

function clearGaugeTimer() {
  if (gaugeAnimTimer) {
    clearTimeout(gaugeAnimTimer)
    gaugeAnimTimer = null
  }
}

function render() {
  const inst = ensureChart()
  if (!inst) return
  inst.setOption(baseOption, false)
  inst.setOption(props.option, true)
}

/** 检测 option 中是否包含 gauge 系列 */
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

/** 生成 gauge 数值归 0 的 option（浅拷贝 series，保留函数引用） */
function zeroGaugeOption(option: echarts.EChartsOption): echarts.EChartsOption {
  const series = ((option as any).series || []).map((s: any) => {
    if (s?.type === 'gauge' && Array.isArray(s.data)) {
      return {
        ...s,
        animation: false,
        data: s.data.map((d: any) => ({ ...d, value: 0 }))
      }
    }
    return s
  })
  return { ...option, series } as echarts.EChartsOption
}

function applyTargetGauge(inst: echarts.ECharts) {
  inst.setOption(
    {
      ...props.option,
      animation: true,
      animationDuration: GAUGE_ANIM_MS,
      animationDurationUpdate: GAUGE_ANIM_MS,
      animationEasing: 'cubicInOut',
      animationEasingUpdate: 'cubicInOut'
    },
    false
  )
}

/** gauge 从 0 慢速动画到真实值：先瞬间归 0，再更新到目标值触发动画 */
function playGaugeFromZero() {
  if (!hasGaugeSeries(props.option)) {
    render()
    return
  }
  const inst = ensureChart()
  if (!inst || !hasSize()) return

  clearGaugeTimer()
  introPlaying = true
  lastGaugeSig = gaugeSignature(props.option)

  inst.setOption(baseOption, false)
  inst.setOption(
    {
      ...zeroGaugeOption(props.option),
      animation: false,
      animationDuration: 0,
      animationDurationUpdate: 0
    },
    true
  )
  inst.resize()

  gaugeAnimTimer = setTimeout(() => {
    applyTargetGauge(inst)
    gaugeAnimTimer = setTimeout(() => {
      introPlaying = false
      gaugeAnimTimer = null
    }, GAUGE_ANIM_MS + 80)
  }, 80)
}

onMounted(() => {
  if (!el.value) return
  if (!hasGaugeSeries(props.option)) render()

  observer = new ResizeObserver(() => {
    if (!chart || !hasSize()) return
    chart.resize()
  })
  observer.observe(el.value)

  visibilityObserver = new IntersectionObserver(
    (entries) => {
      const entry = entries[0]
      const nowVisible = !!entry?.isIntersecting && (entry.intersectionRatio ?? 0) > 0.12
      if (nowVisible && !visible) {
        visible = true
        if (hasGaugeSeries(props.option)) playGaugeFromZero()
        else chart?.resize()
      } else if (!nowVisible) {
        visible = false
        clearGaugeTimer()
        introPlaying = false
      }
    },
    { threshold: [0, 0.12, 0.5, 1] }
  )
  visibilityObserver.observe(el.value)
})

onActivated(() => {
  chart?.resize()
  if (hasGaugeSeries(props.option)) playGaugeFromZero()
})

onBeforeUnmount(() => {
  clearGaugeTimer()
  introPlaying = false
  observer?.disconnect()
  visibilityObserver?.disconnect()
  observer = null
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
    if (sig === lastGaugeSig) {
      if (!introPlaying && chart && visible) chart.setOption(newOpt, false)
      return
    }
    if (visible || hasSize()) playGaugeFromZero()
  },
  { deep: true }
)
</script>

<template>
  <div ref="el" :style="{ width: '100%', height: height || '100%' }" />
</template>
