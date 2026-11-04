<script setup lang="ts">
import { Mail, ShieldCheck, UserRound } from '@lucide/vue'
import { computed, onActivated, onDeactivated, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import type { ProfileInfo, UserInfo } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import SailForm from '@/sail/components/SailForm.vue'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '@/sail/password-policy'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const profile = ref<ProfileInfo | null>(null)
const nickname = ref('')
const loading = ref(true)
const saving = ref(false)
const profileError = useMessageError()
const busy = ref(false)
const error = useMessageError()
const form = reactive({ oldPassword: '', newPassword: '', confirmation: '' })
const changed = computed(() => nickname.value.trim() !== auth.identity?.user.nickname)
const joinedAt = computed(() => profile.value?.createdAt?.replace('T', ' ').slice(0, 16) || '—')

async function load() {
  const session = auth.token
  loading.value = true
  profileError.value = ''
  try {
    const result = await request<ProfileInfo>('/system/auth/profile')
    if (session !== auth.token)
      return
    profile.value = result
    nickname.value = result.user.nickname
    if (auth.identity)
      auth.identity.user = result.user
  }
  catch (failure) {
    profileError.value = errorMessage(failure, '个人资料加载失败')
  }
  finally {
    loading.value = false
  }
}
async function saveProfile() {
  if (saving.value || !changed.value || !nickname.value.trim())
    return
  const session = auth.token
  saving.value = true
  profileError.value = ''
  try {
    const user = await request<UserInfo>('/system/auth/profile', 'PUT', { nickname: nickname.value.trim() })
    if (session !== auth.token)
      return
    if (auth.identity)
      auth.identity.user = user
    if (profile.value)
      profile.value.user = user
    nickname.value = user.nickname
    feedback.success('个人资料已保存')
  }
  catch (failure) {
    profileError.value = errorMessage(failure, '保存失败，请重试')
  }
  finally {
    saving.value = false
  }
}
function clearPasswords() {
  form.oldPassword = form.newPassword = form.confirmation = ''
  error.value = ''
}
/** 修改密码后后端会撤销全部会话，前端同步清除当前资料。 */
async function submit() {
  if (busy.value)
    return
  error.value = ''
  if (form.newPassword !== form.confirmation) {
    error.value = '两次新密码不一致'
    return
  }
  busy.value = true
  try {
    await request('/system/auth/password', 'PUT', {
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    clearPasswords()
    auth.reset()
    feedback.success('密码已修改，请重新登录')
    await router.replace('/auth/sign-in')
  }
  catch (failure) {
    error.value = errorMessage(failure, '修改失败')
  }
  finally {
    busy.value = false
  }
}
onActivated(() => {
  // 返回缓存页面时保留尚未提交的昵称；密码离开页面即清空。
  if (!profile.value)
    void load()
})
onDeactivated(clearPasswords)
</script>

<template>
  <section class="mx-auto max-w-6xl space-y-5">
    <div class="grid items-start gap-5 lg:grid-cols-[18rem_minmax(0,1fr)]">
      <aside class="overflow-hidden rounded-xl border bg-background">
        <div class="flex flex-col items-center border-b bg-muted/30 px-5 py-7 text-center">
          <div class="mb-3 flex size-16 items-center justify-center rounded-2xl bg-primary/10 text-2xl font-semibold text-primary">
            {{ auth.identity?.user.nickname.slice(0, 1) || '我' }}
          </div>
          <h2 class="max-w-full truncate font-semibold">
            {{ auth.identity?.user.nickname }}
          </h2>
          <p class="mt-1 max-w-full truncate text-sm text-muted-foreground">
            @{{ auth.identity?.user.username }}
          </p>
          <span class="mt-3 rounded-full bg-muted px-2.5 py-1 text-xs">{{ auth.identity?.user.status === 1 ? '账号正常' : '账号已停用' }}</span>
        </div>
        <dl class="space-y-4 p-5 text-sm">
          <div>
            <dt class="mb-1 text-xs text-muted-foreground">
              所属部门
            </dt>
            <dd>{{ loading ? '加载中…' : profile ? (profile.department || '未分配部门') : '暂不可用' }}</dd>
          </div>
          <div>
            <dt class="mb-1 text-xs text-muted-foreground">
              当前岗位
            </dt>
            <dd>{{ loading ? '加载中…' : profile ? (profile.posts.join('、') || '未分配岗位') : '暂不可用' }}</dd>
          </div>
          <div>
            <dt class="mb-2 text-xs text-muted-foreground">
              当前角色
            </dt>
            <dd class="flex flex-wrap gap-1.5">
              <span v-for="role in auth.identity?.roles" :key="role" class="break-all rounded-md border px-2 py-0.5 text-xs">{{ role === 'SUPER_ADMIN' ? '超级管理员' : role }}</span>
              <span v-if="!auth.identity?.roles.length">未分配角色</span>
            </dd>
          </div>
          <div>
            <dt class="mb-1 text-xs text-muted-foreground">
              加入时间
            </dt>
            <dd>{{ joinedAt }}</dd>
          </div>
        </dl>
        <div class="border-t p-3">
          <Button as-child variant="ghost" class="w-full justify-start">
            <RouterLink to="/messages">
              <Mail class="size-4" />我的消息
            </RouterLink>
          </Button>
        </div>
      </aside>
      <div class="min-w-0 space-y-5">
        <section class="rounded-xl border bg-background p-5 sm:p-6" aria-labelledby="profile-title">
          <h2 id="profile-title" class="flex items-center gap-2 font-semibold">
            <UserRound class="size-4 text-muted-foreground" />基本资料
          </h2>

          <SailForm class="mt-5 space-y-4" @submit.prevent="saveProfile">
            <div class="space-y-2">
              <Label for="profile-username">登录账号</Label>
              <Input id="profile-username" :model-value="auth.identity?.user.username" readonly class="bg-muted/40" />
            </div>
            <div class="space-y-2">
              <Label for="profile-nickname">昵称</Label>
              <Input id="profile-nickname" v-model="nickname" required :maxlength="64" :disabled="loading || saving || !profile" autocomplete="nickname" />
            </div>

            <Button v-if="!profile && !loading" type="button" variant="outline" @click="load">
              重新加载
            </Button>
            <Button type="submit" :disabled="loading || saving || !profile || !changed || !nickname.trim()">
              {{ saving ? '保存中…' : '保存资料' }}
            </Button>
          </SailForm>
        </section>
        <section class="rounded-xl border bg-background p-5 sm:p-6" aria-labelledby="security-title">
          <h2 id="security-title" class="flex items-center gap-2 font-semibold">
            <ShieldCheck class="size-4 text-muted-foreground" />账号安全
          </h2>

          <SailForm class="mt-5 space-y-4" @submit.prevent="submit">
            <div class="space-y-2">
              <Label for="old-password">当前密码</Label>
              <Input id="old-password" v-model="form.oldPassword" type="password" required autocomplete="current-password" :disabled="busy" />
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-2">
                <Label for="new-password">新密码</Label>
                <Input id="new-password" v-model="form.newPassword" type="password" required :minlength="PASSWORD_MIN_LENGTH" :maxlength="PASSWORD_MAX_LENGTH" autocomplete="new-password" :disabled="busy" />
              </div>
              <div class="space-y-2">
                <Label for="confirm-password">确认新密码</Label>
                <Input id="confirm-password" v-model="form.confirmation" type="password" required :maxlength="PASSWORD_MAX_LENGTH" autocomplete="new-password" :disabled="busy" />
              </div>
            </div>

            <Button type="submit" variant="outline" :disabled="busy">
              {{ busy ? '修改中…' : '修改密码并重新登录' }}
            </Button>
          </SailForm>
        </section>
      </div>
    </div>
  </section>
</template>

<route lang="yaml">
meta:
  sail: true
  auth: true
  title: 个人中心
</route>
