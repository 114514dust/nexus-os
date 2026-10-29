import { defineStore } from 'pinia'
import { fetchLinks, createLink, deleteLink } from '@/api/links'
import type { Link } from '@/types'

export const useLinkStore = defineStore('links', {
  state: () => ({
    items: [] as Link[],
    loading: false
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.items = await fetchLinks()
      } finally {
        this.loading = false
      }
    },
    async add(data: { title: string; url: string }) {
      const link = await createLink(data)
      this.items.push(link)
    },
    async remove(id: number) {
      await deleteLink(id)
      this.items = this.items.filter((l) => l.id !== id)
    }
  }
})
