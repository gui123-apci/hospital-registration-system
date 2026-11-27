// 用 Playwright 自动登录并截图，产出物直接落到仓库根目录 .screenshots/
// 用法（在 hospital-web 下执行）：
//   npm i -D playwright && npx playwright install chromium
//   node scripts/screenshot.mjs
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'

const BASE = process.env.BASE_URL || 'http://localhost:5173'
const OUT = resolve(process.cwd(), '..', '.screenshots')
const VIEWPORT = { width: 1440, height: 900 }

/** 账号：按角色分别登录，页面只截该角色有权访问的部分 */
const ACCOUNTS = {
  patient: { username: process.env.PATIENT_USER || 'perf001', password: 'admin123' },
  doctor: { username: process.env.DOCTOR_USER || 'doctor001', password: 'admin123' },
  admin: { username: process.env.ADMIN_USER || 'admin', password: 'admin123' }
}

/** 每个角色要截的页面：name 是文件名，path 是路由，waitFor 是需要等到的选择器 */
const SHOTS = [
  { role: null, name: '01-login', path: '/login', waitFor: 'form' },
  { role: 'patient', name: '03-schedules', path: '/patient/schedules', waitFor: '.schedule-card, .empty-state, .el-empty' },
  { role: 'patient', name: '04-orders', path: '/patient/orders', waitFor: '.order-card, .el-empty' },
  { role: 'patient', name: '05-triage', path: '/patient/triage', waitFor: 'textarea, .triage-input' },
  // 通知铃铛：点开面板后截图，展示未读列表
  {
    role: 'patient',
    name: '09-notification',
    path: '/patient/schedules',
    waitFor: '.notification-bell .bell-button',
    action: async (page) => {
      await page.click('.notification-bell .bell-button')
      await page.waitForTimeout(600)
    }
  },
  { role: 'doctor', name: '06-doctor-schedules', path: '/doctor/schedules', waitFor: '.schedule-card, .el-empty' },
  { role: 'doctor', name: '07-workbench', path: '/doctor/workbench', waitFor: 'main' },
  { role: 'admin', name: '02-admin-dashboard', path: '/admin/dashboard', waitFor: '.stat-cards, .chart-card' },
  { role: 'admin', name: '10-admin-doctors', path: '/admin/doctors', waitFor: 'table, .el-table' },
  { role: 'admin', name: '11-admin-schedules', path: '/admin/schedules', waitFor: 'table, .el-table' }
]

async function login(page, { username, password }) {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await page.fill('input[type="text"], input[autocomplete="username"]', username)
  await page.fill('input[type="password"]', password)
  await page.click('button[type="submit"]')
  // 登录成功后路由会跳到角色首页，停留在 /login 说明失败了
  await page.waitForFunction(() => !location.pathname.startsWith('/login'), { timeout: 15000 })
}

async function main() {
  mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch()

  // 未登录页面先截
  const anonContext = await browser.newContext({ viewport: VIEWPORT })
  const anonPage = await anonContext.newPage()
  for (const shot of SHOTS.filter((s) => !s.role)) {
    await capture(anonPage, shot)
  }
  await anonContext.close()

  // 按角色分别开 context，互不干扰
  const sessions = {}
  for (const role of ['patient', 'doctor', 'admin']) {
    const context = await browser.newContext({ viewport: VIEWPORT })
    const page = await context.newPage()
    try {
      await login(page, ACCOUNTS[role])
      sessions[role] = { context, page }
    } catch (error) {
      console.warn(`[跳过] ${role} 登录失败：${error.message}`)
      await context.close()
    }
  }

  for (const shot of SHOTS.filter((s) => s.role)) {
    const session = sessions[shot.role]
    if (!session) continue
    await capture(session.page, shot)
  }

  for (const { context } of Object.values(sessions)) await context.close()
  await browser.close()
  console.log(`\n截图输出目录：${OUT}`)
}

async function capture(page, shot) {
  try {
    await page.goto(`${BASE}${shot.path}`, { waitUntil: 'networkidle' })
    if (shot.waitFor) await page.waitForSelector(shot.waitFor, { timeout: 10000 }).catch(() => {})
    await page.waitForTimeout(400)
    // 需要交互才能展示的页面（如点开通知铃铛）
    if (shot.action) await shot.action(page)
    await page.screenshot({ path: resolve(OUT, `${shot.name}.png`), fullPage: false })
    console.log(`[OK] ${shot.name}.png`)
  } catch (error) {
    console.warn(`[失败] ${shot.name}: ${error.message}`)
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
