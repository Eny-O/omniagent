import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/tickets',
    },
    {
      path: '/tickets',
      name: 'TicketList',
      component: () => import('@/views/ticket/TicketListView.vue'),
    },
  ],
})

export default router
