<script setup lang="ts">
import ResourcePage from '@/sail/components/ResourcePage.vue'
</script>

<template>
  <ResourcePage resource="notifications" />
</template>

<route lang="yaml">
meta:
  sail: true
  title: 支付业务通知
  auth: true
  permission: pay:manage
</route>
