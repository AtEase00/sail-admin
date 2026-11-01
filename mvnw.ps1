# 固定 Maven 版本；JDK 21 由调用环境提供，不修改系统 Java 配置。
$ErrorActionPreference = 'Stop'
$version = '3.9.11'
$runtimeRoot = Join-Path $env:LOCALAPPDATA "SailAdmin\tools"
$mavenRoot = Join-Path $runtimeRoot "apache-maven-$version"
$mavenExe = Join-Path $mavenRoot 'bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $mavenExe)) {
    # 下载官方 Maven 发行包并核对 SHA-512 后解压。
    New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
    $archive = Join-Path $runtimeRoot "apache-maven-$version-bin.zip"
    $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
    Invoke-WebRequest -Uri $url -OutFile $archive
    $expected = (Invoke-WebRequest -Uri "$url.sha512").Content.Trim().Split(' ')[0]
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ne $expected) { throw 'Maven SHA-512 校验失败' }
    Expand-Archive -LiteralPath $archive -DestinationPath $runtimeRoot -Force
}
# 只在当前命令使用工程配置，不改写用户 Maven settings.xml。
& $mavenExe -s (Join-Path $PSScriptRoot '.mvn\settings.xml') @args
exit $LASTEXITCODE
