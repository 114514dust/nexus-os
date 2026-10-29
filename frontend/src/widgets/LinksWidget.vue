<template>
  <div class="links-widget">
    <div class="link-form">
      <input v-model="title" placeholder="标题" />
      <input v-model="url" placeholder="https://…" @keyup.enter="add" />
      <button class="btn" @click="add">添加</button>
    </div>
    <div class="link-list">
      <div v-for="l in store.items" :key="l.id" class="link-item">
        <a :href="l.url" :target="openInNewTab ? '_blank' : '_self'" rel="noopener">{{ l.title }}</a>
        <button class="del" title="删除" @click="store.remove(l.id)">×</button>
      </div>
      <div v-if="store.items.length === 0" class="empty">暂无链接</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useLinkStore } from '@/stores/links'

const props = defineProps<{ config?: Record<string, any> }>()

const store = useLinkStore()
const title = ref('')
const url = ref('')

const openInNewTab = computed(() => props.config?.openInNewTab ?? true)

onMounted(() => store.load())

async function add() {
  const t = title.value.trim()
  const u = url.value.trim()
  if (!t || !u) return
  await store.add({ title: t, url: u })
  title.value = ''
  url.value = ''
}
</script>
