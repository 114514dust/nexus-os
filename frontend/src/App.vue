<template>
  <router-view />
  <ToastHost />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import ToastHost from '@/components/ToastHost.vue'
import { useEventsStore } from '@/stores/events'

const events = useEventsStore()

/**
 * 全局订阅后端的数据变更事件。
 * 任何来源（组件内手动操作 / AI 对话的工具调用 / 定时抓取订阅）改了数据，
 * 都会经由同一条 SSE 通道推下来，再由各组件按自己的领域局部刷新。
 */
onMounted(() => events.connect())
</script>
