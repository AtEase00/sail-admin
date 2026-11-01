<script setup lang="ts">
import { ArrowRight } from '@lucide/vue'
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/composables/use-auth'
import SailForm from '@/sail/components/SailForm.vue'

const { login, loading } = useAuth()
const route = useRoute()
const form = reactive({ username: '', password: '' })
const error = ref('')
/** 只提交明确输入的真实凭据，不预填通用密码。 */
async function submit() {
  error.value = ''
  try {
    await login(form)
  }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '登录失败'
  }
}
</script>

<template>
  <section aria-labelledby="login-title" class="flex w-full flex-col gap-7">
    <h1 id="login-title" class="text-2xl font-semibold">
      登录
    </h1>
    <SailForm class="sail-form-vertical space-y-5" @submit.prevent="submit">
      <p v-if="route.query.expired" role="status" class="text-sm text-muted-foreground">
        登录已失效，请重新登录。
      </p>
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
      <p v-if="error" role="alert" class="text-sm text-destructive break-words">
        {{ error }}
      </p>
      <Button class="group h-11 w-full gap-2" type="submit" :disabled="loading">
        {{ loading ? '正在登录…' : '登录' }}
        <ArrowRight v-if="!loading" aria-hidden="true" class="size-4 transition-transform duration-200 group-hover:translate-x-0.5 motion-reduce:transform-none motion-reduce:transition-none" />
      </Button>
    </SailForm>
  </section>
</template>
