<script setup lang="ts">
import { ChevronDown, KeyRound, LogOut, Mail, UserRound } from '@lucide/vue'
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'

import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useAuthStore } from '@/stores/auth'

defineProps<{ unread: number, connection: string }>()
const auth = useAuthStore()
const router = useRouter()
const open = ref(false)
const leaving = ref(false)
let closeTimer: ReturnType<typeof setTimeout> | undefined
/** 悬停即展开；保留组件原生点击、触屏与键盘操作。 */
function show() {
  clearTimeout(closeTimer)
  open.value = true
}
/** 短暂延迟允许指针跨过触发器与弹层间隙。 */
function hide() {
  clearTimeout(closeTimer)
  closeTimer = setTimeout(() => {
    open.value = false
  }, 150)
}
/** 后端撤销会话成功后才离开当前工作台。 */
async function logout() {
  if (leaving.value)
    return
  leaving.value = true
  try {
    await auth.signOut()
    await router.replace('/auth/sign-in')
  }
  catch (failure) {
    toast.error(failure instanceof Error ? failure.message : '退出失败')
  }
  finally {
    leaving.value = false
  }
}
onBeforeUnmount(() => {
  clearTimeout(closeTimer)
})
</script>

<template>
  <DropdownMenu v-model:open="open" :modal="false">
    <DropdownMenuTrigger as-child>
      <button class="flex h-9 items-center gap-2 rounded-md px-1.5 text-left hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring" aria-label="个人菜单" @pointerenter="show" @pointerleave="hide">
        <span class="relative flex size-8 shrink-0 items-center justify-center rounded-md bg-primary text-sm font-semibold text-primary-foreground">{{ auth.identity?.user.nickname.slice(0, 1) }}<span v-if="unread" class="absolute -top-0.5 -right-0.5 size-2 rounded-full bg-destructive ring-2 ring-background" /></span>
        <span class="hidden max-w-32 min-w-0 sm:block">
          <span class="block truncate text-sm font-medium">{{ auth.identity?.user.nickname }}</span>
        </span>
        <ChevronDown class="hidden size-3.5 text-muted-foreground sm:block" />
      </button>
    </DropdownMenuTrigger>
    <DropdownMenuContent side="bottom" align="end" :side-offset="6" class="w-44 min-w-44" @pointerenter="show" @pointerleave="hide">
      <DropdownMenuLabel class="flex items-center gap-2">
        <UserRound class="size-4" /><span class="min-w-0"><span class="block truncate">{{ auth.identity?.user.nickname }}</span><span class="block truncate text-xs font-normal text-muted-foreground">{{ auth.identity?.user.username }}</span></span>
      </DropdownMenuLabel>
      <DropdownMenuSeparator />
      <DropdownMenuItem as-child>
        <RouterLink to="/messages">
          <Mail class="size-4" />我的消息<span v-if="unread" class="ml-auto rounded bg-muted px-1.5 text-xs">{{ unread }}</span>
        </RouterLink>
      </DropdownMenuItem>
      <DropdownMenuItem as-child>
        <RouterLink to="/account">
          <KeyRound class="size-4" />修改密码
        </RouterLink>
      </DropdownMenuItem>
      <DropdownMenuSeparator />
      <p class="px-2 py-1.5 text-xs text-muted-foreground">
        {{ connection }}
      </p>
      <DropdownMenuItem :disabled="leaving" class="text-destructive" @select="logout">
        <LogOut class="size-4" />{{ leaving ? '正在退出…' : '退出登录' }}
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
