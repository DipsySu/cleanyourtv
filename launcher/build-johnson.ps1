param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('com.ts.launcher', 'com.gathin.tvhome')]
    [string]$Package,
    [ValidateRange(1, 2147483647)]
    [int]$VersionCode = 6
)
$ErrorActionPreference = 'Stop'
if ($env:ANDROID_USER_HOME -or $env:ANDROID_SDK_HOME) {
    throw 'ANDROID_USER_HOME or ANDROID_SDK_HOME is set. Restore the original Windows user environment and confirm its original debug.keystore before building.'
}
$key = Join-Path $env:USERPROFILE '.android\debug.keystore'
if (-not (Test-Path -LiteralPath $key -PathType Leaf)) { throw "Original debug keystore not found: $key. Do not generate a new key." }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path -LiteralPath $sdk -PathType Container)) { throw "Android SDK not found: $sdk" }
$properties = Join-Path $PSScriptRoot 'local.properties'
if (Test-Path -LiteralPath $properties -PathType Leaf) {
    $sdkLines = @(Get-Content -LiteralPath $properties | Where-Object { $_ -match '^\s*sdk\.dir\s*[:=]' })
    if ($sdkLines.Count -gt 1) { throw 'Multiple sdk.dir entries in local.properties; resolve the SDK configuration before building.' }
    if ($sdkLines.Count -eq 1) {
        $value = ($sdkLines[0] -replace '^\s*sdk\.dir\s*[:=]\s*', '').Trim()
        # Decode the common Java-properties path escapes; reject unfamiliar escapes rather than guessing.
        $unrecognized = [regex]::Replace($value, '\\[\\: =]', '')
        if ($unrecognized.Contains('\')) { throw 'Unsupported sdk.dir escape in local.properties; confirm the SDK configuration before building.' }
        $value = [regex]::Replace($value, '\\([\\: =])', '$1')
        if (-not [IO.Path]::IsPathRooted($value)) { $value = Join-Path $PSScriptRoot $value }
        $configuredSdk = [IO.Path]::GetFullPath($value).TrimEnd('\', '/')
        $selectedSdk = [IO.Path]::GetFullPath($sdk).TrimEnd('\', '/')
        if (-not [string]::Equals($configuredSdk, $selectedSdk, [StringComparison]::OrdinalIgnoreCase)) {
            throw "local.properties sdk.dir ($configuredSdk) differs from selected SDK ($selectedSdk). Resolve the existing configuration before building; it was not changed."
        }
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $jbr = Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'jbr' } | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1
    if (-not $jbr) { throw 'Set JAVA_HOME to a JDK (or install Android Studio with its jbr).' }
    $env:JAVA_HOME = $jbr
}
$env:ANDROID_HOME = $sdk
$gradleArgs = @('testReleaseUnitTest', 'lintRelease', 'assembleRelease', '--no-daemon', '--console=plain', '-PlauncherProfile=johnson', "-PlauncherApplicationId=$Package", "-PlauncherVersionCode=$VersionCode")
Push-Location $PSScriptRoot
try {
    & (Join-Path $PSScriptRoot 'gradlew.bat') @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "Built APK not found: $apk" }
Write-Host "Built: $apk"
