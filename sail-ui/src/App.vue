<script setup lang="ts">
import { toast } from 'vue-sonner'
import { z } from 'zod'

import Loading from '@/components/loading.vue'
import { Toaster } from '@/components/ui/sonner'
import { useSystemTheme } from '@/composables/use-system-theme'
import { installDialogWindows } from '@/lib/dialog-window'
import MessageTheme from '@/sail/components/MessageTheme.vue'
import { envError } from '@/utils/env'

useSystemTheme()
let disposeDialogWindows: (() => void) | undefined
onBeforeUnmount(() => disposeDialogWindows?.())

onMounted(() => {
  // Teleport 弹层和命令式确认框共用窗口交互，应用卸载时统一释放。
  disposeDialogWindows = installDialogWindows()
  if (envError) {
    const flattenError = z.flattenError(envError)
    toast.error('Env error: you should check your .env file', {
      description: h(
        'pre',
        { class: 'mt-2 rounded-md bg-slate-950 p-4 text-wrap' },
        h('code', { class: 'text-white' }, JSON.stringify(flattenError, null, 2)),
      ),
      duration: 10000,
    })
  }
})
</script>

<template>
  <MessageTheme>
    <Toaster />

    <Suspense>
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>

      <template #fallback>
        <Loading />
      </template>
    </Suspense>
  </MessageTheme>
</template>
