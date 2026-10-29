"""每日总结编排图（LangGraph 固定 workflow 子图）。

这是整个 Agent 服务的核心资产：一条确定性流水线
    fetch（清洗） -> aggregate（聚合） -> summarize（总结） -> save（透传）
所有边固定、无 ReAct 循环——对应设计文档「先做确定性 workflow」的原则。

设计要点：
1. 图结构是「资产」，模型是「可替换零件」——summarize 节点内部只调 llm.summarize()
   抽象，换 LLM 不影响图结构；
2. save 节点当前仅透传（Python 无状态、不落库，最终落库由 Java 负责），
   但节点保留，将来扩展「写记忆 / 向量化」等环节时只需在此处挂新节点、不改主链。
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional, TypedDict

from langgraph.graph import END, StateGraph

import llm


class SummaryState(TypedDict):
    date: str
    messages: List[Dict[str, Any]]
    todos: List[Dict[str, Any]]
    notes: List[Dict[str, Any]]
    raw_text: str
    summary: str
    llm_config: Optional[Dict[str, Any]]


def fetch_node(state: SummaryState) -> Dict[str, Any]:
    """清洗：剔除空内容，保证后续节点只处理有效数据。"""
    messages = [m for m in state.get("messages", []) if str(m.get("content", "")).strip()]
    todos = state.get("todos", [])
    notes = [n for n in state.get("notes", []) if str(n.get("content", "")).strip()]
    return {"messages": messages, "todos": todos, "notes": notes}


def aggregate_node(state: SummaryState) -> Dict[str, Any]:
    """聚合：把三类信息拼成一段结构化的原始文本，供总结节点消费。"""
    lines: List[str] = []

    lines.append(f"## 消息（{len(state['messages'])} 条）")
    for m in state["messages"]:
        source = m.get("source", "unknown")
        sender = str(m.get("sender", "")).strip()
        content = str(m.get("content", "")).strip()
        lines.append(f"- [{source}] {sender}: {content}" if sender else f"- [{source}] {content}")

    lines.append(f"\n## 待办（{len(state['todos'])} 项）")
    for t in state["todos"]:
        mark = "✅" if t.get("done") else "⬜"
        lines.append(f"- {mark} {t.get('content', '')}")

    lines.append(f"\n## 便签（{len(state['notes'])} 条）")
    for n in state["notes"]:
        lines.append(f"- {n.get('content', '')}")

    return {"raw_text": "\n".join(lines)}


def summarize_node(state: SummaryState) -> Dict[str, Any]:
    """总结：调用可插拔 LLM（优先动态配置，其次环境变量，最后规则式降级）。"""
    summary = llm.summarize(state["raw_text"], state["date"], state.get("llm_config"))
    return {"summary": summary}


def save_node(state: SummaryState) -> Dict[str, Any]:
    """保存（MVP 透传）：Python 不落库，最终由 Java 回写 daily_summary。"""
    return {"summary": state["summary"]}


def build_graph():
    g = StateGraph(SummaryState)
    g.add_node("fetch", fetch_node)
    g.add_node("aggregate", aggregate_node)
    g.add_node("summarize", summarize_node)
    g.add_node("save", save_node)
    g.set_entry_point("fetch")
    g.add_edge("fetch", "aggregate")
    g.add_edge("aggregate", "summarize")
    g.add_edge("summarize", "save")
    g.add_edge("save", END)
    return g.compile()


# 模块级编译好的图实例（图结构无状态，可复用）
compiled_graph = build_graph()
