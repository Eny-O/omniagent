import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

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
      redirect: '/tickets',
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

// 路由守卫：未登录用户只能访问登录页；已登录用户访问登录页自动跳转到工单列表
// Vue Router 4 推荐直接 return 值，不再使用 next() 回调
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // 如果 Pinia 中已有用户信息，直接判断
  if (userStore.isLoggedIn) {
    if (to.path === '/login') {
      return '/tickets'
    }
    return true
  }

  // 未登录时访问登录页，直接放行
  if (to.path === '/login') {
    return true
  }

  // 访问其他页面时，先请求后端确认 Session 是否有效
  try {
    const isLogin = await userStore.fetchCurrentUser()
    if (isLogin) {
      return true
    }
  } catch {
    // fetchCurrentUser 内部已经会跳转登录页，这里直接拒绝导航即可
  }
  return '/login'
})

export default router
