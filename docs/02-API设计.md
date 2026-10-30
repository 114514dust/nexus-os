# API 设计规范

> 覆盖 Nexus 后端所有 REST 接口的约定、统一响应、错误码，以及已实现 / 规划中的接口清单。

---

## 一、接口约定

| 项 | 约定 |
|---|---|
| 风格 | REST |
| 前缀 | 所有业务接口统一 `/api` |
| 数据格式 | `application/json`，UTF-8 |
| 认证 | MVP 阶段不认证（单用户本地），预留拦截器位，后续接入登录 |
| 分页 | 规划中的列表接口用 `?page=&size=`，返回 `{ list, total, page, size }` |

---

## 二、统一响应结构

所有接口返回统一结构：

```json
{
  "code": 0,
  "message": "ok",
  "data": { }
}
```

- `code = 0` 表示成功，非 0 为业务错误码。
- `data` 成功时承载业务数据；失败时为 `null`。
- HTTP 状态码与 `code` 保持一致语义（404 → 404，409 → 409 等），但客户端判断以 `code` 为准，便于映射友好文案。

---

## 三、错误码

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 400 | 400 | 请求参数错误（含字段校验失败） |
| 404 | 404 | 资源不存在 |
| 409 | 409 | 资源冲突 |
| 500 | 500 | 服务器内部错误 |

> 设计：错误码与 HTTP 状态解耦，客户端只看到规范化结构，不泄露堆栈与内部细节。

---

## 四、已实现 API

### 1. 组件目录

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/widgets` | 获取组件目录列表 |

**响应示例**：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 1,
      "type": "clock",
      "name": "时钟",
      "description": "显示当前日期与时间",
      "defaultW": 4,
      "defaultH": 2,
      "icon": "clock"
    }
  ]
}
```

### 2. 工作台布局

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/layout` | 读取布局 |
| PUT | `/api/layout` | 保存布局（整体覆盖） |

**PUT 请求体**：

```json
{
  "items": [
    { "widgetType": "clock", "x": 0, "y": 0, "w": 4, "h": 2 },
    { "widgetType": "todo", "x": 4, "y": 0, "w": 4, "h": 6 }
  ]
}
```

**字段约束**：`widgetType` 必填非空；`x/y/w/h` 均 ≥ 0。

### 3. 待办清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/todos` | 待办列表 |
| POST | `/api/todos` | 新建待办 |
| PUT | `/api/todos/{id}` | 更新待办（字段为 null 表示不更新） |
| DELETE | `/api/todos/{id}` | 删除待办 |

**POST 请求体**：`{ "content": "写周报", "dueDate": "2026-11-02" }`（`content` 必填，≤512 字；`dueDate` 可选，`yyyy-MM-dd`）

**PUT 请求体**：`{ "content": "改写周报", "done": true }`（均可选）

`dueDate` 遵循「null = 不更新」的约定，因此**清空到期日要用 `clearDueDate: true`**，不能传 null。

**待办对象**：

```json
{
  "id": 1,
  "content": "写周报",
  "done": false,
  "dueDate": "2026-11-02",
  "createdAt": "2026-10-29T18:00:00"
}
```

### 4. 求职追踪

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/jobs` | 列表，支持 `keyword/category/stage/scope/sort` |
| GET | `/api/jobs/stats` | 概览统计（全量，不受筛选影响） |
| GET | `/api/jobs/{id}` | 详情（记录 + 进展时间线） |
| POST | `/api/jobs` | 新建（只有 `company` 必填） |
| PUT | `/api/jobs/{id}` | 更新；`stage` 变化时自动写一条时间线 |
| POST | `/api/jobs/{id}/advance` | 推进到下一阶段 |
| POST | `/api/jobs/{id}/events` | 只追加一条进展备注，不改阶段 |
| DELETE | `/api/jobs/{id}/events/{eventId}` | 删除一条进展记录（不影响当前阶段） |
| DELETE | `/api/jobs/{id}` | 删除整条投递（连带时间线） |

`scope`：`active`（默认，未到终态）/ `closed`（已入职、未通过）/ `all`；`sort`：`recent` / `due` / `company`，星标永远置顶。
`nextAt` 同样遵循「null = 不更新」，清空日期用 `clearNextAt: true`。

### 5. 校招岗位

外部渠道（当前是秋招网）抓来的在招岗位池，与投递记录分开：`job_posting` 是「外面有什么」，`job_application` 是「我投了什么」。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/postings` | 列表，支持 `keyword/category/scope/onlyMatched` |
| GET | `/api/postings/stats` | 概览（待处理 / 已加入 / 近 3 天 / 收藏） |
| POST | `/api/postings/refresh` | 手动抓取，返回 `fetched/added/updated/newIds` |
| POST | `/api/postings` | 手动添加（只有 `company` 必填） |
| PUT | `/api/postings/{id}` | 收藏 / 忽略（`{starred, ignored}`，null 表示不改） |
| DELETE | `/api/postings/{id}` | 删除 |
| POST | `/api/postings/{id}/apply` | 加入求职追踪，返回 `{posting, job, created}` |
| GET / PUT | `/api/postings/prefs` | 关注关键词、自动抓取开关、抓取页数 |

`scope`：`pending`（默认，未加入未忽略）/ `applied`（已加入）/ `ignored` / `all`。
`apply` 是幂等的：已加入过返回原投递（`created=false`）；若那条投递已被删除，则允许重新加入。
截止文案能解析成日期时（`2026-11-15` / `11月15日`）会自动填进投递的 `nextAt`。

### 6. 邮箱消息

订阅 QQ 邮箱（IMAP）收信，自动判定重要性；高置信度邮件直接转成待办。**只读邮箱**：不标已读、不移动、不删除服务端邮件。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mail/settings` | 读取配置，授权码打码成 `****abcd` |
| PUT | `/api/mail/settings` | 保存配置；`authCode` 以 `****` 开头表示「不修改」 |
| POST | `/api/mail/probe` | 测试连接，返回收件箱总封数（只读） |
| POST | `/api/mail/sync` | 立即收取一次，返回 `{fetched, high, medium, todos, highlights}` |
| GET | `/api/mail/messages` | 列表，支持 `scope/keyword/limit` |
| GET | `/api/mail/messages/{id}` | 详情（含纯文本正文） |
| POST | `/api/mail/messages/{id}/todo` | 手动转成待办 |
| POST | `/api/mail/messages/{id}/ignore` | 不再显示这封邮件 |
| DELETE | `/api/mail/messages/{id}` | 删除邮件记录（不连带删除已生成的待办） |
| GET | `/api/mail/stats` | 概览（重要 / 可关注 / 已转待办 / 已收录 / 上次收取 / 失败原因） |
| GET | `/api/mail/digest?date=` | 某天的摘要（默认昨天），含 `text` 纯文本版，只读不推送 |
| POST | `/api/mail/digest/push?date=` | 立即推送一次摘要，返回 `{pushed, message}` |
| GET | `/api/mail/attachments/{id}/download` | 下载附件；首次自动回 IMAP 取一次并存本地，`inline=true` 走内联预览 |
| GET | `/api/mail/rules` | 发件人规则列表（含命中次数） |
| POST | `/api/mail/rules` | 新增规则 `{pattern, action, note}`，`action` = `important/watch/ignore` |
| PUT | `/api/mail/rules/{id}` | 修改规则 |
| DELETE | `/api/mail/rules/{id}` | 删除规则 |

`scope`：`important`（默认，高 + 中置信度且未忽略）/ `todo`（已转待办）/ `all`。
`importantLevel`：`high`（≥70 分，自动建待办）/ `medium`（45–69）/ `low`（不进重要列表）。
`verdictBy`：`rule`（纯规则）/ `llm`（大模型参与过灰区判定或待办提炼）。

`sync` / `probe` 用 POST 而非 GET：会连外部 IMAP，属于有副作用的动作，不应被缓存或预取。前端对这两个请求单独把超时放宽到 90 秒。

**附件下载**返回二进制流 + `Content-Disposition`（`attachment` 或 `inline`，文件名按 UTF-8 编码）。
首次访问会连一次 IMAP 把字节取回来落到 `data/mail-attachments/`，之后直接读盘；单文件上限 25 MB（`nexus.mail.attachment-max-mb`）。
邮件详情里的 `attachments[]` 只给元信息（文件名 / 类型 / 大小 / 是否已存本地），不暴露本地路径。

**发件人规则**的 `pattern` 同时匹配发件地址与发件人显示名（学校通知常以中文名发信），
多个规则同时命中时**越具体越优先**（pattern 更长者胜），长度相同时 `ignore` 优先。
规则与「永不重要」名单的区别：规则能表达「一律重要」和「只进可关注、别自动建待办」，并且带命中计数。
重复添加同一 pattern 返回 `409`，不会静默覆盖。

---

## 五、规划中 API（Phase 2 / 3 / 4）

> 标注 ⏳ = Phase 2 本次实现；📋 = 后续规划。

### 便签（⏳ Phase 2）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/notes` | 便签列表 |
| POST | `/api/notes` | 新建便签 |
| PUT | `/api/notes/{id}` | 更新便签 |
| DELETE | `/api/notes/{id}` | 删除便签 |

**便签对象**：`{ "id": 1, "content": "...", "createdAt": "...", "updatedAt": "..." }`

### 快捷链接（⏳ Phase 2）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/links` | 链接列表（按 sortOrder 排序） |
| POST | `/api/links` | 新建链接 |
| PUT | `/api/links/{id}` | 更新链接 |
| DELETE | `/api/links/{id}` | 删除链接 |

**链接对象**：`{ "id": 1, "title": "GitHub", "url": "https://github.com", "sortOrder": 0 }`

### 消息（✅ Phase 3）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/messages` | 消息列表（分页） |
| POST | `/api/messages` | 通用 webhook 接收端点（第三方来源） |

**消息对象**：`{ "id": 1, "source": "feishu", "channel": "group", "sender": "张三", "content": "...", "receivedAt": "..." }`

### 每日总结（✅ Phase 4）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/summaries/digest` | 手动触发一次"消化"任务（可选 `?date=2026-10-29`，默认今天） |
| GET | `/api/summaries/latest` | 查询最新一份总结 |

**总结对象**：`{ "id": 1, "summaryDate": "2026-10-29", "content": "## ...", "messageCount": 4, "todoCount": 1, "noteCount": 1, "llmMode": "rule-based", "createdAt": "..." }`

### 订阅（✅ Phase 5）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/subscriptions` | 订阅源列表 |
| POST | `/api/subscriptions` | 新建订阅（type=rss/bilibili） |
| DELETE | `/api/subscriptions/{id}` | 删除订阅（级联清空去重记录） |
| PUT | `/api/subscriptions/{id}/toggle` | 启用/停用订阅 |
| POST | `/api/subscriptions/{id}/fetch` | 手动抓取单个订阅，返回新增条数 |
| POST | `/api/subscriptions/fetch-all` | 抓取所有启用订阅 |

**订阅对象**：`{ "id": 1, "type": "rss", "name": "阮一峰的网络日志", "url": "https://...", "enabled": true, "lastFetchedAt": "...", "createdAt": "..." }`

### 设置（✅ Phase 6）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/settings` | 读取 LLM 配置 |
| PUT | `/api/settings` | 更新 LLM 配置（仅更新非空字段） |

**设置对象**：`{ "apiKey": "sk-...", "baseUrl": "https://api.deepseek.com", "model": "deepseek-chat" }`

### 页面（✅ Phase 6）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/pages` | 页面列表（按 sortOrder 排序） |
| POST | `/api/pages` | 新建页面（自动初始化空布局） |
| PUT | `/api/pages/{id}` | 重命名页面 |
| DELETE | `/api/pages/{id}` | 删除页面（级联删布局，至少保留一页） |
| GET | `/api/pages/{id}/layout` | 读某页布局 |
| PUT | `/api/pages/{id}/layout` | 保存某页布局 |

**页面对象**：`{ "id": 1, "name": "工作", "sortOrder": 0, "createdAt": "..." }`

---

## 六、接口开发规范

1. **控制器不写业务逻辑**：controller 只做参数绑定与返回，业务在 service，持久化在 repository。
2. **请求体用独立 DTO + JSR-303 校验**：不直接接收 entity，避免暴露内部结构、防止过度绑定。
3. **统一响应封装**：返回值统一 `ApiResponse<T>`，禁止裸对象返回。
4. **异常统一抛出 `ApiException`**：由 `GlobalExceptionHandler` 转成规范化错误，业务代码不 try-catch 包装 HTTP。
