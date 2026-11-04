<script setup lang="ts">
import { ArrowRight } from '@lucide/vue'
import { reactive } from 'vue'
import { useRoute } from 'vue-router'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/composables/use-auth'
import { useMessageError } from '@/composables/use-message-error'
import { errorMessage, message } from '@/lib/message'
import SailForm from '@/sail/components/SailForm.vue'

const { login, loading } = useAuth()
const route = useRoute()
watch(() => route.query.expired, (expired) => {
  if (expired)
    message.warning('登录已失效，请重新登录')
}, { immediate: true })
const form = reactive({ username: '', password: '' })
const error = useMessageError()
/** 只提交明确输入的真实凭据，不预填通用密码。 */
async function submit() {
  error.value = ''
  try {
    await login(form)
  }
  catch (failure) {
    error.value = errorMessage(failure, '登录失败')
  }
}
</script>

<template>
  <section aria-labelledby="login-title" class="flex w-full flex-col gap-7">
    <h1 id="login-title" class="text-2xl font-semibold">
      登录
    </h1>
    <SailForm class="sail-form-vertical space-y-5" @submit.prevent="submit">
      <div class="space-y-2">
        <Label for="username">账号</Label>
        <Input
          id="username"
          v-model="form.username"
          class="h-11 bg-white dark:bg-white"
          required
          maxlength="64"
          autocomplete="username"
        />
      </div>
      <div class="space-y-2">
        <Label for="password">密码</Label>
        <Input
          id="password"
          v-model="form.password"
          class="h-11 bg-white dark:bg-white"
          required
          type="password"
          autocomplete="current-password"
        />
      </div>

      <Button class="group h-11 w-full gap-2" type="submit" :disabled="loading">
        {{ loading ? '正在登录…' : '登录' }}
        <ArrowRight v-if="!loading" aria-hidden="true" class="size-4 transition-transform duration-200 group-hover:translate-x-0.5 motion-reduce:transform-none motion-reduce:transition-none" />
      </Button>
    </SailForm>
  </section>
</template>
