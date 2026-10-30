<template>
  <div class="mail-widget">
    <!-- 未配置：先告诉用户授权码从哪来，否则「IMAP 授权码」这五个字会直接把人劝退 -->
    <div v-if="!configured" class="mail-setup">
      <div class="mail-setup-head">
        <span class="mail-setup-icon">📮</span>
        <div>
          <div class="mail-setup-title">订阅你的 QQ 邮箱</div>
          <div class="mail-setup-sub muted">重要邮件（面试、笔试、缴费截止）会自动变成待办</div>
        </div>
      </div>
      <ol class="mail-steps">
        <li>登录 QQ 邮箱 → 设置 → 账户</li>
        <li>找到「IMAP/SMTP 服务」，开启并按提示发短信验证</li>
        <li>复制生成的 <b>16 位授权码</b>（不是登录密码）</li>
        <li>填到下面的配置里，点「测试连接」</li>
      </ol>
      <div class="mail-setup-actions">
        <a class="mail-help" href="https://mail.qq.com/" target="_blank" rel="noopener noreferrer">
          打开 QQ 邮箱 ↗
        </a>
        <button class="btn mail-setup-btn" @click="togglePanel('settings')">填写配置</button>
      </div>
    </div>

    <!-- 概览 -->
    <div v-if="configured" class="mail-stats">
      <div class="mail-stat high">
        <b>{{ store.stats?.high ?? 0 }}</b><span>重要</span>
      </div>
      <div class="mail-stat">
        <b>{{ store.stats?.medium ?? 0 }}</b><span>可关注</span>
      </div>
      <div class="mail-stat done">
        <b>{{ store.stats?.toTodo ?? 0 }}</b><span>已转待办</span>
      </div>
      <div class="mail-stat">
        <b>{{ store.stats?.total ?? 0 }}</b><span>已收录</span>
      </div>
    </div>

    <div class="mail-toolbar">
      <input
        v-if="configured"
        v-model="keyword"
        class="mail-search"
        placeholder="搜索主题 / 发件人…"
      />
      <select v-if="configured" v-model="scope" class="mail-select" title="显示范围">
        <option value="important">重要</option>
        <option value="todo">已转待办</option>
        <option value="all">全部</option>
      </select>
      <button
        v-if="configured"
        class="mail-chip"
        :class="{ on: panel === 'rules' }"
        title="发件人规则"
        @click="togglePanel('rules')"
      >
        🎯
      </button>
      <button
        v-if="configured"
        class="mail-chip"
        :class="{ on: panel === 'digest' }"
        title="每日摘要"
        @click="togglePanel('digest')"
      >
        📮
      </button>
      <button
        class="mail-chip"
        :class="{ on: panel === 'settings' }"
        title="邮箱配置"
        @click="togglePanel('settings')"
      >
        ⚙
      </button>
      <button
        class="mail-chip"
        :disabled="store.syncing || !configured"
        :title="configured ? '立即收取一次' : '先完成配置'"
        @click="sync"
      >
        {{ store.syncing ? '…' : '⟳' }}
      </button>
    </div>

    <!-- 配置面板 -->
    <div v-if="panel === 'settings'" class="mail-panel mail-settings">
      <div class="mail-field">
        <label>邮箱地址</label>
        <input v-model="form.address" class="mail-search" placeholder="例如 123456789@qq.com" />
      </div>
      <div class="mail-field">
        <label>IMAP 授权码</label>
        <input
          v-model="form.authCode"
          class="mail-search"
          type="password"
          :placeholder="settings?.authCode ? '已保存（留空不修改）' : '16 位授权码'"
        />
      </div>

      <div class="mail-field">
        <label class="mail-check">
          <input v-model="form.enabled" type="checkbox" />
          <span>每 15 分钟自动收取</span>
        </label>
      </div>
      <div class="mail-field">
        <label class="mail-check">
          <input v-model="form.autoTodo" type="checkbox" />
          <span>高置信度邮件自动建待办</span>
        </label>
      </div>
      <div class="mail-field">
        <label class="mail-check">
          <input v-model="form.llmJudge" type="checkbox" />
          <span>用 AI 精判模糊邮件（需配置 API Key）</span>
        </label>
      </div>
      <div class="mail-field">
        <label class="mail-check">
          <input v-model="form.digestEnabled" type="checkbox" />
          <span>每天 8:00 推送昨日摘要</span>
        </label>
      </div>

      <div class="mail-field">
        <label>永不重要</label>
        <input v-model="form.ignoreSenders" class="mail-search" placeholder="域名或地址片段，逗号分隔" />
      </div>
      <div class="mail-field">
        <label>重点关注词</label>
        <input v-model="form.extraKeywords" class="mail-search" placeholder="命中即抬为重要，如：三方、政审" />
      </div>

      <details class="mail-advanced">
        <summary>高级：服务器与范围</summary>
        <div class="mail-field">
          <label>IMAP 服务器</label>
          <input v-model="form.imapHost" class="mail-search" />
        </div>
        <div class="mail-field">
          <label>端口 / SSL</label>
          <input v-model.number="form.imapPort" class="mail-port" type="number" />
          <label class="mail-check inline">
            <input v-model="form.ssl" type="checkbox" />
            <span>SSL</span>
          </label>
        </div>
        <div class="mail-field">
          <label>文件夹 / 每次读取</label>
          <input v-model="form.folder" class="mail-port" />
          <input v-model.number="form.fetchLimit" class="mail-port" type="number" min="5" max="200" />
        </div>
      </details>

      <div class="mail-actions">
        <button class="btn" :disabled="probing" @click="probe">
          {{ probing ? '测试中…' : '测试连接' }}
        </button>
        <button class="btn primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button class="btn" @click="panel = 'none'">取消</button>
      </div>

      <div v-if="settings?.lastError" class="mail-error">上次失败：{{ settings.lastError }}</div>
    </div>

    <!-- 发件人规则 -->
    <div v-if="panel === 'rules'" class="mail-panel">
      <div class="mail-panel-head">
        <b>发件人规则</b>
        <span class="muted">谁的信值得打断你，你说了算</span>
      </div>
      <div class="mail-rule-add">
        <input
          v-model="ruleDraft.pattern"
          class="mail-search"
          placeholder="地址片段 / 域名 / 发件人名，如 edu.cn"
          @keyup.enter="addRule"
        />
        <select v-model="ruleDraft.action" class="mail-select">
          <option value="important">一律重要</option>
          <option value="watch">至少可关注</option>
          <option value="ignore">不提醒</option>
        </select>
        <button class="btn" :disabled="!ruleDraft.pattern.trim() || ruleAdding" @click="addRule">
          {{ ruleAdding ? '…' : '添加' }}
        </button>
      </div>

      <ul v-if="store.rules.length" class="mail-rule-list">
        <li v-for="r in store.rules" :key="r.id" class="mail-rule">
          <span class="mail-rule-act" :class="r.action">{{ ruleActionLabel(r.action) }}</span>
          <span class="mail-rule-pattern" :title="r.pattern">{{ r.pattern }}</span>
          <span class="mail-rule-hit muted">{{ r.hitCount ? '命中 ' + r.hitCount : '未命中' }}</span>
          <button class="mail-act ghost" title="删除规则" @click="removeRule(r.id)">×</button>
        </li>
      </ul>
      <p v-else class="muted mail-rule-empty">
        还没有规则。加一条「edu.cn → 一律重要」，学校就业中心的通知就不会被当成普通邮件；
        多个规则同时命中时，写得越具体的那条优先。
      </p>
    </div>

    <!-- 每日摘要 -->
    <div v-if="panel === 'digest'" class="mail-panel">
      <div class="mail-panel-head">
        <b>每日摘要</b>
        <span class="muted">每天 8:00 推一封昨天的要紧邮件</span>
      </div>
      <div class="mail-digest-bar">
        <select v-model="digestDay" class="mail-select" @change="loadDigest">
          <option value="yesterday">昨天</option>
          <option value="today">今天</option>
        </select>
        <button class="btn" :disabled="digestPushing" @click="pushDigest">
          {{ digestPushing ? '推送中…' : '立即推送一次' }}
        </button>
      </div>
      <pre class="mail-digest-text">{{ digestText }}</pre>
      <p class="muted mail-digest-hint">
        没有要紧邮件的那天不会推送 —— 不发消息，本身就是「今天不用管邮箱」的答案。
      </p>
    </div>

    <!-- 列表 -->
    <div class="mail-list">
      <article
        v-for="m in store.items"
        :key="m.id"
        class="mail-card"
        :class="[m.importantLevel, { unread: !m.read }]"
        @click="openDetail(m)"
      >
        <div class="mail-row-1">
          <span class="mail-level" :class="m.importantLevel" :title="m.scoreReason || ''">
            {{ levelLabel(m.importantLevel) }}
          </span>
          <span class="mail-sender" :title="m.senderAddr">{{ m.senderName }}</span>
          <span class="mail-time">{{ fmtTime(m.receivedAt) }}</span>
        </div>
        <div class="mail-subject" :title="m.subject">{{ m.subject }}</div>
        <div v-if="m.snippet" class="mail-snippet">{{ m.snippet }}</div>

        <div class="mail-row-2">
          <span v-if="m.hasAttachment" class="mail-flag" title="含附件">
            📎{{ m.attachments?.length || '' }}
          </span>
          <span v-if="m.verdictBy === 'llm'" class="mail-flag ai" title="AI 参与判定">AI</span>
          <span class="mail-score" :title="m.scoreReason || ''">{{ m.importantScore }} 分</span>
          <span v-if="m.todoExists" class="mail-todo-tag" :title="m.todoContent || ''">
            ✓ 待办{{ m.todoDueDate ? ' · ' + shortDate(m.todoDueDate) : '' }}
          </span>
          <span class="mail-spacer"></span>
          <button v-if="!m.todoExists" class="mail-act" @click.stop="toTodo(m)">＋ 待办</button>
          <button class="mail-act ghost" title="不再显示这封" @click.stop="ignore(m)">忽略</button>
        </div>
      </article>

      <div v-if="store.items.length === 0 && !store.loading" class="mail-empty muted">
        <template v-if="!configured">填好上面的配置，就能开始收信了。</template>
        <template v-else-if="scope === 'todo'">还没有邮件转成待办。</template>
        <template v-else>
          暂时没有重要邮件。点 <b>⟳</b> 立即收一次，或等下次自动收取。
        </template>
      </div>
    </div>

    <footer class="mail-foot">
      <span v-if="store.stats?.lastSyncAt" class="muted">上次收取 {{ store.stats.lastSyncAt }}</span>
      <span v-else class="muted">尚未收取过</span>
      <span v-if="store.stats?.lastError" class="mail-foot-error" :title="store.stats.lastError">
        收取失败
      </span>
    </footer>

    <!-- 详情 -->
    <div v-if="detail" class="mail-detail-mask" @click.self="closeDetail">
      <div class="mail-detail">
        <header class="mail-detail-head">
          <div class="mail-detail-subject">{{ detail.subject }}</div>
          <button class="mail-detail-close" @click="closeDetail">×</button>
        </header>
        <div class="mail-detail-meta">
          <span>{{ detail.senderName }} &lt;{{ detail.senderAddr }}&gt;</span>
          <span>{{ fmtFull(detail.receivedAt) }}</span>
          <span class="mail-level" :class="detail.importantLevel">
            {{ levelLabel(detail.importantLevel) }} · {{ detail.importantScore }} 分
          </span>
        </div>
        <div v-if="detail.scoreReason" class="mail-detail-reason">判定依据：{{ detail.scoreReason }}</div>

        <!-- 附件 -->
        <div v-if="detail.attachments?.length" class="mail-atts">
          <div class="mail-atts-head">
            <span>附件 {{ detail.attachments.length }}</span>
            <button
              v-if="detail.attachments.length > 1"
              class="mail-act"
              title="依次下载全部附件"
              @click="downloadAll(detail)"
            >
              全部下载
            </button>
          </div>
          <div v-for="att in detail.attachments" :key="att.id" class="mail-att">
            <span class="mail-att-icon">{{ attIcon(att) }}</span>
            <span class="mail-att-name" :title="att.filename">{{ att.filename }}</span>
            <span class="mail-att-size muted">{{ fmtSize(att.sizeBytes) }}</span>
            <span v-if="att.downloaded" class="mail-att-saved">已存本地</span>
            <a
              class="mail-act"
              :href="attUrl(att.id)"
              title="保存到本机（首次会回邮箱取一次）"
              @click="afterDownload"
            >下载</a>
            <a
              v-if="att.previewable"
              class="mail-act"
              :href="attUrl(att.id, true)"
              target="_blank"
              rel="noopener"
              title="在新标签页打开"
            >预览</a>
          </div>
        </div>

        <pre class="mail-detail-body">{{ detail.body || detail.snippet || '（无正文）' }}</pre>

        <div v-if="detailRuleOpen" class="mail-rule-inline">
          <span class="muted">此后来自该发件人的邮件</span>
          <input v-model="detailRule.pattern" class="mail-search" title="可改成整个域名，如 @edu.cn" />
          <select v-model="detailRule.action" class="mail-select">
            <option value="important">一律重要</option>
            <option value="watch">至少可关注</option>
            <option value="ignore">不提醒</option>
          </select>
          <button class="btn primary" :disabled="detailRuleSaving" @click="saveDetailRule">
            {{ detailRuleSaving ? '…' : '保存' }}
          </button>
        </div>

        <footer class="mail-detail-foot">
          <span v-if="detail.todoExists" class="mail-todo-tag">✓ 已转待办：{{ detail.todoContent }}</span>
          <button v-else class="btn primary" @click="toTodo(detail)">＋ 加入待办</button>
          <button class="btn" :class="{ primary: detailRuleOpen }" @click="openDetailRule(detail)">
            🎯 发件人规则
          </button>
          <span class="mail-spacer"></span>
          <button class="btn" @click="remove(detail)">删除记录</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useMailStore } from '@/stores/mail'
import { fetchMailDetail, mailAttachmentUrl } from '@/api/mail'
import { useLiveReload } from '@/composables/useLiveReload'
import { friendlyError, toastError, toastInfo, toastSuccess } from '@/utils/toast'
import type { MailMessageView, MailSettings, SenderRule } from '@/types'

const store = useMailStore()

const keyword = ref('')
const scope = ref<'important' | 'all' | 'todo'>('important')
/** 四个面板互斥，同时展开会把列表挤没 */
const panel = ref<'none' | 'settings' | 'rules' | 'digest'>('none')
const detail = ref<MailMessageView | null>(null)
const probing = ref(false)
const saving = ref(false)

const ruleDraft = reactive<{ pattern: string; action: SenderRule['action'] }>({
  pattern: '',
  action: 'important'
})
const ruleAdding = ref(false)

const detailRuleOpen = ref(false)
const detailRuleSaving = ref(false)
const detailRule = reactive<{ pattern: string; action: SenderRule['action'] }>({
  pattern: '',
  action: 'important'
})

const digestDay = ref<'today' | 'yesterday'>('yesterday')
const digestPushing = ref(false)

const settings = computed(() => store.settings)
const configured = computed(() => !!store.settings?.configured)
const digestText = computed(() => {
  if (!store.digest) return '加载中…'
  if (store.digest.total === 0) return '这一天没有收到邮件。'
  return store.digest.text
})

/** 配置表单：用本地副本编辑，保存成功后才回写 store，避免边输边污染状态 */
const form = reactive<MailSettings>({
  enabled: true,
  address: '',
  authCode: '',
  imapHost: 'imap.qq.com',
  imapPort: 993,
  ssl: true,
  folder: 'INBOX',
  autoTodo: true,
  llmJudge: true,
  ignoreSenders: '',
  extraKeywords: '',
  fetchLimit: 30,
  digestEnabled: true,
  lastSyncAt: '',
  lastDigestDate: '',
  configured: false,
  lastError: ''
})

let searchTimer: number | undefined
watch(keyword, () => {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(reload, 300)
})
watch(scope, () => reload())

onMounted(async () => {
  try {
    const s = await store.loadSettings()
    fillForm(s)
  } catch (e) {
    console.warn('[mail] 配置加载失败', e)
  }
  store.loadRules().catch(() => {})
  reload()
})

// 定时收取、AI 工具转待办都会改这批数据
useLiveReload('mail', () => reload())

function reload() {
  store
    .load({
      scope: scope.value,
      keyword: keyword.value.trim() || undefined,
      limit: 100
    })
    .catch((e) => console.warn('[mail] 加载失败', e))
}

function fillForm(s: MailSettings) {
  form.enabled = s.enabled
  form.address = s.address
  // 授权码是打码值，原样放进去；后端遇到 **** 开头的值会视为「不修改」
  form.authCode = s.authCode
  form.imapHost = s.imapHost
  form.imapPort = s.imapPort
  form.ssl = s.ssl
  form.folder = s.folder
  form.autoTodo = s.autoTodo
  form.llmJudge = s.llmJudge
  form.ignoreSenders = s.ignoreSenders
  form.extraKeywords = s.extraKeywords
  form.fetchLimit = s.fetchLimit
  form.digestEnabled = s.digestEnabled
}

function togglePanel(next: 'settings' | 'rules' | 'digest') {
  panel.value = panel.value === next ? 'none' : next
  if (panel.value === 'settings' && store.settings) {
    fillForm(store.settings)
  }
  if (panel.value === 'rules') {
    store.loadRules().catch(() => toastError('规则加载失败'))
  }
  if (panel.value === 'digest') {
    loadDigest()
  }
}

async function save() {
  if (!form.address.trim()) {
    toastError('请填写邮箱地址')
    return
  }
  saving.value = true
  try {
    const saved = await store.saveSettings({ ...form })
    fillForm(saved)
    toastSuccess(saved.configured ? '配置已保存，点 ⟳ 可以开始收信' : '已保存，还需要填写授权码')
    if (saved.configured) {
      panel.value = 'none'
      reload()
    }
  } catch (e) {
    toastError('保存失败：' + friendlyError(e))
  } finally {
    saving.value = false
  }
}

async function probe() {
  probing.value = true
  try {
    const r = await store.probe()
    if (r.ok) {
      toastSuccess(r.message)
    } else {
      toastError(r.message || '连接失败')
    }
    // 失败原因要落到界面上，不能只在 toast 里飘一下
    await store.loadSettings().then(fillForm).catch(() => {})
  } catch (e) {
    toastError('测试连接失败：' + friendlyError(e))
  } finally {
    probing.value = false
  }
}

async function sync() {
  try {
    const r = await store.sync()
    if (!r.ok) {
      toastError(r.message || '收取失败')
    } else {
      toastSuccess(r.message)
    }
    reload()
    store.loadSettings().then(fillForm).catch(() => {})
  } catch (e) {
    toastError('收取失败：' + friendlyError(e))
  }
}

// ---- 发件人规则 ----

async function addRule() {
  const pattern = ruleDraft.pattern.trim()
  if (!pattern) return
  ruleAdding.value = true
  try {
    const rule = await store.addRule(pattern, ruleDraft.action)
    toastSuccess(`已添加规则：${rule.pattern} → ${ruleActionLabel(rule.action)}`)
    ruleDraft.pattern = ''
  } catch (e) {
    toastError('添加失败：' + friendlyError(e))
  } finally {
    ruleAdding.value = false
  }
}

async function removeRule(id: number) {
  try {
    await store.removeRule(id)
    toastSuccess('规则已删除')
  } catch (e) {
    toastError('删除失败：' + friendlyError(e))
  }
}

function ruleActionLabel(action: string) {
  if (action === 'important') return '一律重要'
  if (action === 'watch') return '可关注'
  return '不提醒'
}

function openDetailRule(m: MailMessageView) {
  detailRuleOpen.value = !detailRuleOpen.value
  if (!detailRuleOpen.value) return
  const addr = m.senderAddr || ''
  const at = addr.indexOf('@')
  // 默认按域名：机构换一个发件人是常事，只匹配完整地址会漏
  detailRule.pattern = at > 0 ? addr.slice(at) : addr
  // 已经判成普通邮件的，默认意图多半是「别再提醒我」
  detailRule.action = m.importantLevel === 'low' ? 'ignore' : 'important'
}

async function saveDetailRule() {
  const pattern = detailRule.pattern.trim()
  if (!pattern) {
    toastError('规则内容不能为空')
    return
  }
  detailRuleSaving.value = true
  try {
    const rule = await store.addRule(pattern, detailRule.action)
    toastSuccess(`已添加：${rule.pattern} → ${ruleActionLabel(rule.action)}，下一封起生效`)
    detailRuleOpen.value = false
  } catch (e) {
    toastError('保存失败：' + friendlyError(e))
  } finally {
    detailRuleSaving.value = false
  }
}

// ---- 摘要 ----

function localDate(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function digestDateParam() {
  const d = new Date()
  if (digestDay.value === 'yesterday') d.setDate(d.getDate() - 1)
  return localDate(d)
}

async function loadDigest() {
  try {
    await store.loadDigest(digestDateParam())
  } catch (e) {
    toastError('读取摘要失败：' + friendlyError(e))
  }
}

async function pushDigest() {
  digestPushing.value = true
  try {
    const r = await store.pushDigest(digestDateParam())
    if (r.pushed) {
      toastSuccess(r.message || '摘要已推送')
    } else {
      toastInfo(r.message || '这一天没有要紧邮件')
    }
  } catch (e) {
    toastError('推送失败：' + friendlyError(e))
  } finally {
    digestPushing.value = false
  }
}

// ---- 附件 ----

function attUrl(id: number, inline = false) {
  return mailAttachmentUrl(id, inline)
}

function attIcon(att: { contentType: string | null; filename: string }) {
  const name = (att.filename || '').toLowerCase()
  const ct = (att.contentType || '').toLowerCase()
  if (ct.startsWith('image/') || /\.(png|jpe?g|gif|webp)$/.test(name)) return '🖼'
  if (ct.includes('pdf') || name.endsWith('.pdf')) return '📄'
  if (/\.(docx?|xlsx?|pptx?)$/.test(name)) return '📝'
  if (/\.(zip|rar|7z|gz)$/.test(name)) return '🗜'
  return '📎'
}

function fmtSize(bytes: number) {
  if (!bytes) return '大小未知'
  const kb = bytes / 1024
  if (kb < 1) return `${bytes} B`
  if (kb < 1024) return `${Math.round(kb)} KB`
  return `${(kb / 1024).toFixed(1)} MB`
}

/** 下载是浏览器接管的行为，拿不到完成事件，只能稍后回查一次「是否已存本地」 */
function afterDownload() {
  toastInfo('已开始下载，大附件需要几秒')
  const mailId = detail.value?.id
  if (!mailId) return
  window.setTimeout(() => refreshDetail(mailId), 2600)
}

function downloadAll(m: MailMessageView) {
  const list = m.attachments || []
  if (!list.length) return
  toastInfo(`开始下载 ${list.length} 个附件`)
  list.forEach((att, i) => {
    // 一次触发多个下载会被浏览器拦掉大半，间隔开更稳
    window.setTimeout(() => {
      const a = document.createElement('a')
      a.href = mailAttachmentUrl(att.id)
      a.rel = 'noopener'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
    }, i * 700)
  })
  window.setTimeout(() => refreshDetail(m.id), list.length * 700 + 2600)
}

async function refreshDetail(mailId: number) {
  try {
    const full = await fetchMailDetail(mailId)
    if (detail.value?.id === mailId) detail.value = full
  } catch {
    // 回查失败不影响已经发起的下载
  }
}

// ---- 详情 ----

async function openDetail(m: MailMessageView) {
  // 列表接口不带正文，先用手上的摘要占位，再异步补全，点开就有反馈
  detail.value = m
  detailRuleOpen.value = false
  try {
    const full = await fetchMailDetail(m.id)
    // 用户可能在请求期间关掉了弹层或翻到了另一封
    if (detail.value?.id === m.id) detail.value = full
  } catch (e) {
    toastError('打开邮件失败：' + friendlyError(e))
  }
}

function closeDetail() {
  detail.value = null
  detailRuleOpen.value = false
}

async function toTodo(m: MailMessageView) {
  try {
    const updated = await store.toTodo(m.id)
    toastSuccess('已加入待办：' + (updated.todoContent || m.subject))
    if (detail.value && detail.value.id === m.id) {
      detail.value = { ...detail.value, ...updated }
    }
  } catch (e) {
    toastError('加入待办失败：' + friendlyError(e))
  }
}

async function ignore(m: MailMessageView) {
  try {
    await store.ignore(m.id)
    if (detail.value?.id === m.id) closeDetail()
  } catch (e) {
    toastError('操作失败：' + friendlyError(e))
  }
}

async function remove(m: MailMessageView) {
  try {
    await store.remove(m.id)
    if (detail.value?.id === m.id) closeDetail()
    toastSuccess('已删除这条邮件记录')
  } catch (e) {
    toastError('删除失败：' + friendlyError(e))
  }
}

function levelLabel(level: string) {
  if (level === 'high') return '重要'
  if (level === 'medium') return '可关注'
  return '普通'
}

/** 今天的邮件只显示时刻，更早的显示日期 —— 列表里最常见的是今天 */
function fmtTime(s: string) {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) {
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function fmtFull(s: string) {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 2026-11-02 → 11/2 */
function shortDate(s: string) {
  const parts = s.split('-')
  return parts.length === 3 ? `${Number(parts[1])}/${Number(parts[2])}` : s
}
</script>
