/** 会话令牌只保存在当前浏览器标签会话，不写日志或长期 URL。 */
const TOKEN_KEY = 'sail-admin-token'
export const readToken = () => sessionStorage.getItem(TOKEN_KEY) ?? ''
export function writeToken(value: string) {
  // 空值明确撤销当前本地会话。
  if (value)
    sessionStorage.setItem(TOKEN_KEY, value)
  else sessionStorage.removeItem(TOKEN_KEY)
}
