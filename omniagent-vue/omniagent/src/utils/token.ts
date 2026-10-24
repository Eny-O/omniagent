// JWT 持久化存储：登录后写入 localStorage，请求拦截器读取，登出清除。
// 改用 localStorage（而非纯内存）的原因：刷新页面不会丢失登录态，
// 否则内存变量清空后聊天带不上 Bearer，Agent 返回"缺少身份凭证"。
// 代价：XSS 可窃取 token（MVP 可接受；若需更强安全可后续加 httpOnly Cookie + 短过期）。

const TOKEN_KEY = 'omniagent_token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 隐私模式 / 存储不可用时静默忽略
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 忽略
  }
}
