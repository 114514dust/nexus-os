import { request } from './http'
import type { LlmSettings } from '@/types'

export function fetchSettings() {
  return request<LlmSettings>({ method: 'GET', url: '/settings' })
}

export function saveSettings(settings: LlmSettings) {
  return request<LlmSettings>({ method: 'PUT', url: '/settings', data: settings })
}
