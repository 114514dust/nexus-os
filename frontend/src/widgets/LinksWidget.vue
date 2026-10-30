<template>
  <div class="links-widget">
    <!-- 内嵌浏览模式：在组件内直接显示网页 -->
    <div v-if="embed" class="links-embed">
      <div class="links-embed-bar">
        <button class="btn back" title="返回链接列表" @click="closeEmbed">← 返回</button>
        <span class="links-embed-title" :title="embed.url">{{ embed.title }}</span>
        <a class="btn external" :href="embed.url" target="_blank" rel="noopener">新窗口打开 ↗</a>
      </div>
      <iframe
        class="links-embed-frame"
        :src="embed.url"
        :title="embed.title"
        loading="lazy"
        referrerpolicy="no-referrer-when-downgrade"
      ></iframe>
      <div class="links-embed-hint">
        部分网站（如 B站 / 知乎 / LeetCode）禁止被嵌入，若显示空白请点右上角「新窗口打开」。
      </div>
    </div>

    <!-- 链接列表模式 -->
    <template v-else>
      <div class="link-form">
        <input v-model="title" placeholder="标题" />
        <input v-model="url" placeholder="https://…" @keyup.enter="add" />
        <button class="btn" @click="add">添加</button>
      </div>
      <div class="link-list">
        <div v-for="l in store.items" :key="l.id" class="link-item">
          <a v-if="openInNewTab" :href="l.url" target="_blank" rel="noopener">{{ l.title }}</a>
          <button v-else class="link-open" :title="l.url" @click="openEmbed(l)">{{ l.title }}</button>
          <button class="del" title="删除" @click="store.remove(l.id)">×</button>
        </div>
        <div v-if="store.items.length === 0" class="empty">暂无链接</div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useLinkStore } from '@/stores/links'
import { useLiveReload } from '@/composables/useLiveReload'
import { toastError, friendlyError } from '@/utils/toast'
import type { Link } from '@/types'

const props = defineProps<{ config?: Record<string, any> }>()

const store = useLinkStore()
const title = ref('')
const url = ref('')

// 打开方式：true = 新窗口打开，false = 组件内嵌显示
const openInNewTab = computed(() => props.config?.openInNewTab ?? true)

// 当前内嵌浏览的链接（null 表示停留在链接列表）
const embed = ref<Link | null>(null)

onMounted(() => store.load())

// AI 对话调工具、或其它来源改了链接时，自动拉取最新数据
useLiveReload('link', () => store.load())

async function add() {
  const t = title.value.trim()
  const u = url.value.trim()
  if (!t || !u) return
  try {
    await store.add({ title: t, url: u })
    title.value = ''
    url.value = ''
  } catch (err) {
    toastError('添加链接失败：' + friendlyError(err))
  }
}

function openEmbed(l: Link) {
  embed.value = l
}

function closeEmbed() {
  embed.value = null
}
</script>
