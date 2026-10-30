import { request } from './http'
import type { Subscription, SubscriptionDiscover } from '@/types'

export function fetchSubscriptions() {
  return request<Subscription[]>({ method: 'GET', url: '/subscriptions' })
}

export function createSubscription(data: { type: string; name: string; url: string }) {
  return request<Subscription>({ method: 'POST', url: '/subscriptions', data })
}

/**
 * 识别用户粘贴的网址。
 * 后端要真实访问那个站点（可能还要先抓网页找订阅地址），
 * 默认 10 秒不够，单独放到 45 秒。
 */
export function discoverSubscription(url: string) {
  return request<SubscriptionDiscover>({
    method: 'POST',
    url: '/subscriptions/discover',
    data: { url },
    timeout: 45000
  })
}

export function deleteSubscription(id: number) {
  return request<void>({ method: 'DELETE', url: `/subscriptions/${id}` })
}

export function toggleSubscription(id: number) {
  return request<Subscription>({ method: 'PUT', url: `/subscriptions/${id}/toggle` })
}

export function fetchSubscription(id: number) {
  return request<number>({ method: 'POST', url: `/subscriptions/${id}/fetch`, timeout: 60000 })
}

export function fetchAllSubscriptions() {
  return request<number>({ method: 'POST', url: '/subscriptions/fetch-all', timeout: 120000 })
}
