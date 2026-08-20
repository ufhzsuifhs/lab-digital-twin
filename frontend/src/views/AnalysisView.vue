<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import BaseChart from '@/components/BaseChart.vue'
import RankingList from '@/components/RankingList.vue'
import MetricCard from '@/components/MetricCard.vue'
import { barOption, pieOption, lineOption, gaugeOption, type NamedValue } from '@/utils/chart'
import type { EChartsOption } from 'echarts'
import * as analysisApi from '@/api/analysis'

/**
 * 数据分析中心：九大分析看板（设备/事业部/机种/申请/完成率/异常/结果/DQA/报价）。
 */
type Tab = 'device' | 'biz' | 'type' | 'application' | 'completion' | 'abnormal' | 'result' | 'dqa' | 'quote'

const tabs: Array<{ key: Tab; label: string }> = [
  { key: 'device', label: '设备利用率' },
  { key: 'biz', label: '事业部占用' },
  { key: 'type', label: '机种占比' },
  { key: 'application', label: '实验申请' },
  { key: 'completion', label: '完成率' },
  { key: 'abnormal', label: '异常分析' },
  { key: 'result', label: '实验结果' },
  { key: 'dqa', label: 'DQA 专项' },
  { key: 'quote', label: '报价分析' }
]

const active = ref<Tab>('device')
const data = reactive<Record<string, any>>({})
const loaded = reactive<Record<string, boolean>>({})
const gaugePlayKey = ref(0)

const num = (v: any) => Number(v ?? 0)
const toNamed = (rows: any[], nameKey: string, valueKey: string): NamedValue[] =>
  (rows || []).map((r) => ({ name: String(r[nameKey]), value: num(r[valueKey]) }))

async function load(key: string, fn: () => Promise<any>) {
  if (loaded[key]) return
  try {
    data[key] = await fn()
    loaded[key] = true
  } catch {
    data[key] = null
  }
}

// ============ 设备利用率 ============
function loadDevice() {
  load('deviceUtil', analysisApi.deviceUtilization)
  load('occupation', analysisApi.deviceOccupation)
  load('trend', () => analysisApi.utilizationTrend('daily'))
  load('loadRank', analysisApi.loadRanking)
}
const occupationOpt = computed<EChartsOption>(() => {
  const all = [...(data.occupation || [])].sort((a: any, b: any) => num(b.load_score) - num(a.load_score))
  // 只显示有负荷的机台；太少则补足前 10（横向条形图，y 轴标签不密集）
  let rows = all.filter((r: any) => num(r.load_score) > 0)
  if (rows.length < 5) rows = all.slice(0, 10)
  rows = rows.slice().reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 30, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(120,160,230,0.12)' } }, axisLabel: { color: '#94a3c7' } },
    yAxis: {
      type: 'category',
      data: rows.map((r: any) => r.instrument_name),
      axisLine: { lineStyle: { color: 'rgba(120,160,230,0.3)' } },
      axisLabel: { color: '#94a3c7', fontSize: 11 }
    },
    series: [
      {
        name: '负荷强度',
        type: 'bar',
        data: rows.map((r: any) => num(r.load_score)),
        barMaxWidth: 14,
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [
              { offset: 0, color: 'rgba(34,211,238,0.15)' },
              { offset: 1, color: '#22d3ee' }
            ]
          }
        }
      }
    ]
  }
})
const trendOpt = computed(() => {
  const rows = data.trend || []
  return lineOption(
    rows.map((r: any) => r.period),
    [{ name: '占用机台', data: rows.map((r: any) => num(r.occupied_slots)) }]
  )
})
const loadRankItems = computed(() =>
  (data.loadRank || []).map((r: any) => ({ name: r.instrument_name || '', value: num(r.load_score) }))
)

// ============ 事业部 ============
function loadBiz() {
  load('bizOcc', analysisApi.businessUnitOccupation)
  load('bizTrend', analysisApi.businessUnitTrend)
}
const bizPieOpt = computed(() => pieOption(toNamed(data.bizOcc, 'business_unit', 'occupied_units'), '占用机台'))

// ============ 机种 ============
function loadType() {
  load('typeRatio', analysisApi.machineTypeRatio)
}
const typePieOpt = computed(() => pieOption(toNamed(data.typeRatio, 'machine_type', 'occupied_units'), '占用机台'))

// ============ 实验申请 ============
function loadApplication() {
  load('appTrend', () => analysisApi.applicationTrend('monthly'))
  load('appDept', analysisApi.deptApplicationCount)
  load('appType', analysisApi.requestTypeDistribution)
  load('appCategory', analysisApi.categoryDistribution)
  load('appSupplier', analysisApi.supplierRatio)
}
const appTrendOpt = computed(() => {
  const rows = data.appTrend || []
  return lineOption(
    rows.map((r: any) => r.period),
    [{ name: '申请数量', data: rows.map((r: any) => num(r.cnt)) }]
  )
})
const appDeptOpt = computed(() => {
  const rows = data.appDept || []
  return barOption(rows.map((r: any) => r.dept), rows.map((r: any) => num(r.cnt)), '申请量')
})
const appTypeOpt = computed(() => pieOption(toNamed(data.appType, 'request_type', 'cnt'), '申请类型'))
const appCategoryOpt = computed(() => pieOption(toNamed(data.appCategory, 'category', 'cnt'), '类别'))
const appSupplierOpt = computed(() => {
  const rows = (data.appSupplier || []).map((r: any) => ({
    name: num(r.is_supplier) === 1 ? '供应商' : '非供应商',
    value: num(r.cnt)
  }))
  return pieOption(rows, '供应商占比')
})

// ============ 完成率 ============
function loadCompletion() {
  load('completionOverall', analysisApi.completionRateOverall)
  load('completionTrend', analysisApi.completionTrend)
}
const completionGaugeOpt = computed(() => gaugeOption(num(data.completionOverall?.completion_rate_pct), '实验完成率'))
const completionTrendOpt = computed(() => {
  const rows = data.completionTrend || []
  return lineOption(
    rows.map((r: any) => r.d),
    [{ name: '完成率%', data: rows.map((r: any) => num(r.rate_pct)) }]
  )
})

// ============ 异常 NG ============
function loadAbnormal() {
  load('ngSummary', analysisApi.ngSummary)
  load('ngTrend', analysisApi.ngTrend)
  load('ngTopItem', analysisApi.ngTopItem)
  load('ngTopType', analysisApi.ngTopMachineType)
  load('ngTopDevice', analysisApi.ngTopDevice)
  load('ngInspector', analysisApi.ngInspector)
  load('ngReason', analysisApi.ngReason)
}
const ngTrendOpt = computed(() => {
  const rows = data.ngTrend || []
  return lineOption(
    rows.map((r: any) => r.d),
    [{ name: 'NG 件数', data: rows.map((r: any) => num(r.ng_pieces)) }]
  )
})
const ngTopItemOpt = computed(() => {
  const rows = data.ngTopItem || []
  return barOption(rows.map((r: any) => r.experiment_item), rows.map((r: any) => num(r.ng_count)), 'NG 数')
})
const ngTopTypeOpt = computed(() => {
  const rows = data.ngTopType || []
  return barOption(rows.map((r: any) => r.machine_model), rows.map((r: any) => num(r.ng_count)), 'NG 数')
})
const ngTopDeviceOpt = computed(() => {
  const rows = data.ngTopDevice || []
  return barOption(rows.map((r: any) => r.station_code || r.instrument_name), rows.map((r: any) => num(r.ng_count)), 'NG 数')
})
const ngInspectorOpt = computed(() => {
  const rows = data.ngInspector || []
  return barOption(rows.map((r: any) => r.inspector), rows.map((r: any) => num(r.ng_count)), 'NG 数')
})
const ngReasonItems = computed(() =>
  (data.ngReason || []).map((r: any) => ({ name: String(r.ng_reason), value: num(r.cnt) }))
)

// ============ 实验结果 ============
function loadResult() {
  load('resultRel', () => analysisApi.resultOkNg('reliability'))
  load('resultDqa', () => analysisApi.resultOkNg('dqa'))
  load('resultDist', analysisApi.resultDistribution)
}
const resultRelOpt = computed(() => pieOption(toNamed(data.resultRel, 'result', 'cnt'), '申请单结果'))
const resultDqaOpt = computed(() => pieOption(toNamed(data.resultDqa, 'result', 'cnt'), 'DQA 结果'))
const resultDistOpt = computed(() => pieOption(toNamed(data.resultDist, 'result', 'cnt'), '全部结果'))

// ============ DQA ============
function loadDqa() {
  load('dqaProject', analysisApi.dqaProjectRatio)
  load('dqaPurpose', analysisApi.dqaPurpose)
  load('dqaStage', analysisApi.dqaStage)
  load('dqaResult', analysisApi.dqaResult)
  load('dqaMonth', analysisApi.dqaMonthTrend)
}
const dqaProjectOpt = computed(() => pieOption(toNamed(data.dqaProject, 'project', 'cnt'), 'DQA 项目'))
const dqaStageOpt = computed(() => pieOption(toNamed(data.dqaStage, 'stage', 'cnt'), '评价阶段'))
const dqaResultOpt = computed(() => pieOption(toNamed(data.dqaResult, 'result', 'cnt'), '评价结果'))
const dqaMonthOpt = computed(() => {
  const rows = data.dqaMonth || []
  return lineOption(
    rows.map((r: any) => r.period),
    [{ name: '评价数', data: rows.map((r: any) => num(r.cnt)) }]
  )
})
const dqaPurposeItems = computed(() =>
  (data.dqaPurpose || []).map((r: any) => ({ name: String(r.purpose), value: num(r.cnt) }))
)

// ============ 报价 ============
function loadQuote() {
  load('quoteDept', analysisApi.quoteDepartment)
  load('quoteBiz', analysisApi.quoteBusinessUnit)
  load('quoteItem', analysisApi.quoteItemRanking)
  load('quoteInst', analysisApi.quoteInstrumentRanking)
  load('quoteTrend', analysisApi.quoteCostTrend)
}
const quoteDeptOpt = computed(() => {
  const rows = data.quoteDept || []
  return barOption(rows.map((r: any) => r.dept), rows.map((r: any) => num(r.total_price)), '金额(元)')
})
const quoteBizOpt = computed(() => pieOption(toNamed(data.quoteBiz, 'business_unit', 'total_price'), '报价金额'))
const quoteTrendOpt = computed(() => {
  const rows = data.quoteTrend || []
  return lineOption(
    rows.map((r: any) => r.period),
    [{ name: '金额(元)', data: rows.map((r: any) => num(r.total_price)) }]
  )
})
const quoteItemItems = computed(() =>
  (data.quoteItem || []).map((r: any) => ({ name: String(r.item), value: num(r.total_price) }))
)
const quoteInstItems = computed(() =>
  (data.quoteInst || []).map((r: any) => ({ name: String(r.instrument), value: num(r.total_income) }))
)

function switchTab(tab: Tab) {
  active.value = tab
  const loader: Record<Tab, () => void> = {
    device: loadDevice,
    biz: loadBiz,
    type: loadType,
    application: loadApplication,
    completion: loadCompletion,
    abnormal: loadAbnormal,
    result: loadResult,
    dqa: loadDqa,
    quote: loadQuote
  }
  loader[tab]()
  if (tab === 'completion') gaugePlayKey.value += 1
}

onMounted(() => switchTab('device'))
</script>

<template>
  <div class="analysis">
    <header class="page-header">
      <div class="page-title">数据分析中心</div>
    </header>

    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="tab"
        :class="{ active: active === t.key }"
        @click="switchTab(t.key)"
      >
        {{ t.label }}
      </button>
    </div>

    <div class="panels">
      <!-- 设备利用率 -->
      <template v-if="active === 'device'">
        <div class="grid-3">
          <MetricCard label="占用机台" :value="num(data.deviceUtil?.occupied_units)" :unit="`/ ${num(data.deviceUtil?.total_units)} 台`" color="#fbbf24" />
          <div class="glass-panel"><div class="panel-title">机台占用负荷</div><div class="panel-body chart"><BaseChart :option="occupationOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">利用率趋势(日)</div><div class="panel-body chart"><BaseChart :option="trendOpt" /></div></div>
        </div>
        <div class="grid-1">
          <RankingList title="设备负载排行 TOP20" :items="loadRankItems" />
        </div>
      </template>

      <!-- 事业部 -->
      <template v-else-if="active === 'biz'">
        <div class="grid-2">
          <div class="glass-panel"><div class="panel-title">事业部设备占用</div><div class="panel-body chart"><BaseChart :option="bizPieOpt" /></div></div>
          <RankingList title="事业部占用排行" :items="toNamed(data.bizOcc, 'business_unit', 'occupied_units').map(r => ({ name: r.name, value: r.value }))" unit=" 台" />
        </div>
      </template>

      <!-- 机种 -->
      <template v-else-if="active === 'type'">
        <div class="grid-2">
          <div class="glass-panel"><div class="panel-title">机种占用比例 TOP10</div><div class="panel-body chart"><BaseChart :option="typePieOpt" /></div></div>
          <RankingList title="机种占用排行" :items="toNamed(data.typeRatio, 'machine_type', 'occupied_units').map(r => ({ name: r.name, value: r.value }))" unit=" 台" />
        </div>
      </template>

      <!-- 实验申请 -->
      <template v-else-if="active === 'application'">
        <div class="grid-2">
          <div class="glass-panel"><div class="panel-title">申请数量趋势(月)</div><div class="panel-body chart"><BaseChart :option="appTrendOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">各部门申请量</div><div class="panel-body chart"><BaseChart :option="appDeptOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">申请类型分布</div><div class="panel-body chart"><BaseChart :option="appTypeOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">实验类别分布</div><div class="panel-body chart"><BaseChart :option="appCategoryOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">供应商实验占比</div><div class="panel-body chart"><BaseChart :option="appSupplierOpt" /></div></div>
        </div>
      </template>

      <!-- 完成率 -->
      <template v-else-if="active === 'completion'">
        <div class="grid-2">
          <div class="glass-panel"><div class="panel-title">实验完成率</div><div class="panel-body chart"><BaseChart :option="completionGaugeOpt" :play-key="gaugePlayKey" /></div></div>
          <div class="glass-panel"><div class="panel-title">每日完成率变化</div><div class="panel-body chart"><BaseChart :option="completionTrendOpt" /></div></div>
        </div>
      </template>

      <!-- 异常 NG -->
      <template v-else-if="active === 'abnormal'">
        <div class="grid-3">
          <MetricCard label="NG 总数" :value="num(data.ngSummary?.ng_total)" unit="件" color="#f87171" />
          <MetricCard label="NG 比例" :value="num(data.ngSummary?.ng_rate_pct)" unit="%" color="#fbbf24" />
          <MetricCard label="样品总数" :value="num(data.ngSummary?.sample_total)" unit="件" color="#60a5fa" />
          <div class="glass-panel"><div class="panel-title">异常趋势(日)</div><div class="panel-body chart"><BaseChart :option="ngTrendOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">NG TOP 实验项目</div><div class="panel-body chart"><BaseChart :option="ngTopItemOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">NG TOP 机种</div><div class="panel-body chart"><BaseChart :option="ngTopTypeOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">NG TOP 设备</div><div class="panel-body chart"><BaseChart :option="ngTopDeviceOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">NG 责任人员分布</div><div class="panel-body chart"><BaseChart :option="ngInspectorOpt" /></div></div>
          <RankingList title="NG 原因分析" :items="ngReasonItems" />
        </div>
      </template>

      <!-- 实验结果 -->
      <template v-else-if="active === 'result'">
        <div class="grid-3">
          <div class="glass-panel"><div class="panel-title">申请单结果占比</div><div class="panel-body chart"><BaseChart :option="resultRelOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">DQA 单结果占比</div><div class="panel-body chart"><BaseChart :option="resultDqaOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">全部结果分布</div><div class="panel-body chart"><BaseChart :option="resultDistOpt" /></div></div>
        </div>
      </template>

      <!-- DQA -->
      <template v-else-if="active === 'dqa'">
        <div class="grid-2">
          <div class="glass-panel"><div class="panel-title">DQA 项目占比</div><div class="panel-body chart"><BaseChart :option="dqaProjectOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">评价阶段分布</div><div class="panel-body chart"><BaseChart :option="dqaStageOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">评价结果分布</div><div class="panel-body chart"><BaseChart :option="dqaResultOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">月份趋势</div><div class="panel-body chart"><BaseChart :option="dqaMonthOpt" /></div></div>
          <RankingList title="评价目的 TOP" :items="dqaPurposeItems" />
        </div>
      </template>

      <!-- 报价 -->
      <template v-else-if="active === 'quote'">
        <div class="grid-2">
          <div class="glass-panel"><div class="panel-title">各部门报价金额</div><div class="panel-body chart"><BaseChart :option="quoteDeptOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">事业部报价金额</div><div class="panel-body chart"><BaseChart :option="quoteBizOpt" /></div></div>
          <div class="glass-panel"><div class="panel-title">实验成本趋势(月)</div><div class="panel-body chart"><BaseChart :option="quoteTrendOpt" /></div></div>
          <RankingList title="实验项目收费排行" :items="quoteItemItems" unit=" 元" />
          <RankingList title="设备实验收入排行" :items="quoteInstItems" unit=" 元" />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.analysis {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.tabs {
  display: flex;
  gap: 6px;
  padding: 0 20px 12px;
  flex-wrap: wrap;
}
.tab {
  padding: 6px 16px;
  border-radius: 8px;
  background: rgba(120, 160, 230, 0.08);
  border: 1px solid transparent;
  color: var(--text-mid);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.tab:hover {
  color: var(--text-hi);
}
.tab.active {
  color: var(--primary);
  background: rgba(34, 211, 238, 0.16);
  border-color: var(--border-strong);
}
.panels {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px 20px;
}
.grid-1 {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.grid-3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 14px;
}
.chart {
  height: 280px;
}
@media (max-width: 1100px) {
  .grid-2,
  .grid-3 {
    grid-template-columns: 1fr;
  }
}
</style>
