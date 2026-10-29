<template>
  <div class="todo-widget">
    <div class="todo-input">
      <input v-model="text" placeholder="添加待办，回车确认" @keyup.enter="add" />
      <button class="btn" @click="add">添加</button>
    </div>
    <ul class="todo-list">
      <li v-for="t in store.items" :key="t.id" :class="{ done: t.done }">
        <input type="checkbox" :checked="t.done" @change="store.toggle(t)" />
        <span>{{ t.content }}</span>
        <button class="del" title="删除" @click="store.remove(t.id)">×</button>
      </li>
      <li v-if="store.items.length === 0" class="empty">暂无待办</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useTodoStore } from '@/stores/todos'

const store = useTodoStore()
const text = ref('')

onMounted(() => store.load())

async function add() {
  const v = text.value.trim()
  if (!v) return
  await store.add(v)
  text.value = ''
}
</script>

<style scoped>
.empty {
  color: var(--text-2);
  font-size: 13px;
  padding: 4px 0;
}
</style>
