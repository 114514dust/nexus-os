<template>
  <div class="message-widget">
    <div class="msg-toolbar">
      <span class="count">{{ store.total }} 条</span>
      <button class="btn refresh" :disabled="store.loading" @click="reload">刷新</button>
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
import { computed, onMounted, onUnmounted } from 'vue'
import { useMessageStore } from '@/stores/messages'
import { useLiveReload } from '@/composables/useLiveReload'

const props = defineProps<{ config?: Record<string, any> }>()

const store = useMessageStore()
let timer: number | undefined

const refreshInterval = computed(() => {
  const v = Number(props.config?.refreshInterval ?? 15)
  return v > 0 ? v : 15
})
const pageSize = computed(() => {
  const v = Number(props.config?.pageSize ?? 50)
  return v > 0 ? v : 50
})

function reload() {
  store.load(0, pageSize.value)
}

onMounted(() => {
  reload()
  timer = window.setInterval(reload, refreshInterval.value * 1000)
})

// 有新消息（订阅抓取 / 外部接收 / AI 操作）时立即刷新，不用等下一次轮询
useLiveReload('message', reload)

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})

function formatTime(iso: string) {
  const d = new Date(iso)
  return d.toLocaleString('zh-CN', { hour12: false })
}
</script>
