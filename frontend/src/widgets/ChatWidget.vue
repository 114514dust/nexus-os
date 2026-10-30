<template>
  <div class="chat-widget">
    <div ref="listEl" class="chat-list">
      <div v-if="store.messages.length === 0" class="chat-empty">
        <p>我是你的工作台助手，可以直接让我操作组件：</p>
        <ul>
          <li>「帮我加个待办：明天交周报」</li>
          <li>「看看我的待办 / 便签 / 链接 / 订阅」</li>
          <li>「记个便签：xxx」</li>
          <li>「搜索算法题：链表」</li>
          <li>「看看工作台全貌」</li>
        </ul>
      </div>
      <div v-for="(m, i) in store.messages" :key="i" class="chat-msg" :class="m.role">
        <div v-if="m.error" class="chat-bubble chat-error">
          <span>{{ m.content }}</span>
          <button class="chat-retry" :disabled="store.sending" @click="store.retry()">重试</button>
        </div>
        <div
          v-else-if="m.role === 'assistant'"
          class="chat-bubble markdown-body"
          :class="{ 'is-streaming': m.streaming, 'is-empty': !m.content }"
          v-html="renderBubble(m)"
        ></div>
        <div v-else class="chat-bubble">{{ m.content }}</div>
      </div>
      <div v-if="store.sending" class="chat-hint">正在生成，可随时停止</div>
    </div>
    <div class="chat-input-bar">
      <textarea
        v-model="draft"
        class="chat-input"
        placeholder="输入消息，回车发送，Shift+回车换行…"
        rows="1"
        @keydown="onKeydown"
      ></textarea>
      <button v-if="store.sending" class="btn" title="停止生成" @click="store.stop()">停止</button>
      <button v-else class="btn primary" :disabled="!canSend" @click="send">发送</button>
      <button v-if="store.messages.length" class="btn" title="清空对话" @click="store.clear()">清空</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useChatStore } from '@/stores/chats'
import { renderMarkdown } from '@/utils/markdown'
import type { ChatMessage } from '@/types'

const store = useChatStore()
const draft = ref('')
const listEl = ref<HTMLElement | null>(null)

const canSend = computed(() => draft.value.trim() !== '')

// 挂载时从后端加载持久化的对话历史
onMounted(() => store.load())

function renderBubble(m: ChatMessage) {
  if (!m.content) {
    return '<span class="typing">思考中…</span>'
  }
  return renderMarkdown(m.content)
}

async function send() {
  const text = draft.value
  if (!canSend.value || store.sending) return
  draft.value = ''
  await store.send(text)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

// 新消息时滚动到底部
watch(
  () => store.messages.length,
  async () => {
    await nextTick()
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  }
)

// 流式输出时内容持续变长，条数不变——需要单独监听内容长度才能实时跟随。
// 只在用户本来就贴着底部时才自动滚，避免打断往上翻看历史。
watch(
  () => {
    const last = store.messages[store.messages.length - 1]
    return last?.content.length ?? 0
  },
  async () => {
    const el = listEl.value
    if (!el) return
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80
    if (!nearBottom) return
    await nextTick()
    el.scrollTop = el.scrollHeight
  }
)
</script>
