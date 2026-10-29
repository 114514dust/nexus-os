import { request } from './http'
import type { Note } from '@/types'

export function fetchNotes() {
  return request<Note[]>({ method: 'GET', url: '/notes' })
}

export function createNote(content: string) {
  return request<Note>({ method: 'POST', url: '/notes', data: { content } })
}

export function updateNote(id: number, content: string) {
  return request<Note>({ method: 'PUT', url: `/notes/${id}`, data: { content } })
}

export function deleteNote(id: number) {
  return request<void>({ method: 'DELETE', url: `/notes/${id}` })
}
