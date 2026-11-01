import 'vue-router'

export {}
declare module 'vue-router' {
  interface RouteMeta { auth?: boolean, sail?: boolean, admin?: boolean, permission?: string, title?: string }
}
