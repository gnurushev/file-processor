param(
    [string]$AppDownloadUrl,
    [string]$GhostscriptDownloadUrl = 'https://github.com/ArtifexSoftware/ghostpdl-downloads/releases/download/gs10071/gs10071w64.exe',
    [string]$InstallRoot = "$env:ProgramFiles\Patient Document Manager",
    [string]$DownloadDirectory = "$env:TEMP\patient-document-manager-installer"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-LocalInstallerAsset {
    param(
        [string]$FileName,
        [string[]]$SearchRoots
    )

    foreach ($root in $SearchRoots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }

        $candidate = Join-Path $root $FileName
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }

        $matches = Get-ChildItem -Path $root -Filter $FileName -File -Recurse -ErrorAction SilentlyContinue
        if ($matches) {
            return $matches[0].FullName
        }
    }

    return $null
}

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Start-ElevatedSelf {
    $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    if ($AppDownloadUrl) {
        $arguments += " -AppDownloadUrl `"$AppDownloadUrl`""
    }
    if ($GhostscriptDownloadUrl) {
        $arguments += " -GhostscriptDownloadUrl `"$GhostscriptDownloadUrl`""
    }
    if ($InstallRoot) {
        $arguments += " -InstallRoot `"$InstallRoot`""
    }
    if ($DownloadDirectory) {
        $arguments += " -DownloadDirectory `"$DownloadDirectory`""
    }

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = 'powershell.exe'
    $startInfo.Arguments = $arguments
    $startInfo.Verb = 'RunAs'
    $startInfo.WorkingDirectory = (Get-Location).Path
    [System.Diagnostics.Process]::Start($startInfo) | Out-Null
    exit 0
}

function New-InstallerForm {
    param(
        [string]$InitialAppUrl,
        [string]$InitialGhostscriptUrl,
        [string]$InitialInstallRoot,
        [string]$InitialDownloadDir
    )

    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing

    $form = New-Object System.Windows.Forms.Form
    $form.Text = 'Patient Document Manager Setup'
    $form.Size = New-Object System.Drawing.Size(760, 500)
    $form.StartPosition = 'CenterScreen'
    $form.FormBorderStyle = 'FixedDialog'
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false
    $form.TopMost = $false

    $titleLabel = New-Object System.Windows.Forms.Label
    $titleLabel.Text = 'Install Patient Document Manager'
    $titleLabel.AutoSize = $true
    $titleLabel.Font = New-Object System.Drawing.Font('Segoe UI', 16, [System.Drawing.FontStyle]::Bold)
    $titleLabel.Location = New-Object System.Drawing.Point(24, 18)
    $form.Controls.Add($titleLabel)

    $subtitleLabel = New-Object System.Windows.Forms.Label
    $subtitleLabel.Text = 'This wizard installs the app, registers the PDF file association, installs Ghostscript, and configures the print watcher.'
    $subtitleLabel.Width = 680
    $subtitleLabel.Height = 44
    $subtitleLabel.Location = New-Object System.Drawing.Point(24, 58)
    $form.Controls.Add($subtitleLabel)

    $appLabel = New-Object System.Windows.Forms.Label
    $appLabel.Text = 'App archive or download URL:'
    $appLabel.Location = New-Object System.Drawing.Point(24, 114)
    $appLabel.Size = New-Object System.Drawing.Size(220, 24)
    $form.Controls.Add($appLabel)

    $appUrlBox = New-Object System.Windows.Forms.TextBox
    $appUrlBox.Location = New-Object System.Drawing.Point(24, 138)
    $appUrlBox.Size = New-Object System.Drawing.Size(590, 24)
    $appUrlBox.Text = $InitialAppUrl
    $form.Controls.Add($appUrlBox)

    $appBrowseButton = New-Object System.Windows.Forms.Button
    $appBrowseButton.Text = 'Browse'
    $appBrowseButton.Location = New-Object System.Drawing.Point(628, 136)
    $appBrowseButton.Size = New-Object System.Drawing.Size(90, 28)
    $appBrowseButton.Add_Click({
        $dialog = New-Object System.Windows.Forms.OpenFileDialog
        $dialog.Filter = 'ZIP files (*.zip)|*.zip|All files (*.*)|*.*'
        $dialog.Title = 'Select the packaged Patient Document Manager zip'
        if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            $appUrlBox.Text = $dialog.FileName
        }
    }.GetNewClosure())
    $form.Controls.Add($appBrowseButton)

    $ghostscriptLabel = New-Object System.Windows.Forms.Label
    $ghostscriptLabel.Text = 'Ghostscript installer URL or path:'
    $ghostscriptLabel.Location = New-Object System.Drawing.Point(24, 174)
    $ghostscriptLabel.Size = New-Object System.Drawing.Size(220, 24)
    $form.Controls.Add($ghostscriptLabel)

    $ghostscriptBox = New-Object System.Windows.Forms.TextBox
    $ghostscriptBox.Location = New-Object System.Drawing.Point(24, 198)
    $ghostscriptBox.Size = New-Object System.Drawing.Size(590, 24)
    $ghostscriptBox.Text = $InitialGhostscriptUrl
    $form.Controls.Add($ghostscriptBox)

    $ghostscriptBrowseButton = New-Object System.Windows.Forms.Button
    $ghostscriptBrowseButton.Text = 'Browse'
    $ghostscriptBrowseButton.Location = New-Object System.Drawing.Point(628, 196)
    $ghostscriptBrowseButton.Size = New-Object System.Drawing.Size(90, 28)
    $ghostscriptBrowseButton.Add_Click({
        $dialog = New-Object System.Windows.Forms.OpenFileDialog
        $dialog.Filter = 'Executables (*.exe)|*.exe|All files (*.*)|*.*'
        $dialog.Title = 'Select the Ghostscript installer'
        if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            $ghostscriptBox.Text = $dialog.FileName
        }
    }.GetNewClosure())
    $form.Controls.Add($ghostscriptBrowseButton)

    $installRootLabel = New-Object System.Windows.Forms.Label
    $installRootLabel.Text = 'Install folder:'
    $installRootLabel.Location = New-Object System.Drawing.Point(24, 236)
    $installRootLabel.Size = New-Object System.Drawing.Size(140, 24)
    $form.Controls.Add($installRootLabel)

    $installRootBox = New-Object System.Windows.Forms.TextBox
    $installRootBox.Location = New-Object System.Drawing.Point(24, 260)
    $installRootBox.Size = New-Object System.Drawing.Size(590, 24)
    $installRootBox.Text = $InitialInstallRoot
    $form.Controls.Add($installRootBox)

    $downloadDirLabel = New-Object System.Windows.Forms.Label
    $downloadDirLabel.Text = 'Temporary download folder:'
    $downloadDirLabel.Location = New-Object System.Drawing.Point(24, 296)
    $downloadDirLabel.Size = New-Object System.Drawing.Size(180, 24)
    $form.Controls.Add($downloadDirLabel)

    $downloadDirBox = New-Object System.Windows.Forms.TextBox
    $downloadDirBox.Location = New-Object System.Drawing.Point(24, 320)
    $downloadDirBox.Size = New-Object System.Drawing.Size(590, 24)
    $downloadDirBox.Text = $InitialDownloadDir
    $form.Controls.Add($downloadDirBox)

    $installButton = New-Object System.Windows.Forms.Button
    $installButton.Text = 'Install'
    $installButton.BackColor = [System.Drawing.Color]::FromArgb(42, 115, 204)
    $installButton.ForeColor = [System.Drawing.Color]::White
    $installButton.Location = New-Object System.Drawing.Point(520, 370)
    $installButton.Size = New-Object System.Drawing.Size(110, 38)
    $installButton.Add_Click({
        $appValue = $appUrlBox.Text.Trim()
        $ghostscriptValue = $ghostscriptBox.Text.Trim()
        $installRootValue = $installRootBox.Text.Trim()
        $downloadDirValue = $downloadDirBox.Text.Trim()

        if ([string]::IsNullOrWhiteSpace($appValue)) {
            [System.Windows.Forms.MessageBox]::Show('Select or enter the packaged app zip or its download URL.', 'Install setup', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Warning)
            return
        }

        if ([string]::IsNullOrWhiteSpace($ghostscriptValue)) {
            [System.Windows.Forms.MessageBox]::Show('Select or enter the Ghostscript installer URL or path.', 'Install setup', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Warning)
            return
        }

        if ([string]::IsNullOrWhiteSpace($installRootValue)) {
            [System.Windows.Forms.MessageBox]::Show('Select an install folder.', 'Install setup', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Warning)
            return
        }

        $installScript = Join-Path (Split-Path $PSCommandPath -Parent) 'install-patient-document-manager.ps1'
        if (-not (Test-Path -LiteralPath $installScript)) {
            [System.Windows.Forms.MessageBox]::Show('Installer script was not found next to this wizard.', 'Install setup', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error)
            return
        }

        $installButton.Enabled = $false
        $installButton.Text = 'Installing...'
        $installButton.Refresh()

        try {
            & $installScript -AppDownloadUrl $appValue -GhostscriptDownloadUrl $ghostscriptValue -InstallRoot $installRootValue -DownloadDirectory $downloadDirValue -ProductName 'Patient Document Manager'
            [System.Windows.Forms.MessageBox]::Show('Patient Document Manager was installed successfully.', 'Install setup', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Information)
            $form.Close()
        }
        catch {
            [System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Install setup', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error)
            $installButton.Enabled = $true
            $installButton.Text = 'Install'
        }
    }.GetNewClosure())
    $form.Controls.Add($installButton)

    $cancelButton = New-Object System.Windows.Forms.Button
    $cancelButton.Text = 'Cancel'
    $cancelButton.Location = New-Object System.Drawing.Point(390, 370)
    $cancelButton.Size = New-Object System.Drawing.Size(110, 38)
    $cancelButton.Add_Click({ $form.Close() }.GetNewClosure())
    $form.Controls.Add($cancelButton)

    return $form
}

if (-not (Test-IsAdministrator)) {
    Start-ElevatedSelf
}

$scriptRoot = Split-Path -Parent $PSCommandPath
$searchRoots = @(
    $scriptRoot,
    (Join-Path $scriptRoot '..\..\build\installer'),
    (Join-Path $scriptRoot '..\build\installer'),
    (Join-Path $scriptRoot '..\..\build')
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

$localAppArchive = Get-LocalInstallerAsset -FileName 'patient-document-manager-app.zip' -SearchRoots $searchRoots
$initialAppUrl = if ($AppDownloadUrl) { $AppDownloadUrl } elseif ($localAppArchive) { $localAppArchive } else { '' }

$form = New-InstallerForm -InitialAppUrl $initialAppUrl -InitialGhostscriptUrl $GhostscriptDownloadUrl -InitialInstallRoot $InstallRoot -InitialDownloadDir $DownloadDirectory

[void]$form.ShowDialog()
