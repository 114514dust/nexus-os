<template>
  <div class="config-mask" @click.self="$emit('close')">
    <div class="config-panel settings-panel">
      <div class="config-head">
        <span>设置</span>
        <button class="close" @click="$emit('close')">×</button>
      </div>
      <div class="config-body">
        <div class="setting-section">大模型</div>
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

        <div class="setting-section">消息推送</div>
        <label class="setting-check">
          <input v-model="notifyForm.enabled" type="checkbox" />
          <span>启用主动推送</span>
        </label>
        <div class="setting-field">
          <label>渠道</label>
          <select v-model="notifyForm.channel">
            <option value="feishu">飞书群机器人</option>
            <option value="wecom">企业微信群机器人</option>
          </select>
        </div>
        <div class="setting-field">
          <label>Webhook 地址</label>
          <input
            v-model="notifyForm.webhookUrl"
            type="text"
            :placeholder="
              notifyForm.channel === 'feishu'
                ? 'https://open.feishu.cn/open-apis/bot/v2/hook/...'
                : 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=...'
            "
          />
        </div>
        <div v-if="notifyForm.channel === 'feishu'" class="setting-field">
          <label>签名密钥</label>
          <input
            v-model="notifyForm.secret"
            type="password"
            placeholder="机器人开启「签名校验」时填写，否则留空"
            autocomplete="off"
          />
        </div>
        <div class="setting-checks">
          <label class="setting-check">
            <input v-model="notifyForm.onFeed" type="checkbox" />
            <span>订阅有新内容时推送</span>
          </label>
          <label class="setting-check">
            <input v-model="notifyForm.onDigest" type="checkbox" />
            <span>每日总结生成后推送</span>
          </label>
          <label class="setting-check">
            <input v-model="notifyForm.onInterview" type="checkbox" />
            <span>求职面试提醒（每天 9:00 检查今天/明天的面试与超 7 天无进展的投递）</span>
          </label>
          <label class="setting-check">
            <input v-model="notifyForm.onTodo" type="checkbox" />
            <span>待办到期提醒（每天 8:30 提醒今天到期与已逾期的待办）</span>
          </label>
          <label class="setting-check">
            <input v-model="notifyForm.onPosting" type="checkbox" />
            <span>校招岗位新增提醒（每天 7:30 抓取后推送，命中关注关键词的排前面）</span>
          </label>
          <label class="setting-check">
            <input v-model="notifyForm.onMail" type="checkbox" />
            <span>重要邮件提醒（收到面试/笔试等判为重要的邮件时推送，只看重要不看广告）</span>
          </label>
          <label class="setting-check">
            <input v-model="notifyForm.onMailDigest" type="checkbox" />
            <span>每日邮件摘要（每天 8:00 把昨天要紧的邮件汇总成一条，没有要紧邮件就不发）</span>
          </label>
        </div>
        <p class="setting-hint">
          只做出站推送（群机器人 webhook），无需公网 IP。配好后先点「发送测试」确认能收到，再打开总开关。
        </p>
        <div class="setting-test">
          <button class="btn" :disabled="testing" @click="test">
            {{ testing ? '发送中…' : '发送测试' }}
          </button>
          <span v-if="testMsg" class="test-result" :class="testOk ? 'ok' : 'err'">{{ testMsg }}</span>
        </div>
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
import { useNotifyStore } from '@/stores/notify'
import { toastError, friendlyError } from '@/utils/toast'

const emit = defineEmits<{ (e: 'close'): void }>()

const store = useSettingsStore()
const notifyStore = useNotifyStore()

const saving = ref(false)
const testing = ref(false)
const testOk = ref(false)
const testMsg = ref('')

const form = reactive({ apiKey: '', baseUrl: '', model: '' })
const notifyForm = reactive({
  enabled: false,
  channel: 'feishu',
  webhookUrl: '',
  secret: '',
  onFeed: true,
  onDigest: true,
  onInterview: true,
  onTodo: true,
  onPosting: true,
  onMail: true,
  onMailDigest: true
})

onMounted(async () => {
  await Promise.all([store.load(), notifyStore.load()])
  form.apiKey = store.settings.apiKey
  form.baseUrl = store.settings.baseUrl
  form.model = store.settings.model
  Object.assign(notifyForm, notifyStore.settings)
})

async function save() {
  saving.value = true
  try {
    await store.save({ apiKey: form.apiKey, baseUrl: form.baseUrl, model: form.model })
    await notifyStore.save({ ...notifyForm })
    emit('close')
  } catch (err) {
    toastError('保存设置失败：' + friendlyError(err))
  } finally {
    saving.value = false
  }
}

/**
 * 发送测试消息。
 * 关键：先落库再测。否则用户改了 webhook 地址直接点测试，测的还是旧地址，
 * 会出现「测试成功但正式推送失败」这种很难排查的错觉。
 */
async function test() {
  testing.value = true
  testMsg.value = ''
  try {
    await notifyStore.save({ ...notifyForm })
    await notifyStore.test()
    testOk.value = true
    testMsg.value = '已发送，请查看群聊'
  } catch (err) {
    testOk.value = false
    testMsg.value = friendlyError(err)
  } finally {
    testing.value = false
  }
}
</script>
