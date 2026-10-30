import { defineStore } from 'pinia'
import {
  fetchJobs,
  fetchJobStats,
  fetchJobDetail,
  createJob,
  updateJob,
  advanceJob,
  addJobEvent,
  deleteJob,
  type JobQuery
} from '@/api/jobs'
import type { JobApplication, JobDetail, JobStats } from '@/types'

export const useJobStore = defineStore('jobs', {
  state: () => ({
    items: [] as JobApplication[],
    stats: null as JobStats | null,
    loading: false
  }),
  actions: {
    async load(query: JobQuery = {}) {
      this.loading = true
      try {
        // 列表与统计并行拉：统计基于全量数据，不受当前筛选影响
        const [items, stats] = await Promise.all([fetchJobs(query), fetchJobStats()])
        this.items = items
        this.stats = stats
      } finally {
        this.loading = false
      }
    },

    async refreshStats() {
      try {
        this.stats = await fetchJobStats()
      } catch (e) {
        console.warn('[job] 统计刷新失败', e)
      }
    },

    detail(id: number): Promise<JobDetail> {
      return fetchJobDetail(id)
    },

    async add(data: Parameters<typeof createJob>[0]) {
      const job = await createJob(data)
      // 创建会触发后端广播，useLiveReload 可能已先 load() 回来，按 id 去重
      if (!this.items.some((i) => i.id === job.id)) {
        this.items.unshift(job)
      }
      this.refreshStats()
      return job
    },

    async update(id: number, data: Parameters<typeof updateJob>[1]) {
      const job = await updateJob(id, data)
      const idx = this.items.findIndex((i) => i.id === id)
      if (idx >= 0) {
        this.items[idx] = job
      } else {
        this.items.unshift(job)
      }
      this.refreshStats()
      return job
    },

    async advance(id: number, note?: string) {
      const job = await advanceJob(id, note)
      const idx = this.items.findIndex((i) => i.id === id)
      if (idx >= 0) this.items[idx] = job
      this.refreshStats()
      return job
    },

    async addNote(id: number, note: string): Promise<JobDetail> {
      const detail = await addJobEvent(id, note)
      const idx = this.items.findIndex((i) => i.id === id)
      if (idx >= 0) this.items[idx] = detail.job
      return detail
    },

    async remove(id: number) {
      await deleteJob(id)
      this.items = this.items.filter((i) => i.id !== id)
      this.refreshStats()
    }
  }
})
