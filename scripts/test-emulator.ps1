#Requires -Version 5.1
# Run: powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-emulator.ps1
# Add -KeepRunning to retain only this harness's VM for screenshots; no packages are downloaded.
[CmdletBinding()]
param(
    [string]$JavaHome,
    [switch]$KeepRunning,
    [switch]$BootOnly,
    [ValidateRange(1, 10000)][int]$ExpectedTests = 10,
    [ValidateRange(60, 1200)][int]$BootTimeoutSeconds = 420,
    [ValidateRange(30, 1800)][int]$TestTimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($BootOnly -and -not $KeepRunning) { throw '-BootOnly requires -KeepRunning so the booted test VM remains available.' }
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$toolsRoot = Join-Path $projectRoot '.tools'
$sdkRoot = Join-Path $toolsRoot 'android-sdk'
$avdRoot = Join-Path $toolsRoot 'avd'
$avdName = 'SidGlassOS_API36'
$avdPath = Join-Path $avdRoot "$avdName.avd"
$ownerFile = Join-Path $avdPath '.sidglassos-test-owner.json'
$imagePackage = 'system-images;android-36;google_apis;x86_64'
$serial = 'emulator-5580'
$port = 5580
$runner = 'com.sidglassos.test/androidx.test.runner.AndroidJUnitRunner'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$avdManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\avdmanager.bat'
$aapt = Join-Path $sdkRoot 'build-tools\35.0.0\aapt2.exe'
$appApk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$utf8 = New-Object Text.UTF8Encoding($false)
$emulatorProcess = $null
$ownedVmConfirmed = $false
$mutexAcquired = $false
$mutex = New-Object Threading.Mutex($false, 'Local\SidGlassOS_Emulator_5580')
$runRoot = $null

function Write-RunFile {
    param([string]$Name, [string]$Contents)
    [IO.File]::WriteAllText((Join-Path $runRoot $Name), $Contents, $utf8)
}

function Quote-WindowsArgument {
    param([AllowEmptyString()][string]$Value)
    $builder = New-Object Text.StringBuilder
    [void]$builder.Append('"')
    $slashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq '\') { $slashes++; continue }
        if ($character -eq '"') {
            [void]$builder.Append(('\' * ($slashes * 2 + 1)))
            [void]$builder.Append('"')
        } else {
            [void]$builder.Append(('\' * $slashes))
            [void]$builder.Append($character)
        }
        $slashes = 0
    }
    [void]$builder.Append(('\' * ($slashes * 2)))
    [void]$builder.Append('"')
    return $builder.ToString()
}

function Invoke-BoundedTool {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @(),
        [int]$TimeoutSeconds = 20,
        [string]$InputText = '',
        [string]$RawArguments
    )
    $startInfo = New-Object Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = if ($RawArguments) { $RawArguments } else { ($Arguments | ForEach-Object { Quote-WindowsArgument $_ }) -join ' ' }
    $startInfo.WorkingDirectory = $projectRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        [void]$process.Start()
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if ($InputText) { $process.StandardInput.Write($InputText) }
        $process.StandardInput.Close()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            # This process handle was created here; never stop an existing adb server or unrelated VM.
            try { $process.Kill() } catch { }
            [void]$process.WaitForExit(5000)
            try { [void]$stdoutTask.Wait(1000) } catch { }
            try { [void]$stderrTask.Wait(1000) } catch { }
            $partialOut = if ($stdoutTask.Status -eq [Threading.Tasks.TaskStatus]::RanToCompletion) { $stdoutTask.GetAwaiter().GetResult() } else { '' }
            $partialError = if ($stderrTask.Status -eq [Threading.Tasks.TaskStatus]::RanToCompletion) { $stderrTask.GetAwaiter().GetResult() } else { '' }
            return [PSCustomObject]@{ExitCode=-1; TimedOut=$true; Stdout=$partialOut; Stderr=($partialError + "`nTool timed out after $TimeoutSeconds seconds.")}
        }
        return [PSCustomObject]@{
            ExitCode=$process.ExitCode; TimedOut=$false
            Stdout=$stdoutTask.GetAwaiter().GetResult(); Stderr=$stderrTask.GetAwaiter().GetResult()
        }
    } finally { $process.Dispose() }
}

function Invoke-ProjectAdb {
    param([string[]]$Arguments, [int]$TimeoutSeconds = 20)
    # Every adb operation names this emulator explicitly. Physical devices are never selected.
    return Invoke-BoundedTool -FilePath $adb -Arguments (@('-s', $serial) + $Arguments) -TimeoutSeconds $TimeoutSeconds
}

function Assert-ToolSucceeded {
    param($Result, [string]$Description)
    if ($Result.TimedOut -or $Result.ExitCode -ne 0) {
        throw "$Description failed (exit $($Result.ExitCode)). $($Result.Stdout) $($Result.Stderr)"
    }
}

function Test-ProjectAvdIdentity {
    $identity = Invoke-ProjectAdb -Arguments @('emu', 'avd', 'name') -TimeoutSeconds 10
    if ($identity.ExitCode -ne 0 -or $identity.TimedOut) { return $false }
    $lines = @($identity.Stdout -split '\r?\n' | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -ne 'OK' })
    return $lines.Count -eq 1 -and $lines[0] -ceq $avdName
}

try {
    try { $mutexAcquired = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $mutexAcquired = $true }
    if (-not $mutexAcquired) { throw 'Another Sid Glass OS emulator test run owns port 5580.' }
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if (@($listeners | Where-Object { $_.Port -eq $port -or $_.Port -eq ($port + 1) }).Count -gt 0) {
        throw 'Port 5580 or 5581 is occupied. This harness will not reuse or stop an existing emulator or other service.'
    }
    foreach ($requiredFile in @($adb, $emulator, $avdManager, $aapt, $appApk, $testApk, (Join-Path $sdkRoot 'system-images\android-36\google_apis\x86_64\package.xml'))) {
        if (-not (Test-Path -LiteralPath $requiredFile)) { throw "Required file is missing: $requiredFile. Install the SDK packages and assemble both APKs before running this harness." }
    }

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
        $release = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe')) -and (Test-Path -LiteralPath $release)) {
            if ((Get-Content -LiteralPath $release -Raw) -match 'JAVA_VERSION="(\d+)' -and [int]$Matches[1] -ge 17 -and [int]$Matches[1] -le 23) {
                $selectedJdk = [IO.Path]::GetFullPath($candidate); break
            }
        }
        if ($JavaHome -and $candidate -eq $JavaHome) { throw 'Requested JavaHome must contain JDK 17 through 23.' }
    }
    if (-not $selectedJdk) { throw 'No compatible installed JDK found. Pass -JavaHome pointing to JDK 21.' }

    $env:JAVA_HOME = $selectedJdk
    $env:ANDROID_HOME = $sdkRoot
    $env:ANDROID_SDK_ROOT = $sdkRoot
    $env:ANDROID_USER_HOME = Join-Path $toolsRoot 'android-user'
    $env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
    $env:ANDROID_AVD_HOME = $avdRoot
    $env:Path = "$(Join-Path $selectedJdk 'bin');$env:Path"
    $runRoot = Join-Path $toolsRoot ('emulator-tests\' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
    New-Item -ItemType Directory -Force -Path $runRoot, $avdRoot, $env:ANDROID_USER_HOME | Out-Null
    $env:ANDROID_ADB_LOG_PATH = Join-Path $runRoot 'adb-server.log'

    $acceleration = Invoke-BoundedTool -FilePath $emulator -Arguments @('-accel-check') -TimeoutSeconds 30
    Write-RunFile 'acceleration.txt' ($acceleration.Stdout + $acceleration.Stderr)
    Assert-ToolSucceeded $acceleration 'Emulator acceleration check'
    if (($acceleration.Stdout + $acceleration.Stderr) -notmatch '(?i)installed and usable') { throw 'No usable existing hypervisor was confirmed. This harness does not enable Windows features or install drivers.' }
    $help = Invoke-BoundedTool -FilePath $emulator -Arguments @('-help-all') -TimeoutSeconds 30
    Assert-ToolSucceeded $help 'Emulator flag inspection'
    if (($help.Stdout + $help.Stderr) -notmatch '(?i)-no-metrics') { throw 'Installed emulator help does not confirm -no-metrics support. Review its metrics configuration before running this harness.' }

    $avdPrefix = [IO.Path]::GetFullPath($avdRoot).TrimEnd('\') + '\'
    if (-not [IO.Path]::GetFullPath($avdPath).StartsWith($avdPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'AVD must remain under this workspace .tools\avd directory.' }
    if (Test-Path -LiteralPath $ownerFile) {
        $owner = Get-Content -LiteralPath $ownerFile -Raw | ConvertFrom-Json
        if ($owner.ProjectRoot -ne $projectRoot -or $owner.AvdName -cne $avdName -or $owner.ImagePackage -cne $imagePackage) { throw 'Existing AVD ownership marker does not match this project. Nothing will be overwritten.' }
    } elseif ((Test-Path -LiteralPath $avdPath) -or (Test-Path -LiteralPath (Join-Path $avdRoot "$avdName.ini"))) {
        throw 'An unowned AVD already exists at the reserved project path. It will not be reused or overwritten.'
    } else {
        # cmd.exe is needed for the SDK batch launcher; reject expansion metacharacters in this path.
        if ($avdManager -match '[%!"]' -or $avdPath -match '[%!"]') { throw 'This batch launcher cannot safely use a workspace path containing percent, exclamation or quote characters.' }
        $command = '"' + $avdManager + '" create avd --name "' + $avdName + '" --package "' + $imagePackage + '" --path "' + $avdPath + '" --device "pixel_6"'
        $creation = Invoke-BoundedTool -FilePath $env:ComSpec -RawArguments ('/d /s /c "' + $command + '"') -InputText "no`r`n" -TimeoutSeconds 120
        Write-RunFile 'avd-creation.txt' ($creation.Stdout + $creation.Stderr)
        Assert-ToolSucceeded $creation 'Project AVD creation'
        if (-not (Test-Path -LiteralPath (Join-Path $avdPath 'config.ini'))) { throw 'AVD creation reported success but no configuration exists.' }
        [IO.File]::WriteAllText($ownerFile, ([PSCustomObject]@{ProjectRoot=$projectRoot;AvdName=$avdName;ImagePackage=$imagePackage} | ConvertTo-Json), $utf8)
    }
    $config = Get-Content -LiteralPath (Join-Path $avdPath 'config.ini') -Raw
    if ($config.Replace('\', '/') -notmatch '(?m)^image\.sysdir\.1=.*system-images/android-36/google_apis/x86_64/?\s*$') { throw 'Existing project AVD points to a different system image. Nothing will be overwritten.' }

    foreach ($apkCheck in @(@($appApk, 'com.sidglassos'), @($testApk, 'com.sidglassos.test'))) {
        $badging = Invoke-BoundedTool -FilePath $aapt -Arguments @('dump', 'badging', $apkCheck[0]) -TimeoutSeconds 30
        Assert-ToolSucceeded $badging 'APK package inspection'
        if ($badging.Stdout -notmatch ("(?m)^package: name='" + [regex]::Escape($apkCheck[1]) + "'")) { throw "Unexpected APK package in $($apkCheck[0])." }
    }
    Write-RunFile 'artifacts.json' (@($appApk, $testApk) | ForEach-Object { [PSCustomObject]@{Path=$_;SHA256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash} } | ConvertTo-Json)

    $emulatorArguments = @('-avd', $avdName, '-port', "$port", '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-no-metrics', '-gpu', 'swiftshader', '-memory', '2048', '-cores', '2')
    $emulatorProcess = Start-Process -FilePath $emulator -ArgumentList $emulatorArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runRoot 'emulator-stdout.txt') -RedirectStandardError (Join-Path $runRoot 'emulator-stderr.txt')
    Write-Host "Started project emulator PID $($emulatorProcess.Id), serial $serial. Logs: $runRoot"
    $bootTimer = [Diagnostics.Stopwatch]::StartNew()
    $nextProgress = 0
    $booted = $false
    while ($bootTimer.Elapsed.TotalSeconds -lt $BootTimeoutSeconds) {
        if ($emulatorProcess.HasExited) { throw "Project emulator exited during boot (exit $($emulatorProcess.ExitCode)). Inspect emulator-stderr.txt." }
        $state = Invoke-ProjectAdb -Arguments @('get-state') -TimeoutSeconds 10
        if ($state.ExitCode -eq 0 -and $state.Stdout.Trim() -eq 'device') {
            if (-not (Test-ProjectAvdIdentity)) { throw "Serial $serial does not identify the reserved project AVD. It will not be modified or stopped." }
            $ownedVmConfirmed = $true
            $bootState = Invoke-ProjectAdb -Arguments @('shell', 'getprop', 'sys.boot_completed') -TimeoutSeconds 10
            if ($bootState.ExitCode -eq 0 -and $bootState.Stdout.Trim() -eq '1') { $booted = $true; break }
        }
        if ($bootTimer.Elapsed.TotalSeconds -ge $nextProgress) {
            Write-Host ("Waiting for $serial to boot: {0:N0}s / {1}s" -f $bootTimer.Elapsed.TotalSeconds, $BootTimeoutSeconds)
            $nextProgress = $bootTimer.Elapsed.TotalSeconds + 15
        }
        Start-Sleep -Seconds 3
    }
    if (-not $booted) { throw "Project emulator did not finish booting within $BootTimeoutSeconds seconds." }
    if ($BootOnly) { Write-Host 'Owned emulator booted. No installation or UI tests were performed by this boot-only run.'; return }

    foreach ($package in @($appApk, $testApk)) {
        $installation = Invoke-ProjectAdb -Arguments @('install', '-r', $package) -TimeoutSeconds 180
        Write-RunFile (([IO.Path]::GetFileName($package)) + '-install.txt') ($installation.Stdout + $installation.Stderr)
        Assert-ToolSucceeded $installation 'APK installation on project emulator'
        if ($installation.Stdout -notmatch '(?m)^Success\s*$') { throw 'Package installer did not confirm Success.' }
    }
    $instrumentations = Invoke-ProjectAdb -Arguments @('shell', 'pm', 'list', 'instrumentation') -TimeoutSeconds 20
    Assert-ToolSucceeded $instrumentations 'Installed instrumentation inspection'
    if ($instrumentations.Stdout -notmatch ([regex]::Escape($runner) + '\s+\(target=com\.sidglassos\)')) { throw 'Expected test runner/target package is not installed.' }
    $wake = Invoke-ProjectAdb -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') -TimeoutSeconds 10
    Assert-ToolSucceeded $wake 'Project emulator wake-up'
    $unlock = Invoke-ProjectAdb -Arguments @('shell', 'wm', 'dismiss-keyguard') -TimeoutSeconds 10
    Assert-ToolSucceeded $unlock 'Fresh project emulator keyguard dismissal'
    $clearLogs = Invoke-ProjectAdb -Arguments @('logcat', '-c') -TimeoutSeconds 10
    Assert-ToolSucceeded $clearLogs 'Project emulator log reset'

    Write-Host 'Running Android instrumentation tests on the owned emulator only.'
    $tests = Invoke-ProjectAdb -Arguments @('shell', 'am', 'instrument', '-w', '-r', $runner) -TimeoutSeconds $TestTimeoutSeconds
    Write-RunFile 'instrumentation-stdout.txt' $tests.Stdout
    Write-RunFile 'instrumentation-stderr.txt' $tests.Stderr
    $crashes = Invoke-ProjectAdb -Arguments @('logcat', '-b', 'crash', '-d') -TimeoutSeconds 20
    Write-RunFile 'crash-log.txt' ($crashes.Stdout + $crashes.Stderr)
    Assert-ToolSucceeded $tests 'Instrumentation run'
    Assert-ToolSucceeded $crashes 'App crash log collection'
    $testOutput = $tests.Stdout + "`n" + $tests.Stderr
    if ($testOutput -match '(?im)^\s*FAILURES(?:!!!)?(?:\s|$)|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed|FATAL EXCEPTION|INSTRUMENTATION_STATUS_CODE:\s*-[12](?:\s|$)') { throw 'Instrumentation reported failed tests or a process crash. Inspect the saved logs.' }
    if ($crashes.ExitCode -eq 0 -and $crashes.Stdout -match 'com\.sidglassos(?:\.test)?(?:\s|,|\b)') { throw 'An app/test process crash was recorded. Inspect crash-log.txt.' }
    $passed = [regex]::Match($testOutput, '(?m)^\s*OK\s+\((\d+)\s+tests?\)\s*$')
    if (-not $passed.Success -or [int]$passed.Groups[1].Value -le 0 -or $testOutput -notmatch '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$') { throw 'Instrumentation did not report a positive test count and successful completion. No test pass is inferred.' }
    $testCount = [int]$passed.Groups[1].Value
    if ($testCount -ne $ExpectedTests) { throw "Expected $ExpectedTests authored UI tests; runner reported $testCount. Review discovery in the saved instrumentation log." }
    Write-RunFile 'result.json' ([PSCustomObject]@{Passed=$true;Tests=$testCount;Serial=$serial;Avd=$avdName;EmulatorPID=$emulatorProcess.Id;CompletedUTC=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json)
    Write-Host "PASS: Android runner reported OK ($testCount tests). This emulator result does not verify OPPO/ColorOS hardware behavior."
} finally {
    if ($null -ne $emulatorProcess) {
        if ($KeepRunning -and $ownedVmConfirmed -and -not $emulatorProcess.HasExited) {
            Write-Host "Keeping owned emulator running: PID $($emulatorProcess.Id), serial $serial, AVD $avdName. Logs: $runRoot"
            Write-Host "Stop after inspection: & '.\.tools\android-sdk\platform-tools\adb.exe' -s $serial emu kill"
        } else {
            if ($ownedVmConfirmed -and (Test-ProjectAvdIdentity)) {
                $shutdown = Invoke-ProjectAdb -Arguments @('emu', 'kill') -TimeoutSeconds 15
                if ($shutdown.ExitCode -ne 0) { Write-Warning 'Graceful emulator shutdown failed; stopping only the process started by this harness.' }
            }
            if (-not $emulatorProcess.HasExited -and -not $emulatorProcess.WaitForExit(20000)) {
                $emulatorProcess.Kill()
                [void]$emulatorProcess.WaitForExit(5000)
            }
            Write-Host 'Owned emulator cleanup finished. Existing devices and other emulators were not stopped.'
        }
        $emulatorProcess.Dispose()
    }
    if ($mutexAcquired) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
