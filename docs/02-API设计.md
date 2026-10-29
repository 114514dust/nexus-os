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

**POST 请求体**：`{ "content": "写周报" }`（`content` 必填，≤512 字）

**PUT 请求体**：`{ "content": "改写周报", "done": true }`（均可选）

**待办对象**：

```json
{
  "id": 1,
  "content": "写周报",
  "done": false,
  "createdAt": "2026-10-29T18:00:00"
}
```

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

---

## 六、接口开发规范

1. **控制器不写业务逻辑**：controller 只做参数绑定与返回，业务在 service，持久化在 repository。
2. **请求体用独立 DTO + JSR-303 校验**：不直接接收 entity，避免暴露内部结构、防止过度绑定。
3. **统一响应封装**：返回值统一 `ApiResponse<T>`，禁止裸对象返回。
4. **异常统一抛出 `ApiException`**：由 `GlobalExceptionHandler` 转成规范化错误，业务代码不 try-catch 包装 HTTP。
