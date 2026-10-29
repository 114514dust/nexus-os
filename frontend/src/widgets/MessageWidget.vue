<template>
  <div class="message-widget">
    <div class="msg-toolbar">
      <span class="count">{{ store.total }} 条</span>
      <button class="btn refresh" :disabled="store.loading" @click="store.load()">刷新</button>
    </div>
    <ul class="msg-list">
      <li v-for="m in store.items" :key="m.id" class="msg-item">
        <div class="msg-meta">
          <span class="tag">{{ m.source }}</span>
          <span v-if="m.sender" class="sender">{{ m.sender }}</span>
          <span v-if="m.channel" class="channel">{{ m.channel }}</span>
          <span class="time">{{ formatTime(m.receivedAt) }}</span>
        </div>
        <div class="msg-content">{{ m.content }}</div>
      </li>
      <li v-if="store.items.length === 0" class="empty">暂无消息</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useMessageStore } from '@/stores/messages'

defineProps<{ config?: Record<string, any> }>()

const store = useMessageStore()
let timer: number | undefined

onMounted(() => {
  store.load()
  timer = window.setInterval(() => store.load(), 15000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})

function formatTime(iso: string) {
  const d = new Date(iso)
  return d.toLocaleString('zh-CN', { hour12: false })
}
</script>
