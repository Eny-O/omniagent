<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)

const menuItems = [
  {
    path: '/tickets',
    title: '工单管理',
  },
]

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
            v-for="item in menuItems"
            :key="item.path"
            :index="item.path"
            class="menu-item"
          >
            <svg class="menu-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M9 5H7C5.89543 5 5 5.89543 5 7V19C5 20.1046 5.89543 21 7 21H17C18.1046 21 19 20.1046 19 19V7C19 5.89543 18.1046 5 17 5H15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M9 12H15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M9 16H15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M12 5V3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span class="menu-title">{{ item.title }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-container class="layout-right">
        <el-header class="layout-header">
          <div class="header-title">工单管理系统</div>
          <div class="header-right">
            <span class="header-user">管理员</span>
          </div>
        </el-header>
        <el-main class="layout-main">
          <RouterView />
        </el-main>
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

.layout-main {
  padding: 20px;
  overflow-y: auto;
}
</style>
