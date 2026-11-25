# 智慧医院前端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `D:\hospital-project\hospital-web` 中实现患者、医生、管理三类角色的可联调前端，并保持后端目录只读。

**Architecture:** 使用单个 Vue 3 SPA，按角色划分路由和布局，共享 API 客户端、鉴权、类型与基础组件。开发环境通过 Vite 代理 `/api` 到 `http://localhost:8080`，医生排班先使用演示数据，所有选择控件显示名称并提交 id。

**Tech Stack:** Vue 3、Vite、TypeScript、Vue Router、Pinia、Element Plus、Axios、Vitest、Vue Test Utils、jsdom。

**Spec:** `D:\hospital-project\hospital-web\docs\superpowers\specs\2026-11-25-hospital-frontend-design.md`

## Global Constraints

- 前端只能写入 `D:\hospital-project\hospital-web`。
- `D:\hospital-project\hospital-service`、`sql`、后端配置和 Docker 相关文件均为只读。
- API 基础地址为 `/api`，开发代理目标为 `http://localhost:8080`，代理移除 `/api` 前缀。
- 日期与时间按 `Asia/Shanghai` 解释；浏览器验收使用该时区。
- JWT 保存在 `sessionStorage` 的 `hospital_token` 键中。
- 角色值为 `0=患者`、`1=管理员`、`2=医生`。
- 所有选择控件显示人类可读名称，提交对应数字 id，不要求用户输入原始 id。
- 业务错误即使 HTTP 200 也必须检查 `code !== 200`。
- 医生排班当前使用 `src/mocks/doctorSchedules.ts` 中的演示数据。
- 不实现管理端数据看板、订单审核、医生账号绑定或真实支付。
- 浏览器验收使用可见 Chrome-D：`C:\Users\69389\Desktop\Chrome-D.lnk`。
- 未经用户明确要求，不执行 `git add` 或 `git commit`。

---

## 文件结构

| 路径 | 职责 |
|---|---|
| `hospital-web/package.json` | 依赖与脚本 |
| `hospital-web/vite.config.ts` | 开发代理、别名、Vitest 配置 |
| `hospital-web/tsconfig.json` | TypeScript 配置 |
| `hospital-web/.env.example` | 本地联调环境变量示例 |
| `hospital-web/src/vite-env.d.ts` | Vite 环境变量类型 |
| `hospital-web/index.html` | SPA 入口 |
| `hospital-web/src/main.ts` | 应用注册 |
| `hospital-web/src/App.vue` | 根组件与路由出口 |
| `hospital-web/src/types/models.ts` | 后端请求与响应类型 |
| `hospital-web/src/api/http.ts` | Axios 封装与统一错误 |
| `hospital-web/src/api/*.ts` | 按业务域拆分接口 |
| `hospital-web/src/stores/auth.ts` | 登录态与角色状态 |
| `hospital-web/src/router/index.ts` | 路由与权限守卫 |
| `hospital-web/src/layouts/*.vue` | 患者、医生、管理端布局 |
| `hospital-web/src/components/*.vue` | 共享组件 |
| `hospital-web/src/composables/*.ts` | 异步加载、倒计时等组合逻辑 |
| `hospital-web/src/utils/*.ts` | 枚举、时间与订单状态判断 |
| `hospital-web/src/views/patient/*.vue` | 患者端页面 |
| `hospital-web/src/views/doctor/*.vue` | 医生端页面 |
| `hospital-web/src/views/admin/*.vue` | 管理端页面 |
| `hospital-web/src/mocks/doctorSchedules.ts` | 医生演示排班 |
| `hospital-web/src/styles/tokens.css` | 视觉变量与全局基础样式 |
| `hospital-web/tests/**` | 单元与组件测试 |

---

### Task 1: 搭建独立前端工程与测试工具链

**Files:**
- Create: `hospital-web/package.json`
- Create: `hospital-web/vite.config.ts`
- Create: `hospital-web/tsconfig.json`
- Create: `hospital-web/.env.example`
- Create: `hospital-web/src/vite-env.d.ts`
- Create: `hospital-web/index.html`
- Create: `hospital-web/src/main.ts`
- Create: `hospital-web/src/App.vue`
- Create: `hospital-web/tests/setup.ts`
- Create: `hospital-web/tests/smoke.spec.ts`

**Interfaces:**
- Consumes: 无。
- Produces: `npm run dev`、`npm run build`、`npm test`、`npm run type-check` 脚本；Vite `/api` 代理；Vitest jsdom 环境。

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "hospital-web",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest",
    "type-check": "vue-tsc --noEmit"
  }
}
```

- [ ] **Step 2: 安装依赖**

Run:

```bash
cd "D:/hospital-project/hospital-web"
npm install vue vue-router pinia element-plus axios
npm install -D vite @vitejs/plugin-vue typescript vue-tsc vitest @vue/test-utils jsdom
```

Expected: 生成 `package-lock.json` 和 `node_modules`，后端目录不出现变化。

- [ ] **Step 3: 配置 Vite 与 Vitest**

Create `hospital-web/vite.config.ts`:

```ts
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.spec.ts']
  }
})
```

- [ ] **Step 4: 配置 TypeScript**

Create `hospital-web/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "Bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "preserve",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.vue", "tests/**/*.ts", "vite.config.ts"]
}
```

Create `hospital-web/.env.example`:

```dotenv
VITE_API_BASE_URL=/api
VITE_DEMO_SCHEDULE_IDS=101,102,103
```

Create `hospital-web/src/vite-env.d.ts`:

```ts
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_DEMO_SCHEDULE_IDS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
```

- [ ] **Step 5: 创建入口文件**

Create `hospital-web/index.html`:

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>云和医院</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

Create `hospital-web/src/main.ts`:

```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import App from './App.vue'

createApp(App).use(createPinia()).use(ElementPlus).mount('#app')
```

Create `hospital-web/src/App.vue`:

```vue
<template>
  <main class="app-bootstrap">智慧医院前端工程已启动</main>
</template>
```

Create `hospital-web/tests/setup.ts`:

```ts
import { afterEach } from 'vitest'
import { cleanup } from '@vue/test-utils'

afterEach(() => {
  cleanup()
  sessionStorage.clear()
})
```

- [ ] **Step 6: 写第一个失败测试**

Create `hospital-web/tests/smoke.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'

describe('frontend scaffold', () => {
  it('runs Vitest in jsdom', () => {
    expect(typeof document).toBe('object')
    expect(sessionStorage).toBeDefined()
  })
})
```

- [ ] **Step 7: 运行测试并确认通过**

Run: `npm test`
Expected: 1 test passed。

- [ ] **Step 8: 创建基础主题文件并完成构建**

Create `hospital-web/src/styles/tokens.css`:

```css
:root {
  --color-brand-deep: #12394a;
  --color-brand: #087f7a;
  --color-brand-soft: #e4f3f0;
  --color-page: #f4f8fa;
  --color-border: #dce7eb;
  --color-text: #183b4a;
  --color-muted: #6b818c;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  color: var(--color-text);
  background: var(--color-page);
}

* { box-sizing: border-box; }
body { margin: 0; min-width: 360px; }
button, input, textarea, select { font: inherit; }
:focus-visible { outline: 3px solid rgba(8, 127, 122, .35); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; }
}
```

Run: `npm run build`
Expected: 构建成功，`dist/` 生成，后端目录无新增改动。

---

### Task 2: 建立领域类型与统一 API 客户端

**Files:**
- Create: `hospital-web/src/types/models.ts`
- Create: `hospital-web/src/api/http.ts`
- Create: `hospital-web/tests/api/http.spec.ts`

**Interfaces:**
- Consumes: Task 1 的 Axios 与 Vitest。
- Produces:
  - `Result<T>`、`PageResult<T>`、`Role`、`UserInfo`、`LoginResponse`
  - `Patient`、`Department`、`Doctor`、`Schedule`、`Order`、`Report`、`WaitingPatient`、`TriageResult`
  - `ApiError`
  - `http.get<T>()`、`http.post<T>()`、`http.put<T>()`、`http.delete<T>()`

- [ ] **Step 1: 写 API 客户端失败测试**

Create `hospital-web/tests/api/http.spec.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { ApiError, http } from '@/api/http'

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() }
      },
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    }))
  }
}))

describe('http client contract', () => {
  beforeEach(() => vi.clearAllMocks())

  it('exposes business error code and message', () => {
    const error = new ApiError(2051, '该时段号源已约满')
    expect(error.code).toBe(2051)
    expect(error.message).toBe('该时段号源已约满')
  })

  it('creates an axios instance', () => {
    expect(http).toBeDefined()
    expect(axios.create).toHaveBeenCalledWith({ baseURL: '/api', timeout: 15000 })
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/api/http.spec.ts`
Expected: FAIL，`@/api/http` 不存在。

- [ ] **Step 3: 创建领域类型**

Create `hospital-web/src/types/models.ts`:

```ts
export type Role = 0 | 1 | 2
export type TimeSlot = 1 | 2 | 3
export type OrderStatus = 0 | 1 | 2 | 3 | 4 | 5

export interface Result<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface UserInfo {
  id: number
  username: string
  phone?: string
  role: Role
  roleName: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

export interface Patient {
  id: number
  name: string
  idCard?: string
  relation: number
  phone?: string
}

export interface Department {
  id: number
  name: string
  description?: string
  createTime?: string
  updateTime?: string
}

export interface Doctor {
  id: number
  departmentId: number
  name: string
  title?: string
  intro?: string
  createTime?: string
  updateTime?: string
}

export interface Schedule {
  id: number
  doctorId: number
  departmentId: number
  workDate: string
  timeSlot: TimeSlot
  totalNum: number
  leftNum: number
  price: number
  status: 0 | 1
}

export interface Order {
  id: number
  orderNo: string
  patientId: number
  patientName: string
  scheduleId: number
  workDate: string
  timeSlot: TimeSlot
  amount: number
  status: OrderStatus
  statusDesc: string
  deadlineTime?: string
  createTime: string
}

export interface Report {
  id: number
  patientId: number
  patientName: string
  orderId: number
  reportType?: string
  title: string
  content?: string
  reportTime?: string
}

export interface WaitingPatient {
  seq: number
  orderId: number
  orderNo: string
  patientName: string
  signTime: string
}

export interface TriageResult {
  departmentId?: number
  departmentName?: string
  reason: string
  urgent: boolean
  source: string
}
```

- [ ] **Step 4: 实现统一 API 客户端**

Create `hospital-web/src/api/http.ts`:

```ts
import axios, { type AxiosRequestConfig } from 'axios'
import type { Result } from '@/types/models'

const TOKEN_KEY = 'hospital_token'

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
    public httpStatus?: number
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const instance = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api', timeout: 15000 })

instance.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

instance.interceptors.response.use(
  (response) => {
    const payload = response.data as Result<unknown>
    if (payload.code !== 200) {
      throw new ApiError(payload.code, payload.msg || '操作失败', response.status)
    }
    return payload.data
  },
  (error) => {
    const status = error.response?.status as number | undefined
    const payload = error.response?.data as Result<unknown> | undefined
    if (status === 401) {
      sessionStorage.removeItem(TOKEN_KEY)
      if (window.location.pathname !== '/login') window.location.assign('/login')
    }
    if (status === 403 && window.location.pathname !== '/forbidden') {
      window.location.assign('/forbidden')
    }
    return Promise.reject(
      new ApiError(
        payload?.code ?? status ?? 5000,
        payload?.msg ?? '网络异常，请稍后重试',
        status
      )
    )
  }
)

export const http = {
  get<T>(url: string, config?: AxiosRequestConfig) {
    return instance.get<unknown, T>(url, config)
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return instance.post<unknown, T>(url, data, config)
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return instance.put<unknown, T>(url, data, config)
  },
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return instance.delete<unknown, T>(url, config)
  }
}

export { TOKEN_KEY }
```

- [ ] **Step 5: 运行测试与类型检查**

Run:

```bash
npm test -- tests/api/http.spec.ts
npm run type-check
```

Expected: 测试通过，TypeScript 无错误。

---

### Task 3: 实现认证、路由守卫与三类布局

**Files:**
- Create: `hospital-web/src/api/auth.ts`
- Create: `hospital-web/src/stores/auth.ts`
- Modify: `hospital-web/src/main.ts`
- Modify: `hospital-web/src/App.vue`
- Create: `hospital-web/src/router/index.ts`
- Create: `hospital-web/src/router/guard.ts`
- Create: `hospital-web/src/layouts/AuthLayout.vue`
- Create: `hospital-web/src/layouts/PatientLayout.vue`
- Create: `hospital-web/src/layouts/DoctorLayout.vue`
- Create: `hospital-web/src/layouts/AdminLayout.vue`
- Create: `hospital-web/src/views/NotFound.vue`
- Create: `hospital-web/src/views/Forbidden.vue`
- Create: `hospital-web/src/views/PlaceholderView.vue`
- Test: `hospital-web/tests/auth/store.spec.ts`
- Test: `hospital-web/tests/router/guards.spec.ts`

**Interfaces:**
- Consumes: Task 2 的 `http`、`TOKEN_KEY`、认证类型。
- Produces:
  - `useAuthStore()`，包含 `token`、`user`、`loading`、`isAuthenticated`、`homePath`
  - `login(username, password)`、`register(phone, password)`、`restore()`、`logout()`
  - 路由 `login`、`register`、`patient-home`、`doctor-workbench`、`admin-departments`
  - `routeDecision(toName, requiresAuth, roles, auth)`
  - `router.beforeEach`

- [ ] **Step 1: 写认证 store 失败测试**

Create `hospital-web/tests/auth/store.spec.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  me: vi.fn()
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('stores token and user after login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: 'token-1',
      tokenType: 'Bearer',
      expiresIn: 100,
      user: { id: 1, username: '13800000000', role: 0, roleName: '患者' }
    })
    const store = useAuthStore()
    await store.login('13800000000', '123456')
    expect(store.token).toBe('token-1')
    expect(store.user?.role).toBe(0)
    expect(sessionStorage.getItem('hospital_token')).toBe('token-1')
  })

  it('clears session on logout', () => {
    sessionStorage.setItem('hospital_token', 'token-1')
    const store = useAuthStore()
    store.logout()
    expect(store.token).toBe('')
    expect(sessionStorage.getItem('hospital_token')).toBeNull()
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/auth/store.spec.ts`
Expected: FAIL，认证 API 与 store 不存在。

- [ ] **Step 3: 实现认证 API**

Create `hospital-web/src/api/auth.ts`:

```ts
import { http } from '@/api/http'
import type { LoginResponse, UserInfo } from '@/types/models'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  phone: string
  password: string
}

export const authApi = {
  login(data: LoginRequest) {
    return http.post<LoginResponse>('/auth/login', data)
  },
  register(data: RegisterRequest) {
    return http.post<LoginResponse>('/auth/register', data)
  },
  me() {
    return http.get<UserInfo>('/auth/me')
  }
}
```

- [ ] **Step 4: 实现认证 store**

Create `hospital-web/src/stores/auth.ts`:

```ts
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { TOKEN_KEY } from '@/api/http'
import type { Role, UserInfo } from '@/types/models'

const roleHome: Record<Role, string> = {
  0: '/patient/home',
  1: '/admin/departments',
  2: '/doctor/workbench'
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<UserInfo | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value && user.value))
  const homePath = computed(() => user.value ? roleHome[user.value.role] : '/login')

  async function login(username: string, password: string) {
    const result = await authApi.login({ username, password })
    token.value = result.token
    user.value = result.user
    sessionStorage.setItem(TOKEN_KEY, result.token)
    return result
  }

  async function register(phone: string, password: string) {
    const result = await authApi.register({ phone, password })
    token.value = result.token
    user.value = result.user
    sessionStorage.setItem(TOKEN_KEY, result.token)
    return result
  }

  async function restore() {
    if (!token.value || user.value) return
    loading.value = true
    try {
      user.value = await authApi.me()
    } finally {
      loading.value = false
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    sessionStorage.removeItem(TOKEN_KEY)
  }

  return { token, user, loading, isAuthenticated, homePath, login, register, restore, logout }
})
```

- [ ] **Step 5: 实现路由与守卫**

Create `hospital-web/src/router/index.ts`:

```ts
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { routeDecision } from '@/router/guard'
import type { Role } from '@/types/models'
import PatientLayout from '@/layouts/PatientLayout.vue'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import NotFound from '@/views/NotFound.vue'
import Forbidden from '@/views/Forbidden.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: PlaceholderView },
    { path: '/register', name: 'register', component: PlaceholderView },
    {
      path: '/patient',
      component: PatientLayout,
      meta: { requiresAuth: true, roles: [0] as Role[] },
      children: [{ path: 'home', name: 'patient-home', component: PlaceholderView }]
    },
    {
      path: '/doctor',
      component: DoctorLayout,
      meta: { requiresAuth: true, roles: [2] as Role[] },
      children: [{ path: 'workbench', name: 'doctor-workbench', component: PlaceholderView }]
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { requiresAuth: true, roles: [1] as Role[] },
      children: [{ path: 'departments', name: 'admin-departments', component: PlaceholderView }]
    },
    { path: '/forbidden', name: 'forbidden', component: Forbidden },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFound }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.token) return '/login'
  if (auth.token && !auth.user) {
    try {
      await auth.restore()
    } catch {
      auth.logout()
      return '/login'
    }
  }
  const roles = to.meta.roles as Role[] | undefined
  return routeDecision(
    String(to.name ?? ''),
    Boolean(to.meta.requiresAuth),
    roles,
    auth
  )
})

export default router
```

Create `hospital-web/src/router/guard.ts`:

```ts
import type { Role, UserInfo } from '@/types/models'

export interface GuardAuth {
  token: string
  user: UserInfo | null
  isAuthenticated: boolean
  homePath: string
}

export function routeDecision(
  toName: string,
  requiresAuth: boolean,
  roles: Role[] | undefined,
  auth: GuardAuth
): string | true {
  if (requiresAuth && !auth.token) return '/login'
  if (roles && auth.user && !roles.includes(auth.user.role)) return auth.homePath
  if ((toName === 'login' || toName === 'register') && auth.isAuthenticated) return auth.homePath
  return true
}
```

- [ ] **Step 6: 接入路由入口并创建三类布局**

Update `hospital-web/src/main.ts`：

```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import App from './App.vue'
import router from './router'

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
```

Update `hospital-web/src/App.vue`：

```vue
<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <RouterView />
</template>
```

患者布局使用手机端顶部与底部导航；医生布局使用深色左侧导航和顶部用户栏；管理布局使用深色左侧导航、顶部栏和内容区。`AuthLayout` 必须渲染 `RouterView`。布局文件必须满足：

- 患者布局最小宽度 360px，底部导航包含首页、挂号、订单、我的。
- 医生布局包含“工作台 / 排班 / 报告 / 我的”导航，当前只有工作台可点击。
- 管理布局包含“科室管理 / 医生管理 / 排班管理”导航。
- 三类布局都显示当前用户姓名或用户名，并提供退出登录按钮。
- 退出登录调用 `auth.logout()` 后跳转 `/login`。

Create `hospital-web/src/views/PlaceholderView.vue`：

```vue
<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <main style="padding: 48px; min-height: 60vh">
    <h1>{{ String(route.name ?? '页面') }}</h1>
    <p>页面将在后续任务中实现。</p>
  </main>
</template>
```

Create `hospital-web/src/views/NotFound.vue`：

```vue
<template>
  <main style="padding: 48px; text-align: center">
    <h1>404</h1>
    <p>页面不存在</p>
    <RouterLink to="/login">返回登录</RouterLink>
  </main>
</template>
```

Create `hospital-web/src/views/Forbidden.vue`：

```vue
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
const router = useRouter()
</script>

<template>
  <main style="padding: 48px; text-align: center">
    <h1>403</h1>
    <p>当前账号无权访问此页面</p>
    <button type="button" @click="router.push(auth.homePath)">返回角色首页</button>
  </main>
</template>
```

- [ ] **Step 7: 写路由守卫测试**

Create `hospital-web/tests/router/guards.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { routeDecision, type GuardAuth } from '@/router/guard'
import type { Role, UserInfo } from '@/types/models'

function auth(role: Role | null, authenticated = true): GuardAuth {
  const user: UserInfo | null = role === null ? null : {
    id: 1,
    username: 'test',
    role,
    roleName: '测试'
  }
  return {
    token: authenticated ? 'token' : '',
    user,
    isAuthenticated: authenticated && Boolean(user),
    homePath: role === 0 ? '/patient/home' : role === 1 ? '/admin/departments' : role === 2 ? '/doctor/workbench' : '/login'
  }
}

describe('route guard', () => {
  it('redirects unauthenticated users to login', () => {
    expect(routeDecision('patient-home', true, [0], auth(null, false))).toBe('/login')
  })

  it('redirects a role mismatch to that role home', () => {
    expect(routeDecision('admin-departments', true, [1], auth(0))).toBe('/patient/home')
  })

  it('redirects authenticated users away from login', () => {
    expect(routeDecision('login', false, undefined, auth(2))).toBe('/doctor/workbench')
  })

  it('allows a matching role', () => {
    expect(routeDecision('admin-departments', true, [1], auth(1))).toBe(true)
  })
})
```

- [ ] **Step 8: 运行测试与类型检查**

Run:

```bash
npm test -- tests/auth/store.spec.ts tests/router/guards.spec.ts
npm run type-check
```

Expected: 测试通过；因页面文件尚未创建，路由动态导入会在构建阶段解析，因此本步骤只运行类型检查，不运行完整构建。

---

### Task 4: 实现主题化共享组件与名称/id 选择器

**Files:**
- Create: `hospital-web/src/components/PageHeader.vue`
- Create: `hospital-web/src/components/StatusTag.vue`
- Create: `hospital-web/src/components/EmptyState.vue`
- Create: `hospital-web/src/components/ErrorState.vue`
- Create: `hospital-web/src/components/ConfirmDialog.vue`
- Create: `hospital-web/src/components/FormDrawer.vue`
- Create: `hospital-web/src/components/NameIdSelect.vue`
- Create: `hospital-web/src/components/SearchBar.vue`
- Create: `hospital-web/src/components/DataTable.vue`
- Create: `hospital-web/src/components/DepartmentPicker.vue`
- Create: `hospital-web/src/components/DoctorPicker.vue`
- Create: `hospital-web/src/components/SchedulePicker.vue`
- Create: `hospital-web/src/components/PatientPicker.vue`
- Create: `hospital-web/src/components/OrderStatusTag.vue`
- Test: `hospital-web/tests/components/NameIdSelect.spec.ts`
- Test: `hospital-web/tests/components/StatusTag.spec.ts`

**Interfaces:**
- Consumes: Task 1 的 Element Plus。
- Produces:
  - `NameIdSelect`，`modelValue: number | undefined`，`options: { label: string; value: number }[]`
  - `StatusTag`，`type: 'success' | 'warning' | 'danger' | 'info'`
  - `FormDrawer`，`v-model` 控制显示，`title` 和默认插槽
  - `ConfirmDialog`，`v-model` 控制显示，`message` 和 `confirm` 事件
  - `SearchBar`，`v-model` 控制关键词，触发 `search` 事件
  - `DataTable`，`loading` 控制加载状态，默认插槽渲染行
  - `DepartmentPicker`、`DoctorPicker`、`SchedulePicker`、`PatientPicker`，均复用 `NameIdSelect` 并固定中文 placeholder
  - `OrderStatusTag`，接收 `OrderStatus` 并显示中文状态
  - `AppShell` 职责由 `PatientLayout`、`DoctorLayout`、`AdminLayout` 承担，本期不额外抽象通用壳组件

- [ ] **Step 1: 写名称/id 选择器失败测试**

Create `hospital-web/tests/components/NameIdSelect.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import NameIdSelect from '@/components/NameIdSelect.vue'
import { nextTick } from 'vue'

describe('NameIdSelect', () => {
  it('emits the selected id instead of the label', async () => {
    const wrapper = mount(NameIdSelect, {
      props: {
        modelValue: undefined,
        options: [
          { label: '呼吸内科', value: 11 },
          { label: '心血管内科', value: 12 }
        ]
      }
    })
    const select = wrapper.findComponent({ name: 'ElSelect' })
    select.vm.$emit('update:modelValue', 12)
    await nextTick()
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([12])
  })
})
```

Create `hospital-web/tests/components/StatusTag.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusTag from '@/components/StatusTag.vue'
import OrderStatusTag from '@/components/OrderStatusTag.vue'

describe('status tags', () => {
  it('renders the supplied status text', () => {
    const wrapper = mount(StatusTag, { props: { type: 'success', text: '已支付' } })
    expect(wrapper.text()).toContain('已支付')
  })

  it('maps backend order status codes to Chinese text', () => {
    const wrapper = mount(OrderStatusTag, { props: { status: 2 } })
    expect(wrapper.text()).toContain('已退号')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/components/NameIdSelect.spec.ts tests/components/StatusTag.spec.ts`
Expected: FAIL，共享组件不存在。

- [ ] **Step 3: 实现 NameIdSelect**

Create `hospital-web/src/components/NameIdSelect.vue`:

```vue
<script setup lang="ts">
import { ElSelect, ElOption } from 'element-plus'

export interface NameIdOption {
  label: string
  value: number
}

defineProps<{
  modelValue?: number
  options: NameIdOption[]
  placeholder?: string
  disabled?: boolean
}>()

defineEmits<{
  'update:modelValue': [value: number | undefined]
}>()
</script>

<template>
  <ElSelect
    :model-value="modelValue"
    :placeholder="placeholder ?? '请选择'"
    :disabled="disabled"
    clearable
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <ElOption
      v-for="option in options"
      :key="option.value"
      :label="option.label"
      :value="option.value"
    />
  </ElSelect>
</template>
```

- [ ] **Step 4: 实现状态标签**

Create `hospital-web/src/components/StatusTag.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { ElTag } from 'element-plus'

const props = defineProps<{
  type: 'success' | 'warning' | 'danger' | 'info'
  text: string
}>()

const color = computed(() => ({
  success: '#087f7a',
  warning: '#b7791f',
  danger: '#c53030',
  info: '#526d7a'
}[props.type]))
</script>

<template>
  <ElTag :color="color" effect="plain" style="color: var(--color-text); border: 0">
    {{ text }}
  </ElTag>
</template>
```

- [ ] **Step 5: 实现其余基础组件与业务包装组件**

`PageHeader.vue`：

```vue
<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <header class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <div class="page-header-actions"><slot /></div>
  </header>
</template>
```

`EmptyState.vue`：

```vue
<script setup lang="ts">
import { ElEmpty } from 'element-plus'
defineProps<{ description?: string }>()
</script>

<template>
  <ElEmpty :description="description ?? '暂无数据'" />
</template>
```

`ErrorState.vue`：

```vue
<script setup lang="ts">
import { ElAlert, ElButton } from 'element-plus'
defineProps<{ message: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <ElAlert type="error" :title="message" show-icon />
  <ElButton class="error-retry" @click="$emit('retry')">重试</ElButton>
</template>
```

`ConfirmDialog.vue`：

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { ElDialog, ElButton } from 'element-plus'
const props = defineProps<{ modelValue: boolean; title?: string; message: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: [] }>()
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
</script>

<template>
  <ElDialog v-model="visible" :title="title ?? '操作确认'" width="420px">
    <p>{{ message }}</p>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="danger" @click="emit('confirm')">确认</ElButton>
    </template>
  </ElDialog>
</template>
```

`FormDrawer.vue`：

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { ElDrawer } from 'element-plus'
const props = defineProps<{ modelValue: boolean; title: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
</script>

<template>
  <ElDrawer v-model="visible" :title="title" size="440px"><slot /></ElDrawer>
</template>
```

`SearchBar.vue`：

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { ElInput, ElButton } from 'element-plus'
const props = defineProps<{ modelValue: string; placeholder?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; search: [] }>()
const value = computed({ get: () => props.modelValue, set: (next) => emit('update:modelValue', next) })
</script>

<template>
  <div class="search-bar">
    <ElInput v-model="value" :placeholder="placeholder ?? '请输入关键词'" @keyup.enter="emit('search')" />
    <ElButton @click="emit('search')">查询</ElButton>
  </div>
</template>
```

`DataTable.vue`：

```vue
<script setup lang="ts">
import { ElTable } from 'element-plus'
defineProps<{ loading?: boolean; emptyText?: string }>()
</script>

<template>
  <ElTable v-loading="loading" :empty-text="emptyText ?? '暂无数据'" stripe>
    <slot name="default" />
  </ElTable>
</template>
```

四个业务选择器都只包装 `NameIdSelect`，不自行解析 id。创建以下文件：

`DepartmentPicker.vue`：

```vue
<script setup lang="ts">
import NameIdSelect from '@/components/NameIdSelect.vue'
defineProps<{ modelValue?: number; options: { label: string; value: number }[]; disabled?: boolean }>()
defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
</script>

<template>
  <NameIdSelect
    :model-value="modelValue"
    :options="options"
    :disabled="disabled"
    placeholder="请选择科室"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
```

`DoctorPicker.vue`：

```vue
<script setup lang="ts">
import NameIdSelect from '@/components/NameIdSelect.vue'
defineProps<{ modelValue?: number; options: { label: string; value: number }[]; disabled?: boolean }>()
defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
</script>

<template>
  <NameIdSelect
    :model-value="modelValue"
    :options="options"
    :disabled="disabled"
    placeholder="请选择医生"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
```

`SchedulePicker.vue`：

```vue
<script setup lang="ts">
import NameIdSelect from '@/components/NameIdSelect.vue'
defineProps<{ modelValue?: number; options: { label: string; value: number }[]; disabled?: boolean }>()
defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
</script>

<template>
  <NameIdSelect
    :model-value="modelValue"
    :options="options"
    :disabled="disabled"
    placeholder="请选择排班"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
```

`PatientPicker.vue`：

```vue
<script setup lang="ts">
import NameIdSelect from '@/components/NameIdSelect.vue'
defineProps<{ modelValue?: number; options: { label: string; value: number }[]; disabled?: boolean }>()
defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
</script>

<template>
  <NameIdSelect
    :model-value="modelValue"
    :options="options"
    :disabled="disabled"
    placeholder="请选择就诊人"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
```

`OrderStatusTag.vue`：

```vue
<script setup lang="ts">
import { computed } from 'vue'
import StatusTag from '@/components/StatusTag.vue'
import type { OrderStatus } from '@/types/models'
const props = defineProps<{ status: OrderStatus }>()
const config = computed(() => ({
  0: { text: '待支付', type: 'warning' as const },
  1: { text: '已支付', type: 'success' as const },
  2: { text: '已退号', type: 'info' as const },
  3: { text: '已就诊', type: 'success' as const },
  4: { text: '已取消', type: 'info' as const },
  5: { text: '爽约', type: 'danger' as const }
}[props.status]))
</script>

<template>
  <StatusTag :text="config.text" :type="config.type" />
</template>
```

- [ ] **Step 6: 运行组件测试与类型检查**

Run:

```bash
npm test -- tests/components/NameIdSelect.spec.ts tests/components/StatusTag.spec.ts
npm run type-check
```

Expected: 测试通过，选择器只输出数字 id。

---

### Task 5: 完成患者登录、注册与首页

**Files:**
- Create: `hospital-web/src/views/auth/LoginView.vue`
- Create: `hospital-web/src/views/auth/RegisterView.vue`
- Create: `hospital-web/src/views/patient/HomeView.vue`
- Create: `hospital-web/src/api/department.ts`
- Create: `hospital-web/src/composables/useAsync.ts`
- Modify: `hospital-web/src/layouts/AuthLayout.vue`
- Modify: `hospital-web/src/router/index.ts`
- Test: `hospital-web/tests/patient/login.spec.ts`

**Interfaces:**
- Consumes: Task 3 的认证 store 与路由；Task 4 的基础组件。
- Produces:
  - `departmentApi.list()`
  - `useAsync<T>(loader: () => Promise<T>)`，返回 `data`、`loading`、`error`、`reload`
  - 登录页提交 `{ username, password }`，注册页提交 `{ phone, password }`

- [ ] **Step 1: 写登录页失败测试**

Create `hospital-web/tests/patient/login.spec.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import LoginView from '@/views/auth/LoginView.vue'

describe('LoginView', () => {
  it('validates phone before submitting', async () => {
    setActivePinia(createPinia())
    const wrapper = mount(LoginView, {
      global: { stubs: { RouterLink: true, ElForm: { template: '<form><slot /></form>' }, ElFormItem: { template: '<div><slot /></div>' } } }
    })
    await wrapper.find('input[type="tel"]').setValue('123')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('请输入正确手机号')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/patient/login.spec.ts`
Expected: FAIL，登录页不存在。

- [ ] **Step 3: 实现 department API 与 useAsync**

Create `hospital-web/src/api/department.ts`:

```ts
import { http } from '@/api/http'
import type { Department } from '@/types/models'

export const departmentApi = {
  list() {
    return http.get<Department[]>('/department/list')
  }
}
```

Create `hospital-web/src/composables/useAsync.ts`:

```ts
import { ref, shallowRef } from 'vue'

export function useAsync<T>(loader: () => Promise<T>) {
  const data = shallowRef<T | null>(null)
  const loading = ref(false)
  const error = ref('')

  async function reload() {
    loading.value = true
    error.value = ''
    try {
      data.value = await loader()
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : '加载失败'
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, reload }
}
```

- [ ] **Step 4: 实现登录与注册页**

登录页必须包含：

- 手机号输入框，正则 `^1[3-9]\d{9}$`
- 密码输入框，长度 6–20
- 提交时调用 `auth.login(username, password)`
- 成功后跳转 `auth.homePath`
- 失败时展示 `ApiError.message`

注册页必须包含：

- 手机号输入框，正则 `^1[3-9]\d{9}$`
- 密码输入框，长度 6–20
- 确认密码输入框，两次密码一致
- 提交时调用 `auth.register(phone, password)`
- 成功后跳转患者首页

- [ ] **Step 5: 实现患者首页**

`HomeView.vue` 必须：

- 显示“上午好/下午好/晚上好”与用户名
- 提供症状描述入口，跳转 `/patient/triage`
- 提供预约挂号、我的订单、报告查询快捷入口
- 调用 `departmentApi.list()` 展示科室名称与简介
- 科室项跳转 `/patient/doctors?departmentId={id}`
- 使用加载、空数据、错误重试状态

- [ ] **Step 6: 更新路由**

将 `AuthLayout` 拆分到登录与注册子路由，并把患者首页占位路由替换为真实页面：

```ts
import AuthLayout from '@/layouts/AuthLayout.vue'

// 替换现有 /login 与 /register 路由
{ path: '/login', component: AuthLayout, children: [{ path: '', name: 'login', component: () => import('@/views/auth/LoginView.vue') }] },
{ path: '/register', component: AuthLayout, children: [{ path: '', name: 'register', component: () => import('@/views/auth/RegisterView.vue') }] }

// 替换 patient-home 的 component
{ path: 'home', name: 'patient-home', component: () => import('@/views/patient/HomeView.vue') }
```

- [ ] **Step 7: 运行测试与类型检查**

Run:

```bash
npm test -- tests/patient/login.spec.ts
npm run type-check
```

Expected: 测试通过，手机号校验生效。

---

### Task 6: 完成患者选科、选医生、选排班与挂号确认

**Files:**
- Create: `hospital-web/src/api/doctor.ts`
- Create: `hospital-web/src/api/schedule.ts`
- Create: `hospital-web/src/api/order.ts`
- Create: `hospital-web/src/views/patient/DepartmentListView.vue`
- Create: `hospital-web/src/views/patient/DoctorListView.vue`
- Create: `hospital-web/src/views/patient/ScheduleListView.vue`
- Create: `hospital-web/src/views/patient/BookingView.vue`
- Create: `hospital-web/src/utils/schedule.ts`
- Modify: `hospital-web/src/router/index.ts`
- Test: `hospital-web/tests/patient/booking.spec.ts`

**Interfaces:**
- Consumes: Task 2 的类型与 API；Task 4 的 `NameIdSelect`；Task 5 的患者布局。
- Produces:
  - `doctorApi.list(departmentId)`
  - `scheduleApi.list(doctorId, days = 7)`
  - `orderApi.create({ scheduleId, patientId })`
  - `timeSlotLabel(slot)`、`scheduleLabel(schedule, doctorName)`
  - 挂号确认页只提交 `scheduleId` 与 `patientId`

- [ ] **Step 1: 写排班与挂号载荷测试**

Create `hospital-web/tests/patient/booking.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { timeSlotLabel, scheduleLabel } from '@/utils/schedule'

describe('booking labels and payload rules', () => {
  it('uses Chinese time slot labels', () => {
    expect(timeSlotLabel(1)).toBe('上午')
    expect(timeSlotLabel(2)).toBe('下午')
    expect(timeSlotLabel(3)).toBe('晚间')
  })

  it('builds a human-readable schedule label', () => {
    expect(scheduleLabel({ id: 9, workDate: '2026-11-25', timeSlot: 1 }, '张医生'))
      .toBe('2026-11-25 上午 · 张医生')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/patient/booking.spec.ts`
Expected: FAIL，工具函数不存在。

- [ ] **Step 3: 实现业务 API**

Create `hospital-web/src/api/doctor.ts`:

```ts
import { http } from '@/api/http'
import type { Doctor } from '@/types/models'

export const doctorApi = {
  list(departmentId: number) {
    return http.get<Doctor[]>('/doctor/list', { params: { departmentId } })
  }
}
```

Create `hospital-web/src/api/schedule.ts`:

```ts
import { http } from '@/api/http'
import type { Schedule } from '@/types/models'

export const scheduleApi = {
  list(doctorId: number, days = 7) {
    return http.get<Schedule[]>('/schedule/list', { params: { doctorId, days } })
  }
}
```

Create `hospital-web/src/api/order.ts`:

```ts
import { http } from '@/api/http'
import type { Order, PageResult } from '@/types/models'

export interface CreateOrderRequest {
  scheduleId: number
  patientId: number
}

export const orderApi = {
  create(data: CreateOrderRequest) {
    return http.post<Order>('/order', data)
  },
  list(pageNum = 1, pageSize = 10) {
    return http.get<PageResult<Order>>('/order/list', { params: { pageNum, pageSize } })
  },
  sign(orderId: number) {
    return http.post<void>('/order/sign', null, { params: { orderId } })
  }
}
```

- [ ] **Step 4: 实现排班工具函数**

Create `hospital-web/src/utils/schedule.ts`:

```ts
import type { Schedule, TimeSlot } from '@/types/models'

const labels: Record<TimeSlot, string> = {
  1: '上午',
  2: '下午',
  3: '晚间'
}

export function timeSlotLabel(slot: TimeSlot) {
  return labels[slot]
}

export function scheduleLabel(schedule: Pick<Schedule, 'workDate' | 'timeSlot'>, doctorName: string) {
  return `${schedule.workDate} ${labels[schedule.timeSlot]} · ${doctorName}`
}
```

- [ ] **Step 5: 实现选科、选医生、选排班页面**

- `DepartmentListView.vue`：调用公开科室列表，点击科室跳转医生页并携带 `departmentId`。
- `DoctorListView.vue`：从查询参数读取 `departmentId`，调用 `doctorApi.list`，展示姓名、职称、简介，点击医生跳转排班页并携带 `doctorId`。
- `ScheduleListView.vue`：从查询参数读取 `doctorId`，调用 `scheduleApi.list`，按日期分组展示时段、剩余号源和价格；不可预约或剩余为 0 时禁用；点击可预约排班跳转 `/patient/booking?scheduleId={id}&doctorId={doctorId}&departmentId={schedule.departmentId}`。
- 三个页面都必须有加载、空数据和错误重试状态。

- [ ] **Step 6: 实现挂号确认页**

`BookingView.vue` 必须：

- 从查询参数读取 `scheduleId`、`doctorId` 和 `departmentId`
- 调用 `doctorApi.list(departmentId)` 与 `scheduleApi.list(doctorId)`，按 id 找到医生和排班，保证刷新后仍能恢复上下文
- 调用 `GET /patient/list` 加载就诊人
- 使用 `NameIdSelect` 展示就诊人姓名与关系，选中后得到 `patientId`
- 展示医生、日期、时段、价格
- 提交时只发送 `{ scheduleId, patientId }`
- 下单成功后跳转 `/patient/payment?orderId={orderId}`
- 展示后端返回的挂号成功提示

- [ ] **Step 7: 更新路由并运行测试**

新增路由：

```ts
{ path: 'departments', name: 'patient-departments', component: () => import('@/views/patient/DepartmentListView.vue') },
{ path: 'doctors', name: 'patient-doctors', component: () => import('@/views/patient/DoctorListView.vue') },
{ path: 'schedules', name: 'patient-schedules', component: () => import('@/views/patient/ScheduleListView.vue') },
{ path: 'booking', name: 'patient-booking', component: () => import('@/views/patient/BookingView.vue') }
```

Run:

```bash
npm test -- tests/patient/booking.spec.ts
npm run type-check
```

Expected: 测试通过，排班标签使用中文，挂号请求只包含 id。

---

### Task 7: 完成患者订单、模拟支付、退号与签到

**Files:**
- Create: `hospital-web/src/api/payment.ts`
- Create: `hospital-web/src/composables/useCountdown.ts`
- Create: `hospital-web/src/utils/orderStatus.ts`
- Create: `hospital-web/src/components/PaymentCountdown.vue`
- Create: `hospital-web/src/views/patient/OrderListView.vue`
- Create: `hospital-web/src/views/patient/PaymentView.vue`
- Modify: `hospital-web/src/router/index.ts`
- Test: `hospital-web/tests/patient/order-status.spec.ts`
- Test: `hospital-web/tests/patient/countdown.spec.ts`

**Interfaces:**
- Consumes: Task 6 的订单 API 与类型。
- Produces:
  - `paymentApi.pay(orderId, channel?)`、`paymentApi.refund(orderId)`
  - `canPay(order, now)`、`canRefund(order, now)`、`canSign(order, now)`
  - `useCountdown(deadline: Ref<string | undefined>)`
  - `PaymentCountdown`，接收 `deadline` 字符串并显示 `mm:ss`

- [ ] **Step 1: 写订单状态失败测试**

Create `hospital-web/tests/patient/order-status.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { canPay, canRefund, canSign } from '@/utils/orderStatus'
import type { Order } from '@/types/models'

const base: Order = {
  id: 1,
  orderNo: 'O1',
  patientId: 2,
  patientName: '王建国',
  scheduleId: 3,
  workDate: '2026-11-25',
  timeSlot: 1,
  amount: 20,
  status: 1,
  statusDesc: '已支付',
  deadlineTime: '2026-11-25T10:00:00',
  createTime: '2026-11-25T09:00:00'
}

describe('order actions', () => {
  it('allows signing only on visit day before slot end', () => {
    expect(canSign(base, new Date('2026-11-25T09:30:00'))).toBe(true)
    expect(canSign(base, new Date('2026-11-25T12:30:00'))).toBe(false)
    expect(canSign(base, new Date('2026-11-26T09:30:00'))).toBe(false)
  })

  it('allows refund only before slot start', () => {
    expect(canRefund(base, new Date('2026-11-25T07:30:00'))).toBe(true)
    expect(canRefund(base, new Date('2026-11-25T08:30:00'))).toBe(false)
  })

  it('allows pay only while pending and before deadline', () => {
    const pending = { ...base, status: 0 as const }
    expect(canPay(pending, new Date('2026-11-25T09:30:00'))).toBe(true)
    expect(canPay(pending, new Date('2026-11-25T10:30:00'))).toBe(false)
  })
})
```

Create `hospital-web/tests/patient/countdown.spec.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import PaymentCountdown from '@/components/PaymentCountdown.vue'

afterEach(() => vi.useRealTimers())

describe('PaymentCountdown', () => {
  it('counts down by seconds and never goes below zero', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-25T09:00:00'))
    const wrapper = mount(PaymentCountdown, {
      props: { deadline: '2026-11-25T09:01:00' }
    })
    expect(wrapper.text()).toContain('01:00')
    await vi.advanceTimersByTimeAsync(1000)
    await nextTick()
    expect(wrapper.text()).toContain('00:59')
    await vi.advanceTimersByTimeAsync(61000)
    await nextTick()
    expect(wrapper.text()).toContain('00:00')
    wrapper.unmount()
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/patient/order-status.spec.ts tests/patient/countdown.spec.ts`
Expected: FAIL，状态工具与倒计时组件不存在。

- [ ] **Step 3: 实现支付 API 与订单状态工具**

Create `hospital-web/src/api/payment.ts`:

```ts
import { http } from '@/api/http'

export const paymentApi = {
  pay(orderId: number, channel = 0) {
    return http.post<void>('/payment/pay', { orderId, channel })
  },
  refund(orderId: number) {
    return http.post<void>('/payment/refund', { orderId })
  }
}
```

Create `hospital-web/src/utils/orderStatus.ts`:

```ts
import type { Order, TimeSlot } from '@/types/models'

const startHour: Record<TimeSlot, number> = { 1: 8, 2: 13, 3: 18 }
const endHour: Record<TimeSlot, number> = { 1: 12, 2: 17, 3: 21 }

function atTime(order: Order, hour: number) {
  return new Date(`${order.workDate}T${String(hour).padStart(2, '0')}:00:00`)
}

export function canPay(order: Order, now: Date) {
  return order.status === 0 && Boolean(order.deadlineTime) && now < new Date(order.deadlineTime!)
}

export function canRefund(order: Order, now: Date) {
  return order.status === 1 && now < atTime(order, startHour[order.timeSlot])
}

export function canSign(order: Order, now: Date) {
  return order.status === 1 && now >= atTime(order, startHour[order.timeSlot]) && now < atTime(order, endHour[order.timeSlot])
}
```

- [ ] **Step 4: 实现支付倒计时**

Create `hospital-web/src/composables/useCountdown.ts`:

```ts
import { computed, onBeforeUnmount, ref, type Ref } from 'vue'

export function useCountdown(deadline: Ref<string | undefined>) {
  const now = ref(Date.now())
  const timer = window.setInterval(() => { now.value = Date.now() }, 1000)
  onBeforeUnmount(() => window.clearInterval(timer))

  const text = computed(() => {
    if (!deadline.value) return ''
    const seconds = Math.max(0, Math.floor((new Date(deadline.value).getTime() - now.value) / 1000))
    const mm = String(Math.floor(seconds / 60)).padStart(2, '0')
    const ss = String(seconds % 60).padStart(2, '0')
    return `${mm}:${ss}`
  })

  return { text }
}
```

Create `hospital-web/src/components/PaymentCountdown.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useCountdown } from '@/composables/useCountdown'
const props = defineProps<{ deadline?: string }>()
const { text } = useCountdown(computed(() => props.deadline))
</script>

<template>
  <span :aria-label="`支付剩余时间 ${text}`">{{ text }}</span>
</template>
```

- [ ] **Step 5: 实现订单列表与支付页**

`OrderListView.vue` 必须：

- 分页调用 `orderApi.list`
- 展示订单号、就诊人、日期、时段、金额、状态、支付截止时间
- 待支付且未超时显示“去支付”
- 已支付且可退号显示“退号”，退号前确认
- 已支付且可签到显示“签到”
- 操作成功后刷新列表
- 后端业务错误直接展示

`PaymentView.vue` 必须：

- 从查询参数读取 `orderId`
- 调用 `orderApi.list(1, 100)` 找到对应订单并展示金额；未找到时展示错误与返回订单列表按钮
- 明确标注“模拟支付”
- 提供“模拟支付”按钮，调用 `paymentApi.pay(orderId, 0)`
- 成功后跳转订单列表并提示成功
- 显示支付倒计时，使用 `PaymentCountdown` 组件

- [ ] **Step 6: 更新路由并运行测试**

新增路由：

```ts
{ path: 'orders', name: 'patient-orders', component: () => import('@/views/patient/OrderListView.vue') },
{ path: 'payment', name: 'patient-payment', component: () => import('@/views/patient/PaymentView.vue') }
```

Run:

```bash
npm test -- tests/patient/order-status.spec.ts tests/patient/countdown.spec.ts
npm run type-check
```

Expected: 状态判断与倒计时测试通过。

---

### Task 8: 完成就诊人、报告与 AI 导诊

**Files:**
- Create: `hospital-web/src/api/patient.ts`
- Create: `hospital-web/src/api/report.ts`
- Create: `hospital-web/src/api/triage.ts`
- Create: `hospital-web/src/views/patient/PatientListView.vue`
- Create: `hospital-web/src/views/patient/ReportListView.vue`
- Create: `hospital-web/src/views/patient/TriageView.vue`
- Create: `hospital-web/src/components/PatientFormDrawer.vue`
- Modify: `hospital-web/src/router/index.ts`
- Test: `hospital-web/tests/patient/patient-form.spec.ts`

**Interfaces:**
- Consumes: Task 2 的类型与 API；Task 4 的抽屉与确认组件。
- Produces:
  - `patientApi.list/create/update`
  - `reportApi.list`
  - `triageApi.ask(symptoms)`
  - `PatientFormDrawer`，新增时 `patientId` 为空，编辑时传入患者对象

- [ ] **Step 1: 写就诊人校验失败测试**

Create `hospital-web/tests/patient/patient-form.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { isValidPhone, isValidIdCard } from '@/utils/validation'

describe('patient validation', () => {
  it('validates phone and optional id card', () => {
    expect(isValidPhone('13800000000')).toBe(true)
    expect(isValidPhone('12800000000')).toBe(false)
    expect(isValidIdCard('')).toBe(true)
    expect(isValidIdCard('11010519491231002X')).toBe(true)
    expect(isValidIdCard('123')).toBe(false)
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/patient/patient-form.spec.ts`
Expected: FAIL，校验工具不存在。

- [ ] **Step 3: 实现 API 与校验工具**

Create `hospital-web/src/api/patient.ts`:

```ts
import { http } from '@/api/http'
import type { Patient } from '@/types/models'

export interface PatientCreateRequest {
  name: string
  idCard?: string
  relation: number
  phone?: string
}

export interface PatientUpdateRequest extends PatientCreateRequest {
  id: number
}

export const patientApi = {
  list() {
    return http.get<Patient[]>('/patient/list')
  },
  create(data: PatientCreateRequest) {
    return http.post<Patient>('/patient', data)
  },
  update(data: PatientUpdateRequest) {
    return http.put<Patient>('/patient', data)
  }
}
```

Create `hospital-web/src/api/report.ts`:

```ts
import { http } from '@/api/http'
import type { Report } from '@/types/models'

export const reportApi = {
  list() {
    return http.get<Report[]>('/report/list')
  }
}
```

Create `hospital-web/src/api/triage.ts`:

```ts
import { http } from '@/api/http'
import type { TriageResult } from '@/types/models'

export const triageApi = {
  ask(symptoms: string) {
    return http.post<TriageResult>('/triage/ask', { symptoms })
  }
}
```

Create `hospital-web/src/utils/validation.ts`:

```ts
export function isValidPhone(value: string) {
  return /^1[3-9]\d{9}$/.test(value)
}

export function isValidIdCard(value: string) {
  return value === '' || /^(\d{17}[\dXx])?$/.test(value)
}
```

- [ ] **Step 4: 实现就诊人页面与表单抽屉**

`PatientListView.vue` 必须：

- 调用 `patientApi.list`
- 展示姓名、关系、手机号和脱敏身份证
- 提供新增与编辑
- 删除功能不提供，因为后端没有删除接口
- 表单校验姓名、关系、手机号、身份证
- 新增提交 `PatientCreateRequest`，编辑提交带 `id` 的 `PatientUpdateRequest`
- 成功后刷新列表

- [ ] **Step 5: 实现报告与 AI 导诊页面**

`ReportListView.vue` 必须：

- 调用 `reportApi.list`
- 展示就诊人、报告类型、标题、报告时间
- 点击报告展开完整内容
- 空列表展示空状态

`TriageView.vue` 必须：

- 文本域输入症状，最多 500 字
- 提交时禁用按钮，调用 `triageApi.ask`
- 展示推荐科室、原因、紧急提示和来源
- `urgent=true` 时使用醒目的警告样式
- 无推荐科室时展示补充症状或到导诊台咨询的提示

- [ ] **Step 6: 更新路由并运行测试**

新增路由：

```ts
{ path: 'patients', name: 'patient-patients', component: () => import('@/views/patient/PatientListView.vue') },
{ path: 'reports', name: 'patient-reports', component: () => import('@/views/patient/ReportListView.vue') },
{ path: 'triage', name: 'patient-triage', component: () => import('@/views/patient/TriageView.vue') }
```

Run:

```bash
npm test -- tests/patient/patient-form.spec.ts
npm run type-check
```

Expected: 测试通过。

---

### Task 9: 完成医生工作台与演示排班

**Files:**
- Create: `hospital-web/src/mocks/doctorSchedules.ts`
- Create: `hospital-web/src/api/doctorWork.ts`
- Create: `hospital-web/src/views/doctor/WorkbenchView.vue`
- Create: `hospital-web/src/components/ReportDrawer.vue`
- Modify: `hospital-web/src/router/index.ts`
- Test: `hospital-web/tests/doctor/workbench.spec.ts`

**Interfaces:**
- Consumes: Task 2 的类型与 API；Task 4 的 `NameIdSelect`、抽屉与状态组件。
- Produces:
  - `DemoSchedule`
  - `createDemoSchedules()`
  - `doctorWorkApi.waiting(scheduleId)`、`call(scheduleId)`、`report(data)`
  - `activeOrder: Order | null`，保存 `call` 返回的当前就诊患者
  - 工作台只提交 `scheduleId`、`orderId`，不提交排班名称

- [ ] **Step 1: 写医生工作台载荷测试**

Create `hospital-web/tests/doctor/workbench.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { createDemoSchedules } from '@/mocks/doctorSchedules'

describe('demo schedules', () => {
  it('provides labels and numeric ids', () => {
    const schedules = createDemoSchedules()
    expect(schedules.length).toBeGreaterThan(0)
    expect(typeof schedules[0].id).toBe('number')
    expect(schedules[0].label).toContain('呼吸内科')
    expect(schedules[0].label).not.toContain('id:')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/doctor/workbench.spec.ts`
Expected: FAIL，演示排班模块不存在。

- [ ] **Step 3: 实现演示排班与医生 API**

Create `hospital-web/src/mocks/doctorSchedules.ts`:

```ts
import type { TimeSlot } from '@/types/models'

export interface DemoSchedule {
  id: number
  label: string
  doctorName: string
  workDate: string
  timeSlot: TimeSlot
  startTime: string
  endTime: string
}

function dateString(offset = 0) {
  const date = new Date()
  date.setDate(date.getDate() + offset)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function createDemoSchedules(): DemoSchedule[] {
  const configured = import.meta.env.VITE_DEMO_SCHEDULE_IDS?.split(',').map(Number).filter(Number.isInteger)
  const ids = configured && configured.length >= 3 ? configured : [101, 102, 103]
  return [
    { id: ids[0], label: '呼吸内科 · 上午 · 08:00—12:00', doctorName: '张医生', workDate: dateString(), timeSlot: 1, startTime: '08:00', endTime: '12:00' },
    { id: ids[1], label: '呼吸内科 · 下午 · 13:00—17:00', doctorName: '张医生', workDate: dateString(), timeSlot: 2, startTime: '13:00', endTime: '17:00' },
    { id: ids[2], label: '呼吸内科 · 上午 · 08:00—12:00', doctorName: '张医生', workDate: dateString(1), timeSlot: 1, startTime: '08:00', endTime: '12:00' }
  ]
}
```

Create `hospital-web/src/api/doctorWork.ts`:

```ts
import { http } from '@/api/http'
import type { Order, Report, WaitingPatient } from '@/types/models'

export interface ReportCreateRequest {
  orderId: number
  reportType?: string
  title: string
  content?: string
}

export const doctorWorkApi = {
  waiting(scheduleId: number) {
    return http.get<WaitingPatient[]>('/doctor-work/waiting', { params: { scheduleId } })
  },
  call(scheduleId: number) {
    return http.post<Order>('/doctor-work/call', null, { params: { scheduleId } })
  },
  report(data: ReportCreateRequest) {
    return http.post<Report>('/doctor-work/report', data)
  }
}
```

- [ ] **Step 4: 实现医生工作台**

`WorkbenchView.vue` 必须：

- 使用 `NameIdSelect` 选择演示排班，选项为 `createDemoSchedules()` 的 `label/id`
- 在页面角落显示“演示排班数据”
- 选择排班后调用 `doctorWorkApi.waiting(scheduleId)`
- 单栏展示候诊队列，按后端顺序显示序号、姓名、订单号、签到时间
- “叫下一位”调用 `doctorWorkApi.call(scheduleId)`，将返回订单保存到 `activeOrder` 并刷新队列
- 当前就诊区展示 `activeOrder` 的就诊人、订单号和状态
- 只有 `activeOrder` 存在时才允许打开 `ReportDrawer`
- 空队列禁用叫号并展示空状态
- 报告提交成功后清空 `activeOrder` 并刷新队列

演示排班默认使用 `101/102/103`。本地联调时，在 `hospital-web/.env.local` 中把 `VITE_DEMO_SCHEDULE_IDS` 配置为已绑定医生账号的真实排班 id；用户界面仍只显示排班名称，请求仍只提交 id。

- [ ] **Step 5: 实现报告抽屉**

`ReportDrawer.vue` 必须：

- 接收 `orderId`、`patientName` 和 `v-model`
- 报告类型输入最多 32 字
- 标题必填且最多 64 字
- 内容可选
- 提交 `{ orderId, reportType, title, content }`
- 成功后关闭抽屉并触发 `saved`

- [ ] **Step 6: 接入真实工作台路由并运行检查**

将 `doctor-workbench` 的 `component` 替换为：

```ts
{ path: 'workbench', name: 'doctor-workbench', component: () => import('@/views/doctor/WorkbenchView.vue') }
```

Run:

```bash
npm test -- tests/doctor/workbench.spec.ts
npm run type-check
```

Expected: 测试通过，排班选项包含名称和数字 id。

---

### Task 10: 完成管理端科室 CRUD

**Files:**
- Create: `hospital-web/src/api/adminDepartment.ts`
- Create: `hospital-web/src/views/admin/DepartmentManageView.vue`
- Create: `hospital-web/src/components/DepartmentFormDrawer.vue`
- Modify: `hospital-web/src/router/index.ts`
- Test: `hospital-web/tests/admin/department.spec.ts`

**Interfaces:**
- Consumes: Task 2 的类型与 API；Task 4 的表格、抽屉与确认组件。
- Produces:
  - `adminDepartmentApi.page/list/create/update/remove`
  - 科室表单新增提交 `{ name, description }`，编辑提交 `{ id, name, description }`

- [ ] **Step 1: 写科室请求载荷测试**

Create `hospital-web/tests/admin/department.spec.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { adminDepartmentApi } from '@/api/adminDepartment'
import { http } from '@/api/http'

vi.mock('@/api/http', () => ({ http: { post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))

describe('admin department api', () => {
  it('posts name and description on create', async () => {
    await adminDepartmentApi.create({ name: '儿科', description: '儿童诊疗' })
    expect(http.post).toHaveBeenCalledWith('/admin/department', { name: '儿科', description: '儿童诊疗' })
  })

  it('puts id on update', async () => {
    await adminDepartmentApi.update({ id: 3, name: '儿科', description: '儿童诊疗' })
    expect(http.put).toHaveBeenCalledWith('/admin/department', { id: 3, name: '儿科', description: '儿童诊疗' })
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/admin/department.spec.ts`
Expected: FAIL，管理 API 不存在。

- [ ] **Step 3: 实现科室 API**

Create `hospital-web/src/api/adminDepartment.ts`:

```ts
import { http } from '@/api/http'
import type { Department, PageResult } from '@/types/models'

export interface DepartmentCreateRequest {
  name: string
  description?: string
}

export interface DepartmentUpdateRequest extends DepartmentCreateRequest {
  id: number
}

export const adminDepartmentApi = {
  page(pageNum = 1, pageSize = 10) {
    return http.get<PageResult<Department>>('/admin/department/page', { params: { pageNum, pageSize } })
  },
  list() {
    return http.get<Department[]>('/admin/department/list')
  },
  create(data: DepartmentCreateRequest) {
    return http.post<Department>('/admin/department', data)
  },
  update(data: DepartmentUpdateRequest) {
    return http.put<Department>('/admin/department', data)
  },
  remove(id: number) {
    return http.delete<void>(`/admin/department/${id}`)
  }
}
```

- [ ] **Step 4: 实现科室表单抽屉**

`DepartmentFormDrawer.vue` 必须：

- 新增时 `id` 为空
- 编辑时回填名称与简介
- 名称必填且最多 32 字
- 简介最多 255 字
- 提交时调用 create 或 update
- 成功后触发 `saved`

- [ ] **Step 5: 实现科室管理页**

`DepartmentManageView.vue` 必须：

- 分页调用 `adminDepartmentApi.page`
- 当前页支持按名称前端筛选
- 展示编号、科室名称、简介、操作
- 提供新增、编辑、删除
- 删除前调用 `ConfirmDialog`
- 后端拒绝删除时展示具体原因
- 操作成功后刷新当前页

- [ ] **Step 6: 接入真实科室管理路由并运行检查**

将 `admin-departments` 的 `component` 替换为：

```ts
{ path: 'departments', name: 'admin-departments', component: () => import('@/views/admin/DepartmentManageView.vue') }
```

Run:

```bash
npm test -- tests/admin/department.spec.ts
npm run type-check
```

Expected: 测试通过。

---

### Task 11: 完成管理端医生 CRUD

**Files:**
- Create: `hospital-web/src/api/adminDoctor.ts`
- Create: `hospital-web/src/views/admin/DoctorManageView.vue`
- Create: `hospital-web/src/components/DoctorFormDrawer.vue`
- Test: `hospital-web/tests/admin/doctor.spec.ts`

**Interfaces:**
- Consumes: Task 10 的科室列表 API；Task 4 的 `NameIdSelect`。
- Produces:
  - `adminDoctorApi.page/list/create/update/remove`
  - 医生表单只提交 `departmentId`、`name`、`title`、`intro`，不提交 `userId`

- [ ] **Step 1: 写医生请求载荷测试**

Create `hospital-web/tests/admin/doctor.spec.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { adminDoctorApi } from '@/api/adminDoctor'
import { http } from '@/api/http'

vi.mock('@/api/http', () => ({ http: { post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))

describe('admin doctor api', () => {
  it('creates a doctor without account binding', async () => {
    const payload = { departmentId: 11, name: '张医生', title: '主任医师', intro: '呼吸科' }
    await adminDoctorApi.create(payload)
    expect(http.post).toHaveBeenCalledWith('/admin/doctor', payload)
    expect(vi.mocked(http.post).mock.calls[0][1]).not.toHaveProperty('userId')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/admin/doctor.spec.ts`
Expected: FAIL，管理 API 不存在。

- [ ] **Step 3: 实现医生 API**

Create `hospital-web/src/api/adminDoctor.ts`:

```ts
import { http } from '@/api/http'
import type { Doctor, PageResult } from '@/types/models'

export interface DoctorCreateRequest {
  departmentId: number
  name: string
  title?: string
  intro?: string
}

export interface DoctorUpdateRequest extends DoctorCreateRequest {
  id: number
}

export const adminDoctorApi = {
  page(pageNum = 1, pageSize = 10) {
    return http.get<PageResult<Doctor>>('/admin/doctor/page', { params: { pageNum, pageSize } })
  },
  list() {
    return http.get<Doctor[]>('/admin/doctor/list')
  },
  create(data: DoctorCreateRequest) {
    return http.post<Doctor>('/admin/doctor', data)
  },
  update(data: DoctorUpdateRequest) {
    return http.put<Doctor>('/admin/doctor', data)
  },
  remove(id: number) {
    return http.delete<void>(`/admin/doctor/${id}`)
  }
}
```

- [ ] **Step 4: 实现医生表单抽屉**

`DoctorFormDrawer.vue` 必须：

- 调用 `adminDepartmentApi.list()` 生成科室选项
- 使用 `NameIdSelect`，显示科室名称，提交 `departmentId`
- 姓名必填且最多 32 字
- 职称最多 32 字，简介最多 255 字
- 不提供 `userId` 输入或选择
- 成功后触发 `saved`

- [ ] **Step 5: 实现医生管理页**

`DoctorManageView.vue` 必须：

- 分页调用 `adminDoctorApi.page`
- 调用 `adminDepartmentApi.list()` 建立科室 id 到名称的映射，因为 `DoctorVO` 只返回 `departmentId`
- 当前页支持按姓名前端筛选
- 展示医生姓名、科室名称、职称、简介、操作
- 提供新增、编辑、删除
- 删除前确认
- 后端拒绝删除时展示具体原因
- 操作成功后刷新当前页

- [ ] **Step 6: 更新路由并运行测试**

新增路由：

```ts
{ path: 'doctors', name: 'admin-doctors', component: () => import('@/views/admin/DoctorManageView.vue') }
```

Run:

```bash
npm test -- tests/admin/doctor.spec.ts
npm run type-check
```

Expected: 测试通过，请求中不存在 `userId`。

---

### Task 12: 完成管理端排班 CRUD

**Files:**
- Create: `hospital-web/src/api/adminSchedule.ts`
- Create: `hospital-web/src/views/admin/ScheduleManageView.vue`
- Create: `hospital-web/src/components/ScheduleFormDrawer.vue`
- Test: `hospital-web/tests/admin/schedule.spec.ts`

**Interfaces:**
- Consumes: Task 11 的医生列表 API；Task 6 的时段工具；Task 4 的 `NameIdSelect`。
- Produces:
  - `adminScheduleApi.page/create/update/remove`
  - 排班表单提交 `doctorId`、`workDate`、`timeSlot`、`totalNum`、`price`、`status`

- [ ] **Step 1: 写排班请求载荷测试**

Create `hospital-web/tests/admin/schedule.spec.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { adminScheduleApi } from '@/api/adminSchedule'
import { http } from '@/api/http'

vi.mock('@/api/http', () => ({ http: { post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))

describe('admin schedule api', () => {
  it('posts doctor id and numeric slot', async () => {
    const payload = { doctorId: 21, workDate: '2026-11-25', timeSlot: 1 as const, totalNum: 20, price: 30, status: 1 as const }
    await adminScheduleApi.create(payload)
    expect(http.post).toHaveBeenCalledWith('/admin/schedule', payload)
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- tests/admin/schedule.spec.ts`
Expected: FAIL，管理 API 不存在。

- [ ] **Step 3: 实现排班 API**

Create `hospital-web/src/api/adminSchedule.ts`:

```ts
import { http } from '@/api/http'
import type { PageResult, Schedule } from '@/types/models'

export interface ScheduleCreateRequest {
  doctorId: number
  workDate: string
  timeSlot: 1 | 2 | 3
  totalNum: number
  price: number
  status: 0 | 1
}

export interface ScheduleUpdateRequest extends ScheduleCreateRequest {
  id: number
}

export const adminScheduleApi = {
  page(pageNum = 1, pageSize = 10, doctorId?: number) {
    return http.get<PageResult<Schedule>>('/admin/schedule/page', { params: { pageNum, pageSize, doctorId } })
  },
  create(data: ScheduleCreateRequest) {
    return http.post<Schedule>('/admin/schedule', data)
  },
  update(data: ScheduleUpdateRequest) {
    return http.put<Schedule>('/admin/schedule', data)
  },
  remove(id: number) {
    return http.delete<void>(`/admin/schedule/${id}`)
  }
}
```

- [ ] **Step 4: 实现排班表单抽屉**

`ScheduleFormDrawer.vue` 必须：

- 调用 `adminDoctorApi.list()` 生成医生选项
- 使用 `NameIdSelect`，显示医生姓名，提交 `doctorId`
- 日期选择器不得早于今天
- 时段使用“上午 / 下午 / 晚间”，值为 `1 / 2 / 3`
- 放号数量为大于等于 1 的整数
- 挂号费为 0 到 99999999.99 的数字
- 状态使用“可预约 / 停诊”，值为 `1 / 0`
- 编辑时回填全部字段
- 成功后触发 `saved`

- [ ] **Step 5: 实现排班管理页**

`ScheduleManageView.vue` 必须：

- 分页调用 `adminScheduleApi.page`
- 调用 `adminDoctorApi.list()` 与 `adminDepartmentApi.list()` 建立 id 到名称映射，因为 `ScheduleVO` 不返回医生名和科室名
- 支持按医生筛选，筛选值使用医生 id
- 展示医生姓名、科室名称、日期、时段、总号数、剩余号数、挂号费、状态、操作
- 提供新增、编辑、删除
- 删除前确认
- 后端拒绝删除或提示时间冲突时展示具体原因
- 操作成功后刷新当前页

- [ ] **Step 6: 更新路由并运行测试**

新增路由：

```ts
{ path: 'schedules', name: 'admin-schedules', component: () => import('@/views/admin/ScheduleManageView.vue') }
```

Run:

```bash
npm test -- tests/admin/schedule.spec.ts
npm run type-check
```

Expected: 测试通过，排班请求使用数字时段和医生 id。

---

### Task 13: 全量联调、自动化测试与 Chrome-D 验收

**Files:**
- Modify: `hospital-web/src/router/index.ts`（仅在发现路由或守卫缺口时）
- Modify: `hospital-web/src/api/*.ts`（仅在实际接口契约不符时）
- Create: `hospital-web/tests/integration/api-contract.spec.ts`

**Interfaces:**
- Consumes: Task 1–12 的全部代码。
- Produces: 可构建、可测试、可在 Chrome-D 中完成三端主路径的前端应用。

- [ ] **Step 1: 建立 API 契约测试并运行全量检查**

Create `hospital-web/tests/integration/api-contract.spec.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from '@/api/http'
import { authApi } from '@/api/auth'
import { orderApi } from '@/api/order'
import { paymentApi } from '@/api/payment'
import { doctorWorkApi } from '@/api/doctorWork'
import { adminScheduleApi } from '@/api/adminSchedule'

vi.mock('@/api/http', () => ({
  http: {
    get: vi.fn().mockResolvedValue(undefined),
    post: vi.fn().mockResolvedValue(undefined),
    put: vi.fn().mockResolvedValue(undefined),
    delete: vi.fn().mockResolvedValue(undefined)
  }
}))

beforeEach(() => vi.clearAllMocks())

describe('backend API contract', () => {
  it('uses bearer-auth endpoints without hardcoded user ids', async () => {
    await authApi.login({ username: '13800000000', password: '123456' })
    await authApi.me()
    expect(http.post).toHaveBeenCalledWith('/auth/login', { username: '13800000000', password: '123456' })
    expect(http.get).toHaveBeenCalledWith('/auth/me')
  })

  it('creates an order with schedule and patient ids only', async () => {
    await orderApi.create({ scheduleId: 9, patientId: 12 })
    expect(http.post).toHaveBeenCalledWith('/order', { scheduleId: 9, patientId: 12 })
  })

  it('uses the existing payment and doctor-work parameter locations', async () => {
    await paymentApi.pay(9, 0)
    await doctorWorkApi.call(9)
    await doctorWorkApi.report({ orderId: 12, title: '血常规' })
    expect(http.post).toHaveBeenCalledWith('/payment/pay', { orderId: 9, channel: 0 })
    expect(http.post).toHaveBeenCalledWith('/doctor-work/call', null, { params: { scheduleId: 9 } })
    expect(http.post).toHaveBeenCalledWith('/doctor-work/report', { orderId: 12, title: '血常规' })
  })

  it('does not send departmentId when creating a schedule', async () => {
    await adminScheduleApi.create({ doctorId: 21, workDate: '2026-11-25', timeSlot: 1, totalNum: 20, price: 30, status: 1 })
    expect(vi.mocked(http.post).mock.calls[0][1]).not.toHaveProperty('departmentId')
  })
})
```

Run:

```bash
cd "D:/hospital-project/hospital-web"
npm run type-check
npm test
npm run build
```

Expected: 类型检查无错误，全部测试通过，构建生成 `dist/`。

- [ ] **Step 2: 启动后端与前端**

后端启动命令保持不变：

```bash
cd "D:/hospital-project/hospital-service"
mvn spring-boot:run
```

前端启动：

```bash
cd "D:/hospital-project/hospital-web"
npm run dev -- --host 127.0.0.1
```

Expected: 后端监听 `8080`，前端监听 `5173`，`/api` 代理可用。

- [ ] **Step 3: 用 Chrome-D 打开患者端**

Run:

```bash
powershell.exe -NoProfile -Command "Start-Process -FilePath 'C:/Users/69389/Desktop/Chrome-D.lnk' -ArgumentList 'http://localhost:5173/login'"
```

验证：

- 注册手机号与密码
- 登录后进入患者首页
- 进入科室、医生、排班
- 选择就诊人并挂号
- 进入模拟支付并完成支付
- 订单页状态更新为已支付
- 满足时间条件时完成签到
- 报告页可查看医生已开具的报告

- [ ] **Step 4: 用 Chrome-D 验证医生端**

使用医生账号登录，验证：

- 在 `hospital-web/.env.local` 设置 `VITE_DEMO_SCHEDULE_IDS` 为该医生账号已绑定医生的真实排班 id
- 工作台显示演示排班名称，不显示原始 id
- 选择排班后显示候诊队列
- 空队列时叫号禁用
- 有队列时叫号成功，患者移动到“当前就诊”区域并从候诊队列移除
- 在当前就诊区域打开报告抽屉
- 填写报告并提交成功
- 报告提交后当前就诊区域清空并刷新队列
- 请求只包含 `scheduleId` 和 `orderId`

- [ ] **Step 5: 用 Chrome-D 验证管理端**

使用管理员账号登录，验证：

- 科室新增、编辑、删除
- 医生新增、编辑、删除
- 医生表单科室显示名称，提交 `departmentId`
- 排班新增、编辑、删除
- 排班表单医生显示名称，提交 `doctorId`
- 后端拒绝删除时页面展示具体原因

- [ ] **Step 6: 验证边界场景**

逐项验证并记录结果：

- 号源售罄
- 重复挂号
- 支付超时
- 重复签到
- 不可退号
- 空候诊队列
- 未绑定医生
- 患者访问管理路由
- 管理员访问患者路由
- token 失效
- 后端服务停止时的错误提示

- [ ] **Step 7: 最终仓库检查**

Run:

```bash
git -C "D:/hospital-project" status --short
```

Expected: 只出现用户已有的后端改动/新增文件，以及 `hospital-web/`；前端开发没有修改 `hospital-service`、`sql` 或 Docker 相关文件。

- [ ] **Step 8: 汇报结果**

汇报必须包含：

- 前端目录与访问地址
- 自动化测试与构建结果
- Chrome-D 已验证的患者、医生、管理端主路径
- 未验证或受阻的项目
- 后端未改动的确认
