import { request } from './http'
import type { Subscription } from '@/types'

export function fetchSubscriptions() {
  return request<Subscription[]>({ method: 'GET', url: '/subscriptions' })
}

export function createSubscription(data: { type: string; name: string; url: string }) {
  return request<Subscription>({ method: 'POST', url: '/subscriptions', data })
}

export function deleteSubscription(id: number) {
  return request<void>({ method: 'DELETE', url: `/subscriptions/${id}` })
}

export function toggleSubscription(id: number) {
  return request<Subscription>({ method: 'PUT', url: `/subscriptions/${id}/toggle` })
}

export function fetchSubscription(id: number) {
  return request<number>({ method: 'POST', url: `/subscriptions/${id}/fetch` })
}
