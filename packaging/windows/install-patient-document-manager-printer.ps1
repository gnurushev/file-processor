param(
    [Parameter(Mandatory = $true)]
    [string]$LauncherPath,

    [Parameter(Mandatory = $true)]
    [string]$GhostscriptPath,

    [string]$PrinterName = 'Patient Document Manager',

    [string]$DriverName = 'Microsoft PS Class Driver',

    [string]$ProgramDataRoot = "$env:ProgramData\PatientDocumentManager"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Resolve-PostScriptPrinterDriver {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PreferredDriverName
    )

    $driver = Get-PrinterDriver -Name $PreferredDriverName -ErrorAction SilentlyContinue
    if ($null -ne $driver) {
        return $driver.Name
    }

    $addDriverError = $null
    try {
        Add-PrinterDriver -Name $PreferredDriverName -ErrorAction Stop | Out-Null
    }
    catch {
        $addDriverError = $_.Exception.Message
    }

    $driver = Get-PrinterDriver -Name $PreferredDriverName -ErrorAction SilentlyContinue
    if ($null -ne $driver) {
        return $driver.Name
    }

    $fallbackDriver = Get-PrinterDriver -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like '*PS Class Driver*' } |
        Sort-Object Name |
        Select-Object -First 1
    if ($null -ne $fallbackDriver) {
        Write-Host "Using fallback PostScript printer driver '$($fallbackDriver.Name)'."
        return $fallbackDriver.Name
    }

    $driverErrorDetails = if ([string]::IsNullOrWhiteSpace($addDriverError)) {
        'Automatic driver installation did not make the driver available.'
    }
    else {
        "Automatic driver installation failed: $addDriverError"
    }

    throw @"
No PostScript printer driver is available, so the '$PrinterName' virtual printer cannot be created.

Expected driver: '$PreferredDriverName'
$driverErrorDetails

Install a Microsoft PostScript class driver in Windows (Print Server Properties > Drivers > Add...),
then rerun this installer wizard as Administrator.
"@
}

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

$resolvedDriverName = Resolve-PostScriptPrinterDriver -PreferredDriverName $DriverName

$existingPort = Get-PrinterPort -Name $portName -ErrorAction SilentlyContinue
if ($null -eq $existingPort) {
    Add-PrinterPort -Name $portName | Out-Null
}

$existingPrinter = Get-Printer -Name $PrinterName -ErrorAction SilentlyContinue
if ($null -eq $existingPrinter) {
    Add-Printer -Name $PrinterName -DriverName $resolvedDriverName -PortName $portName | Out-Null
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
