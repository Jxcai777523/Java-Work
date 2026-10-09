<#
.SYNOPSIS
    将 StudyTimeTracker 打包为免安装的 Windows 绿色版程序。

.DESCRIPTION
    执行流程：
      1. 调用 Maven 执行 clean package，生成包含全部依赖的可执行 jar；
      2. 调用 JDK 自带的 jpackage 生成自带 JRE 的应用目录（dist\StudyTimeTracker）。

    产物可直接拷贝到其他 Windows 电脑运行，无需安装 Java 或数据库。

.NOTES
    请在项目根目录下运行本脚本：.\build-package.ps1
    若已把 mvn 加入系统 PATH，可将 $MvnCmd 改为 'mvn'。
#>

$ErrorActionPreference = 'Stop'

# ==================== 可修改配置 ====================
$AppName    = 'StudyTimeTracker'
$AppVersion = '1.0.0'
$MainJar    = 'StudyTimeTracker.jar'
$MainClass  = 'com.study.tracker.app.Main'
$Vendor     = 'Java 程序设计工程实践'
$MvnCmd     = 'D:\programs\IntelliJ IDEA Community Edition 2025.2.1\plugins\maven\lib\maven3\bin\mvn.cmd'
# ===================================================

$ProjectRoot = $PSScriptRoot
$TargetDir   = Join-Path $ProjectRoot 'target'
$StageDir    = Join-Path $TargetDir 'jpackage-input'
$DistDir     = Join-Path $ProjectRoot 'dist'

Write-Host ''
Write-Host '===== StudyTimeTracker 打包脚本 =====' -ForegroundColor Cyan

# ---- 步骤 1：确认程序未在运行 ----
# 程序运行时 JVM 会占用 target 下的 jar，打包插件无法把合并后的 jar 改名替换过去，
# 只能退化成复制，结果是在 target 里留下一个多余的中间 jar。
Write-Host '==> 步骤 1/5：检查程序是否正在运行 ...' -ForegroundColor Yellow
$runningApps = @()

$packagedProcs = Get-Process -Name $AppName -ErrorAction SilentlyContinue
if ($packagedProcs) {
    $runningApps += ('绿色版 ' + $AppName + '.exe（PID ' + (($packagedProcs | Select-Object -ExpandProperty Id) -join ',') + '）')
}

try {
    $javaProcs = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop |
        Where-Object { $_.CommandLine -like ('*' + $MainJar + '*') }
    if ($javaProcs) {
        $runningApps += ('开发版（PID ' + (($javaProcs | Select-Object -ExpandProperty ProcessId) -join ',') + '）')
    }
} catch {
    Write-Host '    （无法读取 Java 进程命令行，已跳开发版检测）' -ForegroundColor DarkYellow
}

if ($runningApps.Count -gt 0) {
    Write-Host ''
    Write-Host '检测到程序正在运行，请先关闭再打包：' -ForegroundColor Red
    $runningApps | ForEach-Object { Write-Host ('  - ' + $_) -ForegroundColor Red }
    Write-Host '原因：程序占用 target 下的 jar 时，打包插件无法完成文件替换，' -ForegroundColor Red
    Write-Host '      会在 target 里留下多余的中间 jar。' -ForegroundColor Red
    throw '请关闭程序后重新运行打包脚本。'
}
Write-Host '    未检测到运行中的实例。' -ForegroundColor Green

# ---- 步骤 1：Maven 构建 ----
Write-Host '==> 步骤 2/5：使用 Maven 构建可执行 jar ...' -ForegroundColor Yellow
& $MvnCmd clean package
if ($LASTEXITCODE -ne 0) {
    throw 'Maven 构建失败，请检查上方错误信息。'
}

# ---- 步骤 3：准备打包输入目录 ----
# jpackage 会把 --input 目录下的所有文件复制进 app 目录，
# 因此这里只放主 jar，避免把 classes、maven-status 等中间产物一起打进去。
Write-Host '==> 步骤 3/5：准备打包输入目录 ...' -ForegroundColor Yellow
if (Test-Path $StageDir) {
    $resolvedStage = (Resolve-Path $StageDir).Path
    if (-not $resolvedStage.StartsWith($ProjectRoot)) {
        throw "拒绝删除：$resolvedStage 不在项目目录内。"
    }
    Remove-Item -LiteralPath $StageDir -Recurse -Force
}
New-Item -ItemType Directory -Path $StageDir | Out-Null
Copy-Item -LiteralPath (Join-Path $TargetDir $MainJar) -Destination $StageDir

# ---- 步骤 4：清理旧的打包产物 ----
Write-Host '==> 步骤 4/5：清理旧的 dist 目录 ...' -ForegroundColor Yellow
if (Test-Path $DistDir) {
    # 安全校验：确认待删除目录确实位于项目根目录之下
    $resolvedDist = (Resolve-Path $DistDir).Path
    if (-not $resolvedDist.StartsWith($ProjectRoot)) {
        throw "拒绝删除：$resolvedDist 不在项目目录内。"
    }
    Remove-Item -LiteralPath $DistDir -Recurse -Force
}

# ---- 步骤 5：jpackage 打包 ----
Write-Host '==> 步骤 5/5：调用 jpackage 生成绿色版 ...' -ForegroundColor Yellow
jpackage `
    --type app-image `
    --name $AppName `
    --app-version $AppVersion `
    --input $StageDir `
    --main-jar $MainJar `
    --main-class $MainClass `
    --dest $DistDir `
    --vendor $Vendor `
    --description '个人学习时间管理与效率分析系统'

if ($LASTEXITCODE -ne 0) {
    throw 'jpackage 打包失败，请检查上方错误信息。'
}

Write-Host ''
Write-Host '打包完成！产物位置：' -ForegroundColor Green
Write-Host "  $DistDir\$AppName" -ForegroundColor Green
Write-Host '把整个文件夹压缩后拷贝到其他电脑，解压双击 exe 即可运行。' -ForegroundColor Green