import { defineStore } from 'pinia'
import { sendChat } from '@/api/chats'
import { friendlyError } from '@/utils/toast'
import type { ChatMessage } from '@/types'

const MAX_HISTORY = 12 // 每次最多携带的最近对话条数（多轮上下文）

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [] as ChatMessage[],
    sending: false
  }),
  actions: {
    async send(text: string) {
      const content = text.trim()
      if (!content || this.sending) return
      this.messages.push({ role: 'user', content })
      this.sending = true
      try {
        // 取当前消息之前的最近 N 条作为上下文（不含刚发的那条）
        const history = this.messages.slice(0, -1).slice(-MAX_HISTORY)
        const res = await sendChat(content, history)
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
    clear() {
      this.messages = []
    }
  }
})
