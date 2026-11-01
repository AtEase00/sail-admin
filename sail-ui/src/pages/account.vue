<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { request } from '@/lib/api-client'
import SailForm from '@/sail/components/SailForm.vue'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '@/sail/password-policy'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const busy = ref(false)
const error = ref('')
const form = reactive({ oldPassword: '', newPassword: '', confirmation: '' })
/** 修改密码后后端会撤销全部会话，前端同步清除当前资料。 */
async function submit() {
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
    auth.reset()
    toast.success('密码已修改，请重新登录')
    await router.replace('/auth/sign-in')
  }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '修改失败'
  }
  finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="max-w-3xl space-y-6 rounded-lg border bg-background p-5">
    <h1 class="text-base font-semibold">
      账号与密码
    </h1>
    <dl class="grid grid-cols-[8rem_1fr] gap-3 rounded-md border p-4 text-sm">
      <dt class="text-muted-foreground">
        登录账号
      </dt>
      <dd>{{ auth.identity?.user.username }}</dd>
      <dt class="text-muted-foreground">
        显示名称
      </dt>
      <dd>{{ auth.identity?.user.nickname }}</dd>
      <dt class="text-muted-foreground">
        当前角色
      </dt>
      <dd>{{ auth.identity?.roles.join('、') || '未分配角色' }}</dd>
    </dl>
    <SailForm class="space-y-4" @submit.prevent="submit">
      <h2 class="font-medium">
        修改登录密码
      </h2>
      <div class="space-y-2">
        <Label for="old-password">当前密码</Label>
        <Input
          id="old-password"
          v-model="form.oldPassword"
          type="password"
          required
          autocomplete="current-password"
        />
      </div>
      <div class="space-y-2">
        <Label for="new-password">新密码</Label>
        <Input
          id="new-password"
          v-model="form.newPassword"
          type="password"
          required
          :minlength="PASSWORD_MIN_LENGTH"
          :maxlength="PASSWORD_MAX_LENGTH"
          autocomplete="new-password"
        />
        <p class="sail-form-help">
          至少 {{ PASSWORD_MIN_LENGTH }} 个字符，UTF-8 不超过 72 字节。
        </p>
      </div>
      <div class="space-y-2">
        <Label for="confirm-password">确认新密码</Label>
        <Input
          id="confirm-password"
          v-model="form.confirmation"
          type="password"
          required
          autocomplete="new-password"
        />
      </div>
      <p v-if="error" role="alert" class="text-sm text-destructive">
        {{ error }}
      </p>
      <Button type="submit" :disabled="busy">
        {{ busy ? '修改中…' : '修改密码并重新登录' }}
      </Button>
    </SailForm>
  </section>
</template>

<route lang="yaml">
meta:
  sail: true
  auth: true
  title: 账号与密码
</route>
