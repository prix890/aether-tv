# Aether TV

A lightweight, ad-free Android TV launcher (native Kotlin, no WebView).
No internet permission, no analytics, no ads, no permissions at all.

## Build (from a phone browser)
1. Repo > Actions > "Build Aether TV" > Run workflow. Wait for the green tick (about 5-10 min).
2. Repo > Releases > "Aether TV (latest build)" > AetherTV.apk.

## Install on the box
Use the Downloader app with:
github.com/prix890/aether-tv/releases/download/latest/AetherTV.apk
Allow "Install unknown apps" for Downloader when asked. Open Aether TV from the apps list.

## Update
Edit a file on GitHub, commit, run the workflow again, download again.
Raise versionCode in app/build.gradle.kts if the box refuses to update.

## Remote
OK opens. Hold OK (or Menu) on an app for favorites, move and hide. Back closes panels, never exits Home.

## Profiles and PIN
User (customizable) and Guest (all apps, no settings). Add more with the + tile.
PIN is optional per profile. Forgotten PIN: Android Settings > Apps > Aether TV > Clear storage.

## Recovery
The Airtel launcher is never changed. Uninstall Aether TV in Android Settings > Apps to return to normal.
