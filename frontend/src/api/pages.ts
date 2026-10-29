import { request } from './http'
import type { Page } from '@/types'

export function fetchPages() {
  return request<Page[]>({ method: 'GET', url: '/pages' })
}

export function createPage(name: string) {
  return request<Page>({ method: 'POST', url: '/pages', data: { name } })
}

export function renamePage(id: number, name: string) {
  return request<Page>({ method: 'PUT', url: `/pages/${id}`, data: { name } })
}

export function deletePage(id: number) {
  return request<void>({ method: 'DELETE', url: `/pages/${id}` })
}
