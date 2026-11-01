/** 在普通 HTTP 页面也可用的加密随机 UUID v4。 */
export function createBrowserUuid(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  // 按 RFC 4122 设置版本和变体位，保留其余随机字节。
  bytes[6] = (bytes[6]! & 0x0F) | 0x40
  bytes[8] = (bytes[8]! & 0x3F) | 0x80
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
