<template>
  <div class="config-mask" @click.self="$emit('close')">
    <div class="config-panel">
      <div class="config-head">
        <span>设置</span>
        <button class="close" @click="$emit('close')">×</button>
      </div>
      <div class="config-body">
        <div class="setting-field">
          <label>API Key</label>
          <input v-model="form.apiKey" type="password" placeholder="sk-..." autocomplete="off" />
        </div>
        <div class="setting-field">
          <label>Base URL</label>
          <input v-model="form.baseUrl" type="text" placeholder="https://api.deepseek.com" />
        </div>
        <div class="setting-field">
          <label>模型</label>
          <input v-model="form.model" type="text" placeholder="deepseek-chat" />
        </div>
        <p class="setting-hint">
          配置 OpenAI 兼容接口（DeepSeek / 通义千问 / OpenAI 等）。留空 API Key 时，每日总结将走规则式降级。
        </p>
      </div>
      <div class="config-foot">
        <button class="btn" @click="$emit('close')">取消</button>
        <button class="btn primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useSettingsStore } from '@/stores/settings'

const emit = defineEmits<{ (e: 'close'): void }>()

const store = useSettingsStore()
const saving = ref(false)
const form = reactive({ apiKey: '', baseUrl: '', model: '' })

onMounted(async () => {
  await store.load()
  form.apiKey = store.settings.apiKey
  form.baseUrl = store.settings.baseUrl
  form.model = store.settings.model
})

async function save() {
  saving.value = true
  try {
    await store.save({ apiKey: form.apiKey, baseUrl: form.baseUrl, model: form.model })
    emit('close')
  } finally {
    saving.value = false
  }
}
</script>
