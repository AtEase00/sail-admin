<script setup lang="ts">
import ResourcePage from '@/sail/components/ResourcePage.vue'
</script>

<template>
  <ResourcePage resource="audit" />
</template>

<route lang="yaml">
meta:
  sail: true
  title: 操作审计
  auth: true
  permission: system:audit:query
</route>
