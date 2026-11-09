#!/usr/bin/env python3
"""
VibeOS TV Revamp Tool — Double-Click Ready!
Auto-downloads ADB if missing, scans & connects to Acer Google TV (192.168.1.33),
applies 60fps speed tweaks, safely removes bloatware/ads, and installs VibeOS Launcher.
"""

import argparse
import concurrent.futures
import os
import platform
import shutil
import socket
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

DEFAULT_TV_IP = "192.168.1.33"
CUSTOM_LAUNCHER_PKG = "com.vibeos.tvlauncher"
CUSTOM_LAUNCHER_ACTIVITY = "com.vibeos.tvlauncher/.MainActivity"

SAFE_DEBLOAT_PACKAGES = [
    "com.google.android.feedback",
    "com.google.android.partnersetup",
    "com.google.android.syncadapters.calendar",
    "com.google.android.syncadapters.contacts",
    "com.android.dreams.basic",
    "com.android.printspooler",
    "com.android.bips",
    "com.google.android.apps.tachyon",
    "com.google.android.play.games",
    "com.google.android.videos",
]

STOCK_LAUNCHER_PACKAGES = [
    "com.google.android.apps.tv.launcherx",
    "com.google.android.tungsten.setupwraith",
]


def ensure_adb() -> str:
    """Finds `adb` in PATH or local folder; if missing, auto-downloads official Google Platform Tools!"""
    script_dir = Path(__file__).resolve().parent
    local_adb_win = script_dir / "platform-tools" / "adb.exe"
    local_adb_unix = script_dir / "platform-tools" / "adb"

    if local_adb_win.exists():
        return str(local_adb_win)
    if local_adb_unix.exists():
        return str(local_adb_unix)

    adb_path = shutil.which("adb")
    if adb_path:
        return adb_path

    for candidate in [
        Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe",
        Path.home() / "Library/Android/sdk/platform-tools/adb",
        Path.home() / "Android/Sdk/platform-tools/adb",
    ]:
        if candidate.exists():
            return str(candidate)

    # Auto-download official Google Platform Tools so the user doesn't have to install ADB manually!
    sys_name = platform.system().lower()
    if "windows" in sys_name:
        url = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
    elif "darwin" in sys_name:
        url = "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
    else:
        url = "https://dl.google.com/android/repository/platform-tools-latest-linux.zip"

    zip_dest = script_dir / "platform-tools.zip"
    print("[*] `adb` aapke PC par installed nahi mila.")
    print(f"[*] Official Google ADB Platform-Tools auto-download ho raha hai ({url})...")
    urllib.request.urlretrieve(url, zip_dest)
    print("[*] Extracting platform-tools...")
    with zipfile.ZipFile(zip_dest, "r") as zf:
        zf.extractall(script_dir)
    zip_dest.unlink(missing_ok=True)

    if local_adb_unix.exists() and "windows" not in sys_name:
        os.chmod(local_adb_unix, 0o755)
        return str(local_adb_unix)
    if local_adb_win.exists():
        return str(local_adb_win)
    raise RuntimeError("Failed to set up ADB automatically.")


def run_adb(adb: str, args: list[str], serial: str | None = None, timeout: int = 20) -> subprocess.CompletedProcess:
    cmd = [adb]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def check_port(ip: str, port: int, timeout: float = 0.25) -> int | None:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(timeout)
            if s.connect_ex((ip, port)) == 0:
                return port
    except OSError:
        pass
    return None


def scan_adb_ports(ip: str) -> list[int]:
    print(f"\n[*] Acer TV ({ip}) par active ADB / Wireless Debugging port scan ho raha hai...")
    open_ports = []
    if check_port(ip, 5555, timeout=0.5):
        open_ports.append(5555)
        print(f"    [+] Standard ADB port mil gaya: {ip}:5555")
        return open_ports

    ports_to_scan = list(range(30000, 50000))
    with concurrent.futures.ThreadPoolExecutor(max_workers=120) as executor:
        futures = {executor.submit(check_port, ip, p, 0.2): p for p in ports_to_scan}
        for future in concurrent.futures.as_completed(futures):
            res = future.result()
            if res is not None:
                open_ports.append(res)
    open_ports.sort()
    if open_ports:
        print(f"    [+] Open Wireless Debugging ports mile: {open_ports}")
    else:
        print(f"    [-] {ip} par abhi koi open port nahi dikha.")
    return open_ports


def auto_connect_tv(adb: str, ip: str) -> str:
    run_adb(adb, ["start-server"])

    devices_out = run_adb(adb, ["devices"]).stdout
    for line in devices_out.splitlines()[1:]:
        if ip in line and "\tdevice" in line:
            serial = line.split("\t")[0].strip()
            print(f"[+] TV pehle se connected hai: {serial}")
            return serial

    open_ports = scan_adb_ports(ip)
    for port in open_ports:
        target = f"{ip}:{port}"
        print(f"[*] Connecting to {target}...")
        res = run_adb(adb, ["connect", target])
        print(f"    {(res.stdout + res.stderr).strip()}")
        time.sleep(1.5)
        devices_out = run_adb(adb, ["devices"]).stdout
        for line in devices_out.splitlines()[1:]:
            if target in line:
                if "\tunauthorized" in line:
                    print("\n[!] TV Screen Dekhein: 'Allow USB/Wireless debugging?' popup aaya hoga!")
                    print("    TV Remote se 'Always allow from this computer' tick karke 'Allow' dabayein.")
                    input("    TV par Allow karne ke baad yahan Enter dabayein...")
                if "\tdevice" in run_adb(adb, ["devices"]).stdout:
                    print(f"[+] Acer Google TV ({target}) successfully connected!")
                    return target

    print("\n========================================================")
    print("  Manual Wireless Debugging / Port Connection")
    print("========================================================")
    print("1) Agar TV par Wireless Debugging -> 'Pair device with pairing code' khula hai, toh 'p' likh kar Enter dabayein.")
    print("2) Agar TV par Wireless Debugging ke main page par IP:Port (jaise 192.168.1.33:39123) dikh raha hai, toh sirf woh PORT number dalein.")
    print("3) Agar standard port 5555 try karna hai, toh Enter dabayein.")
    choice = input("\nApna option dalein (p / port number / Enter): ").strip()

    if choice.lower() == "p":
        pair_port = input(f"TV screen par dikha Pairing Port dalein (e.g. 37123): ").strip()
        pair_code = input(f"6-digit Pairing Code dalein: ").strip()
        pair_res = run_adb(adb, ["pair", f"{ip}:{pair_port}", pair_code])
        print(pair_res.stdout + pair_res.stderr)
        conn_port = input(f"Ab Wireless Debugging main screen wala Port dalein: ").strip()
        target = f"{ip}:{conn_port}"
    elif choice.isdigit():
        target = f"{ip}:{choice}"
    else:
        target = f"{ip}:5555"

    res = run_adb(adb, ["connect", target])
    print((res.stdout + res.stderr).strip())
    print("\n[!] Agar TV screen par 'Allow debugging' popup aaye toh Remote se 'Allow' dabayein.")
    input("Allow karne ke baad Enter dabayein...")
    return target


def main():
    print("========================================================")
    print("  VibeOS TV — Acer Google TV (Android 14) Auto-Revamp")
    print("  Target IP: 192.168.1.33")
    print("========================================================")

    parser = argparse.ArgumentParser()
    parser.add_argument("--ip", default=DEFAULT_TV_IP)
    parser.add_argument("--apk", default="app-debug.apk")
    parser.add_argument("--restore", action="store_true")
    args, _ = parser.parse_known_args()

    adb = ensure_adb()
    serial = auto_connect_tv(adb, args.ip)

    if args.restore:
        for pkg in STOCK_LAUNCHER_PACKAGES + SAFE_DEBLOAT_PACKAGES:
            run_adb(adb, ["shell", "pm", "enable", "--user", "0", pkg], serial=serial)
        run_adb(adb, ["shell", "settings", "put", "global", "window_animation_scale", "1.0"], serial=serial)
        run_adb(adb, ["shell", "settings", "put", "global", "transition_animation_scale", "1.0"], serial=serial)
        run_adb(adb, ["shell", "settings", "put", "global", "animator_duration_scale", "1.0"], serial=serial)
        print("\n[✓] Stock Google TV settings aur packages restore ho gaye!")
        return

    # 1. Backup packages
    res = run_adb(adb, ["shell", "pm", "list", "packages", "-e"], serial=serial)
    pkgs = {line.replace("package:", "").strip() for line in res.stdout.splitlines() if line.startswith("package:")}
    backup_file = Path(__file__).resolve().parent / "acer_tv_enabled_packages_backup.txt"
    backup_file.write_text("\n".join(sorted(pkgs)), encoding="utf-8")
    print(f"\n[1/4] Backed up {len(pkgs)} enabled packages to {backup_file.name}")

    # 2. 60fps Apple TV animation speed tweaks
    print("[2/4] Applying Apple TV-style 0.5x fast animation tweaks...")
    for key in ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"]:
        run_adb(adb, ["shell", "settings", "put", "global", key, "0.5"], serial=serial)

    # 3. Safe Debloat
    print("[3/4] Disabling Google TV ad services, partner bloat & telemetry...")
    for pkg in SAFE_DEBLOAT_PACKAGES:
        if pkg in pkgs:
            r = run_adb(adb, ["shell", "pm", "disable-user", "--user", "0", pkg], serial=serial)
            print(f"      [-] Disabled {pkg}: {(r.stdout + r.stderr).strip()}")

    # 4. Install APK if present & switch Home Launcher safely
    print("[4/4] Checking for Custom Launcher APK...")
    apk_candidate = Path(args.apk)
    if not apk_candidate.exists():
        apk_candidate = Path(__file__).resolve().parent / "app-debug.apk"
    if apk_candidate.exists():
        print(f"      [*] Installing {apk_candidate.name}...")
        r = run_adb(adb, ["install", "-r", str(apk_candidate)], serial=serial, timeout=60)
        print(f"      {(r.stdout + r.stderr).strip()}")

    check_res = run_adb(adb, ["shell", "pm", "list", "packages", CUSTOM_LAUNCHER_PKG], serial=serial)
    if CUSTOM_LAUNCHER_PKG in check_res.stdout:
        run_adb(adb, ["shell", "cmd", "role", "add-role-holder", "android.app.role.HOME", CUSTOM_LAUNCHER_PKG], serial=serial)
        run_adb(adb, ["shell", "cmd", "package", "set-home-activity", CUSTOM_LAUNCHER_ACTIVITY], serial=serial)
        for stock_pkg in STOCK_LAUNCHER_PACKAGES:
            run_adb(adb, ["shell", "pm", "disable-user", "--user", "0", stock_pkg], serial=serial)
        run_adb(adb, ["shell", "am", "start", "-n", CUSTOM_LAUNCHER_ACTIVITY], serial=serial)
        print("\n[✓] VibeOS Apple TV Launcher active ho gaya hai!")
    else:
        print("\n[✓] TV Debloat + 60fps Speed Tweaks complete ho gaye!")
        print("    (Stock launcher abhi चालू rakha gaya hai kyunki `app-debug.apk` is folder mein nahi mila).")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"\n[!] Error: {exc}")
    finally:
        print("\n--------------------------------------------------------")
        input("Window band karne ke liye Enter dabayein...")
