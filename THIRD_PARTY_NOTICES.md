# 来源与许可证

登录背景 GhostFibers 基于用户提供的 [Vue Bits](https://vue-bits.dev/) 组件源码适配，Copyright (c) 2025 David Haz。上游许可为 **MIT + Commons Clause License Condition v1.0**，原文保存于 `licenses/vue-bits-LICENSE.md`，不属于普通 MIT：允许在应用中使用，但禁止出售、再许可或再分发组件本身（含组件集合、模板及移植版本）。若将 Sail Admin 作为框架或模板对外分发，需先确认该背景组件的分发授权。ogl 依赖采用其 npm 发布包内的许可。

架构参考：RuoYi Office，master-jdk17，提交 ad56d726368d8c321644d47898a76d3e86f1b548。

上游 MIT 原文保存在 licenses/ruoyi-office-MIT.txt。本工程重新实现已确认的最小业务范围，未导入上游第三方框架覆盖类、社交登录、钱包或业务示例。后续若引入上游源码片段必须继续保留对应版权与许可。

前端来源：[Whbbit1999/shadcn-vue-admin](https://github.com/Whbbit1999/shadcn-vue-admin)，main 分支 ZIP 快照提交 `31a71ec84d8154486f32755a9e2ab761031a242c`，下载日期 2026-10-31。源码位于 `sail-ui`，原 README 保存在 `sail-ui/README.upstream.md`；原始 MIT 许可与 `Copyright (c) 2024 Whbbit1999` 保存在 `sail-ui/LICENSE`，发行时必须随附。

Spring、MyBatis-Plus、Sa-Token、Hutool、AWS SDK、阿里云 OSS SDK、支付宝及微信支付 SDK 等依赖使用各自发布构件的许可证，主项目 MIT 不覆盖这些依赖。发布前应根据最终解析依赖清单核对对应许可与 NOTICE。

文件预览新增 PDF.js（pdfjs-dist，Apache-2.0）、Mammoth（BSD-2-Clause）、ExcelJS（MIT）、Marked（MIT），服务端 Office 文本提取使用 Apache POI（Apache-2.0）。实际版权、许可和 NOTICE 以锁定发布包为准，分发时保留对应文件。

图片交互使用 [vue-easy-lightbox](https://github.com/XiongAmao/vue-easy-lightbox) 1.19.0（MIT），按需加载。图片预览外观参考 Ant Design，缩放、旋转、拖动和触屏手势使用开源引擎；没有使用 Ant Design Vue Image 预览。富文本继续使用已安装的 Tiptap（MIT），未引入第二套编辑器。

菜单动态图标参考 [Lucide Animated](https://lucide-animated.com/) 的交互方式，使用现有 Lucide Vue 图形与本项目 Vue SVG/CSS 实现 6 个动效，没有引入其 React 注册组件或 React 运行时。Lucide 图形继续遵循安装包内的 ISC 及其图标来源许可。
