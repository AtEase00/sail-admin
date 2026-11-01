<script setup lang="ts">
import ResourcePage from '@/sail/components/ResourcePage.vue'
</script>

<template>
  <ResourcePage resource="refunds" />
</template>

<route lang="yaml">
meta:
  sail: true
  title: 退款记录
  auth: true
  permission: pay:manage
</route>
