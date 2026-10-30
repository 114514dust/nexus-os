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
      // 去重：创建后后端会广播变更，useLiveReload 可能已先 load() 把这条带回来
      if (!this.items.some((t) => t.id === todo.id)) {
        this.items.push(todo)
      }
      return todo
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
