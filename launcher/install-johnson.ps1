param(
    [Parameter(Mandatory = $true)] [string]$TvHost,
    [ValidateSet('com.ts.launcher', 'com.gathin.tvhome')] [string]$Package
)
$ErrorActionPreference = 'Stop'
if ($TvHost -notmatch '^(?<host>[A-Za-z0-9][A-Za-z0-9.-]*)(:(?<port>[0-9]{1,5}))?$') { throw 'TvHost must be an IPv4 address or hostname, optionally followed by :port.' }
$hostName = $Matches.host
$port = if ($Matches.port) { [int]$Matches.port } else { 5555 }
if ($port -lt 1 -or $port -gt 65535) { throw 'Invalid TV port.' }
$serial = "${hostName}:$port"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) { throw "adb not found: $adb" }
$signer = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction Stop | Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending | ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
if (-not $signer) { throw 'Android SDK build-tools apksigner.bat not found.' }
function Invoke-Adb([string[]]$Arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = @(& $adb @Arguments 2>&1)
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($nativeExit -ne 0) { throw "adb $($Arguments -join ' ') failed ($nativeExit): $($result -join ' ')" }
    return $result
}
function Get-Signatures([string]$Path) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $signer verify --print-certs $Path 2>&1)
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($nativeExit -ne 0) { throw "apksigner failed ($nativeExit): $($output -join ' ')" }
    $digests = @($output | ForEach-Object { if ("$_" -match '^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)\s*$') { $Matches[1].Replace(':', '').ToLowerInvariant() } })
    if ($digests.Count -eq 0 -or @($digests | Where-Object { $_ -notmatch '^[0-9a-f]{64}$' }).Count -ne 0) { throw 'Could not parse APK signer SHA-256 digests.' }
    return ($digests | Sort-Object) -join ','
}
function Get-ServiceList([string]$Value) {
    if (-not $Value -or $Value -eq 'null') { return @() }
    $items = @($Value -split ':')
    foreach ($item in $items) {
        if ($item -notmatch '^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+/(?:\.?[A-Za-z0-9_]+)(?:\.[A-Za-z0-9_]+)*$') {
            throw "Unsafe or invalid accessibility service component: $item"
        }
    }
    return $items
}
function Get-LauncherServiceName([string]$Component) {
    $parts = $Component.Split('/')
    if ($parts[0] -cne $Package) { return '' }
    foreach ($name in @('HomeKeyService', 'InputKeyService')) {
        if ($parts[1] -ceq ".$name" -or $parts[1] -ceq "com.gathin.tvhome.$name") { return $name }
    }
    return ''
}
$connected = Invoke-Adb -Arguments @('connect', $serial)
Write-Host ($connected -join ' ')
$state = Invoke-Adb -Arguments @('-s', $serial, 'get-state')
if (($state -join '').Trim() -ne 'device') { throw "TV not authorized or not connected: $($state -join ' ')" }
$installed = @(Invoke-Adb -Arguments @('-s', $serial, 'shell', 'pm', 'list', 'packages'))
$candidates = @('com.ts.launcher', 'com.gathin.tvhome')
$found = @($candidates | Where-Object { $installed -contains "package:$_" })
if ($found.Count -eq 0) { throw 'Neither supported launcher is installed; this script does not perform first installation.' }
if (-not $Package) {
    if ($found.Count -ne 1) { throw 'Both launcher packages are installed. Specify -Package after identifying the intended one.' }
    $Package = $found[0]
} elseif ($found -notcontains $Package) { throw "Selected package is not installed: $Package" }
$details = (Invoke-Adb -Arguments @('-s', $serial, 'shell', 'dumpsys', 'package', $Package)) -join "`n"
if ($details -notmatch '(?<![A-Za-z0-9_.])com\.gathin\.tvhome\.MainActivity(?![A-Za-z0-9_.])') { throw 'Installed package does not contain the expected MainActivity; refusing upgrade.' }
if ($details -notmatch '\bversionCode=(\d+)') { throw 'Could not read installed versionCode.' }
$oldVersion = [long]$Matches[1]
if ($oldVersion -ge 2147483647) { throw 'Installed versionCode cannot be incremented.' }
$newVersion = [int][Math]::Max(6, $oldVersion + 1)
Write-Host "Package: $Package; versionCode: $oldVersion -> $newVersion"
& (Join-Path $PSScriptRoot 'build-johnson.ps1') -Package $Package -VersionCode $newVersion
$paths = @(Invoke-Adb -Arguments @('-s', $serial, 'shell', 'pm', 'path', $Package))
$base = @($paths | ForEach-Object { if ("$_" -match '^package:(/[^\s]*\/base\.apk)\s*$') { $Matches[1] } })
if ($base.Count -ne 1) { throw 'Could not uniquely identify the installed base.apk.' }
$backupRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'backup\johnson-upgrade'
$backup = Join-Path $backupRoot ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $backup -ErrorAction Stop | Out-Null
Write-Host "Backup retained at: $backup"
$oldApk = Join-Path $backup 'base.apk'
Invoke-Adb -Arguments @('-s', $serial, 'pull', $base[0], $oldApk) | Out-Host
if (-not (Test-Path -LiteralPath $oldApk -PathType Leaf)) { throw 'Backup pull did not produce base.apk.' }
$newApk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
$oldSignature = Get-Signatures $oldApk
$newSignature = Get-Signatures $newApk
if ($oldSignature -ne $newSignature) { throw 'APK signing certificates differ. Stop and recover the original key; do not uninstall.' }
$homeArgs = @('-s', $serial, 'shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
$homeBefore = (Invoke-Adb -Arguments $homeArgs) -join "`n"
Write-Host "HOME before upgrade: $homeBefore"
$accessibilityBefore = ((Invoke-Adb -Arguments @('-s', $serial, 'shell', 'settings', 'get', 'secure', 'accessibility_enabled')) -join '').Trim()
$servicesBeforeValue = ((Invoke-Adb -Arguments @('-s', $serial, 'shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')) -join '').Trim()
$servicesBefore = @(Get-ServiceList $servicesBeforeValue)
$restoreServices = @()
if ($accessibilityBefore -eq '1') {
    $restoreServices = @($servicesBefore | Where-Object { Get-LauncherServiceName $_ })
} elseif ($accessibilityBefore -ne '0' -and $accessibilityBefore -ne 'null') { throw "Unexpected accessibility_enabled value: $accessibilityBefore" }
$install = Invoke-Adb -Arguments @('-s', $serial, 'install', '-r', $newApk)
if (-not (@($install | Where-Object { "$_".Trim() -eq 'Success' }).Count -eq 1)) { throw "Install did not report Success: $($install -join ' ')" }
Invoke-Adb -Arguments @('-s', $serial, 'shell', 'am', 'force-stop', $Package) | Out-Host
$start = Invoke-Adb -Arguments @('-s', $serial, 'shell', 'am', 'start', '-n', "$Package/com.gathin.tvhome.MainActivity")
if (($start -join ' ') -match '(?i)error|exception|unable to') { throw "Activity start failed: $($start -join ' ')" }
if ($restoreServices.Count -gt 0) {
    $servicesAfterValue = ((Invoke-Adb -Arguments @('-s', $serial, 'shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')) -join '').Trim()
    $servicesAfter = @(Get-ServiceList $servicesAfterValue)
    $missing = @($restoreServices | Where-Object {
        $wanted = Get-LauncherServiceName $_
        @($servicesAfter | Where-Object { (Get-LauncherServiceName $_) -ceq $wanted }).Count -eq 0
    })
    if ($missing.Count -gt 0) {
        $merged = (@($servicesAfter) + @($missing)) -join ':'
        # Every component was validated above; the argument has no shell metacharacters.
        Invoke-Adb -Arguments @('-s', $serial, 'shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', $merged) | Out-Host
        Invoke-Adb -Arguments @('-s', $serial, 'shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1') | Out-Host
    }
    $verifiedGlobal = ((Invoke-Adb -Arguments @('-s', $serial, 'shell', 'settings', 'get', 'secure', 'accessibility_enabled')) -join '').Trim()
    $verifiedServices = @(Get-ServiceList (((Invoke-Adb -Arguments @('-s', $serial, 'shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')) -join '').Trim()))
    if ($verifiedGlobal -ne '1') { throw 'Previously enabled launcher accessibility services could not be restored: accessibility is disabled.' }
    foreach ($component in $restoreServices) {
        $wanted = Get-LauncherServiceName $component
        if (@($verifiedServices | Where-Object { (Get-LauncherServiceName $_) -ceq $wanted }).Count -eq 0) { throw "Previously enabled service could not be restored: $component" }
    }
}
$homeAfter = (Invoke-Adb -Arguments $homeArgs) -join "`n"
Write-Host "HOME after upgrade: $homeAfter"
if ($homeBefore -eq $homeAfter) { Write-Host 'HOME resolution unchanged.' } else { throw 'HOME resolution changed during the upgrade; inspect the values above.' }
$verified = $false
for ($attempt = 0; $attempt -lt 10; $attempt++) {
    $result = (Invoke-Adb -Arguments @('-s', $serial, 'shell', 'content', 'call', '--uri', "content://$Package.theme", '--method', 'listThemes')) -join ' '
    if ($result -match '\bok=true\b' -and $result -match 'themes=(\[.*?\])') {
        try {
            $themes = @(ConvertFrom-Json -InputObject $Matches[1])
            if ($themes.Count -eq 1 -and $themes[0].id -eq 'johnson-health-tech' -and $themes[0].active -eq $true) { $verified = $true; break }
        } catch { Write-Host "Theme response not ready: $result" }
    }
    Start-Sleep -Seconds 2
}
if (-not $verified) { throw "Theme verification failed; last response: $result. Backup retained: $backup" }
Write-Host "Upgrade and Johnson theme verification succeeded. Backup: $backup"
