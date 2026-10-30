<template>
  <div class="todo-widget">
    <div class="todo-input">
      <input v-model="text" placeholder="添加待办，回车确认" @keyup.enter="add" />
      <button class="btn" @click="add">添加</button>
    </div>
    <ul class="todo-list">
      <li v-for="t in visibleItems" :key="t.id" :class="{ done: t.done }">
        <input type="checkbox" :checked="t.done" @change="store.toggle(t)" />
        <span>{{ t.content }}</span>
        <button class="del" title="删除" @click="store.remove(t.id)">×</button>
      </li>
      <li v-if="visibleItems.length === 0" class="empty">暂无待办</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useTodoStore } from '@/stores/todos'
import { useLiveReload } from '@/composables/useLiveReload'

const props = defineProps<{ config?: Record<string, any> }>()

const store = useTodoStore()
const text = ref('')

const showDone = computed(() => props.config?.showDone ?? true)
const visibleItems = computed(() =>
  showDone.value ? store.items : store.items.filter((t) => !t.done)
)

onMounted(() => store.load())

// AI 对话调工具、或其它页面改了待办时，自动拉取最新数据
useLiveReload('todo', () => store.load())

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
