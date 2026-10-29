<template>
  <div class="toast-host">
    <TransitionGroup name="toast">
      <div v-for="t in state.items" :key="t.id" class="toast" :class="t.type">
        {{ t.message }}
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { useToastState } from '@/utils/toast'

const state = useToastState()
</script>
