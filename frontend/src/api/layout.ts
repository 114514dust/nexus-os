import { request } from './http'
import type { LayoutDto } from '@/types'

export function fetchLayout(pageId: number) {
  return request<LayoutDto>({ method: 'GET', url: `/pages/${pageId}/layout` })
}

export function saveLayout(pageId: number, layout: LayoutDto) {
  return request<LayoutDto>({ method: 'PUT', url: `/pages/${pageId}/layout`, data: layout })
}
