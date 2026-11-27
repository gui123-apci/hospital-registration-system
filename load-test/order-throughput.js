// ============================================================
// 挂号接口吞吐压测（k6）
//
// 与 order-concurrency.js 的区别：
//   order-concurrency.js  验证"不超卖"（500 抢 20，看业务结果分布）
//   本脚本                验证"吞吐与延迟"（大库存排班，持续下单，看 QPS / P99）
//
// 跑法（项目根目录）：
//   $env:SCHEDULE_ID="9206"; k6 run load-test/order-throughput.js
//   $env:VUS="100"; $env:DURATION="30s"; k6 run load-test/order-throughput.js
//
// 前置数据：一个 left_num 足够大的排班（脚本不负责造数据，见 load-test/README 说明）
// ============================================================

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USER_COUNT = Number(__ENV.USER_COUNT || 500);
// 支持多个排班分散热点：SCHEDULE_IDS="9206,9231,9232"
const SCHEDULE_IDS = (__ENV.SCHEDULE_IDS || __ENV.SCHEDULE_ID || '')
  .split(',')
  .map((s) => Number(s.trim()))
  .filter(Boolean);
const PATIENT_ID_BASE = 2000;   // perf{i} 对应就诊人 id = 2000 + i

const businessSuccess = new Counter('order_biz_success');
const businessFail = new Counter('order_biz_fail');
const orderLatency = new Trend('order_latency', true);

export const options = {
  scenarios: {
    throughput: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 100),
      duration: __ENV.DURATION || '30s',
    },
  },
  // 基线来自本地 Docker 单机（MySQL + Redis + 应用同机）：
  //   5 排班 / 100 VU：下单 p95 ≈ 1.2s，完整闭环约 19 单/s
  //   单排班 / 50 VU：下单 p95 ≈ 1.8s（同排班行锁热点）
  // 阈值按本地环境放宽，生产需按真实硬件重测
  thresholds: {
    'http_req_duration{name:order_create}': ['p(95)<2500', 'p(99)<4000'],
    'http_req_failed{name:order_create}': ['rate<0.01'],
  },
};

export function setup() {
  if (!SCHEDULE_IDS.length) {
    throw new Error('缺少 SCHEDULE_ID / SCHEDULE_IDS 环境变量');
  }
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
  console.log(`已登录 ${tokens.length} 个账号，压测排班 ${SCHEDULE_IDS.join(',')}`);
  return { tokens };
}

export default function (data) {
  const idx = (__VU - 1) % data.tokens.length;
  const token = data.tokens[idx];
  const patientId = PATIENT_ID_BASE + (idx + 1);
  const scheduleId = SCHEDULE_IDS[idx % SCHEDULE_IDS.length];

  const res = http.post(
    `${BASE}/order`,
    JSON.stringify({ scheduleId, patientId }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      tags: { name: 'order_create' },
    }
  );

  if (res.status !== 0 && res.body) {
    orderLatency.add(res.timings.duration);
    const body = res.json();
    if (body && body.code === 200) {
      businessSuccess.add(1);
      // 完整闭环：下单 → 支付 → 退号。
      // 只退号不行——待支付单状态不允许退（2056）；不闭环则第二轮起全是 2053 一人一单
      const orderId = body.data.id;
      const payRes = http.post(
        `${BASE}/payment/pay`,
        JSON.stringify({ orderId, channel: 1 }),
        {
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          tags: { name: 'order_pay' },
        }
      );
      const payBody = payRes.status !== 0 && payRes.body ? payRes.json() : null;
      if (payBody && payBody.code === 200) {
        http.post(
          `${BASE}/payment/refund`,
          JSON.stringify({ orderId }),
          {
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${token}`,
            },
            tags: { name: 'order_refund' },
          }
        );
      }
    } else {
      businessFail.add(1);
    }
  }

  check(res, { 'HTTP 200': (r) => r.status === 200 });
}
