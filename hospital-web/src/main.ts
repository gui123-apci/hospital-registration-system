import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import App from './App.vue'
import router, { handleAuthFailure } from './router'
import { setAuthFailureHandler } from './api/http'

// 接口层只上报认证失败，跳转由路由统一处理（避免整页刷新与守卫重复跳转）
setAuthFailureHandler(handleAuthFailure)

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
