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
    async add(content: string) {
      const note = await createNote(content)
      this.items.unshift(note)
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
