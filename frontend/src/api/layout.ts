import { request } from './http'
import type { LayoutDto } from '@/types'

export function fetchLayout() {
  return request<LayoutDto>({ method: 'GET', url: '/layout' })
}

export function saveLayout(layout: LayoutDto) {
  return request<LayoutDto>({ method: 'PUT', url: '/layout', data: layout })
}
