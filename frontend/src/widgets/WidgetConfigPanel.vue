<template>
  <div class="config-panel">
    <div class="config-head">
      <span>{{ meta?.name }} 设置</span>
      <button class="close" title="关闭" @click="$emit('cancel')">×</button>
    </div>
    <div class="config-body">
      <div v-if="schema.length === 0" class="empty">该组件无可配置项</div>
      <div v-for="field in schema" :key="field.key" class="field">
        <label>{{ field.label }}</label>
        <div class="control">
          <input v-if="field.type === 'boolean'" type="checkbox" v-model="form[field.key]" />
          <input v-else-if="field.type === 'number'" type="number" v-model="form[field.key]" />
          <select v-else-if="field.type === 'select'" v-model="form[field.key]">
            <option v-for="o in field.options" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
          <input v-else type="text" v-model="form[field.key]" />
        </div>
      </div>
    </div>
    <div class="config-foot">
      <button class="btn" @click="$emit('cancel')">取消</button>
      <button class="btn primary" @click="save">保存</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import type { WidgetMeta } from './registry'

const props = defineProps<{ meta?: WidgetMeta; config?: Record<string, any> }>()
const emit = defineEmits<{ (e: 'save', config: Record<string, any>): void; (e: 'cancel'): void }>()

const schema = computed(() => props.meta?.configSchema ?? [])

const form = reactive<Record<string, any>>(initForm())

function initForm() {
  const out: Record<string, any> = {}
  for (const f of schema.value) {
    out[f.key] = props.config?.[f.key] ?? f.default ?? defaultValue(f.type)
  }
  return out
}

function defaultValue(type: string) {
  if (type === 'boolean') return false
  if (type === 'number') return 0
  return ''
}

function save() {
  emit('save', { ...form })
}
</script>
