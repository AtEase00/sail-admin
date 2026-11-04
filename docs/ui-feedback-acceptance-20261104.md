# 页面文案与全局 Message 验收

日期：2026-11-04

- 已检查业务页面及共用表单、弹窗，清理功能自述、教学性说明及技术机制提示。保留字段、真实数据、状态和必要的操作确认。
- 规则写入 `sail-ui/.agents/skills/shadcn-vue-admin/SKILL.md`，由 `sail-ui/AGENTS.md` 要求后续开发遵守。
- 操作反馈统一使用 `src/lib/message.ts` 和唯一的 `GlobalMessage.vue`，支持成功、错误、警告、信息；顶部居中，向下进入、向上退出。
- 表单校验复用 `SailForm`，页面错误状态复用 `useMessageError`；移除 Sonner 依赖和局部接口错误正文。追踪号保留在诊断数据中，不拼接到用户反馈。
- 修复携带旧会话登录失败时同时弹出会话过期提示的问题。

## 验证结果

- 前端：25 个测试文件、106 项测试通过；lint 和生产构建通过。构建仍有既有的大包体积警告。
- 后端：40 项集成测试通过，打包成功。
- 浏览器：检查个人中心、系统管理、定时任务与执行记录、文件、消息、支付等业务路由，以及任务和角色表单。
- 实际错误登录仅出现“账号或密码错误”，无内联错误、追踪号或重复过期提示。
- 实际空表单提交通过顶部警告反馈并聚焦首个无效字段。
- 检查桌面与 390px 移动端弹窗；消息位于弹窗上方且可访问，无水平溢出。检查四类消息及自动消失，退出动画实测向上移动后移除。

## 截图

- `output/playwright/global-message-login-error.png`：真实失败登录。
- `output/playwright/clean-job-form-validation.png`：真实表单校验。
- `output/playwright/global-message-types-desktop.png`：调用公共 Message API 展示不同消息类型。
- `output/playwright/global-message-modal-mobile.png`：移动端弹窗上方的全局反馈。
- `output/playwright/clean-role-permissions.png`、`clean-role-form.png`：清理后的角色表单。

本地前后端已更新并保持运行；未部署生产环境。
