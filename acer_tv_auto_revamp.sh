#!/usr/bin/env bash
# ==============================================================================
# VibeOS TV — 1-Click Auto-Connect, Safe Debloat & Launcher Setup
# Target: Acer Google TV (Android 14) @ 192.168.1.33
# Usage:
#   chmod +x acer_tv_auto_revamp.sh
#   ./acer_tv_auto_revamp.sh               # Auto-connect, debloat & install APK
#   ./acer_tv_auto_revamp.sh --restore     # Revert to stock Google TV launcher
# ==============================================================================
set -e

TV_IP="${TV_IP:-192.168.1.33}"
APK_PATH="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
CUSTOM_PKG="com.vibeos.tvlauncher"

echo "========================================================"
echo "  VibeOS TV Revamp — Acer Google TV (Android 14)"
echo "  Target IP: ${TV_IP}"
echo "========================================================"

if ! command -v adb &> /dev/null; then
    echo "[!] Error: 'adb' command nahi mila."
    echo "    Mac: brew install android-platform-tools"
    echo "    Linux: sudo apt install adb"
    echo "    Windows: winget install Google.PlatformTools"
    exit 1
fi

adb start-server

# Step 1: Connect to 192.168.1.33 (try 5555 first, then ask or use python scanner if available)
echo "[1/5] Connecting to Acer Google TV at ${TV_IP}..."
adb connect "${TV_IP}:5555" || true
sleep 1

if ! adb devices | grep -q "${TV_IP}.*device"; then
    echo "[*] Port 5555 par direct connect nahi hua (Android 14 Wireless Debugging dynamic port use kar raha hoga)."
    if command -v python3 &> /dev/null && [ -f "acer_tv_auto_revamp.py" ]; then
        echo "[*] Launching Python Wireless Debugging Port Scanner..."
        python3 acer_tv_auto_revamp.py "$@"
        exit 0
    fi
    read -rp "    TV ke Wireless Debugging page par dikha IP:Port ka PORT number dalein (e.g. 39451): " WPORT
    adb connect "${TV_IP}:${WPORT}"
    echo "    TV screen par 'Always allow from this computer' tick karke Allow dabayein..."
    read -rp "    Allow karne ke baad Enter dabayein..." _
fi

SERIAL=$(adb devices | grep "${TV_IP}" | awk '{print $1}' | head -n 1)
if [ -z "${SERIAL}" ]; then
    echo "[!] TV connect nahi ho paya. Check karein ki TV aur Laptop same Wi-Fi par hain."
    exit 1
fi
echo "[+] Connected to ${SERIAL}"

if [ "${1:-}" == "--restore" ]; then
    echo "[*] Restoring stock Google TV Launcher and packages..."
    adb -s "${SERIAL}" shell pm enable --user 0 com.google.android.apps.tv.launcherx || true
    adb -s "${SERIAL}" shell pm enable --user 0 com.google.android.tungsten.setupwraith || true
    adb -s "${SERIAL}" shell pm enable --user 0 com.google.android.videos || true
    adb -s "${SERIAL}" shell settings put global window_animation_scale 1.0
    adb -s "${SERIAL}" shell settings put global transition_animation_scale 1.0
    adb -s "${SERIAL}" shell settings put global animator_duration_scale 1.0
    echo "[✓] Stock Google TV restored!"
    exit 0
fi

# Step 2: Backup package list
echo "[2/5] Backing up installed package list to acer_tv_packages_backup.txt..."
adb -s "${SERIAL}" shell pm list packages -e > acer_tv_packages_backup.txt

# Step 3: Tune Android 14 animations for snappy Apple TV feel
echo "[3/5] Applying 60fps UI & Animation tweaks..."
adb -s "${SERIAL}" shell settings put global window_animation_scale 0.5
adb -s "${SERIAL}" shell settings put global transition_animation_scale 0.5
adb -s "${SERIAL}" shell settings put global animator_duration_scale 0.5

# Step 4: Safe Non-Root Debloat
echo "[4/5] Disabling Acer / Google TV telemetry, ad services & bloatware..."
BLOAT_PKGS=(
    "com.google.android.feedback"
    "com.google.android.partnersetup"
    "com.google.android.syncadapters.calendar"
    "com.google.android.syncadapters.contacts"
    "com.android.dreams.basic"
    "com.android.printspooler"
    "com.android.bips"
    "com.google.android.apps.tachyon"
    "com.google.android.play.games"
    "com.google.android.videos"
)
for pkg in "${BLOAT_PKGS[@]}"; do
    adb -s "${SERIAL}" shell pm disable-user --user 0 "${pkg}" 2>/dev/null || true
done

# Step 5: Install Custom Launcher APK & Safely Switch Default Home
echo "[5/5] Checking for VibeOS Launcher APK..."
if [ -f "${APK_PATH}" ]; then
    echo "    Installing ${APK_PATH}..."
    adb -s "${SERIAL}" install -r "${APK_PATH}"
elif [ -f "app-debug.apk" ]; then
    echo "    Installing app-debug.apk..."
    adb -s "${SERIAL}" install -r "app-debug.apk"
fi

if adb -s "${SERIAL}" shell pm list packages "${CUSTOM_PKG}" | grep -q "${CUSTOM_PKG}"; then
    echo "    [+] VibeOS Launcher verified installed! Setting as default Home..."
    adb -s "${SERIAL}" shell cmd role add-role-holder android.app.role.HOME "${CUSTOM_PKG}" || true
    adb -s "${SERIAL}" shell cmd package set-home-activity "${CUSTOM_PKG}/.MainActivity" || true
    adb -s "${SERIAL}" shell pm disable-user --user 0 com.google.android.apps.tv.launcherx || true
    adb -s "${SERIAL}" shell pm disable-user --user 0 com.google.android.tungsten.setupwraith || true
    adb -s "${SERIAL}" shell am start -n "${CUSTOM_PKG}/.MainActivity"
    echo "[✓] VibeOS Apple TV Launcher active!"
else
    echo "    [i] Note: '${CUSTOM_PKG}' abhi install nahi hua hai, isliye stock launcher disable nahi kiya gaya (black screen se bachane ke liye)."
    echo "        Jab APK build/download ho jaye, tab './acer_tv_auto_revamp.sh' dobara run karein!"
fi
