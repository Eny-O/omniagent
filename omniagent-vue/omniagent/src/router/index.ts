import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    roles?: string[]
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      name: 'Layout',
      component: () => import('@/views/index/IndexView.vue'),
      children: [
        {
          path: '/tickets',
          name: 'TicketList',
          component: () => import('@/views/ticket/TicketListView.vue'),
        },
        {
          path: '/tickets/:id',
          name: 'TicketDetail',
          component: () => import('@/views/ticket/TicketDetailView.vue'),
        },
        {
          path: '/reporter-tickets',
          name: 'ReporterTickets',
          component: () => import('@/views/ticket/ReporterTicketView.vue'),
          meta: { roles: ['REPORTER'] },
        },
        {
          path: '/users',
          name: 'UserList',
          component: () => import('@/views/user/UserListView.vue'),
        },
        {
          path: '/departments',
          name: 'DepartmentList',
          component: () => import('@/views/department/DepartmentListView.vue'),
        },
      ],
    },
  ],
})

// 路由守卫：
// 1) 未登录只能访问登录页；其余先确认 Session 是否有效
// 2) 按角色分流：报障人(REPORTER)进入「我的报障」，其余进入「工单管理」
// 3) 调试期（角色为空）放行所有页面，便于联调
// Vue Router 4 推荐直接 return 值，不再使用 next() 回调
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // 未登录：登录页直接放行；其余先确认 Session 是否有效
  if (!userStore.isLoggedIn) {
    if (to.path === '/login') return true
    try {
      const ok = await userStore.fetchCurrentUser()
      if (!ok) return '/login'
    } catch {
      return '/login'
    }
  }

  // 此时已确认登录；若仍访问登录页则按角色跳默认首页
  if (to.path === '/login') {
    return userStore.currentRole === 'REPORTER' ? '/reporter-tickets' : '/tickets'
  }

  // 根路径按角色分流
  if (to.path === '/') {
    return userStore.currentRole === 'REPORTER' ? '/reporter-tickets' : '/tickets'
  }

  // 工单管理：报障人不可进入，自动转入「我的报障」
  if (to.path === '/tickets' && userStore.currentRole === 'REPORTER') {
    return '/reporter-tickets'
  }

  // 我的报障：仅报障人与管理员（调试）可进入；处理人/主管不可
  if (
    to.path === '/reporter-tickets' &&
    (userStore.currentRole === 'HANDLER' || userStore.currentRole === 'SUPERVISOR')
  ) {
    return '/tickets'
  }

  return true
})

export default router
