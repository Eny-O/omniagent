// Agent SSE 流客户端：原生 fetch + ReadableStream 解析 SSE 帧。

// 直连 Agent 后端，不经 Java / Vite 代理。
// 可用环境变量 VITE_AGENT_BASE 覆盖（部署到别的域名/端口时）。
const AGENT_BASE = (import.meta.env.VITE_AGENT_BASE as string) || 'http://localhost:8000'

export interface ChatStreamPayload {
  message: string
  threadId: string
  userId: number
  actingRole: string
  resume?: string | null
}

export interface StreamInfo {
  stage: string
  message: string
  count?: number
}

export interface StreamHandlers {
  onToken?: (text: string) => void
  onInfo?: (info: StreamInfo) => void
  onDone?: (data: { reply: string }) => void
  onNeedConfirm?: (payload: Record<string, unknown>) => void
  onError?: (err: unknown) => void
}

/**
 * 向 Agent 流式对话端点发请求，逐帧回调。
 * 帧格式（来自 Agent 端）：`event: <type>\ndata: <json>\n\n`
 *  - token：LLM 增量文本
 *  - info：中间过程（检索中 / 工具调用等）
 *  - done：完整回复 { reply }
 *  - need_confirm：需要人机确认（含 confirm_value / reject_value / thread_id）
 *  - error：错误
 */
export async function streamChat(
  payload: ChatStreamPayload,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const body = {
    message: payload.message,
    user_id: payload.userId,
    acting_role: payload.actingRole,
    thread_id: payload.threadId,
    resume: payload.resume ?? null,
  }

  let resp: Response
  try {
    resp = await fetch(`${AGENT_BASE}/api/agent/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal,
    })
  } catch (e) {
    handlers.onError?.(e)
    return
  }

  if (!resp.ok || !resp.body) {
    handlers.onError?.(new Error(`Agent 返回异常：HTTP ${resp.status}`))
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const dispatch = (frame: string) => {
    let event = ''
    let dataRaw = ''
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataRaw += line.slice(5).trim()
    }
    if (!event) return
    let data: Record<string, unknown> = {}
    try {
      data = dataRaw ? (JSON.parse(dataRaw) as Record<string, unknown>) : {}
    } catch {
      data = {}
    }
    switch (event) {
      case 'token':
        handlers.onToken?.(typeof data.text === 'string' ? data.text : '')
        break
      case 'info':
        handlers.onInfo?.({
          stage: typeof data.stage === 'string' ? data.stage : '',
          message: typeof data.message === 'string' ? data.message : '',
          count: typeof data.count === 'number' ? data.count : undefined,
        })
        break
      case 'done':
        handlers.onDone?.({ reply: typeof data.reply === 'string' ? data.reply : '' })
        break
      case 'need_confirm':
        handlers.onNeedConfirm?.(data)
        break
      case 'error':
        handlers.onError?.(data)
        break
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let idx: number
      while ((idx = buffer.indexOf('\n\n')) !== -1) {
        const frame = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        if (frame.trim()) dispatch(frame)
      }
    }
    if (buffer.trim()) dispatch(buffer)
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') return // 用户主动取消，不算错误
    handlers.onError?.(e)
  }
}
