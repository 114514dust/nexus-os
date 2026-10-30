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
  fetchMailDigest,
  pushMailDigest,
  fetchSenderRules,
  createSenderRule,
  deleteSenderRule,
  type MailQuery
} from '@/api/mail'
import type { MailDigest, MailMessageView, MailSettings, MailStats, SenderRule } from '@/types'

export const useMailStore = defineStore('mail', {
  state: () => ({
    items: [] as MailMessageView[],
    stats: null as MailStats | null,
    settings: null as MailSettings | null,
    rules: [] as SenderRule[],
    digest: null as MailDigest | null,
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

    async loadRules() {
      this.rules = await fetchSenderRules()
      return this.rules
    },

    /** 新增规则：同一 pattern 重复添加时后端会报 409，这里交给调用方提示 */
    async addRule(pattern: string, action: SenderRule['action'], note = '') {
      const rule = await createSenderRule({ pattern, action, note })
      this.rules = [...this.rules, rule]
      return rule
    },

    async removeRule(id: number) {
      await deleteSenderRule(id)
      this.rules = this.rules.filter((r) => r.id !== id)
    },

    async loadDigest(date?: string) {
      this.digest = await fetchMailDigest(date)
      return this.digest
    },

    async pushDigest(date?: string) {
      return pushMailDigest(date)
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
