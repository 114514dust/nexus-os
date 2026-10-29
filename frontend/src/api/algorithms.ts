import { request } from './http'
import type { AlgorithmQuestion } from '@/types'

export interface AlgorithmQuery {
  keyword?: string
  difficulty?: string
  sort?: 'frequency' | 'star' | 'recent'
}

export function fetchAlgorithms(query: AlgorithmQuery = {}) {
  return request<AlgorithmQuestion[]>({ method: 'GET', url: '/algorithms', params: query })
}

export function createAlgorithm(data: {
  title: string
  difficulty: string
  leetcodeUrl: string
  frequency?: number
}) {
  return request<AlgorithmQuestion>({ method: 'POST', url: '/algorithms', data })
}

export function updateAlgorithm(
  id: number,
  data: Partial<Pick<AlgorithmQuestion, 'title' | 'difficulty' | 'leetcodeUrl' | 'frequency' | 'star' | 'note'>>
) {
  return request<AlgorithmQuestion>({ method: 'PUT', url: `/algorithms/${id}`, data })
}

export function deleteAlgorithm(id: number) {
  return request<void>({ method: 'DELETE', url: `/algorithms/${id}` })
}
