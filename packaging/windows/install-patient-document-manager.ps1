param(
    [string]$AppDownloadUrl,

    [string]$GhostscriptDownloadUrl = 'https://github.com/ArtifexSoftware/ghostpdl-downloads/releases/download/gs10010/gs10010.exe',

    [string]$InstallRoot = "$env:ProgramFiles\Patient Document Manager",

    [string]$DownloadDirectory = "$env:TEMP\patient-document-manager-installer",

    [string]$ProductName = 'Patient Document Manager'
)

$ErrorActionPreference = 'Stop'

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
    New-Item -ItemType Directory -Force -Path $DownloadDirectory | Out-Null
    Write-Host "Downloading application payload from $AppDownloadUrl"
    if ([string]::IsNullOrWhiteSpace($AppDownloadUrl)) {
        throw 'No application payload was found locally and no -AppDownloadUrl was supplied. Provide a URL or place patient-document-manager-app.zip in the installer directory or build\installer.'
    }

    Invoke-WebRequest -Uri $AppDownloadUrl -OutFile $appArchivePath
}
elseif ($appArchivePath -match '://') {
    $downloadSource = $appArchivePath
    $appArchivePath = Join-Path $DownloadDirectory 'patient-document-manager-app.zip'
    New-Item -ItemType Directory -Force -Path $DownloadDirectory | Out-Null
    Invoke-WebRequest -Uri $downloadSource -OutFile $appArchivePath
}

$ghostscriptInstallerPath = Resolve-InstallerAsset -PathOrUrl $GhostscriptDownloadUrl -Candidates @(
    'ghostscript-installer.exe',
    'gs10010.exe',
    'gs*.exe'
)

if ($null -eq $ghostscriptInstallerPath -or $ghostscriptInstallerPath -match '^https?://') {
    $ghostscriptInstallerPath = Join-Path $DownloadDirectory 'ghostscript-installer.exe'
    New-Item -ItemType Directory -Force -Path $DownloadDirectory | Out-Null
    Invoke-WebRequest -Uri $GhostscriptDownloadUrl -OutFile $ghostscriptInstallerPath
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

$registerScript = Join-Path $PSScriptRoot 'register-file-processor.ps1'
$printerScript = Join-Path $PSScriptRoot 'install-patient-document-manager-printer.ps1'

& $registerScript -LauncherPath $launcherPath -ApplicationName $ProductName
if (-not $?) {
    throw 'Failed to register the PDF handoff integration.'
}

& $printerScript -LauncherPath $launcherPath -GhostscriptPath $ghostscriptExe.FullName -PrinterName $ProductName
if (-not $?) {
    throw 'Failed to install the Patient Document Manager printer.'
}

Write-Host "Installed $ProductName to $InstallRoot."
