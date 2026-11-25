// ============================================================
// 挂号接口并发压测脚本（k6）
//
// 目的：验证 500 并发抢 20 个号时，三层防线（Redis Lua 削峰 /
//       数据库条件 UPDATE / uk_patient_schedule 唯一键）不出现超卖。
//
// 跑法（在项目根目录）：
//   k6 run load-test/order-concurrency.js
//
// 可选环境变量：BASE_URL（默认 http://localhost:8080）
// ============================================================

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USER_COUNT = 500;          // 并发账号数
const SCHEDULE_ID = 9001;        // sql/data.sql 里的压测专用排班（库存 20）
const PATIENT_ID_BASE = 2000;    // 账号 i 对应的就诊人 id = 2000 + i

// 业务结果分类计数：压测的第一目的是验证"不会超卖"，不是 QPS
const successCount = new Counter('order_success');     // 抢号成功
const soldOutCount = new Counter('order_sold_out');    // 号源已约满（预期）
const otherFailCount = new Counter('order_other_fail'); // 其他失败（要排查）
const networkErrorCount = new Counter('order_network_error'); // 连接层被拒/超时（没拿到响应）

export const options = {
  scenarios: {
    rush: {
      executor: 'per-vu-iterations',
      vus: USER_COUNT,
      iterations: 1,
      maxDuration: '3m',
    },
  },
};

// setup 只跑一次：依次登录 500 个账号拿 token，返回给各 VU 使用
export function setup() {
  const tokens = [];
  for (let i = 1; i <= USER_COUNT; i++) {
    const username = `perf${String(i).padStart(3, '0')}`;
    const res = http.post(
      `${BASE}/auth/login`,
      JSON.stringify({ username, password: 'admin123' }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
    );
    const body = res.json();
    if (!body || body.code !== 200 || !body.data || !body.data.token) {
      throw new Error(`登录失败 ${username}: ${res.body}`);
    }
    tokens.push(body.data.token);
  }
  console.log(`已登录 ${tokens.length} 个账号，开始并发抢 ${SCHEDULE_ID} 的号`);
  return { tokens };
}

export default function (data) {
  const idx = __VU - 1;                       // VU 从 1 开始，转成 0..499
  const token = data.tokens[idx];
  const patientId = PATIENT_ID_BASE + (idx + 1); // 每个 VU 用不同的就诊人

  const res = http.post(
    `${BASE}/order`,
    JSON.stringify({ scheduleId: SCHEDULE_ID, patientId }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      tags: { name: 'order_create' },
    }
  );

  check(res, { 'HTTP 200': (r) => r.status === 200 });

  // 连接层被拒（瞬时并发超过 Tomcat acceptCount）时没有响应体，
  // 单独计数而不是让 res.json() 抛异常——否则一个网络错误会中断整个 VU
  if (res.status === 0 || !res.body) {
    networkErrorCount.add(1);
    console.warn(`网络层失败: status=${res.status}, error=${res.error}`);
    return;
  }

  const body = res.json();
  if (body && body.code === 200) {
    successCount.add(1);
  } else if (body && body.code === 2051) {
    soldOutCount.add(1);                       // 2051 = SCHEDULE_SOLD_OUT
  } else {
    otherFailCount.add(1);
    console.warn(`非预期失败: code=${body && body.code}, msg=${body && body.msg}`);
  }
}
