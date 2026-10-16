import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
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
          path: '/users',
          name: 'UserList',
          component: () => import('@/views/user/UserListView.vue'),
        },
      ],
    },
  ],
})

export default router
