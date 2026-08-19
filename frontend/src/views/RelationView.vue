<script setup lang="ts">
import { ExtensionCategory, Graph, register } from '@antv/g6'
import {
  DragCanvas3D,
  D3Force3DLayout,
  Light,
  Line3D,
  ObserveCanvas3D,
  renderer as renderer3d,
  Sphere,
  ZoomCanvas3D
} from '@antv/g6-extension-3d'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchRelationGraph } from '@/api/relation'

/**
 * 关系分析：优先 3D 力导向；容器未就绪 / WebGL 失败时回退 2D，避免服务器上空白。
 */

try {
  register(ExtensionCategory.PLUGIN, '3d-light', Light)
  register(ExtensionCategory.NODE, 'sphere', Sphere)
  register(ExtensionCategory.EDGE, 'line3d', Line3D)
  register(ExtensionCategory.BEHAVIOR, 'drag-canvas-3d', DragCanvas3D)
  register(ExtensionCategory.BEHAVIOR, 'observe-canvas-3d', ObserveCanvas3D)
  register(ExtensionCategory.BEHAVIOR, 'zoom-canvas-3d', ZoomCanvas3D)
  register(ExtensionCategory.LAYOUT, 'd3-force-3d', D3Force3DLayout as any)
} catch {
  /* 重复 register 忽略 */
}

const container = ref<HTMLDivElement>()
const nodeDetail = ref<any>(null)
const loading = ref(true)
const errorText = ref('')
const empty = ref(false)
const renderMode = ref<'3d' | '2d'>('3d')
let graph: Graph | null = null
let selectedId: string | null = null
let resizeObserver: ResizeObserver | null = null

const CATEGORY_COLOR: Record<string, string> = {
  department: '#22d3ee',
  business_unit: '#6366f1',
  machine_type: '#34d399',
  experiment_item: '#fbbf24',
  machine: '#f87171'
}

const CATEGORY_LABEL: Record<string, string> = {
  department: '部门',
  business_unit: '事业部',
  machine_type: '机种',
  experiment_item: '实验项目',
  machine: '设备'
}

function canWebGL() {
  try {
    const c = document.createElement('canvas')
    return !!(c.getContext('webgl2') || c.getContext('webgl') || c.getContext('experimental-webgl'))
  } catch {
    return false
  }
}

function waitForSize(el: HTMLElement, timeout = 4000): Promise<boolean> {
  if (el.clientWidth >= 8 && el.clientHeight >= 8) return Promise.resolve(true)
  return new Promise((resolve) => {
    const start = Date.now()
    const timer = window.setInterval(() => {
      if (el.clientWidth >= 8 && el.clientHeight >= 8) {
        window.clearInterval(timer)
        resolve(true)
      } else if (Date.now() - start > timeout) {
        window.clearInterval(timer)
        resolve(el.clientWidth > 0 && el.clientHeight > 0)
      }
    }, 50)
  })
}

function bindClicks(g: Graph) {
  g.on('click', (evt: any) => {
    const target = evt.target
    if (evt.targetType === 'node' && target?.id) {
      if (selectedId && selectedId !== target.id) {
        g.setElementState(selectedId, [])
      }
      selectedId = target.id
      g.setElementState(target.id, ['selected'])
      nodeDetail.value = {
        id: target.id,
        label: target.data?.label || target.id,
        category: CATEGORY_LABEL[target.data?.category] || target.data?.category
      }
      g.focusElement(target.id)
    } else {
      if (selectedId) {
        g.setElementState(selectedId, [])
        selectedId = null
      }
      nodeDetail.value = null
    }
  })
}

function createGraph(el: HTMLElement, data: { nodes: any[]; edges: any[] }, use3d: boolean) {
  const width = Math.max(el.clientWidth, 320)
  const height = Math.max(el.clientHeight, 240)
  if (use3d) {
    return new Graph({
      container: el,
      renderer: renderer3d,
      width,
      height,
      data,
      node: {
        type: 'sphere',
        style: {
          size: 42,
          fill: (d: any) => CATEGORY_COLOR[d.data?.category] || '#60a5fa',
          materialType: 'phong',
          pointerEvents: 'all',
          labelText: (d: any) => d.data?.label || d.id,
          labelFill: '#e6f1ff',
          labelFontSize: 13,
          labelPlacement: 'bottom',
          labelOffsetY: 8
        },
        state: { selected: { size: 62 } }
      },
      edge: {
        type: 'line3d',
        style: {
          stroke: 'rgba(120,160,230,0.45)',
          lineWidth: 1.5
        }
      },
      layout: {
        type: 'd3-force-3d',
        link: { distance: 240 },
        manyBody: { strength: -650 },
        collide: { radius: 80 }
      },
      behaviors: [
        { type: 'observe-canvas-3d', mode: 'orbiting' },
        { type: 'drag-canvas-3d', trigger: ['right', 'drag'] },
        'zoom-canvas-3d'
      ],
      plugins: [
        {
          type: 'camera-setting',
          projectionMode: 'perspective',
          near: 0.1,
          far: 1000,
          fov: 50
        },
        {
          type: '3d-light',
          directional: { direction: [0, 0, 1] }
        }
      ]
    })
  }
  return new Graph({
    container: el,
    width,
    height,
    autoFit: 'view',
    data,
    node: {
      style: {
        size: 36,
        fill: (d: any) => CATEGORY_COLOR[d.data?.category] || '#60a5fa',
        stroke: 'rgba(230,241,255,0.35)',
        lineWidth: 1,
        labelText: (d: any) => d.data?.label || d.id,
        labelFill: '#e6f1ff',
        labelFontSize: 11,
        labelPlacement: 'bottom',
        labelOffsetY: 6
      },
      state: {
        selected: {
          size: 48,
          stroke: '#22d3ee',
          lineWidth: 2
        }
      }
    },
    edge: {
      style: {
        stroke: 'rgba(120,160,230,0.45)',
        lineWidth: 1.2,
        endArrow: true
      }
    },
    layout: {
      type: 'd3-force',
      link: { distance: 160 },
      manyBody: { strength: -420 },
      collide: { radius: 40 }
    },
    behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element']
  })
}

async function mountGraph(data: { nodes: any[]; edges: any[] }) {
  const el = container.value
  if (!el) throw new Error('关系图画布未就绪')
  const try3d = canWebGL()
  let lastError: unknown = null
  if (try3d) {
    try {
      graph = createGraph(el, data, true)
      bindClicks(graph)
      await graph.render()
      graph.resize(el.clientWidth, el.clientHeight)
      await graph.fitView()
      renderMode.value = '3d'
      return
    } catch (e) {
      lastError = e
      graph?.destroy()
      graph = null
      el.querySelectorAll('canvas').forEach((c) => c.remove())
    }
  }
  graph = createGraph(el, data, false)
  bindClicks(graph)
  await graph.render()
  graph.resize(el.clientWidth, el.clientHeight)
  await graph.fitView()
  renderMode.value = '2d'
  if (lastError) {
    console.warn('关系分析 3D 不可用，已回退 2D', lastError)
  }
}

function resetView() {
  if (selectedId) {
    graph?.setElementState(selectedId, [])
    selectedId = null
  }
  nodeDetail.value = null
  graph?.fitView()
}

onMounted(async () => {
  loading.value = true
  errorText.value = ''
  empty.value = false
  await nextTick()
  const el = container.value
  if (!el) {
    loading.value = false
    errorText.value = '关系图画布未找到'
    return
  }
  const sized = await waitForSize(el)
  if (!sized) {
    loading.value = false
    errorText.value = '关系图画布尺寸为 0，请检查页面布局后刷新'
    return
  }
  try {
    const data = await fetchRelationGraph()
    const nodes = data?.nodes || []
    const edges = data?.edges || []
    if (!nodes.length) {
      empty.value = true
      return
    }
    await mountGraph({ nodes, edges })
    resizeObserver = new ResizeObserver(() => {
      if (!graph || !container.value) return
      const w = container.value.clientWidth
      const h = container.value.clientHeight
      if (w >= 8 && h >= 8) graph.resize(w, h)
    })
    resizeObserver.observe(el)
  } catch (e: any) {
    errorText.value = e?.message || '关系数据加载失败，请确认 /api/relation/graph 可访问'
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  graph?.destroy()
  graph = null
})
</script>

<template>
  <div class="relation">
    <header class="page-header">
      <div class="page-title">关系分析网络</div>
      <div class="legend">
        <span v-for="(c, k) in CATEGORY_COLOR" :key="k" class="lg">
          <i class="sw" :style="{ background: c }"></i>{{ CATEGORY_LABEL[k] }}
        </span>
        <span class="hint">{{ renderMode === '3d' ? '🖱 按住拖拽旋转 · 滚轮缩放' : '🖱 拖拽画布 · 滚轮缩放' }}</span>
      </div>
    </header>

    <div class="graph-shell">
      <div ref="container" class="graph-canvas" />
      <button v-if="!loading && !errorText && !empty" class="reset-btn" @click="resetView">⟲ 复位视角</button>
      <div v-if="loading" class="graph-msg">正在加载关系网络…</div>
      <div v-else-if="errorText" class="graph-msg is-error">{{ errorText }}</div>
      <div v-else-if="empty" class="graph-msg">暂无关系数据</div>
    </div>

    <transition name="fade">
      <div v-if="nodeDetail" class="glass-panel node-pop">
        <div class="pop-title">{{ nodeDetail.label }}</div>
        <div class="pop-row"><span>类型</span><b>{{ nodeDetail.category }}</b></div>
        <div class="pop-row"><span>ID</span><b class="hud-num">{{ nodeDetail.id }}</b></div>
        <button class="close" @click="nodeDetail = null">×</button>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.relation {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.legend {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--text-mid);
  align-items: center;
  flex-wrap: wrap;
}
.lg {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.sw {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.hint {
  margin-left: 8px;
  color: var(--text-low);
}
.graph-shell {
  flex: 1;
  min-height: 0;
  margin: 0 20px 20px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  overflow: hidden;
  background: radial-gradient(800px 500px at 50% 50%, rgba(18, 35, 90, 0.6), rgba(5, 10, 26, 0.95));
  cursor: grab;
  position: relative;
}
.graph-shell:active {
  cursor: grabbing;
}
.graph-canvas {
  position: absolute;
  inset: 0;
}
.graph-msg {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-mid);
  font-size: 14px;
  z-index: 4;
  pointer-events: none;
}
.graph-msg.is-error {
  color: #fca5a5;
  padding: 0 24px;
  text-align: center;
}
.reset-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 6;
  padding: 6px 14px;
  border-radius: 8px;
  border: 1px solid var(--border-strong);
  background: rgba(8, 16, 38, 0.72);
  color: var(--text-hi);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.reset-btn:hover {
  color: var(--primary);
  background: rgba(34, 211, 238, 0.16);
}
.node-pop {
  position: absolute;
  top: 90px;
  right: 30px;
  width: 240px;
  padding: 16px;
  z-index: 5;
}
.pop-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--primary);
  margin-bottom: 10px;
}
.pop-row {
  display: flex;
  justify-content: space-between;
  padding: 5px 0;
  border-bottom: 1px dashed rgba(120, 160, 230, 0.12);
  font-size: 13px;
}
.pop-row span {
  color: var(--text-mid);
}
.pop-row b {
  color: var(--text-hi);
  font-weight: 500;
  word-break: break-all;
}
.close {
  position: absolute;
  top: 6px;
  right: 10px;
  background: none;
  border: none;
  color: var(--text-mid);
  font-size: 20px;
  cursor: pointer;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
