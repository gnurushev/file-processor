param(
    [Parameter(Mandatory = $true)]
    [string]$LauncherPath
)

$resolvedLauncher = (Resolve-Path $LauncherPath).Path

if (-not (Test-Path $resolvedLauncher)) {
    throw "Launcher not found: $LauncherPath"
}

$progId = 'FileProcessor.Pdf'
$applicationName = Split-Path $resolvedLauncher -Leaf
$quotedCommand = "`"$resolvedLauncher`" `"%1`""

& reg.exe add "HKCU\Software\Classes\$progId" /ve /d "File Processor PDF Handler" /f | Out-Null
& reg.exe add "HKCU\Software\Classes\$progId\shell\open\command" /ve /d $quotedCommand /f | Out-Null
& reg.exe add "HKCU\Software\Classes\.pdf\OpenWithProgids" /v $progId /t REG_NONE /f | Out-Null
& reg.exe add "HKCU\Software\Classes\Applications\$applicationName\shell\open\command" /ve /d $quotedCommand /f | Out-Null
& reg.exe add "HKCU\Software\Classes\Applications\$applicationName\SupportedTypes" /v ".pdf" /t REG_SZ /d "" /f | Out-Null

Write-Host "Registered $applicationName as a PDF Open with option for the current user."

