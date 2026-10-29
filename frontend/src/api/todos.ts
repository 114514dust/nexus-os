import { request } from './http'
import type { Todo } from '@/types'

export function fetchTodos() {
  return request<Todo[]>({ method: 'GET', url: '/todos' })
}

export function createTodo(content: string) {
  return request<Todo>({ method: 'POST', url: '/todos', data: { content } })
}

export function updateTodo(id: number, patch: { content?: string; done?: boolean }) {
  return request<Todo>({ method: 'PUT', url: `/todos/${id}`, data: patch })
}

export function deleteTodo(id: number) {
  return request<void>({ method: 'DELETE', url: `/todos/${id}` })
}
