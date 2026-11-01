import z from 'zod'
/** 空服务地址表示同源代理，显式地址必须是 HTTP 或 HTTPS。 */
export const EnvSchema = z.object({
  VITE_SERVER_API_URL: z
    .union([z.literal(''), z.url().refine(value => /^https?:/.test(value))])
    .default(''),
  VITE_SERVER_API_PREFIX: z.string().default('/api'),
  VITE_SERVER_API_TIMEOUT: z.coerce.number().positive().default(15000),
})
export type env = z.infer<typeof EnvSchema>
