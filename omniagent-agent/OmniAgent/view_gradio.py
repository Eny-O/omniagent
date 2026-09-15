import gradio as gr
from langgraph.types import Command
from langgraph.errors import GraphInterrupt

from test_agent import agent


def add_message(history, role, content):
    """按 Gradio 新版 messages 格式追加消息。"""
    history.append({"role": role, "content": content})
    return history


def build_supplement_hint(payload):
    """把中断 payload 转成面向用户的中文补充提示。"""
    fields = payload.get("missing_fields", [])
    descs = payload.get("field_descriptions", {})
    if fields:
        names = "、".join(descs.get(f, f) for f in fields)
        return f"请补充以下信息：{names}"
    return payload.get("question", "请补充信息")


def chat(message, history, state):
    """
    Gradio 回调函数。
    state: {session_id, waiting_interrupt, pending_fields, pending_descriptions, pending_question}
    """
    session_id = state["session_id"]
    config = {"configurable": {"thread_id": session_id}}

    # 如果正在等待用户补充缺失字段
    if state.get("waiting_interrupt"):
        try:
            # 把用户输入作为补充原文传回，交给 agent 的 parse_supplement 去理解
            supplement = {"supplement_text": message}
            result = agent.invoke(Command(resume=supplement), config)

            state["waiting_interrupt"] = False
            state["pending_fields"] = []
            state["pending_descriptions"] = {}
            state["pending_question"] = ""
            history = add_message(history, "user", message)
            history = add_message(history, "assistant", result.get("response", "已处理"))
            return "", history, state
        except Exception as e:
            history = add_message(history, "user", message)
            history = add_message(history, "assistant", f"出错: {str(e)}")
            return "", history, state

    # 正常发起对话
    input_state = {
        "user_input": message,
        "messages": [],
        "missing_fields": [],
        "entities": {},
        "intent": "",
        "supplement_text": "",
        "response": ""
    }

    try:
        result = agent.invoke(input_state, config)

        # 检查是否触发 interrupt（LangGraph 可能不抛异常，而是返回 __interrupt__）
        if result and "__interrupt__" in result and result["__interrupt__"]:
            interrupt_payload = result["__interrupt__"][0].value
            state["waiting_interrupt"] = True
            state["pending_fields"] = interrupt_payload.get("missing_fields", [])
            state["pending_descriptions"] = interrupt_payload.get("field_descriptions", {})
            state["pending_question"] = interrupt_payload.get("question", "")

            hint = build_supplement_hint(interrupt_payload)
            history = add_message(history, "user", message)
            history = add_message(history, "assistant", hint)
            return "", history, state

        history = add_message(history, "user", message)
        history = add_message(history, "assistant", result.get("response", "处理完成"))
        return "", history, state
    except GraphInterrupt as e:
        payload = e.interrupts[0].value
        state["waiting_interrupt"] = True
        state["pending_fields"] = payload.get("missing_fields", [])
        state["pending_descriptions"] = payload.get("field_descriptions", {})
        state["pending_question"] = payload.get("question", "")

        hint = build_supplement_hint(payload)
        history = add_message(history, "user", message)
        history = add_message(history, "assistant", hint)
        return "", history, state
    except Exception as e:
        history = add_message(history, "user", message)
        history = add_message(history, "assistant", f"出错: {str(e)}")
        return "", history, state


with gr.Blocks(title="OmniAgent 智能工单助手") as demo:
    gr.Markdown("# OmniAgent 智能工单助手")
    gr.Markdown("输入您的问题，Agent 会帮您创建工单或查询知识。")

    # 会话状态
    state = gr.State({
        "session_id": "user_001",
        "waiting_interrupt": False,
        "pending_fields": [],
        "pending_descriptions": {},
        "pending_question": "",
    })

    chatbot = gr.Chatbot(label="对话", height=400)
    msg = gr.Textbox(label="输入", placeholder="请输入您的问题...")

    with gr.Row():
        submit_btn = gr.Button("发送", variant="primary")
        clear_btn = gr.Button("清空")

    submit_btn.click(
        fn=chat,
        inputs=[msg, chatbot, state],
        outputs=[msg, chatbot, state]
    )

    msg.submit(
        fn=chat,
        inputs=[msg, chatbot, state],
        outputs=[msg, chatbot, state]
    )

    clear_btn.click(
        lambda: ("", [], {
            "session_id": "user_001",
            "waiting_interrupt": False,
            "pending_fields": [],
            "pending_descriptions": {},
            "pending_question": "",
        }),
        outputs=[msg, chatbot, state]
    )


if __name__ == "__main__":
    demo.launch()
