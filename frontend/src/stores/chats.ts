import { defineStore } from 'pinia'
import { sendChat, fetchChatHistory, clearChatHistory } from '@/api/chats'
import { friendlyError } from '@/utils/toast'
import type { ChatMessage } from '@/types'

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
    async send(text: string) {
      const content = text.trim()
      if (!content || this.sending) return
      this.messages.push({ role: 'user', content })
      this.sending = true
      try {
        const res = await sendChat(content)
        this.messages.push({ role: 'assistant', content: res.reply })
        this.lastFailed = ''
      } catch (err) {
        this.lastFailed = content
        this.messages.push({
          role: 'assistant',
          content: '对话失败：' + friendlyError(err),
          error: true
        })
      } finally {
        this.sending = false
      }
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
