import { defineStore } from 'pinia'
import {
  fetchSubscriptions,
  createSubscription,
  deleteSubscription,
  toggleSubscription,
  fetchSubscription
} from '@/api/subscriptions'
import type { Subscription } from '@/types'

export const useSubscriptionStore = defineStore('subscriptions', {
  state: () => ({
    items: [] as Subscription[],
    loading: false,
    fetchingId: null as number | null
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.items = await fetchSubscriptions()
      } finally {
        this.loading = false
      }
    },
    async add(data: { type: string; name: string; url: string }) {
      await createSubscription(data)
      await this.load()
    },
    async remove(id: number) {
      await deleteSubscription(id)
      await this.load()
    },
    async toggle(item: Subscription) {
      await toggleSubscription(item.id)
      await this.load()
    },
    async fetchOne(id: number): Promise<number> {
      this.fetchingId = id
      try {
        return await fetchSubscription(id)
      } finally {
        this.fetchingId = null
      }
    }
  }
})
