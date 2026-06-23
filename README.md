# RepoForge

[English](README.md) | [Русский](README.ru.md)

**RepoForge** is a compact Android app for creating clean ZIP archives of project source code.

It is designed for preparing projects for AI audit, GitHub review, and safe local backups without build trash, temporary files, APK outputs, local machine paths, signing keys, or common secret files.

## Features

- Create clean ZIP archives from a selected project folder
- Scan source before packaging
- Show included files, skipped files, source size, and written size
- AI readiness check
- Security check for common secret and signing files
- Three archive modes:
  - AI Audit
  - GitHub Clean
  - Backup Safe
- Markdown report generation
- Package history
- Share latest ZIP
- Open output folder
- Copy archive path
- Copy package summary
- Works fully offline
- No root required
- No internet permission
- No analytics
- No telemetry

## Archive modes

### AI Audit

Best for sending a project to Claude, ChatGPT, or another AI reviewer.

This mode removes build output, Gradle cache, APK files, local machine configuration, IDE files, archives, logs, and common secret files.

### GitHub Clean

Best for preparing source code before publishing or reviewing a repository.

This mode keeps normal source files while removing generated build files, APK artifacts, caches, logs, and local configuration.

### Backup Safe

Best for local project backups.

This mode keeps more project files than AI Audit mode, but still excludes signing keys, secrets, local properties, APK outputs, temporary files, and cache folders.

## What RepoForge excludes

RepoForge excludes common unnecessary or sensitive files such as:

- `build/`
- `.gradle/`
- `.idea/`
- `.vscode/`
- `local.properties`
- `*.apk`
- `*.aab`
- `*.ap_`
- `*.idsig`
- `*.zip`
- `*.7z`
- `*.tar`
- `*.tar.gz`
- `*.tgz`
- `*.log`
- `*.tmp`
- `*.bak`
- `*.jks`
- `*.keystore`
- `*.pem`
- `*.key`
- `*.p12`
- `*.pfx`
- `*.crt`
- `*.cer`
- `.env`
- `.env.local`
- `secrets.properties`
- `google-services.json`
- `service-account.json`

## Output

Created packages are saved to:

```text
Downloads/RepoForge/
```

Example output:

```text
GPD_repoforge_ai_audit_2026-06-23_18-30-00.zip
GPD_repoforge_report_ai_audit_2026-06-23_18-30-00.md
```

## Privacy

RepoForge works locally on the device.

It does not:

- request internet access
- upload projects
- use accounts
- collect analytics
- send telemetry
- require root access

Folder access is handled through Android Storage Access Framework.

## Requirements

- Android 10 or newer
- No root required
- No internet required

## Package

```text
Application ID: app.repoforge
Version: 1.0
```

## Build

```bash
gradle :app:assembleDebug
```

## Status

RepoForge v1.0 is the first public clean release.

The app is intentionally minimal, offline, and focused on one job: creating clean project archives.
