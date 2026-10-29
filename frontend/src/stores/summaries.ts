import { defineStore } from 'pinia'
import { fetchLatestSummary, triggerDigest } from '@/api/summaries'
import type { Summary } from '@/types'

export const useSummaryStore = defineStore('summaries', {
  state: () => ({
    summary: null as Summary | null,
    loading: false,
    digesting: false
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.summary = await fetchLatestSummary()
      } finally {
        this.loading = false
      }
    },
    async digest() {
      this.digesting = true
      try {
        this.summary = await triggerDigest()
      } finally {
        this.digesting = false
      }
    }
  }
})
