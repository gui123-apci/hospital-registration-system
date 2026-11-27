// ============================================================
// 读接口吞吐压测（k6）：GET /schedule/list
//
// 目的：与 order-throughput.js 对比，说明"查询"与"下单"的
// 性能差异来自哪里——下单要走库存行锁 + 多表事务，查询只读。
//
// 跑法：
//   $env:DOCTOR_ID="1"; $env:VUS="100"; k6 run load-test/query-throughput.js
// ============================================================

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const DOCTOR_ID = Number(__ENV.DOCTOR_ID || 1);

export const options = {
  scenarios: {
    query: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 100),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    'http_req_failed{name:schedule_list}': ['rate<0.01'],
  },
};

export function setup() {
  const res = http.post(
    `${BASE}/auth/login`,
    JSON.stringify({ username: 'perf001', password: 'admin123' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
  );
  const body = res.json();
  if (!body || body.code !== 200) {
    throw new Error(`登录失败: ${res.body}`);
  }
  return { token: body.data.token };
}

export default function (data) {
  const res = http.get(`${BASE}/schedule/list?doctorId=${DOCTOR_ID}&days=7`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { name: 'schedule_list' },
  });
  check(res, { 'HTTP 200': (r) => r.status === 200 });
}
