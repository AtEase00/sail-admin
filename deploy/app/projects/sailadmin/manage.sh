#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

PROJECTS=/app/projects
VOLUMES=/app/volumes
BACKUPS=/app/backups/sailadmin
ENV_FILE="$PROJECTS/.env"
COMPOSE_FILE="$PROJECTS/compose.yaml"
APP_DIR="$PROJECTS/sailadmin"
JAR_FILE="$APP_DIR/sail-server-1.0.0.jar"
DIST_DIR="$APP_DIR/dist"
MYSQL_CONFIG="$PROJECTS/mysql/conf.d/sailadmin.cnf"

# 所有操作由服务器操作者以 root 执行，避免数据目录出现混合权限。
if (( EUID != 0 )); then
  printf '请使用 sudo 运行此脚本。\n' >&2
  exit 1
fi
cd "$PROJECTS"
exec 9>"$PROJECTS/.deploy.lock"
flock -n 9 || { printf '已有部署或备份操作正在运行。\n' >&2; exit 1; }

# 将单个组件的首次密码写入目标机；已有文件原样保留。
create_secret() {
  local path=$1 kind=$2
  if [[ -e "$path" ]]; then
    [[ -s "$path" ]] || { printf '秘密文件为空：%s\n' "$path" >&2; exit 1; }
    return
  fi
  if [[ "$kind" == base64 ]]; then
    printf '%s' "$(openssl rand -base64 32)" > "$path"
  else
    printf '%s' "$(openssl rand -hex 32)" > "$path"
  fi
}

# 创建持久化目录和首次密钥，并由 Redis 密码生成只含哈希的 ACL 文件。
prepare() {
  command -v openssl >/dev/null || { printf '缺少 openssl。\n' >&2; exit 1; }
  # 数据、日志和密钥分别创建在指定目录，应用写入目录归属固定运行用户。
  install -d -m 0700 "$PROJECTS/mysql/secrets" "$PROJECTS/redis/secrets" \
    "$PROJECTS/sailadmin/secrets" "$VOLUMES/nginx/certs" "$BACKUPS"
  install -d -m 0755 "$VOLUMES/mysql/data" "$VOLUMES/redis/data" \
    /app/logs/nginx
  install -d -m 0750 -o 10001 -g 10001 "$VOLUMES/sailadmin/storage" /app/logs/sailadmin
  # MySQL 会忽略可被所有用户写入的配置文件，Windows 打包上传后需固定权限。
  [[ -s "$MYSQL_CONFIG" ]] || { printf '缺少 %s。\n' "$MYSQL_CONFIG" >&2; exit 1; }
  chmod 0644 "$MYSQL_CONFIG"

  # 首次生成独立秘密；发现已有值时保持原值，避免旧数据无法解密。
  create_secret "$PROJECTS/mysql/secrets/root_password" hex
  create_secret "$PROJECTS/mysql/secrets/app_password" hex
  create_secret "$PROJECTS/redis/secrets/password" hex
  create_secret "$PROJECTS/sailadmin/secrets/master_key" base64
  create_secret "$PROJECTS/sailadmin/secrets/admin_password" hex

  # 配置树和 Redis 健康检查需要读取文件；宿主机父目录只允许 root 进入。
  chmod 0600 "$PROJECTS/mysql/secrets/root_password"
  chmod 0644 "$PROJECTS/mysql/secrets/app_password" "$PROJECTS/redis/secrets/password" \
    "$PROJECTS/sailadmin/secrets/master_key" "$PROJECTS/sailadmin/secrets/admin_password"
  local acl_hash acl_line acl_file
  acl_hash=$(openssl dgst -sha256 -r "$PROJECTS/redis/secrets/password" | cut -d ' ' -f 1)
  acl_line="user default on #$acl_hash ~* &* +@all"
  acl_file="$PROJECTS/redis/secrets/users.acl"
  if [[ -e "$acl_file" ]]; then
    [[ $(<"$acl_file") == "$acl_line" ]] || { printf 'Redis ACL 与密码文件不一致。\n' >&2; exit 1; }
  else
    printf '%s\n' "$acl_line" > "$acl_file"
  fi
  chmod 0644 "$acl_file"
  if [[ ! -e "$ENV_FILE" ]]; then
    install -m 0600 "$PROJECTS/.env.example" "$ENV_FILE"
  fi
  printf '目录和首次秘密已准备；管理员初始密码见 %s。\n' "$PROJECTS/sailadmin/secrets/admin_password"
}

# 读取公开访问方式，拒绝无效地址及路径字符。
load_origin() {
  [[ -f "$ENV_FILE" ]] || { printf '缺少 %s，请先运行 prepare。\n' "$ENV_FILE" >&2; exit 1; }
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  [[ ${SAIL_SCHEME:-} == http || ${SAIL_SCHEME:-} == https ]] || {
    printf 'SAIL_SCHEME 只能是 http 或 https。\n' >&2; exit 1;
  }
  if [[ ${SAIL_PUBLIC_HOST:-} =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; then
    [[ "$SAIL_SCHEME" == http ]] || { printf 'HTTPS 模式请填写实际域名。\n' >&2; exit 1; }
    # IPv4 各段必须处于有效范围，不能把无效数字地址当成域名。
    local octet
    local -a octets
    IFS=. read -r -a octets <<< "$SAIL_PUBLIC_HOST"
    for octet in "${octets[@]}"; do
      (( 10#$octet <= 255 )) || { printf 'IPv4 地址无效。\n' >&2; exit 1; }
    done
  else
    # HTTP 域名与 HTTPS 域名共用同一来源格式，HTTPS 另在 check 中校验证书。
    [[ ${SAIL_PUBLIC_HOST:-} =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*\.[a-zA-Z]{2,}$ ]] || {
      printf 'SAIL_PUBLIC_HOST 必须是实际公网 IPv4 或域名。\n' >&2; exit 1;
    }
  fi
}

# 确认固定目录中已有本地构建的 JAR 和前端静态资源。
check_artifacts() {
  [[ -s "$JAR_FILE" && -s "$DIST_DIR/index.html" ]] || {
    printf '缺少 %s 或 %s。\n' "$JAR_FILE" "$DIST_DIR/index.html" >&2; exit 1;
  }
  [[ -s "$APP_DIR/Dockerfile" && -s "$APP_DIR/.dockerignore" ]] || {
    printf '缺少后端 Dockerfile 或 .dockerignore，请更新 /app 部署配置。\n' >&2; exit 1;
  }
}

# 核对数据根目录、完整配置；HTTPS 模式再核对证书。
check() {
  load_origin
  check_artifacts
  # Docker 必须使用预定数据根目录，避免数据落到系统盘。
  command -v docker >/dev/null && command -v openssl >/dev/null || {
    printf '缺少 docker 或 openssl。\n' >&2; exit 1;
  }
  [[ $(docker info --format '{{.DockerRootDir}}') == /app/data ]] || {
    printf 'Docker data-root 不是 /app/data，请先核对 daemon.json。\n' >&2; exit 1;
  }
  [[ -s "$MYSQL_CONFIG" && $(stat -c '%a' "$MYSQL_CONFIG") == 644 ]] || {
    printf 'MySQL 配置须为 0644：%s；请重新运行 prepare。\n' "$MYSQL_CONFIG" >&2; exit 1;
  }
  if [[ "$SAIL_SCHEME" == https ]]; then
    local cert="$VOLUMES/nginx/certs/fullchain.pem" key="$VOLUMES/nginx/certs/privkey.pem"
    # 正式域名、剩余有效期和私钥三者都必须匹配。
    [[ -s "$cert" && -s "$key" ]] || { printf '缺少 Nginx 正式证书或私钥。\n' >&2; exit 1; }
    openssl x509 -in "$cert" -noout -checkhost "$SAIL_PUBLIC_HOST" >/dev/null || {
      printf '证书不匹配域名 %s。\n' "$SAIL_PUBLIC_HOST" >&2; exit 1;
    }
    openssl x509 -in "$cert" -noout -checkend 604800 >/dev/null || {
      printf '证书已过期或将在七天内过期。\n' >&2; exit 1;
    }
    [[ $(openssl x509 -in "$cert" -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256) == \
       $(openssl pkey -in "$key" -pubout -outform DER | openssl dgst -sha256) ]] || {
      printf '证书和私钥不配对。\n' >&2; exit 1;
    }
  fi
  [[ -s "$PROJECTS/nginx/templates/$SAIL_SCHEME/default.conf.template" ]] || {
    printf '缺少 %s 模式的 Nginx 配置。\n' "$SAIL_SCHEME" >&2; exit 1;
  }
  local path
  # 所有秘密必须就位，并核对 Redis ACL 与 AES 密钥格式。
  for path in "$PROJECTS/mysql/secrets/root_password" "$PROJECTS/mysql/secrets/app_password" \
    "$PROJECTS/redis/secrets/password" "$PROJECTS/redis/secrets/users.acl" \
    "$PROJECTS/sailadmin/secrets/master_key" "$PROJECTS/sailadmin/secrets/admin_password"; do
    [[ -s "$path" ]] || { printf '秘密文件缺失：%s\n' "$path" >&2; exit 1; }
  done
  local acl_hash expected_acl
  acl_hash=$(openssl dgst -sha256 -r "$PROJECTS/redis/secrets/password" | cut -d ' ' -f 1)
  expected_acl="user default on #$acl_hash ~* &* +@all"
  [[ $(<"$PROJECTS/redis/secrets/users.acl") == "$expected_acl" ]] || {
    printf 'Redis ACL 与密码文件不一致。\n' >&2; exit 1;
  }
  [[ $(openssl base64 -d -A -in "$PROJECTS/sailadmin/secrets/master_key" | wc -c) == 32 ]] || {
    printf 'AES 主密钥必须是 Base64 编码的 32 字节。\n' >&2; exit 1;
  }
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --quiet
  printf '部署前检查通过：%s://%s。\n' "$SAIL_SCHEME" "$SAIL_PUBLIC_HOST"
}

# 通过容器内部秘密文件登录数据库，密码不会出现在命令行参数中。
mysql_query() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql sh -c \
    'MYSQL_PWD="$(cat /run/secrets/mysql_app_password)" exec mysql --default-character-set=utf8mb4 -N -B -usailadmin sail_admin -e "$1"' sh "$1"
}

# 基础表、末尾公告字段和内置角色同时存在，才视为完成首次导入。
check_schema() {
  local tables column role
  tables=$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='sail_admin'")
  column=$(mysql_query "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='sail_admin' AND table_name='message_task' AND column_name='display_mode'")
  role=$(mysql_query 'SELECT COUNT(*) FROM sys_role WHERE id=1')
  [[ "$tables" -ge 28 && "$column" == 1 && "$role" == 1 ]] || {
    printf '基础库结构或种子数据不完整，请核查导入结果。\n' >&2; exit 1;
  }
}

# 仅向空的新库导入基础结构；失败后的部分库必须由操作者核查。
init_db() {
  check
  # 基础 SQL 只在首次空库导入时使用，不要求后续每个发布版本重复携带。
  [[ -s "$APP_DIR/base.sql" ]] || {
    printf '首次初始化缺少 %s/base.sql。\n' "$APP_DIR" >&2; exit 1;
  }
  # 先启动并等待数据库和缓存健康，再确认数据库确实为空。
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --wait mysql redis
  local table_count
  table_count=$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='sail_admin'")
  [[ "$table_count" == 0 ]] || { printf '数据库已有 %s 张表，拒绝重复导入。\n' "$table_count" >&2; exit 1; }
  # 通过标准输入导入当前发布版本的基础 SQL，不在应用启动时自动建表。
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql sh -c \
    'MYSQL_PWD="$(cat /run/secrets/mysql_app_password)" exec mysql --default-character-set=utf8mb4 -usailadmin sail_admin' \
    < "$APP_DIR/base.sql"
  check_schema
  [[ $(mysql_query "SELECT name FROM sys_post WHERE code='DEPT_MANAGER'") == '部门负责人' ]] || {
    printf '岗位种子数据不是正确中文，请核对上传的 base.sql 编码。\n' >&2; exit 1;
  }
  printf '全新 sail_admin 数据库已导入基础结构。\n'
}

# 仅对确认可丢弃的初始化库重建，先备份，再恢复正确编码的种子数据。
reset_db() {
  [[ ${1:-} == --confirm-empty ]] || {
    printf '重建会删除 sail_admin 的现有数据；确认可丢弃后使用 reset-db --confirm-empty。\n' >&2; exit 2;
  }
  check
  [[ -s "$APP_DIR/base.sql" ]] || { printf '缺少 %s/base.sql。\n' "$APP_DIR" >&2; exit 1; }
  # 备份成功后停止应用写入，并重启 MySQL 使修正后的配置文件生效。
  backup leave-app-stopped
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" restart mysql
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --wait mysql redis
  # 只删除本项目数据库；MySQL 用户和密码保留在实例权限表中。
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql sh -c \
    'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql --default-character-set=utf8mb4 -uroot -e "DROP DATABASE IF EXISTS sail_admin; CREATE DATABASE sail_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"'
  init_db
  # 旧会话与一次性票据不应关联到重新初始化的用户数据。
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T redis sh -c \
    'test "$(redis-cli --no-auth-warning -a "$(cat /run/secrets/redis_password)" FLUSHDB)" = OK'
  # 验证通过后再启动后端与 Nginx，管理员使用原首次密码重新初始化。
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-deps --no-build --force-recreate --wait sailadmin
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-deps --no-build --force-recreate --wait nginx
  printf '数据库已重建，岗位名称编码检查通过；请重新登录并核对页面。\n'
}

# 确认数据库已初始化，再构建当前版本镜像并启动四个服务。
deploy() {
  check
  # 数据服务可能在服务器重启后尚未运行，先等它们健康再查询表结构。
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --wait mysql redis
  # 拒绝在半成品库上启动应用。
  check_schema
  # .dockerignore 只允许 JAR 进入构建上下文，避免发送 secrets 和上传数据。
  docker build --tag sailadmin:prod --file "$APP_DIR/Dockerfile" "$APP_DIR"
  # 应用和 Nginx 分步重建，保持 MySQL/Redis 容器与持久化数据不变。
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-deps --no-build --force-recreate --wait sailadmin
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-deps --no-build --force-recreate --wait nginx
  printf '服务已启动；请验证 %s://%s 的登录、权限、文件和消息流程。\n' "$SAIL_SCHEME" "$SAIL_PUBLIC_HOST"
}

# 切换公网 IP、HTTP 域名或 HTTPS 域名时，重建应用和入口以更新来源与 Nginx 模式。
switch_origin() {
  check
  docker image inspect sailadmin:prod >/dev/null || {
    printf '后端镜像不存在，请先执行 deploy。\n' >&2; exit 1;
  }
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-deps --no-build --force-recreate --wait sailadmin
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-deps --no-build --force-recreate --wait nginx
  printf '访问入口已切换到 %s://%s。\n' "$SAIL_SCHEME" "$SAIL_PUBLIC_HOST"
}

# 暂停应用写入并停止 Redis，保存同一时间窗口的 SQL、上传文件和整套 RDB/AOF。
backup() {
  local mode=${1:-resume}
  [[ "$mode" == resume || "$mode" == leave-app-stopped ]] || {
    printf '无效备份模式。\n' >&2; exit 2;
  }
  # 证书到期时仍允许抢救数据，备份只依赖当前版本和服务配置。
  load_origin
  check_artifacts
  install -d -m 0700 "$BACKUPS"
  local destination
  destination=$(mktemp -d "$BACKUPS/$(date +%Y%m%d-%H%M%S).XXXXXX")
  # 停止入口和应用写入；失败退出时也尝试恢复服务。
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" stop nginx sailadmin
  trap 'docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d redis sailadmin nginx >/dev/null' EXIT
  # MySQL 保持运行以导出一致性快照，Redis 停止后才能复制完整持久化目录。
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql sh -c \
    'MYSQL_PWD="$(cat /run/secrets/mysql_app_password)" exec mysqldump --default-character-set=utf8mb4 -usailadmin --single-transaction --no-tablespaces --routines --triggers --events sail_admin' \
    > "$destination/mysql.sql"
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" stop redis
  # 数据、当前版本、秘密和配置进入同一个受限备份目录。
  install -d -m 0700 "$destination/redis" "$destination/storage" "$destination/config" "$destination/artifacts"
  cp -a "$VOLUMES/redis/data/." "$destination/redis/"
  cp -a "$VOLUMES/sailadmin/storage/." "$destination/storage/"
  cp -a "$PROJECTS/.env" "$PROJECTS/compose.yaml" "$PROJECTS/mysql" \
    "$PROJECTS/redis" "$PROJECTS/nginx" "$APP_DIR/application-prod.yaml" \
    "$APP_DIR/Dockerfile" "$APP_DIR/.dockerignore" "$APP_DIR/secrets" \
    "$VOLUMES/nginx/certs" /app/docker/daemon.json "$destination/config/"
  cp -a "$JAR_FILE" "$DIST_DIR" "$destination/artifacts/"
  if [[ -s "$APP_DIR/base.sql" ]]; then
    cp -a "$APP_DIR/base.sql" "$destination/artifacts/"
  fi
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --wait redis
  if [[ "$mode" == resume ]]; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --wait sailadmin nginx
  fi
  trap - EXIT
  LAST_BACKUP_DIR=$destination
  printf '备份已保存：%s\n' "$destination"
}

# 从指定备份恢复 JAR 与前端文件；数据库恢复需要单独决策。
rollback() {
  local target=${1:-}
  check
  [[ "$target" =~ ^[0-9]{8}-[0-9]{6}\.[a-zA-Z0-9]{6}$ ]] || {
    printf '请提供 /app/backups/sailadmin/ 下的备份目录名。\n' >&2; exit 1;
  }
  local source="$BACKUPS/$target/artifacts" staged_dist
  [[ -s "$source/sail-server-1.0.0.jar" && -s "$source/dist/index.html" ]] || {
    printf '备份中缺少完整的 JAR 或前端文件。\n' >&2; exit 1;
  }
  # 先复制旧前端到独立目录并备份当前状态，防止覆盖中途丢失可回退文件。
  staged_dist=$(mktemp -d "$APP_DIR/.dist-restore.XXXXXX")
  cp -a "$source/dist/." "$staged_dist/"
  backup
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" stop nginx sailadmin
  # 当前 dist 移入本次备份，旧版 dist 整体换入固定目录。
  mv "$DIST_DIR" "$LAST_BACKUP_DIR/dist-before-rollback"
  mv "$staged_dist" "$DIST_DIR"
  install -m 0644 "$source/sail-server-1.0.0.jar" "$JAR_FILE"
  deploy
  printf '应用文件已从备份 %s 恢复；请核对旧版与当前数据库结构兼容性。\n' "$target"
}

case "${1:-}" in
  prepare) prepare ;;
  check) check ;;
  init-db) init_db ;;
  reset-db) reset_db "${2:-}" ;;
  deploy) deploy ;;
  switch-origin) switch_origin ;;
  backup) backup ;;
  rollback) rollback "${2:-}" ;;
  *) printf '用法：%s {prepare|check|init-db|reset-db --confirm-empty|deploy|switch-origin|backup|rollback 备份目录名}\n' "$0" >&2; exit 2 ;;
esac
