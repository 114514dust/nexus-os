import { request } from './http'
import type { WidgetDefinition } from '@/types'

export function fetchWidgets() {
  return request<WidgetDefinition[]>({ method: 'GET', url: '/widgets' })
}
