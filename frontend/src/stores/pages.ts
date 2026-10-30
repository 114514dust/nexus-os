import { defineStore } from 'pinia'
import { fetchPages, createPage, renamePage, deletePage } from '@/api/pages'
import type { Page } from '@/types'

/** 记住上次停留的页面，刷新后不用每次都回到第一页 */
const CURRENT_KEY = 'nexus.currentPageId'

function readSavedId(): number | null {
  const raw = localStorage.getItem(CURRENT_KEY)
  const id = raw === null ? NaN : Number(raw)
  return Number.isFinite(id) ? id : null
}

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
        if (this.pages.length === 0) {
          this.currentId = null
          return
        }
        // 优先恢复上次的页面，页面已被删除时退回第一个
        const saved = readSavedId()
        const exists = saved !== null && this.pages.some((p) => p.id === saved)
        if (!exists || this.currentId === null) {
          this.currentId = exists ? saved : this.pages[0].id
        }
      } finally {
        this.loading = false
      }
    },
    select(id: number) {
      this.currentId = id
      localStorage.setItem(CURRENT_KEY, String(id))
    },
    async create(name: string) {
      const page = await createPage(name)
      this.pages.push(page)
      this.select(page.id)
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
        if (this.pages.length > 0) {
          this.select(this.pages[0].id)
        } else {
          this.currentId = null
          localStorage.removeItem(CURRENT_KEY)
        }
      }
    }
  }
})
