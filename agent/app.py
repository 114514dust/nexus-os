"""Agent 服务入口（FastAPI）。

对外暴露：
- GET  /health     健康检查
- POST /summarize  接收 Java 聚合好的当天数据，走 LangGraph 编排图生成总结
- POST /chat       基于 Workflow 的 AI 对话（意图识别 -> 工具分发 -> 生成回复）

服务无状态、不落库：输入当天数据/对话，输出文本，落库由 Java 后端负责。
"""
from __future__ import annotations

import json
from typing import List, Optional

from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

import config
import llm
from chat import compiled_chat_graph, run_chat
from graph import compiled_graph

app = FastAPI(title="Nexus Agent", version="0.2.0")


class MessageItem(BaseModel):
    source: str = "unknown"
    sender: str = ""
    content: str = ""


class TodoItem(BaseModel):
    content: str = ""
    done: bool = False


class NoteItem(BaseModel):
    content: str = ""


class LlmConfig(BaseModel):
    apiKey: str = ""
    baseUrl: str = ""
    model: str = ""


class SummarizeRequest(BaseModel):
    date: str
    messages: List[MessageItem] = []
    todos: List[TodoItem] = []
    notes: List[NoteItem] = []
    llm_config: Optional[LlmConfig] = None


class SummarizeResponse(BaseModel):
    summary: str
    llm: str


def _llm_mode(llm_config: Optional[LlmConfig]) -> str:
    """判断本次总结是否走真实大模型：优先看请求动态配置，再看环境变量。"""
    if llm_config and llm_config.apiKey:
        return "enabled"
    return "enabled" if config.llm_enabled() else "rule-based"


@app.get("/health")
def health() -> dict:
    return {"status": "up", "llm": "enabled" if config.llm_enabled() else "rule-based"}


@app.post("/summarize", response_model=SummarizeResponse)
def summarize(req: SummarizeRequest) -> SummarizeResponse:
    llm_config = req.llm_config.model_dump() if req.llm_config else None
    state = {
        "date": req.date,
        "messages": [m.model_dump() for m in req.messages],
        "todos": [t.model_dump() for t in req.todos],
        "notes": [n.model_dump() for n in req.notes],
        "raw_text": "",
        "summary": "",
        "llm_config": llm_config,
    }
    result = compiled_graph.invoke(state)
    return SummarizeResponse(
        summary=result["summary"],
        llm=_llm_mode(req.llm_config),
    )


class ChatMessage(BaseModel):
    role: str = "user"
    content: str = ""


class ChatRequest(BaseModel):
    message: str
    history: List[ChatMessage] = []
    llm_config: Optional[LlmConfig] = None


class ChatResponse(BaseModel):
    reply: str
    intent: str


@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest) -> ChatResponse:
    llm_config = req.llm_config.model_dump() if req.llm_config else None
    state = {
        "message": req.message,
        "history": [{"role": m.role, "content": m.content} for m in req.history],
        "intent": "chat",
        "tool_input": {},
        "tool_result": "",
        "reply": "",
        "respond_messages": [],
        "llm_config": llm_config,
    }
    result = run_chat(state)
    return ChatResponse(reply=result["reply"], intent=result["intent"])


def _sse(payload: dict) -> str:
    """构造一条 SSE 消息（ensure_ascii=False 保证中文不被转义成 \\uXXXX）。"""
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _chat_events(state: dict, llm_config):
    """SSE 事件流：intent（意图） -> delta*（增量文本） -> done（完整回复）。

    图只跑编排部分（classify/dispatch/组装 messages），文本生成在这里逐块产出，
    这样前端能拿到 token 级的流式效果，而图结构保持确定性。
    """
    try:
        result = compiled_chat_graph.invoke(state)
        intent = result.get("intent", "chat")
        yield _sse({"type": "intent", "intent": intent})

        if result.get("respond_messages"):
            parts: List[str] = []
            for delta in llm.chat_stream(result["respond_messages"], llm_config):
                parts.append(delta)
                yield _sse({"type": "delta", "content": delta})
            reply = "".join(parts)
        else:
            # 无 LLM 且工具结果即最终回复：切块产出，保持前端渲染逻辑一致
            reply = result.get("reply", "")
            for piece in llm._chunk_text(reply):
                yield _sse({"type": "delta", "content": piece})

        yield _sse({"type": "done", "reply": reply, "intent": intent})
    except Exception as e:  # noqa: BLE001 —— 流已开始，只能以事件形式回报错误
        yield _sse({"type": "error", "message": str(e)})


@app.post("/chat/stream")
def chat_stream(req: ChatRequest) -> StreamingResponse:
    """流式对话（SSE）。事件类型：intent / delta / done / error。"""
    llm_config = req.llm_config.model_dump() if req.llm_config else None
    state = {
        "message": req.message,
        "history": [{"role": m.role, "content": m.content} for m in req.history],
        "intent": "chat",
        "tool_input": {},
        "tool_result": "",
        "reply": "",
        "respond_messages": [],
        "llm_config": llm_config,
    }
    return StreamingResponse(
        _chat_events(state, llm_config),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",  # 关闭反向代理缓冲，否则流会被攒成一整块
        },
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=config.HOST, port=config.PORT)
