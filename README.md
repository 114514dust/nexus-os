# 中枢 Nexus · 个人工作台系统

一个**个人 OS（Personal OS）**：把所有工作收拢到一处，让 AI 替你处理信息、沉淀记忆、主动办事。

本仓库是 MVP 第一阶段——**工作台组件 DIY**：一个可拖拽布局的工作台 + 组件注册机制（Widget SDK）+ 布局/待办后端持久化。

> 完整设计见 `个人工作台系统设计文档.md`。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 11 · Spring Boot 2.7 · Spring Data JPA · Flyway · H2 |
| 前端 | Vue 3 · TypeScript · Vite · Pinia · vue-router · grid-layout-plus |
| 接口 | REST（统一 `{ code, message, data }` 响应） |
| 部署 | Docker Compose |

## 目录结构

```
nexus-os/
├── backend/                 # Java 后端
│   ├── src/main/java/com/nexus/
│   │   ├── config/          # 配置（CORS、应用属性）
│   │   ├── common/          # 统一响应、错误体系、全局异常处理
│   │   ├── widget/          # 组件目录
│   │   ├── layout/          # 布局持久化
│   │   └── todo/            # 待办 CRUD
│   └── src/main/resources/db/migration/  # Flyway 迁移
├── frontend/                # Vue 3 前端
│   └── src/
│       ├── widgets/         # Widget SDK：注册表 + 内置组件
│       ├── stores/          # Pinia 状态
│       ├── api/             # axios 封装 + 接口
│       └── views/           # 工作台页面
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
| GET | `/api/layout` | 读取布局 |
| PUT | `/api/layout` | 保存布局 |
| GET | `/api/todos` | 待办列表 |
| POST | `/api/todos` | 新建待办 |
| PUT | `/api/todos/{id}` | 更新待办 |
| DELETE | `/api/todos/{id}` | 删除待办 |

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `NEXUS_CORS_ORIGINS` | `http://localhost:5173` | 允许的前端来源（逗号分隔） |
