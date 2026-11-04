<script setup lang="ts">
import Loading from '@/components/loading.vue'
import { useSystemTheme } from '@/composables/use-system-theme'
import { installDialogWindows } from '@/lib/dialog-window'
import { message } from '@/lib/message'
import GlobalMessage from '@/sail/components/GlobalMessage.vue'
import MessageTheme from '@/sail/components/MessageTheme.vue'
import { envError } from '@/utils/env'

useSystemTheme()
let disposeDialogWindows: (() => void) | undefined
onBeforeUnmount(() => {
  disposeDialogWindows?.()
  message.clear()
})

onMounted(() => {
  // Teleport 弹层和命令式确认框共用窗口交互，应用卸载时统一释放。
  disposeDialogWindows = installDialogWindows()
  if (envError)
    message.error('应用配置异常')
})
</script>

<template>
  <MessageTheme>
    <GlobalMessage />

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
