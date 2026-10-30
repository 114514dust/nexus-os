<template>
  <div class="subscription-widget">
    <div class="sub-form">
      <select v-model="form.type" class="sub-type-select">
        <option value="rss">RSS</option>
        <option value="bilibili">B站UP主</option>
      </select>
      <input v-model="form.name" :placeholder="form.type === 'rss' ? '订阅名' : 'UP主名'" />
      <input
        v-model="form.url"
        :placeholder="form.type === 'rss' ? 'RSS 地址' : 'UP主 uid（纯数字）'"
        @keyup.enter="add"
      />
      <button class="btn primary" :disabled="!canAdd" @click="add">添加</button>
    </div>

    <div v-if="fetchMsg" class="sub-msg">{{ fetchMsg }}</div>

    <div class="sub-list">
      <div v-for="s in store.items" :key="s.id" class="sub-item" :class="{ disabled: !s.enabled }">
        <span class="sub-type" :class="s.type">{{ s.type === 'rss' ? 'RSS' : 'B站' }}</span>
        <span class="sub-name" :title="s.url">{{ s.name }}</span>
        <button class="sub-btn" :disabled="store.fetchingId === s.id" @click="fetch(s)">
          {{ store.fetchingId === s.id ? '抓取中…' : '抓取' }}
        </button>
        <button class="sub-btn" @click="handleToggle(s)">{{ s.enabled ? '停用' : '启用' }}</button>
        <button class="sub-btn del" title="删除" @click="handleRemove(s)">×</button>
      </div>
      <div v-if="store.items.length === 0" class="empty">
        暂无订阅。添加一个 RSS 或 B 站 UP 主，抓取到的新内容会自动进入「消息流」。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useSubscriptionStore } from '@/stores/subscriptions'
import { useLiveReload } from '@/composables/useLiveReload'
import { toastError, friendlyError } from '@/utils/toast'
import type { Subscription } from '@/types'

defineProps<{ config?: Record<string, any> }>()

const store = useSubscriptionStore()
const form = reactive({ type: 'rss', name: '', url: '' })
const fetchMsg = ref('')

const canAdd = computed(() => form.name.trim() !== '' && form.url.trim() !== '')

onMounted(() => store.load())

// 订阅源被增删改（含 AI 操作）时自动刷新；抓取到新消息时消息组件会自己刷新
useLiveReload('subscription', () => store.load())

async function add() {
  if (!canAdd.value) return
  try {
    await store.add({ type: form.type, name: form.name.trim(), url: form.url.trim() })
    form.name = ''
    form.url = ''
  } catch (err) {
    toastError('添加订阅失败：' + friendlyError(err))
  }
}

async function fetch(s: Subscription) {
  try {
    const added = await store.fetchOne(s.id)
    fetchMsg.value = added > 0 ? `「${s.name}」新增 ${added} 条消息` : `「${s.name}」无新内容`
    window.setTimeout(() => (fetchMsg.value = ''), 4000)
  } catch (err) {
    toastError(`抓取「${s.name}」失败：` + friendlyError(err))
  }
}

async function handleToggle(s: Subscription) {
  try {
    await store.toggle(s)
  } catch (err) {
    toastError('操作失败：' + friendlyError(err))
  }
}

async function handleRemove(s: Subscription) {
  try {
    await store.remove(s.id)
  } catch (err) {
    toastError('删除失败：' + friendlyError(err))
  }
}
</script>
