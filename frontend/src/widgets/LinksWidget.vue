<template>
  <div class="links-widget">
    <a
      v-for="link in links"
      :key="link.url"
      :href="link.url"
      target="_blank"
      rel="noopener"
    >
      {{ link.name }}
    </a>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const KEY = 'nexus-links'
const defaults = [
  { name: 'GitHub', url: 'https://github.com' },
  { name: 'Google', url: 'https://www.google.com' },
  { name: '哔哩哔哩', url: 'https://www.bilibili.com' }
]

interface Link {
  name: string
  url: string
}

const links = ref<Link[]>(read())

function read(): Link[] {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) return JSON.parse(raw) as Link[]
  } catch {
    /* ignore */
  }
  return defaults
}
</script>
