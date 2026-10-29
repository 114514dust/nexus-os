"""AI 对话编排图（LangGraph 固定 workflow）。

这是「基于 Workflow 的 AI 对话」核心：一条确定性流水线
    classify（意图识别） -> [dispatch（执行工具，可选）] -> respond（生成回复）
边固定、无 ReAct 自主循环——延续设计文档「先确定性 workflow，后 agent」的原则。

扩展方式（对应「后续拓展流程，让 AI 操控组件」）：
- 想操控新组件 → 在 tools.py 新增 Tool 子类并注册，图结构不变；
- 想在回复前加新环节（如写记忆/向量化）→ 在 respond 前挂新节点，主链不变。
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional, TypedDict

from langgraph.graph import END, StateGraph

import llm
import tools


class ChatState(TypedDict):
    message: str
    history: List[Dict[str, str]]
    intent: str
    tool_input: Dict[str, Any]
    tool_result: str
    reply: str
    llm_config: Optional[Dict[str, Any]]


def classify_node(state: ChatState) -> Dict[str, Any]:
    """意图识别：LLM 把用户消息归为「工具 + 参数」或「纯闲聊」，规则降级兜底。"""
    result = llm.classify(
        state["message"],
        tools.tool_specs(),
        state.get("llm_config"),
        state.get("history"),
    )
    intent = result["intent"]
    # 若意图指向未注册的工具，降级为闲聊，避免 dispatch 抛错
    if intent != "chat" and intent not in tools.TOOLS:
        intent = "chat"
    return {"intent": intent, "tool_input": result.get("params", {})}


def dispatch_node(state: ChatState) -> Dict[str, Any]:
    """分发执行：查工具注册表执行，结果转文本供 respond 节点消费。"""
    try:
        result = tools.dispatch(state["intent"], state.get("tool_input", {}))
        return {"tool_result": result}
    except Exception as e:  # noqa: BLE001 —— 工具失败不打断对话，交给 respond 说明
        return {"tool_result": f"操作失败：{e}"}


def respond_node(state: ChatState) -> Dict[str, Any]:
    """生成回复：把工具结果（或纯聊天）组织成自然语言。"""
    history = state.get("history") or []
    messages = list(history) + [{"role": "user", "content": state["message"]}]

    if state.get("tool_result"):
        # 有工具结果：让 LLM 把执行结果转述给用户（无 LLM 则原样返回结果文本）
        if llm._llm_available(state.get("llm_config")):
            follow = (
                "你刚替用户执行了工作台操作/查询，结果如下。请用简洁的中文转述结果，"
                "必要时给出下一步建议，不要编造结果中没有的信息。\n\n"
                f"工具执行结果：\n{state['tool_result']}"
            )
            reply = llm.chat(messages + [{"role": "user", "content": follow}], state.get("llm_config"))
        else:
            reply = f"已执行「{state['intent']}」：\n\n{state['tool_result']}"
    else:
        reply = llm.chat(messages, state.get("llm_config"))

    return {"reply": reply}


def _route_after_classify(state: ChatState) -> str:
    """条件路由：有工具意图走 dispatch，纯闲聊直接 respond。"""
    return "respond" if state.get("intent") == "chat" else "dispatch"


def build_graph():
    g = StateGraph(ChatState)
    g.add_node("classify", classify_node)
    g.add_node("dispatch", dispatch_node)
    g.add_node("respond", respond_node)
    g.set_entry_point("classify")
    g.add_conditional_edges("classify", _route_after_classify, {"dispatch": "dispatch", "respond": "respond"})
    g.add_edge("dispatch", "respond")
    g.add_edge("respond", END)
    return g.compile()


# 模块级编译好的图实例（图结构无状态，可复用）
compiled_chat_graph = build_graph()
