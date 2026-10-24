<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { streamChat } from '@/api/agent'

const route = useRoute()

// 仅在「我的保障」「工单列表页」「工单详情页」显示悬浮助手（其余页面不显示）
const showFab = computed(() => {
  const p = route.path
  return p === '/reporter-tickets' || p === '/tickets' || p.startsWith('/tickets/')
})

const open = ref(false)

interface ChatMsg {
  id: number
  role: 'user' | 'ai'
  text: string
  info?: string // 状态条文本（处理中提示）
  confirm?: Record<string, unknown> // need_confirm 时的确认卡片数据
}

const input = ref('')
const modifyText = ref('')
const messages = ref<ChatMsg[]>([])
const busy = ref(false)
const threadId = ref(`chat-${Date.now()}`)
let abortCtrl: AbortController | null = null

function newId(): number {
  return Date.now() + Math.floor(Math.random() * 1000)
}

function pushAi(): number {
  const m: ChatMsg = { id: newId(), role: 'ai', text: '' }
  messages.value.push(m)
  // 返回索引，调用方经 messages.value[idx] 取响应式代理
  return messages.value.length - 1
}

async function runStream(opts: { message: string; resume: string | null }) {
  // 取消上一条未完成的流，避免并发叠加
  abortCtrl?.abort()
  abortCtrl = new AbortController()
  busy.value = true

  const aiIdx = pushAi()
  const ai = messages.value[aiIdx] // 经响应式代理访问，ai.text += 才会触发重渲染
  try {
    await streamChat(
      {
        message: opts.message,
        threadId: threadId.value,
        resume: opts.resume,
      },
      {
        onToken: (t: string) => {
          ai.text += t
          ai.info = undefined
        },
        onInfo: (info) => {
          // kb_text：KB 偏转答案的逐句流式文本
          // answer_token：ReAct 主推理节点逐 token 流式文本
          // 两者都直接追加到气泡，实现打字机效果
          if (info.stage === 'kb_text' || info.stage === 'answer_token') {
            ai.text += info.message
            ai.info = undefined
          } else {
            ai.info = info.message
          }
        },
        onDone: (d) => {
          if (d.reply) ai.text = d.reply
          ai.info = undefined
          ai.confirm = undefined
        },
        onNeedConfirm: (payload) => {
          ai.confirm = payload
          ai.info = undefined
        },
        onError: (e) => {
          // e 可能是 Error（网络/HTTP 异常）或 Agent 返回的 {message} 对象（error 事件帧）
          let msg = '未知错误'
          if (
            e &&
            typeof e === 'object' &&
            'message' in e &&
            typeof (e as { message?: unknown }).message === 'string'
          ) {
            msg = (e as { message: string }).message
          } else if (e instanceof Error) {
            msg = e.message
          }
          ai.text = `⚠️ 出错了：${msg}`
          ai.info = undefined
        },
      },
      abortCtrl.signal,
    )
  } finally {
    busy.value = false
  }
}

function send() {
  const text = input.value.trim()
  if (!text || busy.value) return
  messages.value.push({ id: newId(), role: 'user', text })
  input.value = ''
  void runStream({ message: text, resume: null })
}

function confirmAction(value: string) {
  const ai = messages.value.find((m) => m.confirm)
  if (ai) ai.confirm = undefined
  modifyText.value = ''
  void runStream({ message: '', resume: value })
}

onUnmounted(() => {
  abortCtrl?.abort()
})
</script>

<template>
  <div class="fab-root" v-show="showFab">
    <!-- 展开面板 -->
    <transition name="fab-fade">
      <div v-if="open" class="fab-panel">
        <div class="fab-header">
          <span class="fab-title">AI 助手</span>
          <button class="fab-close" type="button" @click="open = false" aria-label="关闭">×</button>
        </div>
        <div class="msg-list">
          <div v-for="m in messages" :key="m.id" :class="['row', m.role]">
            <div class="bubble">
              <div v-if="m.info" class="info-bar">⏳ {{ m.info }}</div>
              <div v-if="m.text" class="text">{{ m.text }}</div>
              <div v-if="m.confirm" class="confirm-card">
                <div class="confirm-msg">{{ (m.confirm.message as string) || '需要你确认' }}</div>
                <div class="confirm-actions">
                  <el-button
                    type="primary"
                    size="small"
                    @click="confirmAction(String(m.confirm?.confirm_value ?? 'yes'))"
                  >
                    确认
                  </el-button>
                  <el-button size="small" @click="confirmAction(String(m.confirm?.reject_value ?? 'no'))">
                    取消
                  </el-button>
                </div>
                <div class="confirm-modify">
                  <el-input v-model="modifyText" size="small" placeholder="或输入修改意见后发送" />
                  <el-button size="small" @click="confirmAction(modifyText || 'modify')">发送修改</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="input-bar">
          <el-input
            v-model="input"
            placeholder="输入消息，回车发送"
            :disabled="busy"
            @keyup.enter="send"
          />
          <el-button type="primary" :disabled="busy" @click="send">发送</el-button>
        </div>
      </div>
    </transition>

    <!-- 悬浮小球 -->
    <button
      class="fab-ball"
      type="button"
      :title="open ? '收起' : '打开 AI 助手'"
      @click="open = !open"
    >
      <svg class="fab-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="4" y="8" width="16" height="11" rx="2" stroke="currentColor" stroke-width="2" />
        <path d="M12 8V4" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        <circle cx="12" cy="3" r="1.5" fill="currentColor" />
        <circle cx="9" cy="13" r="1.5" fill="currentColor" />
        <circle cx="15" cy="13" r="1.5" fill="currentColor" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.fab-root {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 3000;
}

.fab-ball {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  background: linear-gradient(135deg, #1f2d4d 0%, #2b3f6b 100%);
  box-shadow: 0 6px 20px rgba(31, 45, 77, 0.45), 0 0 0 4px rgba(64, 158, 255, 0.18);
  color: #cfe3ff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.fab-ball:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 26px rgba(31, 45, 77, 0.5), 0 0 0 4px rgba(64, 158, 255, 0.28);
}

.fab-icon {
  width: 28px;
  height: 28px;
}

.fab-panel {
  position: fixed;
  right: 24px;
  bottom: 92px;
  width: 380px;
  height: min(540px, 72vh);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.22);
  overflow: hidden;
}

.fab-header {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: linear-gradient(135deg, #1f2d4d 0%, #2b3f6b 100%);
  color: #fff;
}

.fab-title {
  font-size: 15px;
  font-weight: 600;
}

.fab-close {
  border: none;
  background: transparent;
  color: #fff;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  padding: 0 4px;
}

.fab-close:hover {
  opacity: 0.8;
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.row {
  display: flex;
  margin-bottom: 14px;
}

.row.user {
  justify-content: flex-end;
}

.row.ai {
  justify-content: flex-start;
}

.bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 10px;
  line-height: 1.6;
}

.row.user .bubble {
  background: #409eff;
  color: #fff;
}

.row.ai .bubble {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
}

.info-bar {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.text {
  white-space: pre-wrap;
  word-break: break-word;
}

.confirm-card {
  margin-top: 8px;
  padding: 10px;
  border: 1px dashed #e6a23c;
  border-radius: 8px;
  background: #fdf6ec;
}

.confirm-msg {
  font-size: 14px;
  color: #303133;
  margin-bottom: 8px;
}

.confirm-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.confirm-modify {
  display: flex;
  gap: 8px;
}

.input-bar {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}

.fab-fade-enter-active,
.fab-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fab-fade-enter-from,
.fab-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
