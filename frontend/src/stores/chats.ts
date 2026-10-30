import { defineStore } from 'pinia'
import { streamChat, fetchChatHistory, clearChatHistory } from '@/api/chats'
import { friendlyError } from '@/utils/toast'
import type { ChatMessage } from '@/types'

/** 当前流式请求的中断控制器（非响应式，放模块级避免被 Pinia proxy 包装） */
let controller: AbortController | null = null

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [] as ChatMessage[],
    sending: false,
    loading: false,
    /** 最近一次发送失败的用户原文，用于「重试」 */
    lastFailed: ''
  }),
  actions: {
    /** 从后端加载持久化的对话历史 */
    async load() {
      this.loading = true
      try {
        this.messages = await fetchChatHistory()
      } finally {
        this.loading = false
      }
    },

    /**
     * 发送消息（流式）：先插入一条空的 assistant 占位消息，
     * 收到 delta 就追加内容，界面呈现逐字输出。
     *
     * 更新做成 16ms 节流：每个 token 都触发一次 Markdown 渲染 + 代码高亮太贵，
     * 长回复会明显掉帧。攒一小批再刷，观感上依然是连续的。
     */
    async send(text: string) {
      const content = text.trim()
      if (!content || this.sending) return

      this.messages.push({ role: 'user', content })
      this.messages.push({ role: 'assistant', content: '', streaming: true })
      const idx = this.messages.length - 1

      this.sending = true
      controller = new AbortController()

      let pending = ''
      let timer: ReturnType<typeof setTimeout> | null = null

      const flush = () => {
        if (timer) {
          clearTimeout(timer)
          timer = null
        }
        if (pending && this.messages[idx]) {
          this.messages[idx].content += pending
          pending = ''
        }
      }
      const schedule = () => {
        if (timer) return
        timer = setTimeout(flush, 16)
      }

      try {
        await streamChat(
          content,
          {
            onDelta: (delta) => {
              pending += delta
              schedule()
            },
            onDone: (reply) => {
              flush()
              // 以服务端拼好的完整文本为准，避免分块丢失导致内容不全
              if (reply && this.messages[idx]) {
                this.messages[idx].content = reply
              }
            }
          },
          controller.signal
        )
        flush()
        if (this.messages[idx]) this.messages[idx].streaming = false
        this.lastFailed = ''
      } catch (err) {
        flush()
        const target = this.messages[idx]

        if ((err as Error)?.name === 'AbortError') {
          // 用户主动停止：保留已生成的内容，不算失败
          if (target) target.streaming = false
          this.lastFailed = ''
          return
        }

        this.lastFailed = content
        if (target) target.streaming = false

        if (target && target.content) {
          // 已收到部分内容：保留，另起一条提示可重试
          this.messages.push({
            role: 'assistant',
            content: `（回复中断：${friendlyError(err)}）`,
            error: true
          })
        } else if (target) {
          // 一个字都没收到：直接转成可重试的错误气泡
          target.content = '对话失败：' + friendlyError(err)
          target.error = true
        }
      } finally {
        if (timer) clearTimeout(timer)
        this.sending = false
        controller = null
      }
    },

    /** 用户主动停止当前流式输出 */
    stop() {
      controller?.abort()
    },

    /** 重试上一次失败的请求：移除错误气泡与重复的用户消息后重发 */
    async retry() {
      const text = this.lastFailed
      if (!text || this.sending) return
      this.lastFailed = ''

      const last = this.messages[this.messages.length - 1]
      if (last?.error) this.messages.pop()

      const prev = this.messages[this.messages.length - 1]
      if (prev && prev.role === 'user' && prev.content === text) this.messages.pop()

      await this.send(text)
    },

    async clear() {
      try {
        await clearChatHistory()
      } catch {
        /* 清空失败不阻断本地清空 */
      }
      this.messages = []
      this.lastFailed = ''
    }
  }
})
