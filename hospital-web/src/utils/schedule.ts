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

export function localDateString(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const slotStartHours: Record<TimeSlot, number> = { 1: 8, 2: 13, 3: 18 }

export function isScheduleExpired(workDate: string, timeSlot: TimeSlot, now = new Date()) {
  const today = localDateString(now)
  return workDate < today || (workDate === today && now.getHours() >= slotStartHours[timeSlot])
}
