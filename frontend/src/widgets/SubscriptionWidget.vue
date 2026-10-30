<template>
  <div class="subscription-widget">
    <!-- 只留一个输入框：粘链接 → 识别 → 添加，名字由后端读 feed 标题自动填 -->
    <form class="sub-add" @submit.prevent="submit">
      <input
        v-model="input"
        class="sub-add-input"
        placeholder="粘贴博客、RSS 或 B 站空间链接…"
        autocomplete="off"
      />
      <button class="btn primary" type="submit" :disabled="!canSubmit || busy">
        {{ busy ? '识别中…' : '订阅' }}
      </button>
    </form>
    <p class="sub-tip">博客首页也行（会自动找它的订阅源），B 站直接贴 space.bilibili.com/xxx</p>

    <!-- 识别成功：先让用户确认「识别成了什么」，再落库 -->
    <div v-if="pending" class="sub-pending">
      <div class="sub-pending-text">
        <span class="ok">✓</span>
        {{ pending.hint }}
        <b>{{ pending.name }}</b>
        <span v-if="pending.sampleCount > 0" class="dim">· 最近 {{ pending.sampleCount }} 条</span>
      </div>
      <div class="sub-pending-ops">
        <button class="btn primary" :disabled="adding" @click="confirmAdd">
          {{ adding ? '添加中…' : '添加并抓取' }}
        </button>
        <button class="btn ghost" @click="cancelPending">取消</button>
      </div>
    </div>

    <div v-if="failMsg" class="sub-fail">{{ failMsg }}</div>

    <div class="sub-head">
      <span>我的订阅 <em>{{ store.items.length }}</em></span>
      <button
        class="btn ghost"
        :disabled="store.fetchingAll || store.items.length === 0"
        title="抓取所有已启用的订阅源"
        @click="fetchAll"
      >
        {{ store.fetchingAll ? '抓取中…' : '全部抓取' }}
      </button>
    </div>

    <div class="sub-list">
      <div v-for="s in store.items" :key="s.id" class="sub-card" :class="{ disabled: !s.enabled }">
        <div class="sub-line">
          <span class="sub-type" :class="s.type">{{ s.type === 'rss' ? 'RSS' : 'B站' }}</span>
          <span class="sub-name" :title="s.url">{{ s.name }}</span>
        </div>
        <div class="sub-line sub-meta">
          <span class="sub-status" :class="{ paused: !s.enabled }">{{ statusText(s) }}</span>
          <div class="sub-ops">
            <button :disabled="store.fetchingId === s.id" @click="fetch(s)">
              {{ store.fetchingId === s.id ? '抓取中…' : '抓取' }}
            </button>
            <button @click="toggle(s)">{{ s.enabled ? '暂停' : '启用' }}</button>
            <button class="del" @click="remove(s)">删除</button>
          </div>
        </div>
        <div v-if="s.lastError" class="sub-error" :title="s.lastError">
          ⚠ 抓取失败：{{ s.lastError }}
        </div>
      </div>

      <div v-if="store.items.length === 0 && !store.loading" class="sub-empty">
        <p>还没有订阅源。找个你常看的博客，或者先加一个试试：</p>
        <div class="sub-samples">
          <button v-for="s in samples" :key="s.url" :disabled="busy" @click="quickAdd(s.url)">
            ＋ {{ s.label }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useSubscriptionStore } from '@/stores/subscriptions'
import { useLiveReload } from '@/composables/useLiveReload'
import { relativeTime } from '@/utils/time'
import { toastError, toastSuccess, friendlyError } from '@/utils/toast'
import type { Subscription, SubscriptionDiscover } from '@/types'

defineProps<{ config?: Record<string, any> }>()

const store = useSubscriptionStore()

const input = ref('')
const busy = ref(false)
const adding = ref(false)
const pending = ref<SubscriptionDiscover | null>(null)
const failMsg = ref('')

/** 空态时的一键示例，都是实测可访问、内容更新稳定的公开订阅源 */
const samples = [
  { label: '阮一峰的网络日志', url: 'https://www.ruanyifeng.com/blog/atom.xml' },
  { label: '少数派', url: 'https://sspai.com/feed' },
  { label: '美团技术团队', url: 'https://tech.meituan.com/feed/' }
]

const canSubmit = computed(() => input.value.trim().length > 0)

onMounted(() => store.load())
useLiveReload('subscription', () => store.load())

function cancelPending() {
  pending.value = null
  failMsg.value = ''
}

async function submit() {
  if (!canSubmit.value || busy.value) return
  busy.value = true
  failMsg.value = ''
  pending.value = null
  try {
    const result = await store.discover(input.value.trim())
    if (!result.ok) {
      failMsg.value = result.message
      return
    }
    pending.value = result
  } catch (e) {
    failMsg.value = friendlyError(e)
  } finally {
    busy.value = false
  }
}

async function quickAdd(url: string) {
  input.value = url
  await submit()
}

async function confirmAdd() {
  if (!pending.value || adding.value) return
  adding.value = true
  try {
    const sub = await store.add({
      type: pending.value.type,
      name: pending.value.name,
      url: pending.value.url
    })
    pending.value = null
    input.value = ''
    failMsg.value = ''
    toastSuccess(`已订阅《${sub.name}》，正在抓取内容…`)
    // 添加后立刻抓一次：不然用户要再点「抓取」才看得到东西，等于白订
    await fetch(sub)
  } catch (e) {
    toastError('添加订阅失败：' + friendlyError(e))
  } finally {
    adding.value = false
  }
}

async function fetch(s: Subscription) {
  try {
    const added = await store.fetchOne(s.id)
    if (added > 0) {
      toastSuccess(`「${s.name}」新增 ${added} 条，已放进消息流`)
    } else {
      toastSuccess(`「${s.name}」暂时没有新内容`)
    }
  } catch (e) {
    toastError(`抓取「${s.name}」失败：` + friendlyError(e))
  }
}

async function fetchAll() {
  try {
    const total = await store.fetchAll()
    toastSuccess(total > 0 ? `全部抓取完成，新增 ${total} 条` : '全部抓取完成，暂时没有新内容')
  } catch (e) {
    toastError('抓取失败：' + friendlyError(e))
  }
}

async function toggle(s: Subscription) {
  try {
    await store.toggle(s)
  } catch (e) {
    toastError('操作失败：' + friendlyError(e))
  }
}

async function remove(s: Subscription) {
  if (!window.confirm(`删除订阅「${s.name}」？\n已经抓到的消息会保留在消息流里。`)) return
  try {
    await store.remove(s.id)
  } catch (e) {
    toastError('删除失败：' + friendlyError(e))
  }
}

function statusText(s: Subscription): string {
  if (!s.enabled) return '已暂停，不会定时抓取'
  if (!s.lastFetchedAt) return '还没抓取过'
  return `上次抓取 ${relativeTime(s.lastFetchedAt)}`
}
</script>
