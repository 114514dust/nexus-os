import { request } from './http'
import type { NotifySettings } from '@/types'

export function fetchNotifySettings() {
  return request<NotifySettings>({ method: 'GET', url: '/notify/settings' })
}

export function saveNotifySettings(settings: NotifySettings) {
  return request<NotifySettings>({ method: 'PUT', url: '/notify/settings', data: settings })
}

/**
 * 发送测试消息。
 * 后端是同步请求外部 webhook（超时 10s），所以这里单独放宽超时，
 * 避免用默认 10s 卡在临界值上误报超时。
 */
export function sendNotifyTest() {
  return request<void>({ method: 'POST', url: '/notify/test', timeout: 20000 })
}
