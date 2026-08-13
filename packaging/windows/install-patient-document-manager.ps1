param(
    [string]$AppDownloadUrl,

    [string]$GhostscriptDownloadUrl = 'https://github.com/ArtifexSoftware/ghostpdl-downloads/releases/download/gs10071/gs10071w64.exe',

    [string]$InstallRoot = "$env:ProgramFiles\Patient Document Manager",

    [string]$DownloadDirectory = "$env:TEMP\patient-document-manager-installer",

    [string]$ProductName = 'Patient Document Manager',

    [string]$PrinterDriverName = 'Microsoft PS Class Driver'
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

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
No PostScript printer driver is available, so the '$ProductName' virtual printer cannot be created.

Expected driver: '$PreferredDriverName'
$driverErrorDetails

Install a Microsoft PostScript class driver in Windows (Print Server Properties > Drivers > Add...),
then rerun this installer wizard as Administrator.
"@
}

function Invoke-DownloadWithRetry {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,

        [Parameter(Mandatory = $true)]
        [string]$OutFile,

        [int]$MaxAttempts = 3
    )

    Write-Host "Downloading '$Uri' to '$OutFile'."
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            Invoke-WebRequest -Uri $Uri -OutFile $OutFile
            return
        }
        catch {
            if ($attempt -eq $MaxAttempts) {
                throw "Failed to download '$Uri' after $MaxAttempts attempts. $($_.Exception.Message)"
            }

            Start-Sleep -Seconds (2 * $attempt)
        }
    }
}

function Ensure-DownloadedAsset {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Label,

        [Parameter(Mandatory = $true)]
        [string]$Uri,

        [Parameter(Mandatory = $true)]
        [string]$OutFile
    )

    if (Test-Path -LiteralPath $OutFile) {
        $existingFile = Get-Item -LiteralPath $OutFile -ErrorAction SilentlyContinue
        if ($null -ne $existingFile -and $existingFile.Length -gt 0) {
            Write-Host "$Label already downloaded at '$OutFile'. Skipping download."
            return $OutFile
        }

        Write-Host "$Label exists at '$OutFile' but is empty. Re-downloading."
    }

    $downloadParent = Split-Path -Parent $OutFile
    if (-not [string]::IsNullOrWhiteSpace($downloadParent)) {
        New-Item -ItemType Directory -Force -Path $downloadParent | Out-Null
    }

    Invoke-DownloadWithRetry -Uri $Uri -OutFile $OutFile
    return $OutFile
}

function Resolve-InstallerAsset {
    param(
        [string]$PathOrUrl,
        [string[]]$Candidates
    )

    if (-not [string]::IsNullOrWhiteSpace($PathOrUrl)) {
        if (Test-Path -LiteralPath $PathOrUrl) {
            return (Resolve-Path -LiteralPath $PathOrUrl).Path
        }

        return $PathOrUrl
    }

    $searchRoots = @(
        $PSScriptRoot,
        (Split-Path $PSScriptRoot -Parent),
        (Join-Path $PSScriptRoot '..\..\build\installer'),
        (Join-Path (Split-Path $PSScriptRoot -Parent) 'build\installer'),
        (Get-Location).Path
    ) | Select-Object -Unique

    foreach ($root in $searchRoots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }

        foreach ($pattern in $Candidates) {
            $matches = Get-ChildItem -Path $root -Filter $pattern -File -ErrorAction SilentlyContinue
            if ($null -ne $matches -and $matches.Count -gt 0) {
                return $matches[0].FullName
            }
        }

        $zipFiles = Get-ChildItem -Path $root -File -Filter '*.zip' -ErrorAction SilentlyContinue
        foreach ($zipFile in $zipFiles) {
            foreach ($pattern in $Candidates) {
                $isMatch = $false
                if ($pattern.Contains('*')) {
                    $isMatch = $zipFile.Name -like $pattern
                }
                else {
                    $isMatch = $zipFile.Name -eq $pattern
                }

                if ($isMatch) {
                    return $zipFile.FullName
                }
            }
        }
    }

    return $null
}

$windowsIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$windowsPrincipal = New-Object Security.Principal.WindowsPrincipal($windowsIdentity)
if (-not $windowsPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'This installer must be run as Administrator.'
}

$appArchivePath = Resolve-InstallerAsset -PathOrUrl $AppDownloadUrl -Candidates @(
    'patient-document-manager-app.zip',
    'patient-document-manager-*.zip',
    '*patient-document-manager*app*.zip'
)

if ($null -eq $appArchivePath) {
    $appArchivePath = Join-Path $DownloadDirectory 'patient-document-manager-app.zip'
    if ([string]::IsNullOrWhiteSpace($AppDownloadUrl)) {
        throw 'No application payload was found locally and no -AppDownloadUrl was supplied. Provide a URL or place patient-document-manager-app.zip in the installer directory or build\installer.'
    }

    $appArchivePath = Ensure-DownloadedAsset -Label 'Application payload' -Uri $AppDownloadUrl -OutFile $appArchivePath
}
elseif ($appArchivePath -match '://') {
    $downloadSource = $appArchivePath
    $appArchivePath = Join-Path $DownloadDirectory 'patient-document-manager-app.zip'
    $appArchivePath = Ensure-DownloadedAsset -Label 'Application payload' -Uri $downloadSource -OutFile $appArchivePath
}
else {
    Write-Host "Using local application payload '$appArchivePath'."
}

$ghostscriptInstallerPath = Resolve-InstallerAsset -PathOrUrl $GhostscriptDownloadUrl -Candidates @(
    'ghostscript-installer.exe',
    'gs10010.exe',
    'gs*.exe'
)

if ($null -eq $ghostscriptInstallerPath -or $ghostscriptInstallerPath -match '^https?://') {
    $ghostscriptSource = if ($ghostscriptInstallerPath -match '^https?://') { $ghostscriptInstallerPath } else { $GhostscriptDownloadUrl }
    if ([string]::IsNullOrWhiteSpace($ghostscriptSource)) {
        throw 'No Ghostscript installer was found locally and no -GhostscriptDownloadUrl was supplied.'
    }

    $ghostscriptInstallerPath = Join-Path $DownloadDirectory 'ghostscript-installer.exe'
    $ghostscriptInstallerPath = Ensure-DownloadedAsset -Label 'Ghostscript installer' -Uri $ghostscriptSource -OutFile $ghostscriptInstallerPath
}
else {
    Write-Host "Using local Ghostscript installer '$ghostscriptInstallerPath'."
}

New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null

$stagingRoot = Join-Path $DownloadDirectory 'app-staging'
if (Test-Path $stagingRoot) {
    Remove-Item -Recurse -Force $stagingRoot
}

Expand-Archive -LiteralPath $appArchivePath -DestinationPath $stagingRoot -Force

$payloadSource = $stagingRoot
$topLevelEntries = Get-ChildItem -Path $stagingRoot
if ($topLevelEntries.Count -eq 1 -and $topLevelEntries[0].PSIsContainer) {
    $payloadSource = $topLevelEntries[0].FullName
}

Copy-Item -Path (Join-Path $payloadSource '*') -Destination $InstallRoot -Recurse -Force

& $ghostscriptInstallerPath /S

$ghostscriptExe = Get-ChildItem -Path "$env:ProgramFiles\gs" -Filter gswin64c.exe -File -Recurse |
    Sort-Object FullName -Descending |
    Select-Object -First 1

if ($null -eq $ghostscriptExe) {
    throw 'Ghostscript installed, but gswin64c.exe could not be located under Program Files.'
}

$launcherPath = Join-Path $InstallRoot "$ProductName.exe"
if (-not (Test-Path $launcherPath)) {
    throw "Installed launcher not found: $launcherPath"
}

$resolvedDriverName = Resolve-PostScriptPrinterDriver -PreferredDriverName $PrinterDriverName

$registerScript = Join-Path $PSScriptRoot 'register-file-processor.ps1'
$printerScript = Join-Path $PSScriptRoot 'install-patient-document-manager-printer.ps1'

& $registerScript -LauncherPath $launcherPath -ApplicationName $ProductName
if (-not $?) {
    throw 'Failed to register the PDF handoff integration.'
}

& $printerScript -LauncherPath $launcherPath -GhostscriptPath $ghostscriptExe.FullName -PrinterName $ProductName -DriverName $resolvedDriverName
if (-not $?) {
    throw 'Failed to install the Patient Document Manager printer.'
}

Write-Host "Installed $ProductName to $InstallRoot."
