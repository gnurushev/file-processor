param(
    [Parameter(Mandatory = $true)]
    [string]$AppDownloadUrl,

    [string]$GhostscriptDownloadUrl = 'https://github.com/ArtifexSoftware/ghostpdl-downloads/releases/download/gs10010/gs10010.exe',

    [string]$InstallRoot = "$env:ProgramFiles\Patient Document Manager",

    [string]$DownloadDirectory = "$env:TEMP\patient-document-manager-installer",

    [string]$ProductName = 'Patient Document Manager'
)

$windowsIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$windowsPrincipal = New-Object Security.Principal.WindowsPrincipal($windowsIdentity)
if (-not $windowsPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'This installer must be run as Administrator.'
}

New-Item -ItemType Directory -Force -Path $DownloadDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null

$appArchivePath = Join-Path $DownloadDirectory 'patient-document-manager-app.zip'
$ghostscriptInstallerPath = Join-Path $DownloadDirectory 'ghostscript-installer.exe'

Invoke-WebRequest -Uri $AppDownloadUrl -OutFile $appArchivePath
Invoke-WebRequest -Uri $GhostscriptDownloadUrl -OutFile $ghostscriptInstallerPath

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
