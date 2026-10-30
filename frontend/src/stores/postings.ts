import { defineStore } from 'pinia'
import {
  fetchPostings,
  fetchPostingStats,
  refreshPostings,
  createPosting,
  updatePosting,
  deletePosting,
  applyPosting,
  fetchPostingPrefs,
  savePostingPrefs,
  type PostingQuery
} from '@/api/postings'
import type { JobPosting, PostingPrefs, PostingStats } from '@/types'

export const usePostingStore = defineStore('postings', {
  state: () => ({
    items: [] as JobPosting[],
    stats: null as PostingStats | null,
    prefs: null as PostingPrefs | null,
    loading: false,
    refreshing: false
  }),
  actions: {
    async load(query: PostingQuery = {}) {
      this.loading = true
      try {
        const [items, stats] = await Promise.all([fetchPostings(query), fetchPostingStats()])
        this.items = items
        this.stats = stats
      } finally {
        this.loading = false
      }
    },

    async loadPrefs() {
      if (this.prefs) return this.prefs
      this.prefs = await fetchPostingPrefs()
      return this.prefs
    },

    async savePrefs(prefs: PostingPrefs) {
      this.prefs = await savePostingPrefs(prefs)
      return this.prefs
    },

    async refresh() {
      this.refreshing = true
      try {
        return await refreshPostings()
      } finally {
        this.refreshing = false
      }
    },

    async add(data: Parameters<typeof createPosting>[0]) {
      const posting = await createPosting(data)
      // 后端会广播变更、useLiveReload 可能已重新 load，按 id 去重
      if (!this.items.some((i) => i.id === posting.id)) {
        this.items.unshift(posting)
      }
      return posting
    },

    async setStarred(id: number, starred: boolean) {
      const posting = await updatePosting(id, { starred })
      this.replace(posting)
      return posting
    },

    async ignore(id: number) {
      await updatePosting(id, { ignored: true })
      this.items = this.items.filter((i) => i.id !== id)
    },

    /** 加入求职追踪：岗位本身会变成「已加入」，从待处理列表里移除 */
    async apply(id: number, data: Parameters<typeof applyPosting>[1] = {}) {
      const result = await applyPosting(id, data)
      this.items = this.items.filter((i) => i.id !== id)
      if (this.stats) {
        this.stats.pending = Math.max(0, this.stats.pending - 1)
        this.stats.applied += 1
      }
      return result
    },

    async remove(id: number) {
      await deletePosting(id)
      this.items = this.items.filter((i) => i.id !== id)
    },

    replace(posting: JobPosting) {
      const idx = this.items.findIndex((i) => i.id === posting.id)
      if (idx >= 0) {
        this.items[idx] = posting
      }
    }
  }
})
