import { defineStore } from 'pinia'
import { fetchMessages } from '@/api/messages'
import type { Message } from '@/types'

export const useMessageStore = defineStore('messages', {
  state: () => ({
    items: [] as Message[],
    total: 0,
    loading: false
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        const page = await fetchMessages(0, 50)
        this.items = page.list
        this.total = page.total
      } finally {
        this.loading = false
      }
    }
  }
})
