<template>
  <div class="summary-widget">
    <div class="summary-toolbar">
      <span v-if="store.summary" class="summary-meta">
        {{ store.summary.summaryDate }}
        <span class="dot">·</span>
        {{ store.summary.messageCount }}消息 / {{ store.summary.todoCount }}待办 / {{ store.summary.noteCount }}便签
        <span class="tag" :class="{ muted: store.summary.llmMode !== 'enabled' }">
          {{ store.summary.llmMode === 'enabled' ? 'AI' : '规则降级' }}
        </span>
      </span>
      <button class="btn digest" :disabled="store.digesting" @click="store.digest()">
        {{ store.digesting ? '生成中…' : '生成总结' }}
      </button>
    </div>
    <div class="summary-content">
      <div v-if="store.summary" class="summary-text markdown-body" v-html="rendered"></div>
      <div v-else class="empty">暂无总结，点击「生成总结」消化今天的消息 / 待办 / 便签</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useSummaryStore } from '@/stores/summaries'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{ config?: Record<string, any> }>()

const store = useSummaryStore()
let timer: number | undefined

const autoRefresh = computed(() => props.config?.autoRefresh ?? false)

const rendered = computed(() =>
  store.summary ? renderMarkdown(store.summary.content) : ''
)

onMounted(() => {
  store.load()
  if (autoRefresh.value) {
    timer = window.setInterval(() => store.load(), 60000)
  }
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>
