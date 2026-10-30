import { request } from './http'
import type { ChatMessage, ChatReply } from '@/types'

/** 发送对话消息（历史由后端从持久化记忆读取，前端无需传） */
export function sendChat(message: string): Promise<ChatReply> {
  return request<ChatReply>({
    method: 'POST',
    url: '/chat',
    data: { message },
    // LLM 调用可能较慢，单独放宽超时
    timeout: 120000
  })
}

/** 拉取对话历史（时间正序） */
export function fetchChatHistory(): Promise<ChatMessage[]> {
  return request<ChatMessage[]>({ method: 'GET', url: '/chat/history' })
}

/** 清空对话记忆 */
export function clearChatHistory(): Promise<void> {
  return request<void>({ method: 'DELETE', url: '/chat/history' })
}
