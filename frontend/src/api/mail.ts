import { request } from './http'
import type {
  MailDigest,
  MailMessageView,
  MailSettings,
  MailStats,
  MailSyncResult,
  SenderRule
} from '@/types'

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

/** 附件的直链。首次点开时后端会回 IMAP 取一次并存到本地，之后直接读盘 */
export function mailAttachmentUrl(id: number, inline = false) {
  const base = import.meta.env.VITE_API_BASE_URL ?? '/api'
  return `${base}/mail/attachments/${id}/download${inline ? '?inline=true' : ''}`
}

/** 某天的摘要（默认昨天），只读不推送 */
export function fetchMailDigest(date?: string) {
  return request<MailDigest>({ method: 'GET', url: '/mail/digest', params: date ? { date } : {} })
}

/** 立即推送一次摘要；没有要紧邮件时后端会返回 pushed=false */
export function pushMailDigest(date?: string) {
  return request<{ pushed: boolean; message: string }>({
    method: 'POST',
    url: '/mail/digest/push',
    params: date ? { date } : {},
    timeout: 30000
  })
}

export function fetchSenderRules() {
  return request<SenderRule[]>({ method: 'GET', url: '/mail/rules' })
}

export function createSenderRule(data: { pattern: string; action: string; note?: string }) {
  return request<SenderRule>({ method: 'POST', url: '/mail/rules', data })
}

export function updateSenderRule(
  id: number,
  data: { pattern?: string; action?: string; note?: string }
) {
  return request<SenderRule>({ method: 'PUT', url: `/mail/rules/${id}`, data })
}

export function deleteSenderRule(id: number) {
  return request<void>({ method: 'DELETE', url: `/mail/rules/${id}` })
}
