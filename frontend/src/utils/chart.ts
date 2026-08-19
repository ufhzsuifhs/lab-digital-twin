import type { EChartsOption } from 'echarts'

/**
 * 图表 option 构建器：统一暗色科技风，减少看板页重复代码。
 */

export interface NamedValue {
  name: string
  value: number
}

export function barOption(
  categories: string[],
  data: number[],
  name = '数量'
): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: categories,
      axisLine: { lineStyle: { color: 'rgba(120,160,230,0.3)' } },
      axisLabel: { color: '#94a3c7', interval: 0, rotate: categories.length > 8 ? 30 : 0 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(120,160,230,0.12)' } },
      axisLabel: { color: '#94a3c7' }
    },
    series: [
      {
        name,
        type: 'bar',
        data,
        barMaxWidth: 28,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#22d3ee' },
              { offset: 1, color: 'rgba(34,211,238,0.15)' }
            ]
          }
        }
      }
    ]
  }
}

export function pieOption(data: NamedValue[], name = '占比'): EChartsOption {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: {
      orient: 'vertical',
      right: 4,
      top: 'center',
      textStyle: { color: '#94a3c7', fontSize: 11 },
      itemWidth: 10,
      itemHeight: 10
    },
    series: [
      {
        name,
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#0a1226', borderWidth: 2 },
        label: { color: '#94a3c7', fontSize: 11 },
        data
      }
    ]
  }
}

export function lineOption(
  categories: string[],
  series: Array<{ name: string; data: number[] }>
): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    legend: { textStyle: { color: '#94a3c7', fontSize: 11 }, top: 0 },
    grid: { left: 8, right: 16, top: 32, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: categories,
      axisLine: { lineStyle: { color: 'rgba(120,160,230,0.3)' } },
      axisLabel: { color: '#94a3c7' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(120,160,230,0.12)' } },
      axisLabel: { color: '#94a3c7' }
    },
    series: series.map((s) => ({
      name: s.name,
      type: 'line',
      smooth: true,
      data: s.data,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2 },
      areaStyle: { opacity: 0.08 }
    }))
  }
}

export function gaugeOption(value: number, name = '完成率'): EChartsOption {
  return {
    animation: true,
    animationDuration: 1800,
    animationDurationUpdate: 1800,
    animationEasing: 'cubicInOut',
    series: [
      {
        type: 'gauge',
        center: ['50%', '60%'],
        radius: '90%',
        startAngle: 220,
        endAngle: -40,
        min: 0,
        max: 100,
        progress: { show: true, width: 12, itemStyle: { color: '#22d3ee' } },
        axisLine: { lineStyle: { width: 12, color: [[1, 'rgba(34,211,238,0.15)']] } },
        axisTick: { show: false },
        splitLine: { length: 8, lineStyle: { color: 'rgba(120,160,230,0.4)' } },
        axisLabel: { color: '#94a3c7', fontSize: 10, distance: 16 },
        pointer: { itemStyle: { color: '#e6f1ff' } },
        detail: {
          valueAnimation: true,
          formatter: '{value}%',
          color: '#22d3ee',
          fontSize: 26,
          offsetCenter: [0, '62%']
        },
        title: { offsetCenter: [0, '90%'], color: '#94a3c7', fontSize: 12 },
        data: [{ value, name }]
      }
    ]
  }
}
