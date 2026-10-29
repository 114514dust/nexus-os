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
    async load(page = 0, size = 50) {
      this.loading = true
      try {
        const result = await fetchMessages(page, size)
        this.items = result.list
        this.total = result.total
      } finally {
        this.loading = false
      }
    }
  }
})
