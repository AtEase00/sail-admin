import type { App } from 'vue'

import { MutationCache, QueryCache, QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { FetchError } from 'ofetch'

import { message } from '@/lib/message'

function handleError(error: Error) {
  if (error instanceof FetchError && error.status === 401)
    return

  message.error(error, '请求失败')
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: handleError }),
  mutationCache: new MutationCache({ onError: handleError }),
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
    },
  },
})

export function setupTanstackVueQuery(app: App) {
  app.use(VueQueryPlugin, {
    queryClient,
  })
}
