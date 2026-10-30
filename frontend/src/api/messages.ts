import { request } from './http'
import type { Message, MessagePage } from '@/types'

export function fetchMessages(params: {
  page?: number
  size?: number
  source?: string
  unread?: boolean
}) {
  return request<MessagePage>({ method: 'GET', url: '/messages', params })
}

export function receiveMessage(data: {
  source: string
  channel?: string
  sender?: string
  content: string
}) {
  return request<Message>({ method: 'POST', url: '/messages', data })
}

/** 标记单条已读（read=false 可恢复未读） */
export function markMessageRead(id: number, read = true) {
  return request<void>({ method: 'POST', url: `/messages/${id}/read`, params: { read } })
}

/** 全部标记已读，传 source 只清某一类 */
export function markAllMessagesRead(source?: string) {
  return request<number>({
    method: 'POST',
    url: '/messages/read-all',
    params: source ? { source } : {}
  })
}

export function deleteMessage(id: number) {
  return request<void>({ method: 'DELETE', url: `/messages/${id}` })
}
