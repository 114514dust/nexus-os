import { defineStore } from 'pinia'
import {
  fetchMails,
  fetchMailStats,
  fetchMailSettings,
  saveMailSettings,
  probeMail,
  syncMail,
  mailToTodo,
  ignoreMail,
  deleteMail,
  type MailQuery
} from '@/api/mail'
import type { MailMessageView, MailSettings, MailStats } from '@/types'

export const useMailStore = defineStore('mail', {
  state: () => ({
    items: [] as MailMessageView[],
    stats: null as MailStats | null,
    settings: null as MailSettings | null,
    loading: false,
    syncing: false
  }),
  actions: {
    async load(query: MailQuery = {}) {
      this.loading = true
      try {
        const [items, stats] = await Promise.all([fetchMails(query), fetchMailStats()])
        this.items = items
        this.stats = stats
      } finally {
        this.loading = false
      }
    },

    async loadSettings() {
      this.settings = await fetchMailSettings()
      return this.settings
    },

    async saveSettings(patch: Partial<MailSettings>) {
      this.settings = await saveMailSettings(patch)
      return this.settings
    },

    async probe() {
      return probeMail()
    },

    async sync() {
      this.syncing = true
      try {
        return await syncMail()
      } finally {
        this.syncing = false
      }
    },

    async toTodo(id: number) {
      const updated = await mailToTodo(id)
      this.replace(updated)
      return updated
    },

    async ignore(id: number) {
      await ignoreMail(id)
      this.items = this.items.filter((i) => i.id !== id)
      if (this.stats) {
        this.stats.total = Math.max(0, this.stats.total - 1)
      }
    },

    async remove(id: number) {
      await deleteMail(id)
      this.items = this.items.filter((i) => i.id !== id)
    },

    replace(view: MailMessageView) {
      const idx = this.items.findIndex((i) => i.id === view.id)
      // 已转待办的邮件若当前是「重要」视图，保留在列表里并更新状态即可，
      // 让用户看到「这条已经处理了」，而不是突然消失
      if (idx >= 0) {
        this.items[idx] = { ...this.items[idx], ...view }
      }
    }
  }
})
