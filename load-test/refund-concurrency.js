// ============================================================
// 退号回补压测脚本（k6）
//
// 目的：验证"退号"这条逆向链路是完整的——
//   抢到号的人付款后退号，那 20 个号应该真的回到池子里，能被重新挂走。
//   中间任何一环漏了（订单没让位、号源没回可用、库存没 +1、Redis key 没作废），
//   第三段就会抢不动。
//
// 一个 VU 按时间顺序跑三段，用 sleep 隔开：
//   ① 抢号     500 并发抢 20 个号 → 期望成交 20、拒绝 480
//   ② 支付+退号 抢到的人在本地拿到 orderId（不靠查列表猜）→ 期望退号 20
//   ③ 再抢号    同一批人再抢一次 → 期望又成交 20，且没有"重复挂号"
//
// 为什么不用多个 scenario 按 startTime 排期：
//   多个 scenario 下 __VU 是全局编号（不是每个 scenario 从 1 开始），
//   patientId / token 的映射会错位，可能两个人用同一个就诊人 → 冒出 2053 假失败。
//   单 scenario + sleep 分段，VU 编号稳定在 1..N，脚本也更好读。
//
// 跑法（先重置数据，见 load-test/reset-perf-data.sql 顶部说明）：
//   k6 run load-test/refund-concurrency.js
//
// 可选环境变量：
//   BASE_URL  默认 http://localhost:8080
//   VUS       默认 500（sql/data.sql 只预置了 perf001~perf500 共 500 个账号，别超过 500）
//   GAP       段间隔秒数，默认 8
// ============================================================

import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 500);
const GAP = Number(__ENV.GAP || 8);

const SCHEDULE_ID = 9001;      // data.sql 里的压测专用排班
const PATIENT_ID_BASE = 2000;  // 账号 i 对应的就诊人 id = 2000 + i

// ---------- 指标：每段单独计数，最后一眼能看出哪段不对 ----------
const rush1Success = new Counter('rush1_success');
const rush1SoldOut = new Counter('rush1_sold_out');
const rush1Fail = new Counter('rush1_other_fail');

const paySuccess = new Counter('pay_success');
const refundSuccess = new Counter('refund_success');
const payRefundFail = new Counter('pay_refund_fail');

const rush2Success = new Counter('rush2_success');
const rush2SoldOut = new Counter('rush2_sold_out');
const rush2Duplicate = new Counter('rush2_duplicate'); // >0 说明退号没有让位
const rush2Fail = new Counter('rush2_other_fail');

export const options = {
  scenarios: {
    refundRoundTrip: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '5m',
    },
  },
};

// setup 只跑一次：依次登录全部压测账号，把 token 分给各 VU
export function setup() {
  const tokens = [];
  for (let i = 1; i <= VUS; i++) {
    const username = `perf${String(i).padStart(3, '0')}`;
    const res = http.post(
      `${BASE}/auth/login`,
      JSON.stringify({ username, password: 'admin123' }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
    );
    const body = res.json();
    if (!body || body.code !== 200 || !body.data || !body.data.token) {
      throw new Error(
        `登录失败 ${username}: ${res.body}\n` +
        '排查顺序：① 后端连的是哪个库？② 该库里 tb_user 有没有 perf 开头的账号\n' +
        '（缺账号就跑 load-test/seed-perf-accounts.sql 补种，不用重建数据卷）'
      );
    }
    tokens.push(body.data.token);
  }
  console.log(`已登录 ${tokens.length} 个账号，开始三段压测（排班 ${SCHEDULE_ID}，库存 20）`);
  return { tokens };
}

export default function (data) {
  const idx = __VU - 1;                       // VU 从 1 开始 → 0..VUS-1
  const token = data.tokens[idx];
  const patientId = PATIENT_ID_BASE + (idx + 1);
  const auth = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
  const payload = JSON.stringify({ scheduleId: SCHEDULE_ID, patientId });

  // ---------- ① 抢号 ----------
  const r1 = http.post(`${BASE}/order`, payload, { headers: auth, tags: { name: 'order_create' } });
  const code1 = bodyCode(r1);
  let orderId = null;
  if (code1 === 200) {
    orderId = r1.json().data.id;   // 自己下单拿到的 id，三段都用它，不用查列表
    rush1Success.add(1);
  } else if (code1 === 2051) {
    rush1SoldOut.add(1);           // 预期：480 个
  } else {
    rush1Fail.add(1);
    warn('抢号', code1, r1);
  }

  sleep(GAP);

  // ---------- ② 支付 + 退号（只有抢到的 VU 才有 orderId）----------
  if (orderId !== null) {
    const payRes = http.post(
      `${BASE}/payment/pay`,
      JSON.stringify({ orderId, channel: 0 }),
      { headers: auth, tags: { name: 'pay' } }
    );
    const payCode = bodyCode(payRes);
    if (payCode === 200) {
      paySuccess.add(1);

      // 退号只认"已支付"的单，所以必须先 pay 再 refund
      const refundRes = http.post(
        `${BASE}/payment/refund`,
        JSON.stringify({ orderId }),
        { headers: auth, tags: { name: 'refund' } }
      );
      const refundCode = bodyCode(refundRes);
      if (refundCode === 200) {
        refundSuccess.add(1);
      } else {
        payRefundFail.add(1);
        warn('退号', refundCode, refundRes);
      }
    } else {
      payRefundFail.add(1);
      warn('支付', payCode, payRes);
    }
  }

  sleep(GAP);

  // ---------- ③ 再抢一次：退掉的 20 个号应该已经回到池子 ----------
  const r2 = http.post(`${BASE}/order`, payload, { headers: auth, tags: { name: 'order_create_again' } });
  const code2 = bodyCode(r2);
  if (code2 === 200) {
    rush2Success.add(1);           // 预期：20
  } else if (code2 === 2051) {
    rush2SoldOut.add(1);           // 预期：480
  } else if (code2 === 2053) {
    // 退号后还能"重复挂号"，说明 active_flag 没置 NULL，唯一键没让位
    rush2Duplicate.add(1);
    warn('再抢号(重复挂号)', code2, r2);
  } else {
    rush2Fail.add(1);
    warn('再抢号', code2, r2);
  }
}

/**
 * 统一取业务码：
 *  -1  连接层失败（被拒/超时，没有响应体）
 *  -2  有响应但不是合法 JSON
 * 其余为 Result.code
 */
function bodyCode(res) {
  if (res.status === 0 || !res.body) return -1;
  try {
    return res.json().code;
  } catch (e) {
    return -2;
  }
}

function warn(phase, code, res) {
  const msg = (() => {
    try {
      return res.json().msg;
    } catch (e) {
      return '<非 JSON 响应>';
    }
  })();
  console.warn(`${phase} 失败: code=${code}, msg=${msg}`);
}

// 结束时把关键计数打出来，和预期值一眼对照
export function handleSummary(data) {
  const count = (name) => {
    const m = data.metrics[name];
    return m && m.values && typeof m.values.count === 'number' ? m.values.count : 0;
  };

  const expected = [
    ['rush1_success',     '① 抢号成交',        20],
    ['rush1_sold_out',    '① 已约满（预期）',  480],
    ['rush1_other_fail',  '① 其他失败',        0],
    ['refund_success',    '② 退号成功',        20],
    ['pay_refund_fail',   '② 支付/退号失败',   0],
    ['rush2_success',     '③ 再抢成交',        20],
    ['rush2_sold_out',    '③ 已约满（预期）',  480],
    ['rush2_duplicate',   '③ 重复挂号（应为0）', 0],
    ['rush2_other_fail',  '③ 其他失败',        0],
  ];

  const lines = ['', '===== 退号回补压测结果 ====='];
  lines.push('指标'.padEnd(28) + '实际'.padStart(6) + '期望'.padStart(8));
  for (const [name, label, expect] of expected) {
    const actual = count(name);
    const flag = actual === expect ? '' : '  <== 不符合预期';
    lines.push(label.padEnd(24) + String(actual).padStart(8) + String(expect).padStart(8) + flag);
  }
  lines.push('');
  lines.push('别忘了用 SQL 对账（最终状态才是真相）：');
  lines.push('  Get-Content load-test\\reconcile-perf.sql | docker exec -i hospital-mysql mysql -uroot -p123456');
  lines.push('');

  return { stdout: lines.join('\n') };
}
