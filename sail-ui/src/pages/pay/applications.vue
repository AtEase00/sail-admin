<script setup lang="ts">
import ResourcePage from '@/sail/components/ResourcePage.vue'
</script>

<template>
  <ResourcePage resource="applications" />
</template>

<route lang="yaml">
meta:
  sail: true
  title: 支付应用
  auth: true
  permission: pay:query
</route>
