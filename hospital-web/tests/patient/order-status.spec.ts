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
  it('allows paid orders to sign throughout the visit date before slot end', () => {
    expect(canSign(base, new Date('2026-11-25T00:00:00'))).toBe(true)
    expect(canSign(base, new Date('2026-11-25T07:30:00'))).toBe(true)
    expect(canSign(base, new Date('2026-11-25T11:59:59'))).toBe(true)
    expect(canSign(base, new Date('2026-11-25T12:00:00'))).toBe(false)
    expect(canSign(base, new Date('2026-11-24T11:30:00'))).toBe(false)
    expect(canSign(base, new Date('2026-11-26T07:30:00'))).toBe(false)
    expect(canSign({ ...base, status: 0 }, new Date('2026-11-25T07:30:00'))).toBe(false)
    expect(canSign({ ...base, signTime: '2026-11-25T08:10:00' }, new Date('2026-11-25T09:00:00'))).toBe(false)
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

  it('treats the deadline, slot start, and slot end as exclusive boundaries', () => {
    const pending = { ...base, status: 0 as const }
    expect(canPay(pending, new Date('2026-11-25T10:00:00'))).toBe(false)
    expect(canRefund(base, new Date('2026-11-25T08:00:00'))).toBe(false)
    expect(canSign(base, new Date('2026-11-25T08:00:00'))).toBe(true)
    expect(canSign(base, new Date('2026-11-25T12:00:00'))).toBe(false)
  })

  it('does not allow actions for orders in another status or without a valid deadline', () => {
    const pending = { ...base, status: 0 as const, deadlineTime: undefined }
    expect(canPay(pending, new Date('2026-11-25T09:30:00'))).toBe(false)
    expect(canPay({ ...pending, deadlineTime: 'invalid' }, new Date('2026-11-25T09:30:00'))).toBe(false)
    expect(canRefund({ ...base, status: 2 }, new Date('2026-11-25T07:30:00'))).toBe(false)
    expect(canSign({ ...base, status: 3 }, new Date('2026-11-25T09:30:00'))).toBe(false)
  })
})
