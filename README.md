# file-processor

Windows-first Java 21 desktop application for handling downloaded PDF files, prompting for login, loading the selected PDF, collecting a patient ID, and retrieving related documents.

## Stack

- Java 21
- Gradle
- JavaFX
- Apache PDFBox

## Current workflow

1. Windows can hand a `.pdf` file to the app through an **Open with** flow.
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

That task creates a packaged app image under `build\app-image\output\FileProcessor`.

## Register the packaged app for PDF "Open with"

After building the app image, register its launcher for the current user:

```powershell
.\packaging\windows\register-file-processor.ps1 -LauncherPath .\build\app-image\output\FileProcessor\FileProcessor.exe
```

This first implementation uses mocked authentication and mocked patient-document responses so the desktop flow can be exercised before wiring a real backend.
