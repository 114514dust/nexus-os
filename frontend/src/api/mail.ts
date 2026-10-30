import { request } from './http'
import type { MailMessageView, MailSettings, MailStats, MailSyncResult } from '@/types'

export interface MailQuery {
  /** important（默认，只看高/中置信度）/ all / todo（已转待办） */
  scope?: 'important' | 'all' | 'todo'
  keyword?: string
  limit?: number
}

export function fetchMails(query: MailQuery = {}) {
  return request<MailMessageView[]>({ method: 'GET', url: '/mail/messages', params: query })
}

export function fetchMailDetail(id: number) {
  return request<MailMessageView>({ method: 'GET', url: `/mail/messages/${id}` })
}

export function fetchMailStats() {
  return request<MailStats>({ method: 'GET', url: '/mail/stats' })
}

export function fetchMailSettings() {
  return request<MailSettings>({ method: 'GET', url: '/mail/settings' })
}

export function saveMailSettings(data: Partial<MailSettings>) {
  return request<MailSettings>({ method: 'PUT', url: '/mail/settings', data })
}

/** 测试连接：只连一次、读一下收件箱数量 */
export function probeMail() {
  return request<MailSyncResult>({ method: 'POST', url: '/mail/probe', timeout: 30000 })
}

/**
 * 立即收取。
 * 单封邮件的 IMAP 往返就要几百毫秒，遇到大附件更慢，
 * 默认 10 秒的全局超时不够用，这里单独放宽到 90 秒。
 */
export function syncMail() {
  return request<MailSyncResult>({ method: 'POST', url: '/mail/sync', timeout: 90000 })
}

/** 把邮件转成待办（返回更新后的邮件视图） */
export function mailToTodo(id: number) {
  return request<MailMessageView>({ method: 'POST', url: `/mail/messages/${id}/todo` })
}

export function ignoreMail(id: number) {
  return request<void>({ method: 'POST', url: `/mail/messages/${id}/ignore` })
}

export function deleteMail(id: number) {
  return request<void>({ method: 'DELETE', url: `/mail/messages/${id}` })
}
