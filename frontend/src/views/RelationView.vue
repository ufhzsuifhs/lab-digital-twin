<script setup lang="ts">
import { ExtensionCategory, Graph, GraphEvent, register } from '@antv/g6'
import {
  DragCanvas3D,
  D3Force3DLayout,
  Light,
  Line3D,
  ObserveCanvas3D,
  renderer,
  Sphere,
  ZoomCanvas3D
} from '@antv/g6-extension-3d'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchRelationGraph } from '@/api/relation'

/**
 * 关系分析：AntV G6 3D 力导向网络。
 * 节点：部门 / 事业部 / 机种 / 实验项目 / 设备；3D 球体 + 光源；
 * 力导向发散后，鼠标按住拖拽可旋转视角（roll/drag-canvas-3d），滚轮缩放。
 */

// 注册 3D 扩展（模块级，仅执行一次）
register(ExtensionCategory.PLUGIN, '3d-light', Light)
register(ExtensionCategory.NODE, 'sphere', Sphere)
register(ExtensionCategory.EDGE, 'line3d', Line3D)
register(ExtensionCategory.BEHAVIOR, 'drag-canvas-3d', DragCanvas3D)
register(ExtensionCategory.BEHAVIOR, 'observe-canvas-3d', ObserveCanvas3D)
register(ExtensionCategory.BEHAVIOR, 'zoom-canvas-3d', ZoomCanvas3D)
register(ExtensionCategory.LAYOUT, 'd3-force-3d', D3Force3DLayout as any)

const container = ref<HTMLDivElement>()
const nodeDetail = ref<any>(null)
let graph: Graph | null = null
let selectedId: string | null = null
let pendingCenter = false
let resizeObserver: ResizeObserver | null = null

// 自动绕焦点旋转
let rotateRaf = 0
let rotateActive = false
let userInteracting = false
let lastFrameTs = 0
const ROTATE_DEG_PER_SEC = 14 // 旋转速度：每秒 14°，约 26 秒一圈

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

/** 按类型分层拉开 Z，侧看时才有前后纵深，而不是叠成一条线 */
const CATEGORY_Z: Record<string, number> = {
  business_unit: 260,
  department: 130,
  experiment_item: 0,
  machine_type: -130,
  machine: -260
}

function hashOffset(id: string, span = 90) {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0
  return (Math.abs(h) % span) - span / 2
}

function layerZ(category?: string, id = '') {
  return (CATEGORY_Z[category || ''] ?? 0) + hashOffset(id)
}

const ISO_CAMERA = {
  elevation: 32,
  azimuth: 42,
  far: 40000,
  fov: 45
}

let canvasCx = 0
let canvasCy = 0

function waitForSize(el: HTMLElement, timeout = 3000): Promise<{ w: number; h: number }> {
  const read = () => ({ w: el.clientWidth, h: el.clientHeight })
  const cur = read()
  if (cur.w >= 8 && cur.h >= 8) return Promise.resolve(cur)
  return new Promise((resolve) => {
    const start = Date.now()
    const timer = window.setInterval(() => {
      const next = read()
      if (next.w >= 8 && next.h >= 8) {
        window.clearInterval(timer)
        resolve(next)
      } else if (Date.now() - start > timeout) {
        window.clearInterval(timer)
        resolve({ w: Math.max(next.w, 640), h: Math.max(next.h, 400) })
      }
    }, 50)
  })
}

function nodeCentroid(): [number, number, number] {
  if (!graph) return [canvasCx, canvasCy, 0]
  const nodes = graph.getNodeData()
  if (!nodes.length) return [canvasCx, canvasCy, 0]
  let x = 0
  let y = 0
  let z = 0
  nodes.forEach((n) => {
    const p = graph!.getElementPosition(n.id)
    x += Number(p?.[0] || 0)
    y += Number(p?.[1] || 0)
    z += Number(p?.[2] || 0)
  })
  const n = nodes.length
  return [x / n, y / n, z / n]
}

function nodeSpread(center: [number, number, number]) {
  if (!graph) return 240
  const nodes = graph.getNodeData()
  let max = 120
  nodes.forEach((n) => {
    const p = graph!.getElementPosition(n.id)
    const dx = Number(p?.[0] || 0) - center[0]
    const dy = Number(p?.[1] || 0) - center[1]
    const dz = Number(p?.[2] || 0) - center[2]
    max = Math.max(max, Math.hypot(dx, dy, dz))
  })
  return max
}

/** 3D 居中：焦点对准节点质心，距离按包围球温和拟合（~1.2 倍），既装下整图又不缩太小 */
function centerView() {
  if (!graph) return
  const camera = graph.getCanvas()?.getCamera()
  if (!camera) return
  const el = container.value
  if (el && el.clientWidth >= 8 && el.clientHeight >= 8) {
    graph.resize(el.clientWidth, el.clientHeight)
    canvasCx = el.clientWidth / 2
    canvasCy = el.clientHeight / 2
  }
  const [cx, cy, cz] = nodeCentroid()
  const spread = nodeSpread([cx, cy, cz])
  // 温和拟合：按视场角反算距离，~1.2 倍余量，整图装下但不缩太小
  const fovRad = (ISO_CAMERA.fov * Math.PI) / 180
  const dist = (spread / Math.tan(fovRad / 2)) * 1.2 + 60
  camera.setFocalPoint(cx, cy, cz)
  camera.setDistance(dist)
  camera.setNear(0.1)
  camera.setFar(ISO_CAMERA.far)
  camera.setFov(ISO_CAMERA.fov)
  camera.setElevation(ISO_CAMERA.elevation)
  camera.setAzimuth(ISO_CAMERA.azimuth)
}

/** 布局稳定后启动自动旋转：每帧按时间增量改方位角，绕焦点 360° 转 */
function startAutoRotate() {
  if (rotateActive) return
  rotateActive = true
  lastFrameTs = 0
  const tick = (ts: number) => {
    if (!rotateActive || !graph) {
      rotateRaf = 0
      return
    }
    const camera = graph.getCanvas()?.getCamera()
    if (camera && !userInteracting) {
      if (lastFrameTs) {
        const dt = (ts - lastFrameTs) / 1000
        // setAzimuth 接收的是度数，直接按度/秒累加
        const cur = camera.getAzimuth()
        camera.setAzimuth(cur + ROTATE_DEG_PER_SEC * dt)
      }
      lastFrameTs = ts
    } else {
      lastFrameTs = 0
    }
    rotateRaf = requestAnimationFrame(tick)
  }
  rotateRaf = requestAnimationFrame(tick)
}

function stopAutoRotate() {
  rotateActive = false
  if (rotateRaf) cancelAnimationFrame(rotateRaf)
  rotateRaf = 0
}

function initGraph(el: HTMLElement, width: number, height: number) {
  graph = new Graph({
    container: el,
    renderer, // 3D 渲染器
    width,
    height,
    data: { nodes: [], edges: [] },
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
      state: {
        selected: {
          size: 62
        }
      }
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
      numDimensions: 3,
      // 加快收敛：默认 alphaDecay≈0.0228 要 ~10s，调高后 ~2-3s 分散完成即触发 AFTER_LAYOUT
      alpha: 1,
      alphaMin: 0.01,
      alphaDecay: 0.05,
      velocityDecay: 0.4,
      link: { distance: 180, strength: 0.55 },
      manyBody: { strength: -820 },
      collide: { radius: 70 },
      center: { x: canvasCx, y: canvasCy, z: 0, strength: 0.08 },
      z: {
        strength: 0.45,
        z: (node: any) => layerZ(node.data?.category || node.category, String(node.id ?? ''))
      }
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
        cameraType: 'orbiting',
        near: 0.1,
        far: ISO_CAMERA.far,
        fov: ISO_CAMERA.fov,
        aspect: 'auto',
        // 初始斜视 + 合理距离，分散过程中即可见；AFTER_LAYOUT 后由 centerView 重新拟合
        distance: 900,
        elevation: ISO_CAMERA.elevation,
        azimuth: ISO_CAMERA.azimuth
      },
      {
        type: '3d-light',
        directional: { direction: [0.6, -0.7, 0.8] },
        ambient: { fill: '#8aa4d4', intensity: 1.2 }
      }
    ]
  })

  graph.on('click', (evt: any) => {
    const target = evt.target
    if (evt.targetType === 'node' && target?.id) {
      // 清除上一个节点的选中状态
      if (selectedId && selectedId !== target.id) {
        graph?.setElementState(selectedId, [])
      }
      selectedId = target.id
      graph?.setElementState(target.id, ['selected'])
      nodeDetail.value = {
        id: target.id,
        label: target.data?.label || target.id,
        category: CATEGORY_LABEL[target.data?.category] || target.data?.category
      }
      // 以选中节点为中心：聚焦该节点，之后旋转/缩放都围绕它
      graph?.focusElement(target.id)
    } else {
      // 点击空白处：清除选中状态并关闭详情
      if (selectedId) {
        graph?.setElementState(selectedId, [])
        selectedId = null
      }
      nodeDetail.value = null
    }
  })

  graph.on(GraphEvent.AFTER_LAYOUT, () => {
    // 分散稳定后不再做拟合缩放（会导致整图缩太小），旋转已在 render 后启动并一直维持
    pendingCenter = false
  })
}

async function loadGraph() {
  const data = await fetchRelationGraph()
  if (graph && data) {
    graph.setData({
      nodes: data.nodes.map((n, i) => {
        const angle = (i / Math.max(data.nodes.length, 1)) * Math.PI * 2
        const r = 90 + (i % 5) * 18
        return {
          id: n.id,
          data: { label: n.label, category: n.category },
          style: {
            x: canvasCx + Math.cos(angle) * r,
            y: canvasCy + Math.sin(angle) * r,
            z: layerZ(n.category, n.id)
          }
        }
      }),
      edges: data.edges.map((e) => ({ id: e.id, source: e.source, target: e.target, data: { label: e.label } }))
    })
    pendingCenter = true
    await graph.render()
    // 分散过程中即开始旋转，并一直维持（不等待 AFTER_LAYOUT，不在分散后做拟合缩放）
    startAutoRotate()
  }
}

/** 复位视角：焦点回到网络中心并斜视 */
function resetView() {
  if (selectedId) {
    graph?.setElementState(selectedId, [])
    selectedId = null
  }
  nodeDetail.value = null
  centerView()
}

onMounted(async () => {
  await nextTick()
  const el = container.value
  if (!el) return
  const size = await waitForSize(el)
  canvasCx = size.w / 2
  canvasCy = size.h / 2
  initGraph(el, size.w, size.h)
  resizeObserver = new ResizeObserver(() => {
    if (!graph || !container.value) return
    const w = container.value.clientWidth
    const h = container.value.clientHeight
    if (w >= 8 && h >= 8) {
      canvasCx = w / 2
      canvasCy = h / 2
      graph.resize(w, h)
      centerView()
    }
  })
  resizeObserver.observe(el)
  // 用户拖拽/滚轮时暂停自动旋转，松手后恢复
  let wheelResumeTimer: ReturnType<typeof setTimeout> | undefined
  el.addEventListener('pointerdown', () => {
    userInteracting = true
  })
  const resume = () => {
    userInteracting = false
    lastFrameTs = 0
  }
  el.addEventListener('pointerup', resume)
  el.addEventListener('pointerleave', resume)
  el.addEventListener('wheel', () => {
    userInteracting = true
    clearTimeout(wheelResumeTimer)
    wheelResumeTimer = setTimeout(resume, 1200)
  })
  try {
    await loadGraph()
  } catch {
    /* 后端未启动时静默 */
  }
})

onBeforeUnmount(() => {
  stopAutoRotate()
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
        <span class="hint">🖱 按住拖拽旋转 · 滚轮缩放</span>
      </div>
    </header>

    <div ref="container" class="graph-container">
      <button class="reset-btn" @click="resetView">⟲ 复位视角</button>
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
.graph-container {
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
.graph-container:active {
  cursor: grabbing;
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
