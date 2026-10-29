import { defineStore } from 'pinia'
import { fetchTodos, createTodo, updateTodo, deleteTodo } from '@/api/todos'
import type { Todo } from '@/types'

export const useTodoStore = defineStore('todos', {
  state: () => ({
    items: [] as Todo[],
    loading: false
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.items = await fetchTodos()
      } finally {
        this.loading = false
      }
    },
    async add(content: string) {
      const todo = await createTodo(content)
      this.items.push(todo)
    },
    async toggle(todo: Todo) {
      await updateTodo(todo.id, { done: !todo.done })
      todo.done = !todo.done
    },
    async remove(id: number) {
      await deleteTodo(id)
      this.items = this.items.filter((t) => t.id !== id)
    }
  }
})
