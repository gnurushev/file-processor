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

$progIdKey = "HKCU:\Software\Classes\$ProgId"
$progIdCommandKey = "$progIdKey\shell\open\command"

New-Item -Path $progIdKey -Force | Out-Null
Set-ItemProperty -Path $progIdKey -Name '(Default)' -Value "$ApplicationName PDF Handler" -Type String -Force

New-Item -Path $progIdCommandKey -Force | Out-Null
Set-ItemProperty -Path $progIdCommandKey -Name '(Default)' -Value $quotedCommand -Type String -Force

$pdfOpenWithKey = 'HKCU:\Software\Classes\.pdf\OpenWithProgids'
New-Item -Path $pdfOpenWithKey -Force | Out-Null
Set-ItemProperty -Path $pdfOpenWithKey -Name $ProgId -Value '' -Type String -Force

$appKey = "HKCU:\Software\Classes\Applications\$applicationExecutable"
$appCommandKey = "$appKey\shell\open\command"
$appSupportedTypesKey = "$appKey\SupportedTypes"

New-Item -Path $appKey -Force | Out-Null
Set-ItemProperty -Path $appKey -Name 'FriendlyAppName' -Value $ApplicationName -Type String -Force

New-Item -Path $appCommandKey -Force | Out-Null
Set-ItemProperty -Path $appCommandKey -Name '(Default)' -Value $quotedCommand -Type String -Force

New-Item -Path $appSupportedTypesKey -Force | Out-Null
Set-ItemProperty -Path $appSupportedTypesKey -Name '.pdf' -Value '' -Type String -Force

Write-Host "Registered $ApplicationName as a PDF Open with option for the current user."
