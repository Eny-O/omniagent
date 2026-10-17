"""LangGraph debug / checkpoint 输出格式化工具。

用法：
    from utils.debug_printer import print_debug_stream, format_langgraph_event

    for event in agent.stream(input_state, config, stream_mode="debug"):
        print(format_langgraph_event(event))
"""

from typing import Any, Dict, List


def format_langgraph_event(event: Dict[str, Any]) -> str:
    """把单个 LangGraph debug/checkpoint 事件格式化成人类可读文本。"""
    step = event.get("step", "?")
    ts = event.get("timestamp", "")
    etype = event.get("type", "unknown")
    payload = event.get("payload", {})

    lines = [f"--- Step {step} | {etype} | {ts[:19]} ---"]

    if etype == "checkpoint":
        values = payload.get("values", {})
        next_nodes = payload.get("next", [])
        lines.append(f"  next: {next_nodes}")
        lines.append(f"  state keys: {list(values.keys())}")
        for key, val in values.items():
            preview = summarize_value(val)
            lines.append(f"    {key}: {preview}")

    elif etype == "task":
        task = payload
        lines.append(f"  node: {task.get('name')}")
        lines.append(f"  input keys: {list(task.get('input', {}).keys())}")

    elif etype == "task_result":
        task = payload
        lines.append(f"  node: {task.get('name')}")
        result = task.get("result", {}) or {}
        lines.append(f"  result keys: {list(result.keys())}")
        for key, val in result.items():
            preview = summarize_value(val)
            lines.append(f"    {key}: {preview}")

        interrupts = task.get("interrupts", [])
        if interrupts:
            lines.append(f"  interrupts: {len(interrupts)}")
            for inter in interrupts:
                value = inter.get("value", {})
                lines.append(f"    - type: {value.get('type')}")
                lines.append(f"      missing_fields: {value.get('missing_fields', [])}")
                lines.append(f"      question: {value.get('question', '')[:80]}")

    elif "error" in payload:
        lines.append(f"  error: {payload['error']}")

    return "\n".join(lines)


def summarize_value(val: Any, max_len: int = 120) -> str:
    """把任意值压缩成一行预览。"""
    text = repr(val)
    if len(text) > max_len:
        text = text[:max_len] + "..."
    return text


def print_debug_stream(events: List[Dict[str, Any]]) -> None:
    """格式化打印整个 debug stream。"""
    for event in events:
        print(format_langgraph_event(event))
        print()


def collect_and_print_debug(agent, input_state: Dict[str, Any], config: Dict[str, Any]) -> None:
    """一键运行 agent 的 debug stream 并格式化打印。"""
    for event in agent.stream(input_state, config, stream_mode="debug"):
        print(format_langgraph_event(event))
        print()
