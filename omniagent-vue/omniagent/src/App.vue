<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="app-wrapper">
    <header class="app-header">
      <div class="header-left">
        <span class="app-title">OmniAgent 工单助手</span>
      </div>
      <nav class="header-nav">
        <RouterLink to="/tickets" class="nav-link" active-class="nav-link-active">
          工单管理
        </RouterLink>
      </nav>
    </header>
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-wrapper {
  min-height: 100vh;
  background-color: #f5f7fa;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 24px;
  background-color: #ffffff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.header-left {
  display: flex;
  align-items: center;
}

.app-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 16px;
}

.nav-link {
  color: #606266;
  text-decoration: none;
  font-size: 14px;
  padding: 4px 8px;
  border-radius: 4px;
  transition: color 0.2s, background-color 0.2s;
}

.nav-link:hover {
  color: #409eff;
  background-color: #ecf5ff;
}

.nav-link-active {
  color: #409eff;
  background-color: #ecf5ff;
  font-weight: 500;
}

.app-main {
  padding: 20px;
}
</style>
