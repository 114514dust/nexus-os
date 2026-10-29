<template>
  <div class="note-widget">
    <button class="btn add" @click="add">+ 新建便签</button>
    <div class="note-list">
      <div v-for="n in store.items" :key="n.id" class="note-item">
        <textarea
          :value="n.content"
          placeholder="写点什么…"
          @input="onEdit(n, ($event.target as HTMLTextAreaElement).value)"
        ></textarea>
        <button class="del" title="删除" @click="store.remove(n.id)">×</button>
      </div>
      <div v-if="store.items.length === 0" class="empty">点击「新建便签」开始记录</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useNoteStore } from '@/stores/notes'
import type { Note } from '@/types'

defineProps<{ config?: Record<string, any> }>()

const store = useNoteStore()
const timers = new Map<number, number>()

onMounted(() => store.load())

async function add() {
  await store.add('')
}

function onEdit(note: Note, value: string) {
  const item = store.items.find((n) => n.id === note.id)
  if (item) item.content = value
  const t = timers.get(note.id)
  if (t) window.clearTimeout(t)
  timers.set(note.id, window.setTimeout(() => store.update(note.id, value), 600))
}
</script>
