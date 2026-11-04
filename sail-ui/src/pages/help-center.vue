<script setup lang="ts">
interface ISocialMediaAccount {
  title: string
  url: string
}

const socialMediaAccounts = ref<ISocialMediaAccount[]>([
  { title: 'GitHub', url: 'https://github.com/Whbbit1999/shadcn-vue-admin' },
  { title: 'Discord', url: '' },
  { title: 'Bilibili', url: 'https://space.bilibili.com/104376935' },
])
</script>

<template>
  <section>
    <ul class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <li v-for="account in socialMediaAccounts" :key="account.title">
        <a :href="account.url || '#'" target="_blank" rel="noopener noreferrer" class="block p-4 border rounded-lg shadow-sm bg-background border-border">
          <h2 class="mb-2 text-lg font-bold">
            {{ account.title }}
          </h2>
        </a>
      </li>
    </ul>
  </section>
</template>
