<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
// 原存储入口保留跳转，历史收藏进入系统配置中的同一套真实资料。
onMounted(() => {
  void router.replace('/system/configs?tab=storage')
})
</script>

<template>
  <p class="p-5 text-sm text-muted-foreground">
    正在打开文件存储配置…
  </p>
</template>

<route lang="yaml">
meta:
  sail: true
  title: 文件存储
  auth: true
  admin: true
</route>
