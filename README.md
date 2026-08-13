# file-processor

Windows-first Java 21 desktop application for handling downloaded PDF files, prompting for login, loading the selected PDF, collecting a patient ID, and retrieving related documents.

## Stack

- Java 21
- Gradle
- JavaFX
- Apache PDFBox

## Current workflow

1. Windows can hand a `.pdf` file to **Patient Document Manager** through an **Open with** flow.
2. The app launches or reuses the existing window.
3. If the user is not signed in, a login overlay is shown.
4. Once signed in, the selected PDF is loaded and previewed.
5. The app prompts for a patient ID and loads mocked related documents.

## Run locally

Use a Java 21 JDK.

```powershell
.\gradlew.bat run
```

To simulate Windows handing the app a PDF:

```powershell
.\gradlew.bat run --args="C:\path\to\file.pdf"
```

## Package a Windows app image

```powershell
.\gradlew.bat packageWindowsApp
```

That task creates a packaged app image under `build\app-image\output\Patient Document Manager`.

To also produce the downloadable app payload used by the bootstrap installer:

```powershell
.\gradlew.bat zipWindowsAppImage prepareWindowsInstallerAssets
```

That stages the packaged app zip plus installer scripts under `build\installer`.

## Register the packaged app for PDF "Open with"

After building the app image, register its launcher for the current user:

```powershell
.\packaging\windows\register-file-processor.ps1 -LauncherPath '.\build\app-image\output\Patient Document Manager\Patient Document Manager.exe'
```

## Install the app and printer integration

The repository now includes a guided installer wizard and a bootstrap installer script that can:

1. download the packaged app zip,
2. install it under `Program Files`,
3. register the app as **Patient Document Manager** for PDF handoff,
4. install a Windows printer queue named **Patient Document Manager**, and
5. start a background watcher that converts print jobs into PDFs and forwards them to the app.

For the end-user experience, launch the installer wizard:

```powershell
PowerShell -ExecutionPolicy Bypass -File .\packaging\windows\launch-installer-wizard.ps1
```

The same steps can also be run directly in automation mode:

```powershell
PowerShell -ExecutionPolicy Bypass -File .\packaging\windows\install-patient-document-manager.ps1 `
  -AppDownloadUrl https://example.invalid/patient-document-manager-app.zip
```

`GhostscriptDownloadUrl` is already set to a default installer URL, so the user does not need to pass it unless they want to override it. The printer install uses the built-in **Microsoft PS Class Driver** plus Ghostscript to turn spool files into PDFs that the app opens automatically.

This implementation still uses mocked authentication and mocked patient-document responses so the desktop flow can be exercised before wiring a real backend.
