<script setup lang="ts">
import GhostFibers from './components/GhostFibers.vue'
import LoginForm from './components/login-form.vue'
</script>

<template>
  <div class="login-page relative isolate flex min-h-svh w-full items-center justify-center overflow-hidden bg-white px-4 py-8">
    <!-- 灰色纤维只作为全屏背景，白色表单独立承载登录交互。 -->
    <div class="pointer-events-none absolute inset-0 -z-10" aria-hidden="true">
      <GhostFibers line-color="#737373" glow-color="#a3a3a3" light-mode :speed="0.12" :grain="0.015" :fps="30" />
    </div>
    <main class="w-full max-w-lg rounded-xl border border-neutral-200 bg-white px-6 py-8 sm:px-10 sm:py-10">
      <LoginForm />
      <p class="mt-7 text-center text-xs text-muted-foreground">
        忘记密码？请联系系统管理员
      </p>
    </main>
  </div>
</template>

<style scoped>
/* 登录页固定使用白天灰阶，避免继承工作台暗色主题后出现白底白字。 */
.login-page {
  color-scheme: light;
  --background: white;
  --control-border: #d9d9d9;
  --control-placeholder: #737373;
  --foreground: oklch(0.145 0 0);
  --primary-foreground: #ffffff;
  --muted-foreground: oklch(0.45 0 0);
  --input: oklch(0.85 0 0);
  --destructive: oklch(0.577 0.245 27.325);
  color: var(--foreground);
}
</style>
