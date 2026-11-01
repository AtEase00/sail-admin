# Sail Admin UI

基于 [shadcn-vue-admin](https://github.com/Whbbit1999/shadcn-vue-admin) 的 Sail Admin 管理前端，源快照 `31a71ec84d8154486f32755a9e2ab761031a242c`。原项目说明见 [README.upstream.md](README.upstream.md)，MIT 许可见 [LICENSE](LICENSE)。

已编写真实接口对接：登录与权限、RBAC/组织/字典/参数/审计、文件与多实例存储、消息收发、微信与支付宝扫码订单/查单/关单/整单退款。入口为 `/auth/sign-in`，管理入口为 `/dashboard`。

环境要求 Node.js >=22.15、pnpm 11.28.6；复制 `.env.example` 为 `.env.local`。默认请求同源 `/api`，开发代理后端 `http://127.0.0.1:8080`，消息使用 `/ws/messages`。完整说明与人工操作命令见 [前端接入](../docs/frontend-integration.md)。

上游演示路由、演示数据和旧导航实现已清理，保留 Sail 业务页面、公共组件及 MIT 许可。弹窗统一支持标题拖动、双击标题全屏、按钮全屏/还原；遮罩点击不会丢失未保存内容。当前构建、测试和浏览器验收结果见 [弹窗与清理验收](../docs/dialog-window-acceptance-20261101.md)。

Git 提交和推送不自动运行前端检查，也不在安装依赖时注册 Git 钩子。需要检查时，在 `sail-ui` 手动执行 `pnpm lint` 或 `pnpm build`。
