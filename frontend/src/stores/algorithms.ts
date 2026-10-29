import { defineStore } from 'pinia'
import {
  fetchAlgorithms,
  createAlgorithm,
  updateAlgorithm,
  deleteAlgorithm,
  type AlgorithmQuery
} from '@/api/algorithms'
import type { AlgorithmQuestion } from '@/types'

export const useAlgorithmStore = defineStore('algorithms', {
  state: () => ({
    items: [] as AlgorithmQuestion[],
    loading: false
  }),
  actions: {
    async load(query: AlgorithmQuery = {}) {
      this.loading = true
      try {
        this.items = await fetchAlgorithms(query)
      } finally {
        this.loading = false
      }
    },
    async add(data: { title: string; difficulty: string; leetcodeUrl: string; frequency?: number }) {
      const q = await createAlgorithm(data)
      this.items.unshift(q)
    },
    async setStar(id: number, star: number) {
      await updateAlgorithm(id, { star })
      const q = this.items.find((i) => i.id === id)
      if (q) q.star = star
    },
    async setNote(id: number, note: string) {
      await updateAlgorithm(id, { note })
      const q = this.items.find((i) => i.id === id)
      if (q) q.note = note
    },
    async remove(id: number) {
      await deleteAlgorithm(id)
      this.items = this.items.filter((i) => i.id !== id)
    }
  }
})
