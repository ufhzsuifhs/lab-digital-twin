<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import BaseChart from '@/components/BaseChart.vue'
import MetricCard from '@/components/MetricCard.vue'
import RankingList from '@/components/RankingList.vue'
import { lineOption } from '@/utils/chart'
import * as aiApi from '@/api/ai'

/**
 * AI 智能分析：六项能力（设备压力预测/延期风险/瓶颈/效率/部门排行/资源推荐）。
 * 内置统计算法，无外部依赖。
 */
const pressure = ref<any>(null)
const delay = ref<any>(null)
const bottleneck = ref<any>(null)
const efficiency = ref<any>(null)
const deptEfficiency = ref<any[]>([])
const recommend = ref<any[]>([])
const itemInput = ref('')
const recommendLoading = ref(false)

const num = (v: any) => Number(v ?? 0)

const pressureOpt = computed(() => {
  const history = pressure.value?.history || []
  const forecast = pressure.value?.forecast || []
  const categories = [...history.map((r: any) => r.d), ...forecast.map((r: any) => r.date)]
  const data = [...history.map((r: any) => num(r.occupied)), ...forecast.map((r: any) => num(r.value))]
  return lineOption(categories, [{ name: '占用机台数', data }])
})

const bottleneckItems = computed(() =>
  (bottleneck.value?.bottlenecks || []).map((r: any) => ({
    name: `${r.instrument_name || ''} ${r.station_code || ''}`.trim(),
    value: num(r.load_score)
  }))
)

const deptRows = computed(() => deptEfficiency.value || [])

async function loadAll() {
  try {
    pressure.value = await aiApi.aiDevicePressure(7)
  } catch { /* ignore */ }
  try {
    delay.value = await aiApi.aiDelayRisk()
  } catch { /* ignore */ }
  try {
    bottleneck.value = await aiApi.aiBottleneck()
  } catch { /* ignore */ }
  try {
    efficiency.value = await aiApi.aiEfficiency()
  } catch { /* ignore */ }
  try {
    deptEfficiency.value = await aiApi.aiDeptEfficiency()
  } catch { /* ignore */ }
}

async function doRecommend() {
  if (!itemInput.value.trim()) return
  recommendLoading.value = true
  try {
    recommend.value = await aiApi.aiResourceRecommend(itemInput.value.trim())
  } finally {
    recommendLoading.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="ai">
    <header class="page-header">
      <div class="page-title">AI 智能分析</div>
    </header>

    <div class="scroll">
      <!-- 设备资源预测 -->
      <div class="glass-panel block">
        <div class="panel-title">设备资源预测（未来 7 天压力）</div>
        <div class="panel-body">
          <div class="chart"><BaseChart :option="pressureOpt" /></div>
          <div v-if="pressure?.fullLoadWarnings?.length" class="warns">
            <div class="warn-title">⚠ 可能满载设备预警</div>
            <div v-for="(w, i) in pressure.fullLoadWarnings" :key="i" class="warn-item">{{ w }}</div>
          </div>
        </div>
      </div>

      <div class="grid-3">
        <!-- 延期风险 -->
        <div class="glass-panel">
          <div class="panel-title">实验延期风险预测</div>
          <div class="panel-body">
            <div class="big-num hud-num" style="color: var(--danger)">{{ delay?.overdueCount ?? '--' }}</div>
            <div class="sub">个阶段已过应完成时间未完成</div>
            <div class="delay-list">
              <div v-for="(s, i) in (delay?.overdueStages || []).slice(0, 8)" :key="i" class="delay-row">
                <span>{{ s.stage_name || '阶段' }}</span>
                <span class="hud-num">{{ s.due_at }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 设备瓶颈 -->
        <div class="glass-panel">
          <div class="panel-title">设备瓶颈分析</div>
          <div class="panel-body">
            <div class="table">
              <div class="tr head"><span>设备</span><span>负荷</span><span>NG</span></div>
              <div v-for="(b, i) in (bottleneck?.bottlenecks || [])" :key="i" class="tr">
                <span>{{ b.instrument_name }} {{ b.station_code }}</span>
                <span class="hud-num">{{ b.load_score }}</span>
                <span class="hud-num" style="color: var(--danger)">{{ b.ng_count }}</span>
              </div>
            </div>
            <div class="sub">{{ bottleneck?.note }}</div>
          </div>
        </div>

        <!-- 实验效率 -->
        <div class="glass-panel">
          <div class="panel-title">实验效率分析</div>
          <div class="panel-body">
            <MetricCard label="平均实验周期" :value="efficiency?.avg_duration ?? '--'" unit="h" color="#22d3ee" />
            <div class="mini-grid">
              <MetricCard label="最长实验" :value="efficiency?.max_duration ?? '--'" unit="h" color="#f87171" />
              <MetricCard label="最短实验" :value="efficiency?.min_duration ?? '--'" unit="h" color="#34d399" />
            </div>
            <div class="sub">样本 {{ efficiency?.cnt ?? 0 }} 条</div>
          </div>
        </div>
      </div>

      <!-- 部门效率排行 -->
      <div class="glass-panel block">
        <div class="panel-title">部门实验效率排行（申请/完成/耗时/NG 比例）</div>
        <div class="panel-body">
          <div class="table">
            <div class="tr head"><span>部门</span><span>申请数</span><span>完成数</span><span>平均耗时(h)</span><span>NG 比例</span></div>
            <div v-for="(d, i) in deptRows" :key="i" class="tr">
              <span>{{ d.dept }}</span>
              <span class="hud-num">{{ d.apply_count }}</span>
              <span class="hud-num">{{ d.completed_count }}</span>
              <span class="hud-num">{{ d.avg_duration }}</span>
              <span class="hud-num" :style="{ color: num(d.ng_rate_pct) > 20 ? 'var(--danger)' : 'var(--success)' }">{{ d.ng_rate_pct }}%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 资源推荐 -->
      <div class="glass-panel block">
        <div class="panel-title">实验资源推荐</div>
        <div class="panel-body">
          <div class="recommend-bar">
            <input v-model="itemInput" placeholder="输入实验项目 ID" class="input" />
            <button class="btn" :disabled="recommendLoading" @click="doRecommend">推荐设备</button>
          </div>
          <div v-if="recommend.length" class="table">
            <div class="tr head"><span>设备</span><span>编号</span><span>历史次数</span><span>成功率</span><span>预计周期(h)</span></div>
            <div v-for="(r, i) in recommend" :key="i" class="tr">
              <span>{{ r.instrument_name }}</span>
              <span class="hud-num">{{ r.station_code }}</span>
              <span class="hud-num">{{ r.history_count }}</span>
              <span class="hud-num" style="color: var(--success)">{{ r.success_rate_pct ?? '--' }}{{ r.success_rate_pct != null ? '%' : '' }}</span>
              <span class="hud-num">{{ r.expected_duration ?? '--' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.block {
  padding-bottom: 4px;
}
.chart {
  height: 240px;
}
.grid-3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 14px;
}
.warns {
  margin-top: 10px;
}
.warn-title {
  color: var(--warning);
  font-size: 13px;
  margin-bottom: 6px;
}
.warn-item {
  color: var(--text-mid);
  font-size: 12px;
  padding: 4px 0;
}
.big-num {
  font-size: 40px;
  font-weight: 700;
}
.sub {
  color: var(--text-low);
  font-size: 12px;
  margin-top: 6px;
}
.delay-list {
  margin-top: 10px;
}
.delay-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  border-bottom: 1px dashed rgba(120, 160, 230, 0.1);
  font-size: 12px;
  color: var(--text-mid);
}
.table {
  margin-top: 8px;
}
.tr {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr 1fr;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px dashed rgba(120, 160, 230, 0.1);
  font-size: 13px;
  color: var(--text-hi);
}
.tr.head {
  color: var(--text-mid);
  font-size: 12px;
}
.mini-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
}
.recommend-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}
.input {
  flex: 1;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: rgba(8, 16, 38, 0.6);
  color: var(--text-hi);
  outline: none;
}
.input:focus {
  border-color: var(--border-strong);
}
.btn {
  padding: 8px 20px;
  border-radius: 8px;
  border: none;
  background: linear-gradient(90deg, var(--primary-dim), var(--accent));
  color: #fff;
  cursor: pointer;
  font-size: 13px;
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@media (max-width: 1100px) {
  .grid-3 {
    grid-template-columns: 1fr;
  }
}
</style>
