#Requires -Version 5.1
[CmdletBinding()]
param(
    [switch]$AcceptAndroidSdkLicense,
    [switch]$GenerateWrapper,
    [string]$JavaHome
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AcceptAndroidSdkLicense) {
    throw 'Read https://developer.android.com/studio/terms, then run with -AcceptAndroidSdkLicense to accept the Android SDK licenses and install local build tools. No downloads were started.'
}

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$toolsRoot = Join-Path $projectRoot '.tools'
$downloadRoot = Join-Path $toolsRoot 'downloads'
$sdkRoot = Join-Path $toolsRoot 'android-sdk'
$gradleVersion = '8.13'
$gradleHash = '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'
$commandToolsHash = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'

function Find-BuildJdk {
    param([string]$Requested)
    $candidates = @()
    if ($Requested) { $candidates += $Requested }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $adoptiumRoot = Join-Path $env:ProgramFiles 'Eclipse Adoptium'
    if (Test-Path -LiteralPath $adoptiumRoot) {
        $candidates += @(Get-ChildItem -LiteralPath $adoptiumRoot -Directory | Sort-Object Name -Descending | ForEach-Object FullName)
    }
    $candidates += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr')
    foreach ($candidate in $candidates) {
        $releasePath = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe')) -and (Test-Path -LiteralPath $releasePath)) {
            $releaseText = Get-Content -LiteralPath $releasePath -Raw
            if ($releaseText -match 'JAVA_VERSION="(\d+)') {
                $major = [int]$Matches[1]
                if ($major -ge 17 -and $major -le 23) { return [IO.Path]::GetFullPath($candidate) }
            }
        }
        if ($Requested -and $candidate -eq $Requested) { throw 'The requested JavaHome must contain a JDK from 17 through 23 for Gradle 8.13.' }
    }
    throw 'No compatible JDK found. Install JDK 21, or pass -JavaHome pointing to an installed JDK 17 through 23.'
}

function Get-VerifiedArchive {
    param([string]$Uri, [string]$FileName, [string]$ExpectedHash)
    $archivePath = Join-Path $downloadRoot $FileName
    if (-not (Test-Path -LiteralPath $archivePath)) {
        $partialPath = "$archivePath.partial"
        Write-Host "Downloading $FileName from $Uri"
        $curlCommand = Get-Command curl.exe -ErrorAction SilentlyContinue
        if ($null -ne $curlCommand) {
            & $curlCommand.Source --fail --location --retry 3 --output $partialPath $Uri
            if ($LASTEXITCODE -ne 0) { throw "Download failed (curl exit $LASTEXITCODE): $Uri" }
        } else {
            $previousProgress = $ProgressPreference
            try {
                $ProgressPreference = 'SilentlyContinue'
                Invoke-WebRequest -UseBasicParsing -Uri $Uri -OutFile $partialPath
            } finally { $ProgressPreference = $previousProgress }
        }
        $actualHash = (Get-FileHash -LiteralPath $partialPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $ExpectedHash) { throw "SHA-256 mismatch for $FileName. The archive was not installed. Expected $ExpectedHash; got $actualHash." }
        Move-Item -LiteralPath $partialPath -Destination $archivePath
    }
    $cachedHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($cachedHash -ne $ExpectedHash) { throw "Cached archive $archivePath does not match its published SHA-256. Remove that file and rerun." }
    return $archivePath
}

function Expand-WorkspaceZip {
    param([string]$ArchivePath, [string]$DestinationPath)
    # Avoid Expand-Archive's provider path handling and preserve explicit SDK
    # directory entries. The caller must also keep staging paths short on WinPS.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $destination = [IO.Path]::GetFullPath($DestinationPath)
    $workspaceToolsPrefix = [IO.Path]::GetFullPath($toolsRoot).TrimEnd('\') + '\'
    if (-not $destination.StartsWith($workspaceToolsPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'ZIP extraction must remain inside this workspace .tools directory.'
    }
    if (Test-Path -LiteralPath $destination) {
        throw "Extraction destination already exists: $destination. Existing files were preserved."
    }
    $destinationPrefix = $destination.TrimEnd('\') + '\'
    $zip = [IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        # Check every entry before writing anything; never follow absolute paths,
        # parent traversal or symbolic links outside the intended staging tree.
        foreach ($entry in $zip.Entries) {
            $relativeName = $entry.FullName.Replace('/', '\')
            if ([IO.Path]::IsPathRooted($relativeName) -or $relativeName.Contains(':')) {
                throw "ZIP contains an absolute or invalid entry path: $($entry.FullName)"
            }
            $target = [IO.Path]::GetFullPath((Join-Path $destination $relativeName))
            if (-not $target.StartsWith($destinationPrefix, [StringComparison]::OrdinalIgnoreCase)) {
                throw "ZIP entry escapes its staging directory: $($entry.FullName)"
            }
            if ((($entry.ExternalAttributes -shr 16) -band 0xF000) -eq 0xA000) {
                throw "Symbolic link entries are not supported: $($entry.FullName)"
            }
        }
        [IO.Directory]::CreateDirectory($destination) | Out-Null
        foreach ($entry in $zip.Entries) {
            $relativeName = $entry.FullName.Replace('/', '\')
            $target = [IO.Path]::GetFullPath((Join-Path $destination $relativeName))
            if ($entry.FullName.EndsWith('/') -or $entry.FullName.EndsWith('\')) {
                [IO.Directory]::CreateDirectory($target) | Out-Null
            } else {
                [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $false)
            }
        }
    } finally { $zip.Dispose() }
}

$env:JAVA_HOME = Find-BuildJdk -Requested $JavaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
# Java SDK tooling honors this; native Windows adb still uses the Windows profile .android directory.
$env:ANDROID_USER_HOME = Join-Path $toolsRoot 'android-user'
$env:GRADLE_USER_HOME = Join-Path $toolsRoot 'gradle-user-home'
$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"
New-Item -ItemType Directory -Force -Path $downloadRoot, $sdkRoot, $env:ANDROID_USER_HOME, $env:GRADLE_USER_HOME | Out-Null

$gradleExecutable = Join-Path $toolsRoot "gradle-$gradleVersion\bin\gradle.bat"
if (-not (Test-Path -LiteralPath $gradleExecutable)) {
    $archive = Get-VerifiedArchive -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -FileName "gradle-$gradleVersion-bin.zip" -ExpectedHash $gradleHash
    Expand-Archive -LiteralPath $archive -DestinationPath $toolsRoot -Force
}

$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
    $archive = Get-VerifiedArchive -Uri 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -FileName 'commandlinetools-win-15859902_latest.zip' -ExpectedHash $commandToolsHash
    # The SDK contains a deeply nested Guava JAR. A full GUID staging name here
    # exceeds legacy Windows path limits when the workspace lives in OneDrive.
    $stagingRoot = Join-Path $toolsRoot ("sdk-x-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
    Expand-WorkspaceZip -ArchivePath $archive -DestinationPath $stagingRoot
    $sourceRoot = [IO.Path]::GetFullPath((Join-Path $stagingRoot 'cmdline-tools'))
    $destinationRoot = [IO.Path]::GetFullPath((Join-Path $sdkRoot 'cmdline-tools\latest'))
    $allowedPrefix = $toolsRoot.TrimEnd('\') + '\'
    if (-not $sourceRoot.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase) -or -not $destinationRoot.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'SDK extraction paths must remain inside this workspace .tools directory.'
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $sdkRoot 'cmdline-tools') | Out-Null
    Move-Item -LiteralPath $sourceRoot -Destination $destinationRoot
}

Write-Host 'Accepting SDK licenses explicitly authorized by -AcceptAndroidSdkLicense.'
1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" --licenses
if ($LASTEXITCODE -ne 0) { throw "Android SDK license processing failed (exit $LASTEXITCODE)." }
& $sdkManager "--sdk_root=$sdkRoot" 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw "Android SDK package installation failed (exit $LASTEXITCODE)." }

$sdkProperty = $sdkRoot.Replace('\', '/').Replace(':', '\:')
[IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$sdkProperty`n", [Text.Encoding]::ASCII)
& $gradleExecutable --version
if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed.' }
& (Join-Path $sdkRoot 'platform-tools\adb.exe') version
if ($LASTEXITCODE -ne 0) { throw 'Android platform-tools verification failed.' }
if ($GenerateWrapper) {
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'settings.gradle.kts'))) { throw 'Project settings.gradle.kts must exist before generating the wrapper.' }
    Push-Location -LiteralPath $projectRoot
    try {
        & $gradleExecutable wrapper --gradle-version $gradleVersion --distribution-type bin --gradle-distribution-sha256-sum $gradleHash --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
    $wrapperJar = Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.jar'
    $wrapperHash = (Get-FileHash -LiteralPath $wrapperJar -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($wrapperHash -ne '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f') {
        throw 'The generated Gradle wrapper JAR does not match the published Gradle 8.13 SHA-256.'
    }
}
Write-Host 'Build tools prepared in .tools. No machine-wide environment variables were changed.'
Write-Host 'Next: .\scripts\build.ps1'
