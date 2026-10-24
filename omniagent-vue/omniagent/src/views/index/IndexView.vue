<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import AgentChatFab from '@/views/agent/AgentChatFab.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => {
  // 详情页 /tickets/:id 高亮「工单管理」父菜单
  const p = route.path
  if (p.startsWith('/tickets/')) return '/tickets'
  return p
})
const currentUserName = computed(() => userStore.user?.realName || userStore.user?.username || '未登录')

onMounted(() => {
  // 页面刷新后如果 Pinia 中没有用户信息，尝试从后端 Session 恢复
  if (!userStore.isLoggedIn) {
    userStore.fetchCurrentUser()
  }
})

const handleLogout = () => {
  userStore.doLogout()
}

// 菜单按当前角色显隐：
// - REPORTER：仅「我的报障」
// - HANDLER / SUPERVISOR：仅管理类菜单（工单/用户/部门）
// - ADMIN：管理类菜单 + 「我的报障」（便于以报障视角调试）
// - 调试期（角色为空）：展示全部入口
const visibleMenuItems = computed(() => {
  const role = userStore.currentRole
  const items: { path: string; title: string }[] = []
  if (role === null) {
    items.push({ path: '/tickets', title: '工单管理' })
    items.push({ path: '/users', title: '用户管理' })
    items.push({ path: '/departments', title: '部门管理' })
    items.push({ path: '/reporter-tickets', title: '我的报障' })
    return items
  }
  if (role === 'REPORTER') {
    items.push({ path: '/reporter-tickets', title: '我的报障' })
  } else {
    items.push({ path: '/tickets', title: '工单管理' })
    items.push({ path: '/users', title: '用户管理' })
    items.push({ path: '/departments', title: '部门管理' })
    if (role === 'ADMIN') {
      items.push({ path: '/reporter-tickets', title: '我的报障' })
    }
  }
  return items
})

const handleMenuSelect = (path: string) => {
  router.push(path)
}
</script>

<template>
  <div class="common-layout">
    <el-container class="layout-container">
      <el-aside width="220px" class="layout-aside">
        <div class="aside-header">
          <div class="logo-box">
            <svg class="logo-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="#409eff" />
              <path d="M2 17L12 22L22 17" stroke="#409eff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M2 12L12 17L22 12" stroke="#409eff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span class="logo-text">OmniAgent</span>
          </div>
        </div>
        <el-menu
          :default-active="activeMenu"
          class="layout-menu"
          router
          @select="handleMenuSelect"
        >
          <el-menu-item
            v-for="item in visibleMenuItems"
            :key="item.path"
            :index="item.path"
            class="menu-item"
          >
            <!-- 工单管理图标 -->
            <svg
              v-if="item.path === '/tickets'"
              class="menu-icon"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M9 5H7C5.89543 5 5 5.89543 5 7V19C5 20.1046 5.89543 21 7 21H17C18.1046 21 19 20.1046 19 19V7C19 5.89543 18.1046 5 17 5H15"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path d="M9 12H15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M9 16H15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M12 5V3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <!-- 用户管理图标 -->
            <svg
              v-else-if="item.path === '/users'"
              class="menu-icon"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M17 21V19C17 17.9391 16.5786 16.9217 15.8284 16.1716C15.0783 15.4214 14.0609 15 13 15H5C3.93913 15 2.92172 15.4214 2.17157 16.1716C1.42143 16.9217 1 17.9391 1 19V21"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                d="M9 11C11.2091 11 13 9.20914 13 7C13 4.79086 11.2091 3 9 3C6.79086 3 5 4.79086 5 7C5 9.20914 6.79086 11 9 11Z"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                d="M23 21V19C22.9993 18.1137 22.7044 17.2528 22.1614 16.5523C21.6184 15.8519 20.8581 15.3516 20 15.13"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                d="M16 3.13C16.8604 3.35031 17.623 3.85071 18.1676 4.55232C18.7122 5.25392 19.0078 6.11683 19.0078 7.005C19.0078 7.89318 18.7122 8.75608 18.1676 9.45769C17.623 10.1593 16.8604 10.6597 16 10.88"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <!-- 部门管理图标 -->
            <svg
              v-else-if="item.path === '/departments'"
              class="menu-icon"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M12 2V6"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                d="M12 18V22"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                d="M2 12H6"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                d="M18 12H22"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <rect
                x="8"
                y="6"
                width="8"
                height="4"
                rx="1"
                stroke="currentColor"
                stroke-width="2"
              />
              <rect
                x="2"
                y="14"
                width="6"
                height="4"
                rx="1"
                stroke="currentColor"
                stroke-width="2"
              />
              <rect
                x="16"
                y="14"
                width="6"
                height="4"
                rx="1"
                stroke="currentColor"
                stroke-width="2"
              />
            </svg>
            <!-- 我的报障图标 -->
            <svg
              v-else-if="item.path === '/reporter-tickets'"
              class="menu-icon"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M9 5H7C5.89543 5 5 5.89543 5 7V19C5 20.1046 5.89543 21 7 21H17C18.1046 21 19 20.1046 19 19V7C19 5.89543 18.1046 5 17 5H15"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                d="M12 3V5"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                d="M12 12V17"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                d="M9.5 14.5L12 12L14.5 14.5"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <svg
              v-else
              class="menu-icon"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <rect x="4" y="8" width="16" height="11" rx="2" stroke="currentColor" stroke-width="2" />
              <path d="M12 8V4" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
              <circle cx="12" cy="3" r="1.5" fill="currentColor" />
              <circle cx="9" cy="13" r="1.5" fill="currentColor" />
              <circle cx="15" cy="13" r="1.5" fill="currentColor" />
            </svg>
            <span class="menu-title">{{ item.title }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-container class="layout-right">
        <el-header class="layout-header">
          <div class="header-title">工单管理系统</div>
          <div class="header-right">
            <span class="header-user">{{ currentUserName }}</span>
            <el-button
              type="danger"
              link
              class="logout-btn"
              @click="handleLogout"
            >
              退出登录
            </el-button>
          </div>
        </el-header>
        <el-main class="layout-main">
          <RouterView />
        </el-main>
        <!-- 全局悬浮 AI 助手：仅在「我的保障/工单列表/详情」页显示（内部按路由 v-show 控制） -->
        <AgentChatFab />
      </el-container>
    </el-container>
  </div>
</template>

<style scoped>
.common-layout {
  height: 100vh;
}

.layout-container {
  height: 100%;
}

.layout-aside {
  display: flex;
  flex-direction: column;
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.04);
}

.aside-header {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid #ebeef5;
}

.logo-box {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  width: 28px;
  height: 28px;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 0.5px;
}

.layout-menu {
  flex: 1;
  padding: 12px 10px;
  border-right: none;
}

.layout-menu :deep(.el-menu-item) {
  height: 46px;
  line-height: 46px;
  margin-bottom: 4px;
  padding: 0 14px !important;
  border-radius: 6px;
  color: #606266;
  transition: all 0.2s ease;
}

.layout-menu :deep(.el-menu-item:hover) {
  background-color: #ecf5ff;
  color: #409eff;
}

.layout-menu :deep(.el-menu-item.is-active) {
  background-color: #409eff;
  color: #ffffff;
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.3);
}

.menu-item {
  display: flex;
  align-items: center;
}

.menu-icon {
  width: 18px;
  height: 18px;
  margin-right: 10px;
  flex-shrink: 0;
}

.menu-title {
  font-size: 14px;
}

.layout-right {
  background-color: #f5f7fa;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  background-color: #ffffff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.header-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
}

.header-user {
  font-size: 14px;
  color: #606266;
}

.logout-btn {
  margin-left: 16px;
  font-size: 14px;
}

.layout-main {
  padding: 20px;
  overflow-y: auto;
}
</style>
