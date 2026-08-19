<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import BaseChart from '@/components/BaseChart.vue'
import MetricCard from '@/components/MetricCard.vue'
import { fetchOverview } from '@/api/dashboard'
import { stomp, subscribeExperimentStatus } from '@/ws/websocket'
import type { EChartsOption } from 'echarts'

/** 首页运营大屏：指标卡 + 设备状态环形 + 完成率仪表盘 + 申请趋势 + 进行中列表 + NG 告警 */
const now = ref(new Date())
const overview = ref<Record<string, any>>({})
let timer: number | undefined

const num = (v: any) => Number(v ?? 0)
const planStatus = () => overview.value.planStatus || {}
const deviceUtil = () => overview.value.deviceUtilization || {}
const completionRate = () => overview.value.completionRate || {}
const dist = () => overview.value.deviceStatusDist || {}

function load() {
  fetchOverview().then((d) => (overview.value = d)).catch(() => {})
}

function fmtTime(d: Date) {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

// 设备状态环形图（自定义四态配色）
const statusPieOpt = computed<EChartsOption>(() => {
  const d = dist()
  const items = [
    { name: '空闲', value: num(d.idle), color: '#34d399' },
    { name: '运行', value: num(d.running), color: '#60a5fa' },
    { name: '满载', value: num(d.full), color: '#fbbf24' },
    { name: '异常', value: num(d.error), color: '#f87171' }
  ]
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 台 ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#94a3c7', fontSize: 11 }, itemWidth: 10, itemHeight: 10 },
    series: [
      {
        type: 'pie',
        radius: ['46%', '68%'],
        center: ['50%', '44%'],
        itemStyle: { borderColor: '#0a1226', borderWidth: 2 },
        label: { color: '#94a3c7', fontSize: 11 },
        data: items.map((i) => ({ name: i.name, value: i.value, itemStyle: { color: i.color } }))
      }
    ]
  }
})

// 完成率仪表盘
const gaugeOpt = computed<EChartsOption>(() => ({
  animation: true,
  animationDuration: 1800,
  animationDurationUpdate: 1800,
  animationEasing: 'cubicInOut',
  series: [
    {
      type: 'gauge',
      center: ['50%', '58%'],
      radius: '95%',
      startAngle: 220,
      endAngle: -40,
      min: 0,
      max: 100,
      progress: { show: true, width: 14, itemStyle: { color: '#6366f1' } },
      axisLine: { lineStyle: { width: 14, color: [[1, 'rgba(99,102,241,0.15)']] } },
      axisTick: { show: false },
      splitLine: { length: 8, lineStyle: { color: 'rgba(120,160,230,0.4)' } },
      axisLabel: { color: '#94a3c7', fontSize: 10, distance: 16 },
      pointer: { itemStyle: { color: '#e6f1ff' } },
      detail: {
        valueAnimation: true,
        formatter: '{value}%',
        color: '#6366f1',
        fontSize: 28,
        offsetCenter: [0, '64%']
      },
      title: { offsetCenter: [0, '92%'], color: '#94a3c7', fontSize: 12 },
      data: [{ value: num(completionRate().completion_rate_pct), name: '实验完成率' }]
    }
  ]
}))

// 今日实验申请趋势（近 30 天）
const trendOpt = computed<EChartsOption>(() => {
  const rows = overview.value.experimentTrend || []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 10, right: 14, top: 24, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: rows.map((r: any) => r.d),
      axisLine: { lineStyle: { color: 'rgba(120,160,230,0.3)' } },
      axisLabel: { color: '#94a3c7', fontSize: 10 }
    },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(120,160,230,0.12)' } }, axisLabel: { color: '#94a3c7' } },
    series: [
      {
        name: '申请量',
        type: 'line',
        smooth: true,
        data: rows.map((r: any) => num(r.cnt)),
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.12 }
      }
    ]
  }
})

const runningList = computed(() => overview.value.runningList || [])
const recentNg = computed(() => overview.value.recentNg || [])

// NG 告警滚动文案
const alertText = computed(() =>
  recentNg.value
    .map((r: any) => `[${r.date}] ${r.item_name || '未知项目'} · ${r.machine_model || ''} · NG ${num(r.ng_count)}`)
    .join('　｜　')
)

onMounted(() => {
  load()
  timer = window.setInterval(() => (now.value = new Date()), 1000)
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  stomp.connect(`${proto}://${location.host}/ws`).catch(() => {})
  subscribeExperimentStatus(() => load())
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="dashboard">
    <header class="page-header">
      <div class="page-title">实验室智能运营中心</div>
      <div class="realtime hud-num">
        <span class="dot glow-breathe"></span>
        实时更新时间 {{ fmtTime(now) }}
      </div>
    </header>

    <!-- NG 告警滚动条 -->
    <div v-if="alertText" class="alert-bar">
      <span class="alert-tag">⚠ NG 提醒</span>
      <div class="alert-scroll"><span class="alert-text">{{ alertText }}</span></div>
    </div>

    <!-- 六大指标卡 -->
    <div class="metric-grid">
      <MetricCard label="今日实验数量" :value="overview.todayExperimentCount ?? '--'" unit="单" color="#22d3ee" />
      <MetricCard label="进行中实验" :value="planStatus().running_count ?? '--'" unit="项" color="#60a5fa" />
      <MetricCard label="完成实验" :value="planStatus().completed_count ?? '--'" unit="项" color="#34d399" />
      <MetricCard label="异常实验" :value="overview.todayAbnormalCount ?? '--'" unit="项" color="#f87171" />
      <MetricCard
        label="设备利用率"
        :value="deviceUtil().occupied_units ?? '--'"
        :unit="`/ ${deviceUtil().total_units ?? '--'} 台`"
        color="#fbbf24"
      />
      <MetricCard label="实验完成率" :value="completionRate().completion_rate_pct ?? '--'" unit="%" color="#6366f1" />
    </div>

    <!-- 中间三栏图表 -->
    <div class="charts">
      <div class="glass-panel">
        <div class="panel-title">设备状态分布</div>
        <div class="panel-body chart"><BaseChart :option="statusPieOpt" /></div>
      </div>
      <div class="glass-panel">
        <div class="panel-title">实验完成率</div>
        <div class="panel-body chart"><BaseChart :option="gaugeOpt" /></div>
      </div>
      <div class="glass-panel">
        <div class="panel-title">实验申请趋势（近 30 天）</div>
        <div class="panel-body chart"><BaseChart :option="trendOpt" /></div>
      </div>
    </div>

    <!-- 底部列表 -->
    <div class="lists">
      <div class="glass-panel list-panel">
        <div class="panel-title">进行中实验</div>
        <div class="panel-body list-body">
          <div v-for="r in runningList" :key="r.plan_id" class="row">
            <span class="row-main">{{ r.item_name || '未命名项目' }}</span>
            <span class="row-sub">{{ r.station_code || r.instrument_name || '--' }}</span>
            <span class="row-sub">{{ r.dept }}</span>
          </div>
          <div v-if="!runningList.length" class="empty">暂无进行中实验</div>
        </div>
      </div>
      <div class="glass-panel list-panel">
        <div class="panel-title">最近异常（NG）记录</div>
        <div class="panel-body list-body">
          <div v-for="(r, i) in recentNg" :key="i" class="row">
            <span class="row-main">{{ r.item_name || '未命名项目' }}</span>
            <span class="row-sub">{{ r.machine_model || '' }}</span>
            <span class="ng-badge hud-num">NG {{ num(r.ng_count) }}</span>
          </div>
          <div v-if="!recentNg.length" class="empty">暂无异常记录</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.realtime {
  font-size: 14px;
  color: var(--text-mid);
  display: flex;
  align-items: center;
  gap: 8px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 10px var(--success);
}

/* NG 告警条 */
.alert-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 24px 6px;
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(248, 113, 113, 0.1);
  border: 1px solid rgba(248, 113, 113, 0.35);
  overflow: hidden;
}
.alert-tag {
  flex-shrink: 0;
  color: var(--danger);
  font-size: 12px;
  font-weight: 600;
}
.alert-scroll {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
}
.alert-text {
  display: inline-block;
  padding-left: 100%;
  animation: scroll 30s linear infinite;
  color: #fca5a5;
  font-size: 12px;
}
@keyframes scroll {
  0% { transform: translateX(0); }
  100% { transform: translateX(-100%); }
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  padding: 6px 24px;
}

.charts {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
  padding: 8px 24px;
  flex: 1;
  min-height: 0;
}
.chart {
  height: 100%;
  min-height: 180px;
}

.lists {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 8px 24px 16px;
  min-height: 0;
}
.list-panel {
  display: flex;
  flex-direction: column;
}
.list-body {
  flex: 1;
  overflow-y: auto;
  max-height: 200px;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed rgba(120, 160, 230, 0.1);
  font-size: 13px;
}
.row-main {
  flex: 1;
  color: var(--text-hi);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-sub {
  color: var(--text-mid);
  font-size: 12px;
  flex-shrink: 0;
}
.ng-badge {
  color: var(--danger);
  font-size: 12px;
  flex-shrink: 0;
}
.empty {
  color: var(--text-low);
  text-align: center;
  padding: 24px 0;
}
@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .charts,
  .lists {
    grid-template-columns: 1fr;
  }
}
</style>
