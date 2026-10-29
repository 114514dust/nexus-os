import { request } from './http'
import type { Link } from '@/types'

export function fetchLinks() {
  return request<Link[]>({ method: 'GET', url: '/links' })
}

export function createLink(data: { title: string; url: string; sortOrder?: number }) {
  return request<Link>({ method: 'POST', url: '/links', data })
}

export function updateLink(id: number, patch: { title?: string; url?: string; sortOrder?: number }) {
  return request<Link>({ method: 'PUT', url: `/links/${id}`, data: patch })
}

export function deleteLink(id: number) {
  return request<void>({ method: 'DELETE', url: `/links/${id}` })
}
