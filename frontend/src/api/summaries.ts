import { request } from './http'
import type { Summary } from '@/types'

export function fetchLatestSummary() {
  return request<Summary | null>({ method: 'GET', url: '/summaries/latest' })
}

export function triggerDigest(date?: string) {
  return request<Summary>({
    method: 'POST',
    url: '/summaries/digest',
    params: date ? { date } : undefined
  })
}
