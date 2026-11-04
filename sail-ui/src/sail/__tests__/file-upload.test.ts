// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'

import { message, messageItems } from '@/lib/message'

import FileUpload from '../components/FileUpload.vue'

afterEach(() => vi.unstubAllGlobals())

/** 同批选择必须在父组件回传 props 前完整累积，不能丢失前面的文件。 */
describe('批量文件选择', () => {
  it('连续选择图片和文档保留全部文件，并遵守数量限制', async () => {
    message.clear()
    const getRandomValues = crypto.getRandomValues.bind(crypto)
    vi.stubGlobal('crypto', { getRandomValues })
    const dragger = defineComponent({ name: 'AUploadDragger', props: ['beforeUpload', 'fileList'], template: '<div />' })
    const wrapper = mount(FileUpload, { props: { 'maxCount': 3, 'modelValue': [], 'onUpdate:modelValue': value => wrapper.setProps({ modelValue: value }) }, global: { stubs: { AUploadDragger: dragger, FilePreview: true } } })
    const select = wrapper.findComponent(dragger).props('beforeUpload')
    const files = ['a.png', 'b.pdf', 'c.docx', 'd.md'].map(name => new File(['验收内容'], name))
    files.forEach(file => select(file, files))
    await nextTick()
    expect(wrapper.props('modelValue').map(file => file.name)).toEqual(['a.png', 'b.pdf', 'c.docx'])
    const uids = wrapper.findComponent(dragger).props('fileList').map(file => file.uid)
    expect(new Set(uids).size).toBe(3)
    expect(messageItems.value.at(-1)?.content).toBe('最多选择 3 个文件')
    wrapper.unmount()
  })
})
