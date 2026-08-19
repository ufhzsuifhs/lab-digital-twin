<script setup lang="ts">
/**
 * 指标卡：首页六大指标（数值 HUD + 标签 + 颜色状态）。
 */
defineProps<{
  label: string
  value: string | number
  unit?: string
  color?: string
  sub?: string
}>()
</script>

<template>
  <div class="glass-panel metric-card">
    <div class="metric-label">{{ label }}</div>
    <div class="metric-value hud-num" :style="{ color: color || 'var(--primary)' }">
      {{ value }}<span v-if="unit" class="metric-unit">{{ unit }}</span>
    </div>
    <div v-if="sub" class="metric-sub">{{ sub }}</div>
  </div>
</template>

<style scoped>
.metric-card {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}
.metric-label {
  font-size: 12px;
  color: var(--text-mid);
  letter-spacing: 1px;
}
.metric-value {
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  text-shadow: 0 0 14px currentColor;
}
.metric-unit {
  font-size: 13px;
  margin-left: 4px;
  color: var(--text-mid);
  font-weight: 400;
}
.metric-sub {
  font-size: 11px;
  color: var(--text-low);
}
</style>
