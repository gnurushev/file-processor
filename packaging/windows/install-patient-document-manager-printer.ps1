param(
    [Parameter(Mandatory = $true)]
    [string]$LauncherPath,

    [Parameter(Mandatory = $true)]
    [string]$GhostscriptPath,

    [string]$PrinterName = 'Patient Document Manager',

    [string]$DriverName = 'Microsoft PS Class Driver',

    [string]$ProgramDataRoot = "$env:ProgramData\PatientDocumentManager"
)

$resolvedLauncher = if (Test-Path $LauncherPath) {
    (Resolve-Path $LauncherPath).Path
} else {
    $null
}
$resolvedGhostscript = if (Test-Path $GhostscriptPath) {
    (Resolve-Path $GhostscriptPath).Path
} else {
    $null
}

if ($null -eq $resolvedLauncher) {
    throw "Launcher not found: $LauncherPath"
}

if ($null -eq $resolvedGhostscript) {
    throw "Ghostscript executable not found: $GhostscriptPath"
}

$watchDirectory = Join-Path $ProgramDataRoot 'print-jobs'
$archiveDirectory = Join-Path $watchDirectory 'processed'
$portName = Join-Path $watchDirectory 'patient-document-manager.ps'
$taskName = 'PatientDocumentManager Print Watcher'

New-Item -ItemType Directory -Force -Path $watchDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $archiveDirectory | Out-Null

$driver = Get-PrinterDriver -Name $DriverName -ErrorAction SilentlyContinue
if ($null -eq $driver) {
    throw "Required printer driver '$DriverName' is not installed on this machine."
}

$existingPort = Get-PrinterPort -Name $portName -ErrorAction SilentlyContinue
if ($null -eq $existingPort) {
    Add-PrinterPort -Name $portName | Out-Null
}

$existingPrinter = Get-Printer -Name $PrinterName -ErrorAction SilentlyContinue
if ($null -eq $existingPrinter) {
    Add-Printer -Name $PrinterName -DriverName $DriverName -PortName $portName | Out-Null
}

$watchArguments = @(
    '--watch-print-jobs'
    "`"$watchDirectory`""
    '--archive-directory'
    "`"$archiveDirectory`""
    '--ghostscript'
    "`"$resolvedGhostscript`""
) -join ' '

$taskAction = New-ScheduledTaskAction -Execute $resolvedLauncher -Argument $watchArguments
$taskTrigger = New-ScheduledTaskTrigger -AtLogOn
$taskPrincipal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Highest
$taskSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries

Register-ScheduledTask -TaskName $taskName -Action $taskAction -Trigger $taskTrigger -Principal $taskPrincipal -Settings $taskSettings -Force | Out-Null

Write-Host "Installed printer '$PrinterName' and scheduled the print watcher."
