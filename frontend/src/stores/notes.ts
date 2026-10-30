import { defineStore } from 'pinia'
import { fetchNotes, createNote, updateNote, deleteNote } from '@/api/notes'
import type { Note } from '@/types'

export const useNoteStore = defineStore('notes', {
  state: () => ({
    items: [] as Note[],
    loading: false
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.items = await fetchNotes()
      } finally {
        this.loading = false
      }
    },
    /**
     * 新建便签，返回创建后的实体（组件用它定位并聚焦新文本框）。
     * 内容允许为空：先建空白草稿，用户再直接输入。
     */
    async add(content: string) {
      const note = await createNote(content)
      // 创建成功会触发后端广播 → useLiveReload 可能已先一步 load() 把这条带回来，
      // 按 id 去重，避免同一便签出现两次
      if (!this.items.some((n) => n.id === note.id)) {
        this.items.unshift(note)
      }
      return note
    },
    async update(id: number, content: string) {
      await updateNote(id, content)
      const note = this.items.find((n) => n.id === id)
      if (note) note.content = content
    },
    async remove(id: number) {
      await deleteNote(id)
      this.items = this.items.filter((n) => n.id !== id)
    }
  }
})
