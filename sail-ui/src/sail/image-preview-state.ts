import { ref } from 'vue'

/** 图片预览位于弹窗之上，打开期间让底层 Reka 弹窗暂停焦点和指针锁。 */
export const imagePreviewCount = ref(0)
