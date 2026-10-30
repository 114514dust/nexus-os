import { defineStore } from 'pinia'
import { fetchNotifySettings, saveNotifySettings, sendNotifyTest } from '@/api/notify'
import type { NotifySettings } from '@/types'

const emptySettings: NotifySettings = {
  enabled: false,
  channel: 'feishu',
  webhookUrl: '',
  secret: '',
  onFeed: true,
  onDigest: true,
  onInterview: true,
  onTodo: true,
  onPosting: true,
  onMail: true
}

export const useNotifyStore = defineStore('notify', {
  state: () => ({
    settings: { ...emptySettings } as NotifySettings,
    loading: false,
    saving: false
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.settings = await fetchNotifySettings()
      } finally {
        this.loading = false
      }
    },
    async save(settings: NotifySettings) {
      this.saving = true
      try {
        this.settings = await saveNotifySettings(settings)
      } finally {
        this.saving = false
      }
    },
    async test() {
      await sendNotifyTest()
    }
  }
})
