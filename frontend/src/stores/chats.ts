import { defineStore } from 'pinia'
import { sendChat, fetchChatHistory, clearChatHistory } from '@/api/chats'
import { friendlyError } from '@/utils/toast'
import type { ChatMessage } from '@/types'

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [] as ChatMessage[],
    sending: false,
    loading: false
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
      } catch (err) {
        this.messages.push({
          role: 'assistant',
          content: '对话失败：' + friendlyError(err)
        })
      } finally {
        this.sending = false
      }
    },
    async clear() {
      try {
        await clearChatHistory()
      } catch {
        /* 清空失败不阻断本地清空 */
      }
      this.messages = []
    }
  }
})
