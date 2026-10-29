# Widget 组件开发规范

> 工作台组件 DIY 是 MVP 切入点，Widget SDK 是其核心机制。本文规范"一个组件由什么构成、如何注册、如何配置、如何新增"。

---

## 一、Widget 是什么

一个 Widget = **前端 Vue 组件（渲染与交互）** + **后端元信息（组件目录）** + **可选的配置 Schema（可配置项）**。

组件通过 `type` 字符串关联三者：
- 前端 `registry.ts` 用 `type` 注册组件实现
- 后端 `widget_definition` 表用 `type` 存元信息
- 布局 `layout.items_json` 用 `widgetType` 引用组件

---

## 二、组件构成

### 1. 前端组件（Vue SFC）

```vue
<template>
  <div class="clock-widget">...</div>
</template>
<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
// 组件自包含：数据获取、定时器、事件都内部管理
</script>
```

约定：
- 组件是**自包含**的，接收统一的 `props`（见下），不依赖外部页面状态
- 需持久化数据的组件，通过 `api/` 层调用后端，不直接 localStorage

### 2. 注册元信息（WidgetMeta）

```ts
interface WidgetMeta {
  type: string          // 唯一标识，与后端 widget_definition.type 一致
  name: string          // 显示名
  description: string   // 描述
  icon: string          // 图标标识
  defaultW: number      // 默认宽（栅格）
  defaultH: number      // 默认高（栅格）
  component: Component  // Vue 组件（建议 defineAsyncComponent 按需加载）
  configSchema?: ConfigField[]  // 可选：组件可配置项（Phase 2 引入）
}
```

### 3. 统一 props

`WidgetRenderer` 渲染组件时传入：

```ts
interface WidgetProps {
  widgetId: string      // 布局实例 id（同一组件类型可多个实例）
  config: Record<string, any>  // 组件实例配置（来自布局/配置面板）
}
```

---

## 三、注册机制

`registry.ts` 提供三个能力：

```ts
registerWidget(meta)   // 上架一个组件
getWidget(type)        // 按类型取组件
registerBuiltinWidgets()  // 注册全部内置组件（main.ts 调用）
```

组件注册表用 `Map<type, WidgetMeta>` 实现，天然可插拔、可扩展——未来"组件市场"只需把"上架"动作从本地注册表换成远端元信息 + 动态加载即可。

---

## 四、可配置组件（Phase 2 引入）

### configSchema 声明

```ts
interface ConfigField {
  key: string            // 配置项 key
  label: string          // 显示名
  type: 'text' | 'number' | 'boolean' | 'select'
  default?: any
  options?: { value: string; label: string }[]  // type=select 时
}
```

示例（快捷链接组件可配置"打开方式"）：

```ts
configSchema: [
  { key: 'openInNewTab', label: '新标签页打开', type: 'boolean', default: true }
]
```

### 配置面板

- 通用配置面板组件读取当前组件类型的 `configSchema` 动态渲染表单
- 配置值随布局项持久化（存进 `layout` 的组件项，作为组件项的自定义字段）
- 组件通过 `props.config` 读取自己的配置

---

## 五、如何新增一个组件

以"天气组件"为例：

1. **建前端组件** `frontend/src/widgets/WeatherWidget.vue`，自包含实现渲染。
2. **注册**：在 `registry.ts` 的 `registerBuiltinWidgets()` 里 `registerWidget({ type: 'weather', name: '天气', ... , component: () => import('./WeatherWidget.vue') })`。
3. **（可选）建后端元信息**：在 `widget_definition` 表插入一行（通过 Flyway 迁移或数据初始化）。
4. **（可选）声明配置**：若有可配置项，加 `configSchema`。
5. **验证**：工作台"添加组件"里出现"天气"，可拖拽、渲染、配置、持久化。

---

## 六、内置组件清单

| type | 名称 | 数据来源 | 状态 |
|---|---|---|---|
| clock | 时钟 | 无（纯前端） | ✅ |
| todo | 待办清单 | 后端 `/api/todos` | ✅ |
| note | 便签 | localStorage → 后端 `/api/notes` | ⏳ Phase 2 改持久化 |
| links | 快捷链接 | localStorage → 后端 `/api/links` | ⏳ Phase 2 改持久化 |
| message | 消息流 | 后端 `/api/messages` | 📋 Phase 3 |

> 演进方向：note/links 从 localStorage 迁移到后端后，数据可跨设备一致、可被 Agent 读取，是"信息可流入 → AI 可办事"的基础。
