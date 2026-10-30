<template>
  <div class="message-widget">
    <div class="msg-toolbar">
      <div class="msg-summary">
        <b v-if="store.unread" class="unread-count">{{ store.unread }} 条未读</b>
        <b v-else class="all-read">全部已读</b>
        <span class="dim">共 {{ store.total }} 条</span>
      </div>
      <div class="msg-actions">
        <button class="btn ghost" :disabled="!store.unread" title="把当前范围全部标记为已读" @click="markAll">
          全部已读
        </button>
        <button class="btn refresh" :disabled="store.loading" title="刷新" @click="reload">
          {{ store.loading ? '…' : '↻' }}
        </button>
      </div>
    </div>

    <!-- 来源筛选：一个来源一个标签，未读用蓝点数字提示 -->
    <div v-if="store.sources.length > 0" class="msg-chips">
      <button class="msg-chip" :class="{ on: !store.source }" @click="store.setSource('')">
        全部<span class="chip-num">{{ store.total }}</span>
      </button>
      <button
        v-for="s in store.sources"
        :key="s.key"
        class="msg-chip"
        :class="{ on: store.source === s.key }"
        @click="store.setSource(s.key)"
      >
        {{ s.label }}<span class="chip-num" :class="{ hot: s.unread > 0 }">{{ s.unread || s.count }}</span>
      </button>
      <button
        class="msg-chip toggle"
        :class="{ on: store.unreadOnly }"
        title="只看未读"
        @click="store.setUnreadOnly(!store.unreadOnly)"
      >
        只看未读
      </button>
    </div>

    <ul class="msg-list">
      <li v-for="m in store.items" :key="m.id" class="msg-item" :class="{ 'is-unread': !m.read }">
        <button
          class="msg-dot"
          :class="{ on: !m.read }"
          :title="m.read ? '标记为未读' : '标记为已读'"
          @click.stop="toggleRead(m)"
        ></button>
        <div class="msg-main">
          <a
            v-if="m.link"
            class="msg-title"
            :href="m.link"
            target="_blank"
            rel="noopener"
            :title="m.link"
            @click="onOpen(m)"
            >{{ m.title }}</a
          >
          <button v-else class="msg-title as-btn" :title="m.title" @click="toggleExpand(m)">
            {{ m.title }}
          </button>

          <div class="msg-meta">
            <span class="msg-src" :class="m.source">{{ m.sourceLabel }}</span>
            <span v-if="m.sender" class="msg-sender">{{ m.sender }}</span>
            <span class="msg-time" :title="fullTime(m.publishedAt || m.receivedAt)">
              {{ relativeTime(m.publishedAt || m.receivedAt) }}
            </span>
            <button class="msg-del" title="删除这条消息" @click.stop="remove(m)">✕</button>
          </div>

          <p
            v-if="m.content"
            class="msg-brief"
            :class="{ clamp: !isOpen(m) }"
            :title="isOpen(m) ? '' : '点击展开'"
            @click="toggleExpand(m)"
          >
            {{ m.content }}
          </p>
        </div>
      </li>

      <li v-if="store.items.length === 0 && !store.loading" class="empty">
        <template v-if="store.source || store.unreadOnly">
          当前筛选下没有消息，换个来源看看。
        </template>
        <template v-else>
          还没有消息。在右侧「订阅」里粘一个博客或 B 站链接，<br />
          抓到的内容就会出现在这里。
        </template>
      </li>
    </ul>

    <div v-if="store.hasMore" class="msg-more">
      <button class="btn ghost" :disabled="store.loadingMore" @click="store.loadMore()">
        {{ store.loadingMore ? '加载中…' : `加载更多（还有 ${store.total - store.items.length} 条）` }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useMessageStore } from '@/stores/messages'
import { useLiveReload } from '@/composables/useLiveReload'
import { relativeTime, fullTime } from '@/utils/time'
import { toast, toastError, toastSuccess, friendlyError } from '@/utils/toast'
import type { Message } from '@/types'

const props = defineProps<{ config?: Record<string, any> }>()

const store = useMessageStore()
let timer: number | undefined

/** 展开的消息 id：有链接的默认只显示两行摘要，点一下才展开全文 */
const expanded = ref<Set<number>>(new Set())

const refreshInterval = computed(() => {
  const v = Number(props.config?.refreshInterval ?? 15)
  return v > 0 ? v : 15
})

function reload() {
  store.load().catch((e) => console.warn('[消息流] 刷新失败', e))
}

onMounted(() => {
  const size = Number(props.config?.pageSize ?? 30)
  if (size > 0) store.size = size
  reload()
  timer = window.setInterval(reload, refreshInterval.value * 1000)
})

// 有新消息（订阅抓取 / 外部接收 / AI 操作）时立即刷新，不用等下一次轮询
useLiveReload('message', reload)

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})

function isOpen(m: Message): boolean {
  return expanded.value.has(m.id)
}

function toggleExpand(m: Message) {
  const next = new Set(expanded.value)
  if (next.has(m.id)) {
    next.delete(m.id)
  } else {
    next.add(m.id)
  }
  expanded.value = next
  if (!m.read) store.markRead(m).catch(() => undefined)
}

/** 点标题：打开原文，同时算读过 */
function onOpen(m: Message) {
  if (!m.read) store.markRead(m).catch(() => undefined)
}

async function toggleRead(m: Message) {
  try {
    await store.markRead(m, !m.read)
  } catch (e) {
    toastError('操作失败：' + friendlyError(e))
  }
}

async function markAll() {
  try {
    const n = await store.markAllRead()
    toastSuccess(n > 0 ? `已把 ${n} 条标记为已读` : '没有未读消息')
  } catch (e) {
    toastError('操作失败：' + friendlyError(e))
  }
}

async function remove(m: Message) {
  try {
    await store.remove(m)
    toast('已删除这条消息')
  } catch (e) {
    toastError('删除失败：' + friendlyError(e))
  }
}
</script>
