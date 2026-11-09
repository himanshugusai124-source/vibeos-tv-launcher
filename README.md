# VibeOS TV Launcher — Apple TV-Style OS Revamp for Acer Google TV (Android 14)

A fast, zero-ad, glassmorphic Android TV / Google TV home launcher inspired by **Apple tvOS 18**, paired with an automated ADB wireless port scanner & safe debloater for **Acer Google TV (`192.168.1.33`)**.

## Key Features
- **Apple tvOS Glassmorphic Top Dock**: Pin up to 6 favorite apps with smooth 60fps spring zoom & dynamic ambient background glow.
- **Native + Sideloaded Apps in One Grid**: Automatically displays both Android TV (`LEANBACK_LAUNCHER`) and sideloaded mobile/tablet (`LAUNCHER`) apps without needing a separate Sideload Launcher.
- **Remote D-Pad Long-Press Menu**: Hold the center `OK` button on any app to Pin/Unpin to Dock, open Android App Info / Force Stop, or Uninstall.
- **Brick-Safe Stock Launcher Switcher**: The automation script verifies `com.vibeos.tvlauncher` is installed on the TV before disabling Google TV's ad-heavy `launcherx`, and includes a `--restore` flag to revert anytime.
- **Cloud APK Build (No Android Studio Needed)**: Push this repo to GitHub and GitHub Actions (`.github/workflows/build-apk.yml`) automatically compiles `app-debug.apk` for you in 2 minutes.

## Quick Start (Acer Google TV @ `192.168.1.33`)

### Step 1: Get the APK (2 Ways)
1. **Via GitHub Actions (Zero local setup)**: Push this repo to your GitHub account -> open the **Actions** tab -> click the latest workflow run -> download **`VibeOS-TV-Launcher-APK`** (`app-debug.apk`) into this folder.
2. **Or via Android Studio / Gradle locally**: Run `gradle assembleDebug` and the APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Step 2: Run the 1-Click Auto-Connect & Debloat Script
From your laptop on the same Wi-Fi network:

```bash
# Cross-platform Python script (Windows, macOS, Linux) — includes Wireless Debugging Port Scanner!
python3 acer_tv_auto_revamp.py --ip 192.168.1.33 --apk app-debug.apk

# Or via Bash (macOS / Linux / Git Bash):
chmod +x acer_tv_auto_revamp.sh
./acer_tv_auto_revamp.sh
```

### Revert to Stock Google TV Anytime
```bash
python3 acer_tv_auto_revamp.py --ip 192.168.1.33 --restore
```
