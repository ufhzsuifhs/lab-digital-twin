<script setup lang="ts">
import { computed } from 'vue'

/**
 * 排行列表：排名 + 名称 + 数值 + 进度条，用于设备/项目/部门等 TOP 排行。
 */
const props = defineProps<{
  title: string
  items: Array<{ name: string; value: number | string }>
  unit?: string
}>()

const maxVal = computed(() => {
  const vals = props.items.map((i) => Number(i.value) || 0)
  return Math.max(1, ...vals)
})
</script>

<template>
  <div class="glass-panel ranking">
    <div class="panel-title">{{ title }}</div>
    <div class="panel-body ranking-body">
      <div v-for="(it, i) in items" :key="it.name" class="ranking-row">
        <span class="rank-idx" :class="{ top: i < 3 }">{{ i + 1 }}</span>
        <div class="rank-main">
          <div class="rank-line">
            <span class="rank-name">{{ it.name }}</span>
            <span class="rank-val hud-num">{{ it.value }}<i v-if="unit">{{ unit }}</i></span>
          </div>
          <div class="rank-bar">
            <div
              class="rank-bar-fill"
              :class="{ top: i < 3 }"
              :style="{ width: Math.max(2, (Number(it.value) / maxVal) * 100) + '%' }"
            ></div>
          </div>
        </div>
      </div>
      <div v-if="!items.length" class="empty">暂无数据</div>
    </div>
  </div>
</template>

<style scoped>
.ranking {
  display: flex;
  flex-direction: column;
}
.ranking-body {
  flex: 1;
  overflow-y: auto;
  padding: 6px 16px 14px;
}
.ranking-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 7px 0;
}
.rank-idx {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  flex-shrink: 0;
  margin-top: 2px;
  background: rgba(120, 160, 230, 0.12);
  color: var(--text-mid);
}
.rank-idx.top {
  background: rgba(34, 211, 238, 0.2);
  color: var(--primary);
  box-shadow: var(--glow);
}
.rank-main {
  flex: 1;
  min-width: 0;
}
.rank-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.rank-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-hi);
  font-size: 13px;
}
.rank-val {
  color: var(--primary);
  font-size: 14px;
  flex-shrink: 0;
}
.rank-val i {
  font-style: normal;
  font-size: 11px;
  color: var(--text-mid);
  margin-left: 2px;
}
.rank-bar {
  margin-top: 5px;
  height: 6px;
  border-radius: 3px;
  background: rgba(120, 160, 230, 0.1);
  overflow: hidden;
}
.rank-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, rgba(34, 211, 238, 0.25), rgba(34, 211, 238, 0.7));
  transition: width 0.4s ease;
}
.rank-bar-fill.top {
  background: linear-gradient(90deg, #22d3ee, #6366f1);
}
.empty {
  color: var(--text-low);
  text-align: center;
  padding: 20px 0;
}
</style>
