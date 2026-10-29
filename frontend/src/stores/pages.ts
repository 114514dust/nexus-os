import { defineStore } from 'pinia'
import { fetchPages, createPage, renamePage, deletePage } from '@/api/pages'
import type { Page } from '@/types'

export const usePagesStore = defineStore('pages', {
  state: () => ({
    pages: [] as Page[],
    currentId: null as number | null,
    loading: false
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.pages = await fetchPages()
        if (this.currentId === null && this.pages.length > 0) {
          this.currentId = this.pages[0].id
        }
      } finally {
        this.loading = false
      }
    },
    select(id: number) {
      this.currentId = id
    },
    async create(name: string) {
      const page = await createPage(name)
      this.pages.push(page)
      this.currentId = page.id
      return page
    },
    async rename(id: number, name: string) {
      const page = await renamePage(id, name)
      const idx = this.pages.findIndex((p) => p.id === id)
      if (idx !== -1) this.pages[idx] = page
      return page
    },
    async remove(id: number) {
      await deletePage(id)
      this.pages = this.pages.filter((p) => p.id !== id)
      if (this.currentId === id) {
        this.currentId = this.pages.length > 0 ? this.pages[0].id : null
      }
    }
  }
})
