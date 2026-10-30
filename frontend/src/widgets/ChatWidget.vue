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
        <div v-else-if="m.role === 'assistant'" class="chat-bubble markdown-body" v-html="render(m.content)"></div>
        <div v-else class="chat-bubble">{{ m.content }}</div>
      </div>
      <div v-if="store.sending" class="chat-msg assistant">
        <div class="chat-bubble typing">思考中…</div>
      </div>
    </div>
    <div class="chat-input-bar">
      <textarea
        v-model="draft"
        class="chat-input"
        placeholder="输入消息，回车发送，Shift+回车换行…"
        rows="1"
        @keydown="onKeydown"
      ></textarea>
      <button class="btn primary" :disabled="!canSend || store.sending" @click="send">发送</button>
      <button v-if="store.messages.length" class="btn" title="清空对话" @click="store.clear()">清空</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useChatStore } from '@/stores/chats'
import { renderMarkdown } from '@/utils/markdown'

const store = useChatStore()
const draft = ref('')
const listEl = ref<HTMLElement | null>(null)

const canSend = computed(() => draft.value.trim() !== '')

// 挂载时从后端加载持久化的对话历史
onMounted(() => store.load())

function render(md: string) {
  return renderMarkdown(md)
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
</script>
