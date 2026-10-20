#Requires -Version 5.1
[CmdletBinding()]
param(
    [string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'),
    [string]$JavaHome,
    [string]$AndroidSdk
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$toolsRoot = Join-Path $projectRoot '.tools'

$jdkCandidates = @()
if ($JavaHome) { $jdkCandidates += $JavaHome }
if ($env:JAVA_HOME) { $jdkCandidates += $env:JAVA_HOME }
$adoptiumRoot = Join-Path $env:ProgramFiles 'Eclipse Adoptium'
if (Test-Path -LiteralPath $adoptiumRoot) {
    $jdkCandidates += @(Get-ChildItem -LiteralPath $adoptiumRoot -Directory | Sort-Object Name -Descending | ForEach-Object FullName)
}
$jdkCandidates += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr')
$selectedJdk = $null
foreach ($candidate in $jdkCandidates) {
    $releasePath = Join-Path $candidate 'release'
    if ((Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe')) -and (Test-Path -LiteralPath $releasePath)) {
        $releaseText = Get-Content -LiteralPath $releasePath -Raw
        if ($releaseText -match 'JAVA_VERSION="(\d+)') {
            $major = [int]$Matches[1]
            if ($major -ge 17 -and $major -le 23) { $selectedJdk = [IO.Path]::GetFullPath($candidate); break }
        }
    }
    if ($JavaHome -and $candidate -eq $JavaHome) { throw 'The requested JavaHome must contain JDK 17 through 23.' }
}
if (-not $selectedJdk) { throw 'No compatible JDK found. Pass -JavaHome pointing to JDK 17 through 23.' }

$sdkCandidates = @()
if ($AndroidSdk) { $sdkCandidates += $AndroidSdk }
$sdkCandidates += (Join-Path $toolsRoot 'android-sdk')
if ($env:ANDROID_HOME) { $sdkCandidates += $env:ANDROID_HOME }
if ($env:ANDROID_SDK_ROOT) { $sdkCandidates += $env:ANDROID_SDK_ROOT }
$sdkCandidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
$selectedSdk = $null
foreach ($candidate in $sdkCandidates) {
    if ((Test-Path -LiteralPath (Join-Path $candidate 'platforms\android-36\android.jar')) -and (Test-Path -LiteralPath (Join-Path $candidate 'build-tools\35.0.0\aapt2.exe'))) {
        $selectedSdk = [IO.Path]::GetFullPath($candidate)
        break
    }
    if ($AndroidSdk -and $candidate -eq $AndroidSdk) { throw 'The requested AndroidSdk requires platforms;android-36 and build-tools;35.0.0.' }
}
if (-not $selectedSdk) { throw 'Android SDK packages are missing. Run .\scripts\bootstrap.ps1 -AcceptAndroidSdkLicense, or pass -AndroidSdk pointing to a prepared SDK.' }

$env:JAVA_HOME = $selectedJdk
$env:ANDROID_HOME = $selectedSdk
$env:ANDROID_SDK_ROOT = $selectedSdk
# Java SDK tooling honors this; native Windows adb still uses the Windows profile .android directory.
$env:ANDROID_USER_HOME = Join-Path $toolsRoot 'android-user'
$env:GRADLE_USER_HOME = Join-Path $toolsRoot 'gradle-user-home'
$env:Path = "$(Join-Path $selectedJdk 'bin');$env:Path"
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME, $env:GRADLE_USER_HOME | Out-Null
$sdkProperty = $selectedSdk.Replace('\', '/').Replace(':', '\:')
[IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$sdkProperty`n", [Text.Encoding]::ASCII)

$localGradle = Join-Path $toolsRoot 'gradle-8.13\bin\gradle.bat'
$gradleExecutable = if (Test-Path -LiteralPath $localGradle) { $localGradle } else { Join-Path $projectRoot 'gradlew.bat' }
if (-not (Test-Path -LiteralPath $gradleExecutable)) { throw 'Gradle 8.13 is missing. Run .\scripts\bootstrap.ps1 -AcceptAndroidSdkLicense first.' }

Push-Location -LiteralPath $projectRoot
try {
    & $gradleExecutable @Tasks --console=plain --stacktrace
    $buildExitCode = $LASTEXITCODE
} finally { Pop-Location }
if ($buildExitCode -ne 0) { throw "Gradle build/checks failed (exit $buildExitCode)." }
Write-Host 'Requested Gradle tasks completed successfully. Device tests are separate.'
