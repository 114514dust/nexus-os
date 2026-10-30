import { request } from './http'
import type { ChatMessage, ChatReply } from '@/types'

/** 发送对话消息（非流式，历史由后端从持久化记忆读取） */
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

export interface StreamHandlers {
  /** 收到意图识别结果（用于展示"正在查待办…"之类的状态） */
  onIntent?: (intent: string) => void
  /** 收到一段增量文本 */
  onDelta: (content: string) => void
  /** 流正常结束，带完整回复 */
  onDone?: (reply: string, intent: string) => void
}

/**
 * 流式对话：用 fetch + ReadableStream 消费 SSE。
 *
 * 为什么不用 EventSource：它只支持 GET、不能带请求体，也无法自定义 header。
 * 这里需要 POST 发送 message，所以手动解析 SSE 协议（data: {...}\n\n）。
 */
export async function streamChat(
  message: string,
  handlers: StreamHandlers,
  signal?: AbortSignal
): Promise<void> {
  const base = import.meta.env.VITE_API_BASE_URL ?? '/api'
  const res = await fetch(`${base}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify({ message }),
    signal
  })

  if (!res.ok) {
    const body = await res.json().catch(() => null)
    const msg = body?.message || (res.status >= 500 ? '服务器开小差了，请稍后重试' : '请求失败')
    throw new Error(msg)
  }
  if (!res.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 事件以空行分隔；统一按 \n\n 切，兼容 \r\n\r\n
      let sep = buffer.indexOf('\n\n')
      while (sep >= 0) {
        const rawEvent = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)

        for (const line of rawEvent.split('\n')) {
          const trimmed = line.trim()
          if (!trimmed.startsWith('data:')) continue
          const data = trimmed.slice(5).trim()
          if (!data) continue

          let payload: { type?: string; content?: string; intent?: string; reply?: string; message?: string }
          try {
            payload = JSON.parse(data)
          } catch {
            continue // 单条事件解析失败不影响后续
          }

          if (payload.type === 'intent' && payload.intent) {
            handlers.onIntent?.(payload.intent)
          } else if (payload.type === 'delta' && payload.content) {
            handlers.onDelta(payload.content)
          } else if (payload.type === 'done') {
            handlers.onDone?.(payload.reply ?? '', payload.intent ?? 'chat')
          } else if (payload.type === 'error') {
            throw new Error(payload.message || '对话失败')
          }
        }
        sep = buffer.indexOf('\n\n')
      }
    }
  } finally {
    // 正常结束或被中断都要释放底层连接
    reader.cancel().catch(() => {})
  }
}
