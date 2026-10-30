/**
 * 时间相对化。
 *
 * 消息流和订阅列表里，用户真正关心的是「多久以前」，
 * 完整时间戳（2026/10/30 21:26:46）占了地方还看不出新鲜度。
 */

function pad(n: number): string {
  return n < 10 ? '0' + n : String(n)
}

function sameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

export function relativeTime(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''

  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`

  if (diff < 60_000) return '刚刚'

  const minutes = Math.floor(diff / 60_000)
  if (minutes < 60 && sameDay(d, now)) return `${minutes} 分钟前`

  if (sameDay(d, now)) return `今天 ${hm}`

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (sameDay(d, yesterday)) return `昨天 ${hm}`

  if (d.getFullYear() === now.getFullYear()) {
    return `${d.getMonth() + 1} 月 ${d.getDate()} 日`
  }
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 完整时间，用于 title 悬浮提示 */
export function fullTime(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', { hour12: false })
}
