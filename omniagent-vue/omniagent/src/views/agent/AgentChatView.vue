<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { streamChat } from '@/api/agent'

const userStore = useUserStore()

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
        userId: userStore.user?.id ?? 0,
        actingRole: userStore.currentRole ?? '',
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
          ai.text = `⚠️ 出错了：${e instanceof Error ? e.message : String(e)}`
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
  <div class="chat-page">
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
        placeholder="输入消息，回车发送（Enter 发送）"
        :disabled="busy"
        @keyup.enter="send"
      />
      <el-button type="primary" :disabled="busy" @click="send">发送</el-button>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 60vh;
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
  max-width: 72%;
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
  padding: 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}
</style>
