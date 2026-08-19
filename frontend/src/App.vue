<script setup lang="ts">
import { useRoute } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()

const links = [
  { path: '/dashboard', label: '运营驾驶舱' },
  { path: '/twin', label: '数字孪生' },
  { path: '/analysis', label: '数据分析' },
  { path: '/ai', label: 'AI 分析' },
  { path: '/relation', label: '关系分析' }
]

const active = computed(() => route.path)
</script>

<template>
  <div class="app-shell">
    <nav class="top-nav">
      <div class="brand">🧪 智能实验室数字孪生运营驾驶舱</div>
      <div class="nav-links">
        <router-link
          v-for="l in links"
          :key="l.path"
          :to="l.path"
          class="nav-link"
          :class="{ active: active === l.path }"
        >
          {{ l.label }}
        </router-link>
      </div>
    </nav>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.top-nav {
  flex: 0 0 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: linear-gradient(180deg, rgba(12, 22, 48, 0.95), rgba(10, 18, 40, 0.8));
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(10px);
  z-index: 10;
}
.brand {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-hi);
  letter-spacing: 1px;
}
.nav-links {
  display: flex;
  gap: 4px;
}
.nav-link {
  padding: 6px 16px;
  border-radius: 8px;
  color: var(--text-mid);
  text-decoration: none;
  font-size: 13px;
  transition: all 0.2s;
}
.nav-link:hover {
  color: var(--text-hi);
  background: rgba(34, 211, 238, 0.1);
}
.nav-link.active {
  color: var(--primary);
  background: rgba(34, 211, 238, 0.16);
  box-shadow: inset 0 0 0 1px var(--border-strong);
}
.app-main {
  flex: 1;
  overflow: hidden;
}
</style>
