<template>
  <div class="clock">
    <div class="time">{{ time }}</div>
    <div v-if="showDate" class="date">{{ date }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'

const props = defineProps<{ config?: Record<string, any> }>()

const showSeconds = computed(() => props.config?.showSeconds ?? true)
const hour12 = computed(() => props.config?.hour12 ?? false)
const showDate = computed(() => props.config?.showDate ?? true)

const time = ref('')
const date = ref('')
let timer: number | undefined

function update() {
  const now = new Date()
  const opts: Intl.DateTimeFormatOptions = {
    hour12: hour12.value,
    hour: '2-digit',
    minute: '2-digit'
  }
  if (showSeconds.value) opts.second = '2-digit'
  time.value = now.toLocaleTimeString('zh-CN', opts)
  date.value = now.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })
}

onMounted(() => {
  update()
  timer = window.setInterval(update, 1000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>
