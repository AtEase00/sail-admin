<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
onMounted(() => {
  void router.replace('/dashboard')
})
</script>

<template>
  <p role="status">
    正在进入工作台…
  </p>
</template>

<route lang="yaml">
meta:
  sail: true
  auth: true
</route>
