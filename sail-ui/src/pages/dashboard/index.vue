<script setup lang="ts">
import { ArrowUpRight, Bell, CalendarDays, CheckCheck, RefreshCw, Users } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import type { MessageRecord, PageResult } from '@/services/types/sail'
import type { AuditOverview, OnlineOverview } from '@/services/types/workbench'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import { canAccessPage, navigation } from '@/sail/navigation'
import { resolveMenuIcon } from '@/sail/navigation-icons'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
watch(() => route.query.denied, (denied) => {
  if (denied)
    feedback.warning('没有该页面的访问权限')
}, { immediate: true })
const busy = ref(false)
const messages = ref<MessageRecord[]>([])
const unread = ref<number | null>(null)
const audit = ref<AuditOverview | null>(null)
const online = ref<OnlineOverview | null>(null)
const messageError = useMessageError()
const unreadError = useMessageError()
const auditError = useMessageError()
const onlineError = useMessageError()
const today = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const canAudit = computed(() => auth.can('system:audit:query'))
const shortcuts = computed(() => navigation.filter(item => ['/system/users', '/system/roles', '/files', '/messages/history', '/pay/orders', '/system/audit'].includes(item.path) && canAccessPage(item, auth)))
const totals = computed(() => audit.value?.days.reduce((sum, day) => ({ success: sum.success + day.success, failed: sum.failed + day.failed }), { success: 0, failed: 0 }))
const todayActivity = computed(() => audit.value?.days.at(-1))
const successRate = computed(() => {
  const sum = totals.value
  if (!sum || sum.success + sum.failed === 0)
    return null
  return (sum.success / (sum.success + sum.failed) * 100).toFixed(1)
})
const peak = computed(() => Math.max(1, ...audit.value?.days.map(day => day.success + day.failed) ?? []))
const actionPeak = computed(() => Math.max(1, ...audit.value?.actions.map(item => item.count) ?? []))

/** 区域各自展示读取结果；请求失败不伪装成零值，刷新时清除过期数据。 */
async function load() {
  if (busy.value)
    return
  busy.value = true
  const tasks: Promise<void>[] = []
  tasks.push((async () => {
    unreadError.value = ''
    try {
      unread.value = await request<number>('/messages/unread-count')
    }
    catch (failure) {
      unread.value = null
      unreadError.value = errorMessage(failure, '未读数量读取失败')
    }
  })(), (async () => {
    messageError.value = ''
    try {
      messages.value = (await request<PageResult<MessageRecord>>('/messages/mine', 'GET', undefined, { page: 1, size: 3 })).records
    }
    catch (failure) {
      messages.value = []
      messageError.value = errorMessage(failure, '消息暂不可用')
    }
  })())
  if (canAudit.value) {
    tasks.push((async () => {
      auditError.value = ''
      try {
        audit.value = await request<AuditOverview>('/system/workbench/audit')
      }
      catch (failure) {
        audit.value = null
        auditError.value = errorMessage(failure, '日志统计暂不可用')
      }
    })())
  }
  if (auth.isAdmin) {
    tasks.push((async () => {
      onlineError.value = ''
      try {
        online.value = await request<OnlineOverview>('/system/workbench/online')
      }
      catch (failure) {
        online.value = null
        onlineError.value = errorMessage(failure, '会话数据暂不可用')
      }
    })())
  }
  await Promise.allSettled(tasks)
  busy.value = false
}
/** 服务端时间按业务本地时间直接展示，避免浏览器重复时区换算。 */
function time(value: string) {
  return value.replace('T', ' ').slice(0, 19)
}
/** 日期来自服务端统计自然日，图表保留完整日期作为辅助描述。 */
function dayLabel(value: string) {
  return value.slice(5).replace('-', '/')
}
onMounted(() => {
  void load()
  window.addEventListener('sail:messages-changed', load)
  window.addEventListener('sail:messages-read', load)
})
onBeforeUnmount(() => {
  window.removeEventListener('sail:messages-changed', load)
  window.removeEventListener('sail:messages-read', load)
})
</script>

<template>
  <section class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold">
          你好，{{ auth.identity?.user.nickname }}
        </h1>
        <p class="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground">
          <CalendarDays class="size-3.5" />{{ today }}
        </p>
      </div>
      <Button variant="outline" size="sm" :disabled="busy" @click="load">
        <RefreshCw class="size-3.5" :class="{ 'animate-spin': busy }" />刷新数据
      </Button>
    </div>

    <div class="workbench-summary grid overflow-hidden rounded-lg border bg-background" :style="{ gridTemplateColumns: `repeat(${1 + (auth.isAdmin ? 1 : 0) + (canAudit ? 2 : 0)}, minmax(0, 1fr))` }">
      <div v-if="auth.isAdmin" class="px-5 py-4">
        <p class="text-xs text-muted-foreground">
          在线用户<span class="ml-2 inline-block size-1.5 rounded-full bg-primary" />
        </p>
        <Skeleton v-if="busy && !online" class="mt-2 h-8 w-16" />
        <p v-else class="mt-1 text-2xl font-semibold tabular-nums">
          {{ online?.total ?? '—' }}<span class="ml-2 text-xs font-normal text-muted-foreground">人</span>
        </p>
        <p class="mt-1 text-xs text-muted-foreground">
          {{ onlineError ? '会话数据读取失败' : `有效登录会话${online ? ` ${online.sessionTotal} 个` : ''}` }}
        </p>
      </div>
      <div v-if="canAudit" class="px-5 py-4">
        <p class="text-xs text-muted-foreground">
          今日操作
        </p><Skeleton v-if="busy && !audit" class="mt-2 h-8 w-16" />
        <p v-else class="mt-1 text-2xl font-semibold tabular-nums">
          {{ todayActivity ? todayActivity.success + todayActivity.failed : '—' }}<span class="ml-2 text-xs font-normal text-muted-foreground">次</span>
        </p>
        <p class="mt-1 text-xs text-muted-foreground">
          {{ auditError ? '日志数据读取失败' : `失败 ${todayActivity?.failed ?? '—'} 次，登录失败 ${todayActivity?.loginFailures ?? '—'} 次` }}
        </p>
      </div>
      <div v-if="canAudit" class="px-5 py-4">
        <p class="text-xs text-muted-foreground">
          近 7 天操作成功率
        </p><Skeleton v-if="busy && !audit" class="mt-2 h-8 w-16" />
        <p v-else class="mt-1 text-2xl font-semibold tabular-nums">
          {{ successRate ?? '—' }}<span v-if="successRate !== null" class="ml-1 text-sm font-normal text-muted-foreground">%</span>
        </p>
        <p class="mt-1 text-xs text-muted-foreground">
          {{ auditError ? '日志数据读取失败' : totals ? `共 ${totals.success + totals.failed} 次操作` : '暂无统计' }}
        </p>
      </div>
      <RouterLink to="/messages" class="group px-5 py-4 transition-colors hover:bg-muted/40">
        <p class="flex items-center justify-between text-xs text-muted-foreground">
          未读消息<Bell class="size-3.5" />
        </p><Skeleton v-if="busy && unread === null" class="mt-2 h-8 w-16" />
        <p v-else class="mt-1 text-2xl font-semibold tabular-nums">
          {{ unread ?? '—' }}<span class="ml-2 text-xs font-normal text-muted-foreground">条</span>
        </p>
        <p class="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
          {{ unreadError ? '消息数量读取失败' : '查看我的消息' }}<ArrowUpRight class="size-3" />
        </p>
      </RouterLink>
    </div>

    <div class="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_20rem]">
      <div class="min-w-0 space-y-4">
        <section v-if="canAudit" class="rounded-lg border bg-background">
          <div class="flex flex-wrap items-center justify-between gap-2 border-b px-5 py-3.5">
            <h2 class="text-sm font-semibold">
              日志分析
            </h2><span class="text-xs text-muted-foreground">最近 7 天 · {{ audit ? `${dayLabel(audit.from.slice(0, 10))} 至 ${dayLabel(audit.to.slice(0, 10))}` : '按自然日统计' }}</span>
          </div>
          <div v-if="busy && !audit" class="space-y-4 p-5">
            <Skeleton class="h-44 w-full" /><Skeleton class="h-20 w-full" />
          </div>

          <div v-else-if="audit" class="p-5">
            <div class="mb-4 flex flex-wrap items-center justify-between gap-2 text-xs">
              <span class="text-muted-foreground">操作次数<span class="ml-2 tabular-nums">峰值 {{ peak === 1 && !totals?.success && !totals?.failed ? 0 : peak }}</span></span>
              <div class="flex items-center gap-4">
                <span class="flex items-center gap-1.5"><i class="size-2 rounded-sm bg-primary" />成功</span><span class="flex items-center gap-1.5"><i class="size-2 rounded-sm bg-destructive" />失败</span>
              </div>
            </div>
            <figure aria-label="最近七日操作成功与失败次数">
              <div class="relative grid h-48 grid-cols-7 gap-3 border-b px-2 sm:gap-6">
                <div aria-hidden="true" class="pointer-events-none absolute inset-0 flex flex-col justify-between">
                  <div v-for="n in 4" :key="n" class="border-t border-dashed border-border/60" />
                </div>
                <div v-for="day in audit.days" :key="day.date" class="relative flex min-w-0 flex-col items-center justify-end" :title="`${day.date}：成功 ${day.success} 次，失败 ${day.failed} 次`" :aria-label="`${day.date}：成功 ${day.success} 次，失败 ${day.failed} 次`">
                  <span class="mb-1.5 text-xs tabular-nums text-muted-foreground">{{ day.success + day.failed }}</span>
                  <div class="flex w-full max-w-10 flex-col overflow-hidden rounded-t-sm" :style="{ height: `${(day.success + day.failed) / peak * 148}px` }">
                    <div class="bg-destructive" :style="{ flexGrow: day.failed }" /><div class="bg-primary" :style="{ flexGrow: day.success }" />
                  </div>
                </div>
              </div>
              <figcaption class="mt-2 grid grid-cols-7 gap-3 px-2 text-center text-xs tabular-nums text-muted-foreground sm:gap-6">
                <span v-for="day in audit.days" :key="day.date">{{ dayLabel(day.date) }}</span>
              </figcaption>
            </figure>
            <div class="mt-5 border-t pt-4">
              <h3 class="mb-3 text-xs font-medium">
                操作分布<span class="ml-2 font-normal text-muted-foreground">最近 7 天，按次数排序</span>
              </h3>
              <div class="grid gap-x-6 gap-y-3 sm:grid-cols-2">
                <div v-for="item in audit.actions" :key="item.action" class="min-w-0">
                  <div class="mb-1.5 flex items-center justify-between gap-2 text-xs">
                    <span class="truncate">{{ item.action }}</span><span class="shrink-0 tabular-nums text-muted-foreground">{{ item.count }} 次</span>
                  </div><div class="h-1 overflow-hidden rounded-full bg-muted">
                    <div class="h-full rounded-full bg-primary/65" :style="{ width: `${item.count / actionPeak * 100}%` }" />
                  </div>
                </div>
              </div>
              <p v-if="!audit.actions.length" class="py-3 text-sm text-muted-foreground">
                最近 7 天暂无操作记录。
              </p>
            </div>
          </div>
        </section>

        <section v-if="canAudit" class="overflow-hidden rounded-lg border bg-background">
          <div class="flex items-center justify-between border-b px-5 py-3">
            <h2 class="text-sm font-semibold">
              操作日志
            </h2><Button variant="ghost" size="sm" as-child>
              <RouterLink to="/system/audit">
                查看全部<ArrowUpRight class="size-3.5" />
              </RouterLink>
            </Button>
          </div>
          <div v-if="busy && !audit" class="space-y-3 p-5">
            <Skeleton v-for="n in 4" :key="n" class="h-8 w-full" />
          </div>

          <div v-else class="overflow-x-auto">
            <table class="w-full text-left text-sm">
              <caption class="sr-only">
                最近七日最新的八条操作日志
              </caption><thead class="bg-muted/40 text-xs text-muted-foreground">
                <tr>
                  <th class="px-5 py-2.5 font-normal">
                    操作内容
                  </th><th class="px-3 py-2.5 font-normal">
                    操作人
                  </th><th class="px-3 py-2.5 font-normal">
                    结果
                  </th><th class="px-5 py-2.5 text-right font-normal">
                    操作时间
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y">
                <tr v-for="item in audit?.recent" :key="item.id" class="hover:bg-muted/25">
                  <td class="max-w-64 truncate px-5 py-3" :title="item.action">
                    {{ item.action }}
                  </td><td class="px-3 py-3">
                    <span class="block whitespace-nowrap text-xs">{{ item.nickname ?? (item.userId === null ? '未关联账号' : `账号 #${item.userId}`) }}</span><span v-if="item.username" class="text-xs text-muted-foreground">{{ item.username }}</span>
                  </td><td class="whitespace-nowrap px-3 py-3">
                    <Badge :variant="item.success ? 'secondary' : 'destructive'">
                      {{ item.success ? '成功' : '失败' }}
                    </Badge>
                  </td><td class="whitespace-nowrap px-5 py-3 text-right text-xs tabular-nums text-muted-foreground">
                    {{ time(item.createdAt) }}
                  </td>
                </tr>
              </tbody>
            </table><p v-if="!audit?.recent.length" class="p-8 text-center text-sm text-muted-foreground">
              最近 7 天暂无操作记录。
            </p>
          </div>
        </section>
        <section class="rounded-lg border bg-background">
          <h2 class="border-b px-5 py-3.5 text-sm font-semibold">
            常用操作
          </h2>
          <div class="grid sm:grid-cols-2 lg:grid-cols-3">
            <RouterLink v-for="item in shortcuts" :key="item.path" :to="item.path" class="group flex items-center gap-2.5 px-5 py-4 text-sm hover:bg-muted/40">
              <component :is="resolveMenuIcon(auth.menus.find(menu => menu.path === item.path)?.icon)" class="size-4 text-muted-foreground" /><span class="flex-1">{{ item.title }}</span><ArrowUpRight class="size-3.5 text-muted-foreground" />
            </RouterLink>
          </div>
          <p v-if="!shortcuts.length" class="p-5 text-sm text-muted-foreground">
            暂无可用业务入口，请联系管理员分配角色。
          </p>
        </section>
      </div>
      <aside class="min-w-0 space-y-4">
        <section v-if="auth.isAdmin" class="rounded-lg border bg-background">
          <div class="flex items-center justify-between border-b px-4 py-3.5">
            <h2 class="text-sm font-semibold">
              在线用户
            </h2><span class="flex items-center gap-1.5 text-xs text-muted-foreground"><Users class="size-3.5" />{{ online?.total ?? '—' }} 人</span>
          </div>
          <div v-if="busy && !online" class="space-y-4 p-4">
            <Skeleton v-for="n in 3" :key="n" class="h-10 w-full" />
          </div>

          <ul v-else class="divide-y">
            <li v-for="person in online?.records" :key="person.id" class="flex items-center gap-3 px-4 py-3">
              <span class="flex size-8 shrink-0 items-center justify-center rounded-md bg-muted text-xs font-medium">{{ person.nickname.slice(0, 1) }}</span><div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <span class="truncate text-sm font-medium">{{ person.nickname }}</span><span v-if="person.id === auth.identity?.user.id" class="shrink-0 text-xs text-muted-foreground">我</span>
                </div><p class="truncate text-xs text-muted-foreground">
                  {{ person.username }} · {{ person.sessions }} 个会话
                </p><time class="text-xs tabular-nums text-muted-foreground">{{ time(person.latestLoginAt) }} 登录</time>
              </div><span class="size-1.5 shrink-0 rounded-full bg-primary" />
            </li>
          </ul>
          <p v-if="online && !online.records.length" class="p-5 text-sm text-muted-foreground">
            暂无有效登录会话。
          </p>
        </section>
        <section class="rounded-lg border bg-background">
          <div class="flex items-center justify-between border-b px-4 py-3">
            <h2 class="text-sm font-semibold">
              我的消息
            </h2><Button as-child variant="ghost" size="sm">
              <RouterLink to="/messages">
                全部<ArrowUpRight class="size-3.5" />
              </RouterLink>
            </Button>
          </div>
          <div v-if="busy && !messages.length" class="space-y-3 p-4">
            <Skeleton v-for="n in 2" :key="n" class="h-12 w-full" />
          </div>
          <div v-else class="divide-y">
            <RouterLink v-for="message in messages" :key="message.id" to="/messages" class="block px-4 py-3.5 hover:bg-muted/40">
              <div class="flex items-start gap-2">
                <span class="mt-1.5 size-1.5 shrink-0 rounded-full" :class="message.readFlag ? 'bg-muted-foreground/40' : 'bg-primary'" /><p class="truncate text-sm font-medium">
                  {{ message.title }}
                </p>
              </div><p class="mt-1 line-clamp-2 pl-3.5 text-xs leading-5 text-muted-foreground">
                {{ message.content }}
              </p><p class="mt-1.5 pl-3.5 text-xs text-muted-foreground">
                {{ time(message.createdAt) }}
              </p>
            </RouterLink>
            <div v-if="!messages.length" class="px-5 py-8 text-center">
              <CheckCheck class="mx-auto mb-2 size-6 text-muted-foreground" /><p class="text-sm">
                {{ messageError ? '消息读取失败' : '暂无消息' }}
              </p>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.workbench-summary > * + * {
  border-left: 1px solid var(--border);
}
@media (max-width: 639px) {
  .workbench-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  }
  .workbench-summary > :nth-child(odd) {
    border-left: 0;
  }
  .workbench-summary > :nth-child(n + 3) {
    border-top: 1px solid var(--border);
  }
}
</style>

<route lang="yaml">
meta:
  sail: true
  auth: true
  title: 工作台
</route>
