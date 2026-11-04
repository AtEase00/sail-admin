<script setup lang="ts">
definePage({
  redirect: to => ({ path: '/messages/history', query: { ...to.query, compose: '1' } }),
})
</script>

<template>
  <span />
</template>
