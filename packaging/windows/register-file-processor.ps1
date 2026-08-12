param(
    [Parameter(Mandatory = $true)]
    [string]$LauncherPath,

    [string]$ApplicationName = 'Patient Document Manager',

    [string]$ProgId = 'PatientDocumentManager.Pdf'
)

$resolvedLauncher = if (Test-Path $LauncherPath) {
    (Resolve-Path $LauncherPath).Path
} else {
    $null
}

if ($null -eq $resolvedLauncher) {
    throw "Launcher not found: $LauncherPath"
}

$applicationExecutable = Split-Path $resolvedLauncher -Leaf
$quotedCommand = "`"$resolvedLauncher`" `"%1`""

& reg.exe add "HKCU\Software\Classes\$ProgId" /ve /d "$ApplicationName PDF Handler" /f | Out-Null
& reg.exe add "HKCU\Software\Classes\$ProgId\shell\open\command" /ve /d $quotedCommand /f | Out-Null
& reg.exe add "HKCU\Software\Classes\.pdf\OpenWithProgids" /v $ProgId /t REG_NONE /f | Out-Null
& reg.exe add "HKCU\Software\Classes\Applications\$applicationExecutable" /v "FriendlyAppName" /t REG_SZ /d $ApplicationName /f | Out-Null
& reg.exe add "HKCU\Software\Classes\Applications\$applicationExecutable\shell\open\command" /ve /d $quotedCommand /f | Out-Null
& reg.exe add "HKCU\Software\Classes\Applications\$applicationExecutable\SupportedTypes" /v ".pdf" /t REG_SZ /d "" /f | Out-Null

Write-Host "Registered $ApplicationName as a PDF Open with option for the current user."
