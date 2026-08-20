<script setup lang="ts">
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchDevices, fetchDeviceDetail } from '@/api/dashboard'
import { stomp, subscribeDeviceStatus } from '@/ws/websocket'

/**
 * 实验室数字孪生：Three.js 三维场景。
 * 设备模型由 lab_machine(父) × lab_machine_unit(机台) 生成，腔体尺寸驱动几何；
 * 状态颜色：空闲绿 / 运行蓝 / 满载黄 / 异常红；点击设备查看详情；WebSocket 实时更新。
 */
const container = ref<HTMLDivElement>()
const detail = ref<Record<string, any> | null>(null)
const loading = ref(true)

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

let renderer: THREE.WebGLRenderer
let scene: THREE.Scene
let camera: THREE.PerspectiveCamera
let controls: OrbitControls
let raycaster = new THREE.Raycaster()
let pointer = new THREE.Vector2()
const deviceMeshes = new Map<string, THREE.Mesh>()
let animId = 0
let resizeObserver: ResizeObserver | null = null

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

function statusOf(d: Record<string, any>): DeviceStatus {
  if (d.status) return d.status as DeviceStatus
  if (Number(d.is_full) === 1) return 'full'
  if (d.load_id) return 'running'
  return 'idle'
}

function initScene() {
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x050a1a)
  scene.fog = new THREE.Fog(0x050a1a, 40, 120)

  camera = new THREE.PerspectiveCamera(55, 1, 0.1, 300)
  camera.position.set(22, 18, 26)

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  container.value!.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.target.set(0, 1.5, 0)
  controls.enableDamping = true
  controls.maxPolarAngle = Math.PI / 2.05

  // 灯光
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
  // 地面
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(60, 60),
    new THREE.MeshStandardMaterial({ color: 0x0a1226, roughness: 0.9, metalness: 0.1 })
  )
  ground.rotation.x = -Math.PI / 2
  scene.add(ground)

  // 网格（实验区域 + 设备区域）
  const grid = new THREE.GridHelper(60, 30, 0x1c3a5e, 0x12233f)
  scene.add(grid)

  // 实验室建筑：半透明墙体
  const wallMat = new THREE.MeshStandardMaterial({
    color: 0x123a5e,
    transparent: true,
    opacity: 0.18,
    side: THREE.DoubleSide
  })
  const wall = new THREE.Mesh(new THREE.BoxGeometry(40, 0.5, 30), wallMat)
  wall.position.y = 0.25
  scene.add(wall)

  // 区域标记：实验区（左）与设备区（右）发光平面
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

function buildDevices(devices: any[]) {
  deviceMeshes.forEach((m) => {
    scene.remove(m)
    m.geometry.dispose()
    ;(m.material as THREE.Material).dispose()
  })
  deviceMeshes.clear()

  const cols = 8
  const spacing = 2.2
  devices.forEach((d, i) => {
    const row = Math.floor(i / cols)
    const col = i % cols
    const x = 9 - (cols - 1) * spacing / 2 + col * spacing
    const z = -11 + row * spacing

    // 尺寸由腔体长宽高驱动（归一化到 0.6~1.6）
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

    // 发光边框
    const edges = new THREE.LineSegments(
      new THREE.EdgesGeometry(geometry),
      new THREE.LineBasicMaterial({ color: 0x7dd3fc, transparent: true, opacity: 0.6 })
    )
    mesh.add(edges)

    deviceMeshes.set(String(d.machine_unit_id ?? d.station_code ?? i), mesh)
  })
}

function clamp(v: number, min: number, max: number) {
  return Math.min(Math.max(v, min), max)
}

function updateDeviceColor(mesh: THREE.Mesh, status: DeviceStatus) {
  const color = STATUS_COLOR[status]
  const mat = mesh.material as THREE.MeshStandardMaterial
  mat.color.setHex(color)
  mat.emissive.setHex(color)
  mesh.userData.status = status
}

function applyDevices(devices: any[]) {
  devices.forEach((d) => {
    const key = String(d.machine_unit_id ?? d.station_code)
    const mesh = deviceMeshes.get(key)
    if (mesh) updateDeviceColor(mesh, statusOf(d))
  })
}

function onPointerMove(event: PointerEvent) {
  if (!renderer) return
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
}

async function onPointerClick(event: PointerEvent) {
  if (!renderer) return
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const hits = raycaster.intersectObjects([...deviceMeshes.values()], false)
  if (hits.length) {
    const device = hits[0].object.userData.device as Record<string, any>
    const id = device.machine_unit_id
    try {
      detail.value = await fetchDeviceDetail(id)
    } catch {
      detail.value = device
    }
  } else {
    detail.value = null
  }
}

function animate() {
  animId = requestAnimationFrame(animate)
  controls.update()
  renderer.render(scene, camera)
}

function resize() {
  if (!container.value || !renderer) return
  const { clientWidth, clientHeight } = container.value
  if (clientWidth < 8 || clientHeight < 8) return
  renderer.setSize(clientWidth, clientHeight)
  camera.aspect = clientWidth / clientHeight
  camera.updateProjectionMatrix()
}

onMounted(async () => {
  const el = container.value
  if (!el) return
  // 等容器有真实尺寸再初始化，避免 setSize(0,0) 导致画面空白
  await waitForSize(el)
  try {
    initScene()
  } catch (e) {
    console.error('WebGL 初始化失败', e)
    loading.value = false
    return
  }
  resize()
  window.addEventListener('resize', resize)
  renderer.domElement.addEventListener('pointermove', onPointerMove)
  renderer.domElement.addEventListener('click', onPointerClick)
  // 容器尺寸变化时重算（flex 布局/tab 切换/窗口缩放）
  resizeObserver = new ResizeObserver(() => resize())
  resizeObserver.observe(el)
  animate()
  try {
    const devices = await fetchDevices()
    buildDevices(devices)
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
  renderer?.dispose()
  renderer?.domElement.remove()
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
      </div>
    </header>

    <div ref="container" class="scene"></div>

    <div v-if="loading" class="loading">加载设备模型…</div>

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
}
.legend {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--text-mid);
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
.scene {
  flex: 1;
  margin: 0 20px 20px;
  border-radius: var(--radius);
  overflow: hidden;
  border: 1px solid var(--border);
  cursor: grab;
}
.scene:active {
  cursor: grabbing;
}
.loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: var(--text-mid);
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
