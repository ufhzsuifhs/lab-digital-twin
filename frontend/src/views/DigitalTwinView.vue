<script setup lang="ts">
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchDevices, fetchDeviceDetail } from '@/api/dashboard'
import { stomp, subscribeDeviceStatus } from '@/ws/websocket'

/**
 * 实验室数字孪生：优先 Three.js 三维场景；
 * 画布未就绪或 WebGL 失败时回退 2D 机台平面图，避免服务器上空白。
 */
const container = ref<HTMLDivElement>()
const detail = ref<Record<string, any> | null>(null)
const loading = ref(true)
const errorText = ref('')
const mode = ref<'3d' | '2d'>('3d')
const devices = ref<any[]>([])

type DeviceStatus = 'idle' | 'running' | 'full' | 'error'
const STATUS_COLOR: Record<DeviceStatus, number> = {
  idle: 0x34d399,
  running: 0x60a5fa,
  full: 0xfbbf24,
  error: 0xf87171
}
const STATUS_LABEL: Record<DeviceStatus, string> = {
  idle: '空闲',
  running: '运行',
  full: '满载',
  error: '异常'
}

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene
let camera: THREE.PerspectiveCamera
let controls: OrbitControls | null = null
let raycaster = new THREE.Raycaster()
let pointer = new THREE.Vector2()
const deviceMeshes = new Map<string, THREE.Mesh>()
let animId = 0
let resizeObserver: ResizeObserver | null = null

function statusOf(d: Record<string, any>): DeviceStatus {
  if (d.status) return d.status as DeviceStatus
  if (Number(d.is_full) === 1) return 'full'
  if (d.load_id) return 'running'
  return 'idle'
}

function statusClass(d: Record<string, any>) {
  return `is-${statusOf(d)}`
}

function clamp(v: number, min: number, max: number) {
  return Math.min(Math.max(v, min), max)
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

function initScene(el: HTMLElement) {
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x050a1a)
  scene.fog = new THREE.Fog(0x050a1a, 40, 120)

  const w = Math.max(el.clientWidth, 320)
  const h = Math.max(el.clientHeight, 240)
  camera = new THREE.PerspectiveCamera(55, w / h, 0.1, 300)
  camera.position.set(22, 18, 26)

  renderer = new THREE.WebGLRenderer({
    antialias: true,
    alpha: false,
    powerPreference: 'default',
    failIfMajorPerformanceCaveat: false
  })
  renderer.setClearColor(0x050a1a, 1)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setSize(w, h, false)
  renderer.domElement.style.display = 'block'
  renderer.domElement.style.width = '100%'
  renderer.domElement.style.height = '100%'
  el.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.target.set(0, 1.5, 0)
  controls.enableDamping = true
  controls.maxPolarAngle = Math.PI / 2.05

  scene.add(new THREE.AmbientLight(0x304060, 1.4))
  const dir = new THREE.DirectionalLight(0xbfe3ff, 1.6)
  dir.position.set(20, 30, 15)
  scene.add(dir)
  const point = new THREE.PointLight(0x22d3ee, 30, 60)
  point.position.set(0, 12, 0)
  scene.add(point)

  buildLab()
}

function buildLab() {
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(60, 60),
    new THREE.MeshStandardMaterial({ color: 0x0a1226, roughness: 0.9, metalness: 0.1 })
  )
  ground.rotation.x = -Math.PI / 2
  scene.add(ground)

  scene.add(new THREE.GridHelper(60, 30, 0x1c3a5e, 0x12233f))

  const wallMat = new THREE.MeshStandardMaterial({
    color: 0x123a5e,
    transparent: true,
    opacity: 0.18,
    side: THREE.DoubleSide
  })
  const wall = new THREE.Mesh(new THREE.BoxGeometry(40, 0.5, 30), wallMat)
  wall.position.y = 0.25
  scene.add(wall)

  const zoneMat = new THREE.MeshBasicMaterial({
    color: 0x22d3ee,
    transparent: true,
    opacity: 0.06,
    side: THREE.DoubleSide
  })
  const labZone = new THREE.Mesh(new THREE.PlaneGeometry(16, 24), zoneMat)
  labZone.rotation.x = -Math.PI / 2
  labZone.position.set(-9, 0.02, 0)
  scene.add(labZone)
  const deviceZone = new THREE.Mesh(new THREE.PlaneGeometry(16, 24), zoneMat)
  deviceZone.rotation.x = -Math.PI / 2
  deviceZone.position.set(9, 0.02, 0)
  scene.add(deviceZone)
}

function clearMeshes() {
  deviceMeshes.forEach((m) => {
    scene.remove(m)
    m.geometry.dispose()
    ;(m.material as THREE.Material).dispose()
  })
  deviceMeshes.clear()
}

function buildDevices(list: any[]) {
  if (!scene) return
  clearMeshes()
  const cols = 8
  const spacing = 2.2
  list.forEach((d, i) => {
    const row = Math.floor(i / cols)
    const col = i % cols
    const x = 9 - ((cols - 1) * spacing) / 2 + col * spacing
    const z = -11 + row * spacing
    const w = clamp(Number(d.unit_width) || 1, 0.6, 1.6)
    const h = clamp(Number(d.unit_height) || 1.2, 0.6, 2.0)
    const depth = clamp(Number(d.unit_length) || 1, 0.6, 1.6)
    const geometry = new THREE.BoxGeometry(w, h, depth)
    const color = STATUS_COLOR[statusOf(d)]
    const material = new THREE.MeshStandardMaterial({
      color,
      roughness: 0.35,
      metalness: 0.35,
      emissive: color,
      emissiveIntensity: 0.35
    })
    const mesh = new THREE.Mesh(geometry, material)
    mesh.position.set(x, h / 2, z)
    mesh.userData = { device: d, status: statusOf(d) }
    scene.add(mesh)
    mesh.add(
      new THREE.LineSegments(
        new THREE.EdgesGeometry(geometry),
        new THREE.LineBasicMaterial({ color: 0x7dd3fc, transparent: true, opacity: 0.6 })
      )
    )
    deviceMeshes.set(String(d.machine_unit_id ?? d.station_code ?? i), mesh)
  })
}

function updateDeviceColor(mesh: THREE.Mesh, status: DeviceStatus) {
  const color = STATUS_COLOR[status]
  const mat = mesh.material as THREE.MeshStandardMaterial
  mat.color.setHex(color)
  mat.emissive.setHex(color)
  mesh.userData.status = status
}

function applyDevices(list: any[]) {
  devices.value = list
  if (mode.value !== '3d') return
  list.forEach((d) => {
    const key = String(d.machine_unit_id ?? d.station_code)
    const mesh = deviceMeshes.get(key)
    if (mesh) updateDeviceColor(mesh, statusOf(d))
  })
}

function onPointerMove(event: PointerEvent) {
  if (!renderer) return
  const rect = renderer.domElement.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
}

async function onPointerClick(event: PointerEvent) {
  if (!renderer || !camera) return
  const rect = renderer.domElement.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const hits = raycaster.intersectObjects([...deviceMeshes.values()], false)
  if (hits.length) {
    await openDetail(hits[0].object.userData.device as Record<string, any>)
  } else {
    detail.value = null
  }
}

async function openDetail(device: Record<string, any>) {
  const id = device.machine_unit_id
  try {
    detail.value = id ? await fetchDeviceDetail(id) : device
  } catch {
    detail.value = device
  }
}

function animate() {
  animId = requestAnimationFrame(animate)
  controls?.update()
  if (renderer && scene && camera) renderer.render(scene, camera)
}

function resize() {
  const el = container.value
  if (!el || !renderer || !camera) return
  const w = el.clientWidth
  const h = el.clientHeight
  if (w < 8 || h < 8) return
  renderer.setSize(w, h, false)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
}

function fallback2d(reason?: string) {
  mode.value = '2d'
  if (reason) console.warn('数字孪生 3D 不可用，已回退 2D', reason)
  if (renderer) {
    renderer.dispose()
    renderer.domElement.remove()
    renderer = null
  }
  if (controls) {
    controls.dispose()
    controls = null
  }
  if (animId) {
    cancelAnimationFrame(animId)
    animId = 0
  }
}

async function loadDeviceList() {
  const raw = await fetchDevices()
  const list = Array.isArray(raw) ? raw : Array.isArray((raw as any)?.data) ? (raw as any).data : []
  devices.value = list
  if (mode.value === '3d') buildDevices(list)
}

onMounted(async () => {
  loading.value = true
  errorText.value = ''
  await nextTick()
  const el = container.value
  if (!el) {
    loading.value = false
    errorText.value = '数字孪生画布未找到'
    return
  }

  const sized = await waitForSize(el)
  if (sized) {
    try {
      initScene(el)
      resize()
      renderer?.domElement.addEventListener('pointermove', onPointerMove)
      renderer?.domElement.addEventListener('click', onPointerClick)
      animate()
      mode.value = '3d'
    } catch (e: any) {
      fallback2d(e?.message || String(e))
    }
  } else {
    fallback2d('画布尺寸为 0')
  }

  resizeObserver = new ResizeObserver(() => resize())
  resizeObserver.observe(el)
  window.addEventListener('resize', resize)

  try {
    await loadDeviceList()
  } catch (e: any) {
    errorText.value = e?.message || '设备列表加载失败，请确认 /api/dashboard/devices 可访问'
  } finally {
    loading.value = false
  }

  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  stomp.connect(`${proto}://${location.host}/ws`).catch(() => {})
  subscribeDeviceStatus((data) => {
    if (data?.devices) applyDevices(data.devices)
  })
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animId)
  window.removeEventListener('resize', resize)
  resizeObserver?.disconnect()
  resizeObserver = null
  controls?.dispose()
  if (renderer) {
    renderer.domElement.removeEventListener('pointermove', onPointerMove)
    renderer.domElement.removeEventListener('click', onPointerClick)
    renderer.dispose()
    renderer.domElement.remove()
    renderer = null
  }
})

function statusText(d: Record<string, any>) {
  return STATUS_LABEL[statusOf(d)] ?? '空闲'
}
</script>

<template>
  <div class="twin">
    <header class="page-header">
      <div class="page-title">实验室数字孪生</div>
      <div class="legend">
        <span class="lg"><i class="sw idle"></i>空闲</span>
        <span class="lg"><i class="sw running"></i>运行</span>
        <span class="lg"><i class="sw full"></i>满载</span>
        <span class="lg"><i class="sw error"></i>异常</span>
        <span class="hint">{{ mode === '3d' ? '🖱 拖拽旋转 · 滚轮缩放' : '2D 平面图（当前环境无法使用 WebGL）' }}</span>
      </div>
    </header>

    <div class="stage">
      <div ref="container" class="scene-canvas" />
      <div v-if="mode === '2d' && !loading" class="floor-2d">
        <button
          v-for="(d, i) in devices"
          :key="d.machine_unit_id || d.station_code || i"
          class="floor-cell"
          :class="statusClass(d)"
          type="button"
          @click="openDetail(d)"
        >
          <span class="floor-cell__code">{{ d.station_code || d.machine_unit_id || '--' }}</span>
          <span class="floor-cell__name">{{ d.instrument_name || d.machine_name || '机台' }}</span>
          <span class="floor-cell__st">{{ statusText(d) }}</span>
        </button>
        <div v-if="!devices.length && !errorText" class="graph-msg">暂无启用机台</div>
      </div>
      <div v-if="loading" class="graph-msg">加载设备模型…</div>
      <div v-else-if="errorText" class="graph-msg is-error">{{ errorText }}</div>
      <div v-else-if="mode === '3d' && !devices.length" class="graph-msg">暂无启用机台</div>
    </div>

    <transition name="fade">
      <div v-if="detail" class="glass-panel detail-pop">
        <div class="detail-title">{{ detail.machine_name || detail.instrument_name || '设备详情' }}</div>
        <div class="detail-row"><span>设备编号</span><b class="hud-num">{{ detail.station_code || '--' }}</b></div>
        <div class="detail-row"><span>仪器名称</span><b>{{ detail.instrument_name || '--' }}</b></div>
        <div class="detail-row"><span>当前实验</span><b>{{ detail.current_experiment || '--' }}</b></div>
        <div class="detail-row"><span>占用部门</span><b>{{ detail.application_department || '--' }}</b></div>
        <div class="detail-row"><span>占用事业部</span><b>{{ detail.business_unit || '--' }}</b></div>
        <div class="detail-row"><span>预计完成时间</span><b class="hud-num">{{ detail.expected_finish || '--' }}</b></div>
        <div class="detail-row">
          <span>当前负荷</span>
          <b class="hud-num">{{ statusText(detail) }} · 完成 {{ detail.completed_count ?? 0 }} 枚</b>
        </div>
        <button class="close" @click="detail = null">×</button>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.twin {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  position: relative;
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
.sw.idle { background: #34d399; box-shadow: 0 0 8px #34d399; }
.sw.running { background: #60a5fa; box-shadow: 0 0 8px #60a5fa; }
.sw.full { background: #fbbf24; box-shadow: 0 0 8px #fbbf24; }
.sw.error { background: #f87171; box-shadow: 0 0 8px #f87171; }
.hint {
  margin-left: 4px;
  color: var(--text-low);
}
.stage {
  flex: 1;
  min-height: 0;
  margin: 0 20px 20px;
  border-radius: var(--radius);
  overflow: hidden;
  border: 1px solid var(--border);
  position: relative;
  background: radial-gradient(800px 500px at 50% 50%, rgba(18, 35, 90, 0.55), rgba(5, 10, 26, 0.95));
}
.scene-canvas {
  position: absolute;
  inset: 0;
  cursor: grab;
}
.scene-canvas:active {
  cursor: grabbing;
}
.floor-2d {
  position: absolute;
  inset: 0;
  overflow: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
  padding: 16px;
  align-content: start;
}
.floor-cell {
  min-height: 88px;
  border-radius: 10px;
  border: 1px solid rgba(120, 160, 230, 0.28);
  background: rgba(8, 16, 38, 0.72);
  color: var(--text-hi);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
}
.floor-cell.is-idle { box-shadow: inset 0 0 0 1px rgba(52, 211, 153, 0.45); }
.floor-cell.is-running { box-shadow: inset 0 0 0 1px rgba(96, 165, 250, 0.5); }
.floor-cell.is-full { box-shadow: inset 0 0 0 1px rgba(251, 191, 36, 0.5); }
.floor-cell.is-error { box-shadow: inset 0 0 0 1px rgba(248, 113, 113, 0.55); }
.floor-cell__code {
  font-size: 14px;
  font-weight: 700;
}
.floor-cell__name,
.floor-cell__st {
  font-size: 12px;
  color: var(--text-mid);
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
  text-align: center;
  padding: 0 24px;
}
.graph-msg.is-error {
  color: #fca5a5;
}
.detail-pop {
  position: absolute;
  top: 90px;
  right: 30px;
  width: 280px;
  padding: 18px;
  z-index: 5;
}
.detail-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--primary);
}
.detail-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed rgba(120, 160, 230, 0.12);
  font-size: 13px;
}
.detail-row span {
  color: var(--text-mid);
  flex-shrink: 0;
}
.detail-row b {
  color: var(--text-hi);
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}
.close {
  position: absolute;
  top: 8px;
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
