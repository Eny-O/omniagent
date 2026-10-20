import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// Element Plus 全量样式：项目依赖 unplugin 按需引入，但 ElMessageBox / ElMessage 等
// 函数式调用的样式（含遮罩层 .el-overlay）在显式 import 下易被漏引入，导致弹窗无样式/无遮罩。
// 此处显式引入全量 CSS 一次性兜底，与按需组件样式不冲突。
import 'element-plus/dist/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
