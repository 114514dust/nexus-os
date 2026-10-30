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
      <button class="btn digest" :disabled="store.digesting" @click="handleDigest">
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
import { useLiveReload } from '@/composables/useLiveReload'
import { renderMarkdown } from '@/utils/markdown'
import { toastError, friendlyError } from '@/utils/toast'

const props = defineProps<{ config?: Record<string, any> }>()

const store = useSummaryStore()
let timer: number | undefined

const autoRefresh = computed(() => props.config?.autoRefresh ?? false)

const rendered = computed(() =>
  store.summary ? renderMarkdown(store.summary.content) : ''
)

async function handleDigest() {
  try {
    await store.digest()
  } catch (err) {
    toastError('生成总结失败：' + friendlyError(err))
  }
}

onMounted(() => {
  store.load()
  if (autoRefresh.value) {
    timer = window.setInterval(() => store.load(), 60000)
  }
})

// 生成/更新每日总结后立即刷新，不必等 60s 轮询
useLiveReload('summary', () => store.load())

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>
