# 中枢 Nexus · 个人工作台系统

一个**个人 OS（Personal OS）**：把所有工作收拢到一处，让 AI 替你处理信息、沉淀记忆、主动办事。

当前已完成 **MVP 四个 Phase**（V0 · 打通闭环）：

- **Phase 1** 工作台组件 DIY：可拖拽布局 + Widget SDK
- **Phase 2** 组件配置化与持久化：便签/快捷链接落库 + 配置面板
- **Phase 3** 消息接入：统一消息模型 + 通用 webhook
- **Phase 4** 每日总结 Agent：LangGraph 编排 + 可插拔 LLM + 定时消化

> 完整设计见 `个人工作台系统设计文档.md`，开发计划见 `docs/01`，进度见 `进度.md`。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 11 · Spring Boot 2.7 · Spring Data JPA · Flyway · H2（生产可切 PostgreSQL） |
| AI 编排 | Python 3 · LangGraph · FastAPI（可插拔 LLM：OpenAI 兼容 + 规则式降级） |
| 前端 | Vue 3 · TypeScript · Vite · Pinia · vue-router · grid-layout-plus |
| 接口 | REST（统一 `{ code, message, data }` 响应） |
| 部署 | Docker Compose |

## 目录结构

```
nexus-os/
├── backend/                 # Java 业务后端（数据所有者）
│   └── src/main/java/com/nexus/
│       ├── config/          # 配置（CORS、应用属性）
│       ├── common/          # 统一响应、错误体系、全局异常处理、分页
│       ├── widget/          # 组件目录
│       ├── layout/          # 布局持久化
│       ├── todo/            # 待办 CRUD
│       ├── note/            # 便签 CRUD
│       ├── link/            # 快捷链接 CRUD
│       ├── message/         # 统一消息模型 + webhook
│       └── summary/         # 每日总结（聚合 + 调 Agent + 定时任务）
├── agent/                   # Python AI 编排服务（无状态）
│   ├── app.py               # FastAPI 入口（POST /summarize）
│   ├── graph.py             # LangGraph 固定 workflow 子图
│   ├── llm.py               # 可插拔 LLM 客户端
│   └── config.py            # 配置（环境变量注入）
├── frontend/                # Vue 3 前端
│   └── src/
│       ├── widgets/         # Widget SDK：注册表 + 内置组件
│       ├── stores/          # Pinia 状态
│       ├── api/             # axios 封装 + 接口
│       └── views/           # 工作台页面
├── docs/                    # 开发文档
├── docker-compose.yml
└── 个人工作台系统设计文档.md
```

## 本地开发

### 后端（需 JDK 11）

```bash
cd backend
mvn spring-boot:run
# 或用 IDEA 打开 backend 目录，直接运行 NexusApplication
```

启动后：http://localhost:8080 ，健康检查 http://localhost:8080/actuator/health

> 注意：若你的 shell 环境存在 `SERVER__PORT` / `SERVER__HOST` 环境变量（部分 IDE 代理环境会注入），
> 会被 Spring Boot 的 relaxed binding 映射为 `server.port`，覆盖配置的 8080。可显式指定端口规避：
> `mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"`

### AI 编排服务（需 Python 3）

```bash
cd agent
python -m pip install -r requirements.txt
python -m uvicorn app:app --host 127.0.0.1 --port 9001
```

未配置 LLM key 时自动走**规则式降级**（不调大模型，链路仍可用）。配置以下环境变量后切换为智能总结：

| 变量 | 说明 | 示例 |
|---|---|---|
| `LLM_API_KEY` | API Key（OpenAI 兼容） | `sk-xxx` |
| `LLM_BASE_URL` | 接口地址 | DeepSeek `https://api.deepseek.com` |
| `LLM_MODEL` | 模型名 | `deepseek-chat` / `gpt-4o-mini` / `qwen-plus` |

### 前端（需 Node 18+）

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173 ，`/api` 已代理到后端 8080。

## Docker 部署

```bash
docker compose up --build
```

- 前端：http://localhost
- 后端：http://localhost:8080

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/widgets` | 组件目录 |
| GET / PUT | `/api/layout` | 读取 / 保存布局 |
| GET / POST | `/api/todos` | 待办列表 / 新建 |
| PUT / DELETE | `/api/todos/{id}` | 更新 / 删除待办 |
| GET / POST | `/api/notes` | 便签列表 / 新建 |
| PUT / DELETE | `/api/notes/{id}` | 更新 / 删除便签 |
| GET / POST | `/api/links` | 链接列表 / 新建 |
| PUT / DELETE | `/api/links/{id}` | 更新 / 删除链接 |
| GET / POST | `/api/messages` | 消息分页列表 / 接收（通用 webhook） |
| POST | `/api/summaries/digest` | 手动触发每日总结消化 |
| GET | `/api/summaries/latest` | 查询最新总结 |

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `NEXUS_CORS_ORIGINS` | `http://localhost:5173` | 允许的前端来源（逗号分隔） |
| `NEXUS_AGENT_BASE_URL` | `http://127.0.0.1:9001` | Python Agent 服务地址 |
| `NEXUS_DIGEST_CRON` | `0 30 23 * * *` | 每日总结定时触发 cron |
| `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` | - | 真实大模型配置（Agent 侧，缺省走降级） |
