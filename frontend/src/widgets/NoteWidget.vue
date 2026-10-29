<template>
  <div class="note-widget">
    <textarea v-model="text" placeholder="写点什么…" @input="save"></textarea>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const KEY = 'nexus-note'
const text = ref(localStorage.getItem(KEY) ?? '')

function save() {
  localStorage.setItem(KEY, text.value)
}
</script>
