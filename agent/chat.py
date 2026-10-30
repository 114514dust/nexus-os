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
    # respond 节点只「组装」待生成的 messages，真正逐字生成交给外层流式通道
    respond_messages: List[Dict[str, str]]
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
    """组装回复所需的 messages —— 不在这里生成文本。

    为什么要拆：LangGraph 图擅长确定性编排（路由、工具分发），而 token 级流式
    需要把「生成过程」交给 HTTP 层逐块推送。所以节点只负责决定「该说什么」，
    由 app.py 的 SSE 端点调用 llm.chat_stream() 逐字产出。
    图结构完全不变，同时获得流式体验。

    例外：无 LLM 且有工具结果时，工具结果本身就是最终回复，直接给 reply，
    不必再走生成（respond_messages 留空即表示「无需生成」）。
    """
    history = state.get("history") or []
    messages = list(history) + [{"role": "user", "content": state["message"]}]

    if state.get("tool_result"):
        if llm._llm_available(state.get("llm_config")):
            # 有工具结果：让 LLM 把执行结果转述给用户
            follow = (
                "你刚替用户执行了工作台操作/查询，结果如下。请用简洁的中文转述结果，"
                "必要时给出下一步建议，不要编造结果中没有的信息。\n\n"
                f"工具执行结果：\n{state['tool_result']}"
            )
            return {"respond_messages": messages + [{"role": "user", "content": follow}]}
        # 无 LLM：直接返回结构化结果文本
        return {
            "reply": f"已执行「{state['intent']}」：\n\n{state['tool_result']}",
            "respond_messages": [],
        }

    return {"respond_messages": messages}


def run_chat(state: ChatState) -> Dict[str, Any]:
    """执行图并补齐最终回复（非流式路径用，如 POST /chat）。

    流式路径不走这里——它直接 invoke 图拿 respond_messages，再逐块生成。
    """
    result = compiled_chat_graph.invoke(state)
    if result.get("respond_messages"):
        result["reply"] = llm.chat(result["respond_messages"], result.get("llm_config"))
    elif not result.get("reply"):
        result["reply"] = ""
    return result


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
