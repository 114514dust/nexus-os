<template>
  <div class="widget">
    <div class="widget-head">
      <span>{{ displayName }}</span>
    </div>
    <div class="widget-body">
      <component :is="meta?.component" :config="config" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getWidget } from './registry'

const props = defineProps<{ type: string; config?: Record<string, any> }>()

const meta = computed(() => getWidget(props.type))

// 组件实例可配置自定义标题（config.title），否则显示组件类型名
const displayName = computed(() => props.config?.title || meta.value?.name)
</script>
