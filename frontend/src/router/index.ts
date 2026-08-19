import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { title: '运营驾驶舱' }
  },
  {
    path: '/twin',
    name: 'twin',
    component: () => import('@/views/DigitalTwinView.vue'),
    meta: { title: '数字孪生' }
  },
  {
    path: '/analysis',
    name: 'analysis',
    component: () => import('@/views/AnalysisView.vue'),
    meta: { title: '数据分析' }
  },
  {
    path: '/ai',
    name: 'ai',
    component: () => import('@/views/AiView.vue'),
    meta: { title: 'AI 智能分析' }
  },
  {
    path: '/relation',
    name: 'relation',
    component: () => import('@/views/RelationView.vue'),
    meta: { title: '关系分析' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || '驾驶舱'
  document.title = `${title} - 智能实验室数字孪生运营驾驶舱`
})

export default router
