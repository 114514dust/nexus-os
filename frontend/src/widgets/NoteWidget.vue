<template>
  <div class="note-widget">
    <button class="btn add" @click="add">+ 新建便签</button>
    <div ref="listEl" class="note-list">
      <div v-for="n in store.items" :key="n.id" class="note-item">
        <textarea
          :data-note-id="n.id"
          :value="n.content"
          placeholder="写点什么…"
          @input="onEdit(n, ($event.target as HTMLTextAreaElement).value)"
          @blur="onBlur(n)"
        ></textarea>
        <button class="del" title="删除" @click="remove(n)">×</button>
      </div>
      <div v-if="store.items.length === 0" class="empty">点击「新建便签」开始记录</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, nextTick, ref } from 'vue'
import { useNoteStore } from '@/stores/notes'
import { useLiveReload } from '@/composables/useLiveReload'
import { toastError, friendlyError } from '@/utils/toast'
import type { Note } from '@/types'

defineProps<{ config?: Record<string, any> }>()

const store = useNoteStore()
const timers = new Map<number, number>()
const listEl = ref<HTMLElement | null>(null)

/** 本次刚新建、尚未写过内容的便签 id：失焦时若仍为空则丢弃，避免堆积空便签 */
const blankDrafts = new Set<number>()

onMounted(() => store.load())

// AI 对话调工具、或其它来源改了便签时，自动拉取最新数据
useLiveReload('note', () => store.load())

async function add() {
  try {
    const note = await store.add('')
    blankDrafts.add(note.id)
    await nextTick()
    focusNote(note.id)
  } catch (err) {
    toastError('新建便签失败：' + friendlyError(err))
  }
}

/** 新建后把光标落到新便签的文本框，用户可以直接开始写 */
function focusNote(id: number) {
  const el = listEl.value?.querySelector(
    `textarea[data-note-id="${id}"]`
  ) as HTMLTextAreaElement | null
  el?.focus()
}

function onEdit(note: Note, value: string) {
  const item = store.items.find((n) => n.id === note.id)
  if (item) item.content = value
  const t = timers.get(note.id)
  if (t) window.clearTimeout(t)
  timers.set(
    note.id,
    window.setTimeout(() => {
      store.update(note.id, value).catch((err) => {
        toastError('保存便签失败：' + friendlyError(err))
      })
    }, 600)
  )
}

/** 失焦：刚新建的空白便签直接丢弃，不留垃圾数据 */
function onBlur(note: Note) {
  if (!blankDrafts.has(note.id)) return
  blankDrafts.delete(note.id)
  if (!note.content.trim()) {
    store.remove(note.id).catch(() => {
      /* 清理失败不打扰用户，下次保存时会再次落库 */
    })
  }
}

async function remove(note: Note) {
  try {
    await store.remove(note.id)
    blankDrafts.delete(note.id)
  } catch (err) {
    toastError('删除便签失败：' + friendlyError(err))
  }
}
</script>
