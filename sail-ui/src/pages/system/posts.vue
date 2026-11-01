<script setup lang="ts">
import ResourcePage from '@/sail/components/ResourcePage.vue'
</script>

<template>
  <ResourcePage resource="posts" />
</template>

<route lang="yaml">
meta:
  sail: true
  title: 岗位管理
  auth: true
  permission: system:post:query
</route>
