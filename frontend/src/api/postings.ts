import { request } from './http'
import type {
  JobPosting,
  PostingApplyResult,
  PostingPrefs,
  PostingRefreshResult,
  PostingStats
} from '@/types'

export interface PostingQuery {
  keyword?: string
  category?: string
  /** pending（默认，待处理）/ applied（已加入追踪）/ ignored（已忽略）/ all */
  scope?: 'pending' | 'applied' | 'ignored' | 'all'
  /** 只看命中关注关键词的 */
  onlyMatched?: boolean
}

export function fetchPostings(query: PostingQuery = {}) {
  return request<JobPosting[]>({ method: 'GET', url: '/postings', params: query })
}

export function fetchPostingStats() {
  return request<PostingStats>({ method: 'GET', url: '/postings/stats' })
}

/** 手动触发抓取 */
export function refreshPostings() {
  return request<PostingRefreshResult>({ method: 'POST', url: '/postings/refresh' })
}

export function createPosting(data: {
  company: string
  title?: string
  positions?: string
  city?: string
  category?: string
  url?: string
  deadlineText?: string
  cohort?: string
}) {
  return request<JobPosting>({ method: 'POST', url: '/postings', data })
}

export function updatePosting(id: number, data: { starred?: boolean; ignored?: boolean }) {
  return request<JobPosting>({ method: 'PUT', url: `/postings/${id}`, data })
}

export function deletePosting(id: number) {
  return request<void>({ method: 'DELETE', url: `/postings/${id}` })
}

/** 一键加入求职追踪 */
export function applyPosting(
  id: number,
  data: { jobTitle?: string; category?: string; starred?: boolean; note?: string } = {}
) {
  return request<PostingApplyResult>({ method: 'POST', url: `/postings/${id}/apply`, data })
}

export function fetchPostingPrefs() {
  return request<PostingPrefs>({ method: 'GET', url: '/postings/prefs' })
}

export function savePostingPrefs(prefs: PostingPrefs) {
  return request<PostingPrefs>({ method: 'PUT', url: '/postings/prefs', data: prefs })
}
