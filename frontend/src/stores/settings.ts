import { defineStore } from 'pinia'
import { fetchSettings, saveSettings } from '@/api/settings'
import type { LlmSettings } from '@/types'

export const useSettingsStore = defineStore('settings', {
  state: () => ({
    settings: { apiKey: '', baseUrl: '', model: '' } as LlmSettings,
    loading: false,
    saving: false
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.settings = await fetchSettings()
      } finally {
        this.loading = false
      }
    },
    async save(settings: LlmSettings) {
      this.saving = true
      try {
        this.settings = await saveSettings(settings)
      } finally {
        this.saving = false
      }
    }
  }
})
