# Hercules

Hercules is an Android app for browsing and installing modules on rooted devices. This repository contains version 1.0.1.

## Features

- Browse featured packages and recent updates, or search and filter the catalog.
- Review package details and inspect ZIP installer scripts for common risky patterns.
- Install Magisk, KernelSU, and APatch ZIP modules, as well as Xposed modules distributed as APKs.
- Check installed modules and available updates.
- Enable or disable the Haze Liquid Glass interface in Settings.
- Fall back to the bundled catalog when the remote catalog is unavailable.

## Security

Script inspection checks selected installer scripts for common risky patterns. See [SECURITY.md](SECURITY.md) for the inspected archive entries, detection rules, limitations, and reporting instructions.

## Requirements

- Android 8.0 (API 26) or later.
- Root access to install modules and inspect installed root modules. After installing an Xposed APK, enable it in the device's Xposed manager.
- Network access to refresh the catalog and download packages.

## Build

Use JDK 17 and Android SDK platform 35. With Android Studio on Windows:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

On macOS or Linux, set `JAVA_HOME` to JDK 17 and run:

```sh
./gradlew assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Links

- [Source repository](https://github.com/vuloid/hercules)
- [Releases](https://github.com/vuloid/hercules/releases)
