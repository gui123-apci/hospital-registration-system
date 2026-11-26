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
  return order.status === 1
    && !order.signTime
    && now >= atTime(order, 0)
    && now < atTime(order, endHour[order.timeSlot])
}
