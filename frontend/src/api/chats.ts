import { request } from './http'
import type { ChatMessage, ChatReply } from '@/types'

/** 发送对话消息（history 为最近多轮上下文） */
export function sendChat(message: string, history: ChatMessage[]): Promise<ChatReply> {
  return request<ChatReply>({
    method: 'POST',
    url: '/chat',
    data: { message, history },
    // LLM 调用可能较慢，单独放宽超时
    timeout: 120000
  })
}
