let offsetMs = 0

/**
 * 用 HTTP 响应头的 Date 校正客户端时钟。
 *
 * 退号、签到都有时段边界，用户的设备时间可能不准（时区/手动改时间/系统漂移），
 * 以后再拿本地 new Date() 判断就会出现"明明在时段内却点不了"或反之。
 * Date 头精确到秒，误差小于 1 秒，足够判断边界；拿不到时退化为本地时间。
 */
export function syncServerClock(dateHeader: unknown) {
  if (typeof dateHeader !== 'string') return
  const server = Date.parse(dateHeader)
  if (Number.isNaN(server)) return
  offsetMs = server - Date.now()
}

/** 以服务端时间为准的"现在" */
export function serverNow() {
  return new Date(Date.now() + offsetMs)
}
