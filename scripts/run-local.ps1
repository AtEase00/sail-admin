#requires -Version 7.0
# 开发启动入口，业务配置直接读取 application-local.yaml，不导入 .env。
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$jar = Join-Path $PSScriptRoot '..\sail-server\target\sail-server-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw '应用 JAR 尚未构建，请先按 README 执行构建' }
# 允许参数指定 JDK 21，不改变其他工程的 JAVA_HOME。
$java = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { 'java' }
# 从工程根目录运行，确保相对存储路径不受调用脚本时的目录影响。
$sailProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Push-Location -LiteralPath $sailProjectRoot
try {
    & $java '-Duser.timezone=Asia/Shanghai' -jar $jar '--spring.profiles.active=local'
    $sailExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $sailExitCode
