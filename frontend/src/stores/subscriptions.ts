import { defineStore } from 'pinia'
import {
  fetchSubscriptions,
  createSubscription,
  deleteSubscription,
  toggleSubscription,
  fetchSubscription,
  fetchAllSubscriptions,
  discoverSubscription
} from '@/api/subscriptions'
import type { Subscription, SubscriptionDiscover } from '@/types'

export const useSubscriptionStore = defineStore('subscriptions', {
  state: () => ({
    items: [] as Subscription[],
    loading: false,
    fetchingId: null as number | null,
    fetchingAll: false
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
    /** 只识别不落库，让用户先看到「识别成了什么」 */
    async discover(url: string): Promise<SubscriptionDiscover> {
      return discoverSubscription(url)
    },
    async add(data: { type: string; name: string; url: string }): Promise<Subscription> {
      const sub = await createSubscription(data)
      await this.load()
      return sub
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
    },
    async fetchAll(): Promise<number> {
      this.fetchingAll = true
      try {
        const total = await fetchAllSubscriptions()
        await this.load()
        return total
      } finally {
        this.fetchingAll = false
      }
    }
  }
})
