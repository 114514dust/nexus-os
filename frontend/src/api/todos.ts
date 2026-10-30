import { request } from './http'
import type { Todo } from '@/types'

export function fetchTodos() {
  return request<Todo[]>({ method: 'GET', url: '/todos' })
}

export function createTodo(content: string, dueDate?: string | null) {
  return request<Todo>({ method: 'POST', url: '/todos', data: { content, dueDate: dueDate || null } })
}

/**
 * 更新待办。
 * 后端约定 `null = 不更新`，所以清空日期不能传 null，要用 clearDueDate 显式标记。
 */
export function updateTodo(
  id: number,
  patch: { content?: string; done?: boolean; dueDate?: string; clearDueDate?: boolean }
) {
  return request<Todo>({ method: 'PUT', url: `/todos/${id}`, data: patch })
}

export function deleteTodo(id: number) {
  return request<void>({ method: 'DELETE', url: `/todos/${id}` })
}
