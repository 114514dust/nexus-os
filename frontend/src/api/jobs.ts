import { request } from './http'
import type { JobApplication, JobDetail, JobStats } from '@/types'

export interface JobQuery {
  keyword?: string
  category?: string
  stage?: string
  /** active（默认，未到终态）/ closed（已入职、未通过）/ all */
  scope?: 'active' | 'closed' | 'all'
  /** recent（默认）/ due（面试日期近的优先）/ company */
  sort?: 'recent' | 'due' | 'company'
}

export function fetchJobs(query: JobQuery = {}) {
  return request<JobApplication[]>({ method: 'GET', url: '/jobs', params: query })
}

export function fetchJobStats() {
  return request<JobStats>({ method: 'GET', url: '/jobs/stats' })
}

export function fetchJobDetail(id: number) {
  return request<JobDetail>({ method: 'GET', url: `/jobs/${id}` })
}

export function createJob(data: {
  company: string
  jobTitle?: string
  category?: string
  stage?: string
  url?: string
  nextAction?: string
  nextAt?: string | null
  starred?: boolean
  note?: string
  firstNote?: string
}) {
  return request<JobApplication>({ method: 'POST', url: '/jobs', data })
}

export function updateJob(
  id: number,
  data: Partial<
    Pick<
      JobApplication,
      'company' | 'jobTitle' | 'category' | 'stage' | 'url' | 'nextAction' | 'nextAt' | 'starred' | 'note'
    >
  > & { stageNote?: string; clearNextAt?: boolean }
) {
  return request<JobApplication>({ method: 'PUT', url: `/jobs/${id}`, data })
}

/** 一键推进到下一阶段 */
export function advanceJob(id: number, note?: string) {
  return request<JobApplication>({ method: 'POST', url: `/jobs/${id}/advance`, data: { note } })
}

/** 只追加一条进展备注，不改阶段 */
export function addJobEvent(id: number, note: string) {
  return request<JobDetail>({ method: 'POST', url: `/jobs/${id}/events`, data: { note } })
}

export function deleteJob(id: number) {
  return request<void>({ method: 'DELETE', url: `/jobs/${id}` })
}
