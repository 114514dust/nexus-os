<template>
  <div class="widget">
    <div class="widget-head">
      <span>{{ meta?.name }}</span>
    </div>
    <div class="widget-body">
      <component :is="meta?.component" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getWidget } from './registry'

const props = defineProps<{ type: string }>()

const meta = computed(() => getWidget(props.type))
</script>
