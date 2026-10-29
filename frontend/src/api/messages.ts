import { request } from './http'
import type { Message, PageResult } from '@/types'

export function fetchMessages(page = 0, size = 50) {
  return request<PageResult<Message>>({ method: 'GET', url: '/messages', params: { page, size } })
}

export function receiveMessage(data: {
  source: string
  channel?: string
  sender?: string
  content: string
}) {
  return request<Message>({ method: 'POST', url: '/messages', data })
}
